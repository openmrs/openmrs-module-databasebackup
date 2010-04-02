/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.databasebackup.web.controller;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.databasebackup.util.Zip;
import org.openmrs.module.databasebackup.web.util.DbDump;
import org.openmrs.notification.Alert;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.SimpleFormController;

/**
 * This controller backs the /web/module/backupForm.jsp page. This controller is tied to that jsp
 * page in the /metadata/moduleApplicationContext.xml file
 */
public class BackupFormController extends SimpleFormController {
	
	/** Logger for this class and subclasses */
	protected final Log log = LogFactory.getLog(getClass());
	
	private Properties props;	
	private String filename;	
	private String folder;
	private UserContext ctx;
		
	// holds progress information of current dump thread
	private static Map<String,String> progressInfo = new HashMap<String,String>();
	
	/**
	 * Returns any extra data in a key-->value pair kind of way
	 * 
	 * @see org.springframework.web.servlet.mvc.SimpleFormController#referenceData(javax.servlet.http.HttpServletRequest,
	 *      java.lang.Object, org.springframework.validation.Errors)
	 */
	@Override
	protected Map<String, Object> referenceData(HttpServletRequest request, Object obj, Errors err) throws Exception {		
		// this method doesn't return any extra data right now, just an empty map
		return new HashMap<String, Object>();
	}
	
	/**
	 * @see org.springframework.web.servlet.mvc.SimpleFormController#onSubmit(javax.servlet.http.HttpServletRequest,
	 *      javax.servlet.http.HttpServletResponse, java.lang.Object,
	 *      org.springframework.validation.BindException)
	 */
	@Override
	protected ModelAndView onSubmit(HttpServletRequest request, HttpServletResponse response, Object object,
	                                BindException exceptions) throws Exception {
		
		String message;
		
		// if user clicked the backup execution button...
		if ("backup".equals(request.getParameter("act"))) {
			
			// set jdbc connection properties
			Properties prop = Context.getRuntimeProperties();			
			props = new Properties();
			props.setProperty("driver.class", "com.mysql.jdbc.Driver");
			props.setProperty("driver.url", prop.getProperty("connection.url"));
			props.setProperty("user", prop.getProperty("connection.username"));
			props.setProperty("password", prop.getProperty("connection.password"));			
			
			// tables to be in/exluded are also passed as properties to the db dump class  
			String tablesIncluded = (String) Context.getAdministrationService().getGlobalProperty("databasebackup.tablesIncluded", "all");
            String tablesExcluded = (String) Context.getAdministrationService().getGlobalProperty("databasebackup.tablesExcluded", "none");
			props.setProperty("tables.included", tablesIncluded==null?"":tablesIncluded);
            props.setProperty("tables.excluded", tablesExcluded==null?"":tablesExcluded);
			
			// read backup folder path from config and make it absolute
            folder = getAbsoluteBackupFolderPath();

            // check if backup path exists (sub folder by sub folder), otherwise create 
            String[] folderPath = folder.split( "\\" + System.getProperty("file.separator") ); 
            String s = folderPath[0];
            File f;            
            boolean success = true;            
            for (int i=1;i<=folderPath.length-1&&success;i++) {
				if (!"".equals(folderPath[i]))
					s += System.getProperty("file.separator") + folderPath[i];
            	f = new File(s);

            	System.out.println("check exit folder: " + s + ", " + f.exists());
            	
            	if ( !f.exists()) {
    				success = f.mkdir();
    			}
            	System.out.println("create folder: " + s + ", " + success);
            }
			if (!folder.endsWith("\\" + System.getProperty("file.separator")))
				folder += System.getProperty("file.separator");
            

            // if no problems occured with creating or finding the backup folder...
            if (success) {
            	            
				// create file name with timestamp
				filename = "openmrs.backup."
				        + new SimpleDateFormat("yyyy-MM-dd-HHmmss").format(Calendar.getInstance().getTime()) + ".sql";						
				
				props.setProperty("filename", filename);
				props.setProperty("folder", folder);
				
				// make ctx available for the thread
				ctx = Context.getUserContext();
				
				new Thread(new Runnable() {
					
					public void run() {
						try {
							UserContext ctxInThread = ctx;
							String filenameInThread = filename;
							DbDump.dumpDB(props);
							BackupFormController.getProgressInfo().put(filenameInThread, "Zipping file...");
							
							// zip sql file
							Zip.zip(folder, filenameInThread);
							
							// remove sql file after zipping it
							try {
								File f = new File(folder + filenameInThread);
								f.delete();
							} catch (SecurityException e) {
								log.error("Could not delete raw sql file.",e);
							}						
	
							BackupFormController.getProgressInfo().put(filenameInThread, "Backup complete.");
							
							Context.setUserContext(ctxInThread);
							Alert alert = new Alert("The backup file is ready at: " + folder + filenameInThread + ".zip", 
								Context.getUserContext().getAuthenticatedUser());
							Context.getAlertService().saveAlert(alert);
							
						}
						catch (Exception e) {
							System.err.println("Unable to backup database: " + e);
							log.error("Unable to backup database: ", e);
						}
					}
				}).start();
				
			}
			message = "<strong>Database is now being exported to file: " + folder + filename + ".zip"
			        + ".</strong><br/>This might take a few minutes, please be patient. You will be notified upon completion.";			
		} else {
			message = "<strong>Could not find or create the path to the backup folder: " + folder + ".</strong><br/>Please check or ask your system administrator for help.";
		}
		
		ModelAndView mv = new ModelAndView(getFormView());
		mv.addObject("fileId", filename);
		mv.addObject("msg", message);
		
		return mv;
		

	}
	
	/**
	 * 
	 * Makes eventual relative path to absolute path, based on OpenMRS app 
	 * data dir and returns it.
	 * 
	 * @return Absolute path to the backup folder
	 */
	private String getAbsoluteBackupFolderPath() {
		String folder;
		String appDataDir = OpenmrsUtil.getApplicationDataDirectory();
	    folder = (String) Context.getAdministrationService().getGlobalProperty("databasebackup.folderPath", "backup");            
	    if (folder.startsWith("./")) folder = folder.substring(2);
	    if (!folder.startsWith("/") && folder.indexOf(":")==-1) folder = appDataDir + folder;              
	    folder = folder.replaceAll( "/", "\\" + System.getProperty("file.separator"));
	    return folder;
	}
	
	
	public String getProgress(String filename) {
		return BackupFormController.getProgressInfo().get(filename)==null?"":(String)BackupFormController.getProgressInfo().get(filename);		
	}

	/**
	 * This class returns the form backing object. This can be a string, a boolean, or a normal java
	 * pojo. The type can be set in the /config/moduleApplicationContext.xml file or it can be just
	 * defined by the return type of this method
	 * 
	 * @see org.springframework.web.servlet.mvc.AbstractFormController#formBackingObject(javax.servlet.http.HttpServletRequest)
	 */
	@Override
	protected String formBackingObject(HttpServletRequest request) throws Exception {
		return "";
	}

	
    /**
     * @return the progressInfo
     */
    public static Map<String,String> getProgressInfo() {
    	return progressInfo;
    }

	
    /**
     * @param progressInfo the progressInfo to set
     */
    public static void setProgressInfo(Map<String,String> progressInfo) {
    	BackupFormController.progressInfo = progressInfo;
    }
	
}
