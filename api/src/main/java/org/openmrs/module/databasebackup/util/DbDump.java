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
package org.openmrs.module.databasebackup.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

//import org.openmrs.module.databasebackup.web.controller.BackupFormController;

/**
 * This class connects to a database and dumps all the tables and contents out to stdout in the form of
 * a set of SQL executable statements
 *
 * Author: Mathias Lin <mathias.lin@metahealthcare.com>
 */
public class DbDump {

	/** Logger for this class and subclasses */
	protected final static Log log = LogFactory.getLog(DbDump.class);
	
	private static final String fileEncoding = "UTF8";
	
	private static final HashMap<String,String> sqlTokens;
	private static Pattern sqlTokenPattern;
	
	static
	{           
	    //MySQL escape sequences: http://dev.mysql.com/doc/refman/5.1/en/string-syntax.html
	    String[][] search_regex_replacement = new String[][]
	    {
	                //search string     search regex        sql replacement regex
	            {   "\u0000"    ,       "\\x00"     ,       "\\\\0"     },
	            {   "'"         ,       "'"         ,       "\\\\'"     },
	            {   "\""        ,       "\""        ,       "\\\\\""    },
	            {   "\b"        ,       "\\x08"     ,       "\\\\b"     },
	            {   "\n"        ,       "\\n"       ,       "\\\\n"     },
	            {   "\r"        ,       "\\r"       ,       "\\\\r"     },
	            {   "\t"        ,       "\\t"       ,       "\\\\t"     },
	            {   "\u001A"    ,       "\\x1A"     ,       "\\\\Z"     },
	            {   "\\"        ,       "\\\\"      ,       "\\\\\\\\"  }
	    };

	    sqlTokens = new HashMap<String,String>();
	    String patternStr = "";
	    for (String[] srr : search_regex_replacement)
	    {
	        sqlTokens.put(srr[0], srr[2]);
	        patternStr += (patternStr.isEmpty() ? "" : "|") + srr[1];            
	    }
	    sqlTokenPattern = Pattern.compile('(' + patternStr + ')');
	}
	
	private static String escape(String s)
	{
	    Matcher matcher = sqlTokenPattern.matcher(s);
	    StringBuffer sb = new StringBuffer();
	    while(matcher.find())
	    {
	        matcher.appendReplacement(sb, sqlTokens.get(matcher.group(1)));
	    }
	    matcher.appendTail(sb);
	    return sb.toString();
	}
	
    /** Dump the whole database to an SQL string */
    public static void dumpDB(Properties props, boolean showProgress, Class showProgressToClass) throws Exception {
    	String filename = props.getProperty("filename");
    	String folder= props.getProperty("folder");
        String driverClassName = props.getProperty("driver.class");
        String driverURL = props.getProperty("driver.url");
        DatabaseMetaData dbMetaData = null;
        Connection dbConn = null;

        Class.forName(driverClassName);
        dbConn = DriverManager.getConnection(driverURL, props);
        dbMetaData = dbConn.getMetaData();
        
        FileOutputStream fos = new FileOutputStream(folder + filename);        
        OutputStreamWriter result = new OutputStreamWriter(fos, fileEncoding);            
        
        String catalog = props.getProperty("catalog");
        String schema = props.getProperty("schemaPattern");
        
        String tablesIncluded = props.getProperty("tables.included");
        List<String> tablesIncludedVector = Arrays.asList(tablesIncluded.split(","));

        String tablesExcluded = props.getProperty("tables.excluded");
        List<String> tablesExcludedVector = Arrays.asList(tablesExcluded.split(","));

        ResultSet rs = dbMetaData.getTables(catalog, schema, null, null);
        int progressCnt = 0;

        log.debug("tablesIncluded: " + tablesIncluded);
        log.debug("tablesExcluded: " + tablesExcluded);

        result.write( "/*\n" + 
        		" * DB jdbc url: " + driverURL + "\n" +
        		" * Database product & version: " + dbMetaData.getDatabaseProductName() + " " + dbMetaData.getDatabaseProductVersion() + "\n" +
        		" */"
        		);                                   
        
        result.write("\nSET FOREIGN_KEY_CHECKS=0;\n");
        
        List<String> tableVector = new Vector<String>();
        int progressTotal = 0;
        while(rs.next()) {
            String tableName = rs.getString("TABLE_NAME");
            if (
                    ( tablesIncluded.contains("all")&&!tablesExcludedVector.contains(tableName)||tablesIncluded.contains(tableName) )
                    || ( tablesExcludedVector.contains("none")&&!tablesIncludedVector.contains("none") )
                    ) {
                progressTotal++;
                tableVector.add(tableName);
            }                
        }
        rs.beforeFirst();
        
        if (! rs.next()) {
            log.error("Unable to find any tables matching: catalog="+catalog+" schema=" + schema + " tables=" + tableVector.toArray().toString());
            rs.close();
        } else {
            do {
                String tableName = rs.getString("TABLE_NAME");                    
                String tableType = rs.getString("TABLE_TYPE");
                
                if (tableVector.contains(tableName)) {

                	progressCnt++;
                	//BackupFormController.getProgressInfo().put(filename, "Backing up table " + progressCnt + " of " + progressTotal + " (" + tableName + ")...");

                    if (showProgress) {
                        Map<String,String> info = (Map<String,String>)showProgressToClass.getMethod("getProgressInfo", null).invoke(showProgressToClass);
                        info.put(filename, "Backing up table " + progressCnt + " of " + progressTotal + " (" + tableName + ")...");
                        showProgressToClass.getMethod("setProgressInfo", new Class[]{Map.class}).invoke(showProgressToClass, info);
                    }

                    if ("TABLE".equalsIgnoreCase(tableType)) {

                    	result.write( "\n\n-- Structure for table `" + tableName + "`\n" );
                    	result.write( "DROP TABLE IF EXISTS `"+tableName+"`;\n" );
                    	
                    	PreparedStatement tableStmt = dbConn.prepareStatement("SHOW CREATE TABLE "+ tableName +";");
                    	ResultSet tablesRs = tableStmt.executeQuery();
                    	while (tablesRs.next()) {
                    		result.write(tablesRs.getString("Create Table") + ";\n\n");	
                    	}
                    	tablesRs.close();
                    	tableStmt.close();                    	

                        dumpTable(dbConn, result, tableName);
                        System.gc();
                    }
                }
            } while (rs.next());
            rs.close();
        }
        
        result.write("\nSET FOREIGN_KEY_CHECKS=1;\n");
        
        result.flush();
        result.close();
        
        dbConn.close();       
    }

    /** dump this particular table to the string buffer */
    private static void dumpTable(Connection dbConn, OutputStreamWriter result, String tableName) {
        try {
            // Rows per insert
            int max = 10000;
            // Get total number of rows for table
            Statement s = dbConn.createStatement();
            ResultSet r = s.executeQuery("SELECT COUNT(*) AS rowcount FROM " + tableName);
            r.next();
            int count = r.getInt("rowcount");
            r.close();

            // Variables for dividing rows

            int startIndex = 0;
            boolean notProcessed = true;
            int offset = 0;

            result.write("\n\n-- Data for table '" + tableName + "'\n");

            while (notProcessed) {

                PreparedStatement stmt = dbConn.prepareStatement("SELECT * FROM " + tableName + " LIMIT " + String.valueOf(offset) + ", " + String.valueOf(max) + ";");
                ResultSet rs = stmt.executeQuery();
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                String dataHeaders = "(" + metaData.getColumnName(1);
                for (int i = 2; i <= columnCount; i++) {
                    dataHeaders += "," + metaData.getColumnName(i);
                }
                dataHeaders += ")";

                // Check that row exist before insert statement can be created
                if(rs.isBeforeFirst()) {
                    result.write("INSERT INTO " + tableName + " " + dataHeaders + " VALUES ");
                }
                // data inserts
                while (rs.next()) {

                    result.write("(");
                    for (int i = 0; i < columnCount; i++) {
                        if (i > 0) {
                            result.write(", ");
                        }
                        Object value = rs.getObject(i + 1);
                        if (value == null) {
                            result.write("NULL");
                        } else {
                            String outputValue = value.toString();
                            if (value instanceof Boolean) {
                                outputValue = (((Boolean) value) ? "1" : "0");
                            }
                            outputValue = escape(outputValue);
                            result.write("'" + outputValue + "'");
                        }
                    }
                    if (rs.isLast()) {
                        result.write(");\n");
                    } else {
                        result.write("),");
                    }
                }
                rs.close();
                stmt.close();

                if (offset >= count || count <= max) {
                    notProcessed = false;
                } else {
                    startIndex = startIndex + 1;
                }
                offset = (startIndex * max);
            }
            // Construct columns
        } catch (SQLException e) {
            log.error("Unable to dump table " + tableName + ".  " + e);
        } catch (IOException e) {
            log.error("Unable to dump table " + tableName + ".  " + e);
        }
    }
}
