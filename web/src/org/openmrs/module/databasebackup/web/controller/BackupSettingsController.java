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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;
import org.springframework.web.servlet.view.RedirectView;

/**
 * This controller backs the /web/module/backupForm.jsp page. This controller is tied to that jsp
 * page in the /metadata/moduleApplicationContext.xml file
 *
 * Author: Mathias Lin <mathias.lin@metahealthcare.com>
 */
public class BackupSettingsController extends AbstractController {
	
	/** Logger for this class and subclasses */
	protected final Log log = LogFactory.getLog(getClass());

	private String successView;
	
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {
    	return new ModelAndView(new RedirectView(getSuccessView()));
    }
	
    /**
     * @return the successView
     */
    public String getSuccessView() {
    	return successView;
    }
	
    /**
     * @param successView the successView to set
     */
    public void setSuccessView(String successView) {
    	this.successView = successView;
    }
		
    
	
}
