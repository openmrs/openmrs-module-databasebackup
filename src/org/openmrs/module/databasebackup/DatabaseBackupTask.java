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
package org.openmrs.module.databasebackup;

import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.databasebackup.util.DbDump;
import org.openmrs.module.databasebackup.util.Zip;
import org.openmrs.notification.Alert;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.openmrs.util.OpenmrsUtil;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;
import java.util.Properties;


public class DatabaseBackupTask extends AbstractTask {
    	
	private Properties props;
	private String filename;
	private String folder;
	private UserContext ctx;

    public void execute() {
        Context.openSession();
        if (!Context.isAuthenticated()) {
           authenticate();
        }
        
        // create file name with timestamp
        filename = "openmrs.backup."
                + new SimpleDateFormat("yyyy-MM-dd-HHmmss").format(Calendar.getInstance().getTime()) + ".sql";

        // do backup without process notification (no controller class passed)
        handleBackup(filename, false, null, taskDefinition.getProperty("tablesExcluded"), taskDefinition.getProperty("tablesIncluded"));
        Context.closeSession();
    }


    public void handleBackup(final String filename, final boolean showProgress, final Class showProgressToClass, String overridenTablesExcluded, String overridenTablesIncluded) {

        System.out.println("=========================== handleBackup( " + filename + "," + showProgress + "," + showProgressToClass + "===================");

        // set jdbc connection properties
        props = new Properties();
        props.setProperty("driver.class", "com.mysql.jdbc.Driver");
        props.setProperty("driver.url", Context.getRuntimeProperties().getProperty("connection.url"));
        props.setProperty("user", Context.getRuntimeProperties().getProperty("connection.username"));
        props.setProperty("password", Context.getRuntimeProperties().getProperty("connection.password"));

        // tables to be in/exluded are also passed as properties to the db dump class
        String tablesIncluded = (String) Context.getAdministrationService().getGlobalProperty("databasebackup.tablesIncluded", "all");
        String tablesExcluded = (String) Context.getAdministrationService().getGlobalProperty("databasebackup.tablesExcluded", "none");
        if (overridenTablesExcluded!=null && !"".equals(overridenTablesExcluded)) {
            props.setProperty("tables.excluded", overridenTablesExcluded);
        } else {
            props.setProperty("tables.excluded", tablesExcluded==null?"":tablesExcluded);
        }

        if (overridenTablesExcluded!=null && !"".equals(overridenTablesIncluded)) {
            props.setProperty("tables.included", overridenTablesIncluded);
        } else {
            props.setProperty("tables.included", tablesIncluded==null?"":tablesIncluded);
        }

        // read backup folder path from config and make it absolute
        folder = getAbsoluteBackupFolderPath();

        // check if backup path exists (sub folder by sub folder), otherwise create
        boolean success = checkFolderPath(folder);

        /*String[] folderPath = folder.split( "\\" + System.getProperty("file.separator") );
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
            folder += System.getProperty("file.separator");*/


        // if no problems occured with creating or finding the backup folder...
        if (success) {

            // create file name with timestamp
//            filename = "openmrs.backup."
//                    + new SimpleDateFormat("yyyy-MM-dd-HHmmss").format(Calendar.getInstance().getTime()) + ".sql";

            props.setProperty("filename", filename);
            props.setProperty("folder", folder);

            // make ctx available for the thread
            ctx = Context.getUserContext();

            new Thread(new Runnable() {

                public void run() {
                    try {
                        UserContext ctxInThread = ctx;
                        String filenameInThread = filename;
                        // DbDump.dumpDB(props, false, null);

                        DbDump.dumpDB(props, showProgress, showProgressToClass);

                        // BackupFormController.getProgressInfo().put(filenameInThread, "Zipping file...");
                        if (showProgress) {
                            try {
                                Map<String,String> info = (Map<String,String>)showProgressToClass.getMethod("getProgressInfo", new Class[]{}).invoke(showProgressToClass, new Object[]{});
                                System.out.println("*** info "+info);
                                info.put(filenameInThread, "Zipping file...");
                                showProgressToClass.getMethod("setProgressInfo", new Class[]{Map.class}).invoke(showProgressToClass, info);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        // zip sql file
                        Zip.zip(folder, filenameInThread);

                        // remove sql file after zipping it
                        try {
                            File f = new File(folder + filenameInThread);
                            f.delete();
                        } catch (SecurityException e) {
                            // log.error("Could not delete raw sql file.",e);
                        }

                        // BackupFormController.getProgressInfo().put(filenameInThread, "Backup complete.");
                        if (showProgress) {
                            try {
                                Map<String,String> info = (Map<String,String>)showProgressToClass.getMethod("getProgressInfo", null).invoke(showProgressToClass,new Object[]{});
                                System.out.println("*** info "+info);
                                info.put(filenameInThread, "Backup complete.");
                                showProgressToClass.getMethod("setProgressInfo",new Class[]{Map.class}).invoke(showProgressToClass, info);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        
                        Context.setUserContext(ctxInThread);
                        Alert alert = new Alert("The backup file is ready at: " + folder + filenameInThread + ".zip",
                            Context.getUserContext().getAuthenticatedUser());
                        Context.getAlertService().saveAlert(alert);

                    }
                    catch (Exception e) {
                        System.err.println("Unable to backup database: " + e);
                        e.printStackTrace();
                        // log.error("Unable to backup database: ", e);
                    }
                }
            }).start();
        }
    }





	/**
	 *
	 * Makes eventual relative path to absolute path, based on OpenMRS app
	 * data dir and returns it.
	 *
	 * @return Absolute path to the backup folder
	 */
	public static String getAbsoluteBackupFolderPath() {
		String folder;
		String appDataDir = OpenmrsUtil.getApplicationDataDirectory();
	    folder = (String) Context.getAdministrationService().getGlobalProperty("databasebackup.folderPath", "backup");
	    if (folder.startsWith("./")) folder = folder.substring(2);
	    if (!folder.startsWith("/") && folder.indexOf(":")==-1) folder = appDataDir + folder;
	    folder = folder.replaceAll( "/", "\\" + System.getProperty("file.separator"));
	    return folder;
	}

    private static boolean checkFolderPath(String folder) {
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
        return success;
    }

}
