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
package org.openmrs.module.databasebackup.web.util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.databasebackup.web.controller.BackupFormController;

/**
 * This class connects to a database and dumps all the tables and contents out to stdout in the form of
 * a set of SQL executable statements
 */
public class DbDump {

	/** Logger for this class and subclasses */
	protected final static Log log = LogFactory.getLog(DbDump.class);
	
    /** Dump the whole database to an SQL string */
    public static void dumpDB(Properties props) {
    	String filename = props.getProperty("filename");
    	String folder= props.getProperty("folder");
        String driverClassName = props.getProperty("driver.class");
        String driverURL = props.getProperty("driver.url");
        // Default to not having a quote character
        String columnNameQuote = props.getProperty("columnName.quoteChar", "");
        DatabaseMetaData dbMetaData = null;
        Connection dbConn = null;
        try {
            Class.forName(driverClassName);
            dbConn = DriverManager.getConnection(driverURL, props);
            dbMetaData = dbConn.getMetaData();
        }
        catch( Exception e ) {
            System.err.println("Unable to connect to database: "+e);
            //return null;
        }

        try {
            FileOutputStream result = new FileOutputStream(folder + filename);
                        
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
                    	BackupFormController.getProgressInfo().put(filename, "Backing up table " + progressCnt + " of " + progressTotal + " (" + tableName + ")...");                    	
                    	
	                    if ("TABLE".equalsIgnoreCase(tableType)) {

	                    	result.write( ("\n\n-- "+tableName).getBytes() );
	                    	result.write( ("\nCREATE TABLE "+tableName+" (\n").getBytes() );
	                        ResultSet tableMetaData = dbMetaData.getColumns(null, null, tableName, "%");	                        
	                        boolean firstLine = true;
	                        while (tableMetaData.next()) {
	                            if (firstLine) {
	                                firstLine = false;
	                            } else {
	                                // If not the first line, then finish the previous line with a comma
	                            	result.write( (",\n").getBytes() );
	                            }
	                            String columnName = tableMetaData.getString("COLUMN_NAME");
	                            String columnType = tableMetaData.getString("TYPE_NAME");
	                            // WARNING: this may give daft answers for some types on some databases (eg JDBC-ODBC link)
	                            int columnSize = tableMetaData.getInt("COLUMN_SIZE");
	                            String nullable = tableMetaData.getString("IS_NULLABLE");
	                            String nullString = "NULL";
	                            if ("NO".equalsIgnoreCase(nullable)) {
	                                nullString = "NOT NULL";
	                            }
	                            result.write( ("    "+columnNameQuote+columnName+columnNameQuote+" "+columnType+" ("+columnSize+")"+" "+nullString).getBytes() );
	                        }
	                        tableMetaData.close();
	
	                        // Now we need to put the primary key constraint
	                        try {
	                            ResultSet primaryKeys = dbMetaData.getPrimaryKeys(catalog, schema, tableName);
	                            String primaryKeyName = null;
	                            StringBuffer primaryKeyColumns = new StringBuffer();
	                            while (primaryKeys.next()) {
	                                String thisKeyName = primaryKeys.getString("PK_NAME");
	                                if ((thisKeyName != null && primaryKeyName == null)
	                                        || (thisKeyName == null && primaryKeyName != null)
	                                        || (thisKeyName != null && ! thisKeyName.equals(primaryKeyName))
	                                        || (primaryKeyName != null && ! primaryKeyName.equals(thisKeyName))) {
	                                    if (primaryKeyColumns.length() > 0) {
	                                        result.write(",\n    PRIMARY KEY ".getBytes());
	                                        if (primaryKeyName != null) { result.write(primaryKeyName.getBytes()); }
	                                        result.write( ("("+primaryKeyColumns.toString()+")").getBytes() );
	                                    }
	                                    // Start again with the new name
	                                    primaryKeyColumns = new StringBuffer();
	                                    primaryKeyName = thisKeyName;
	                                }
	                                // Now append the column
	                                if (primaryKeyColumns.length() > 0) {
	                                    primaryKeyColumns.append(", ");
	                                }
	                                primaryKeyColumns.append(primaryKeys.getString("COLUMN_NAME"));
	                            }
	                            if (primaryKeyColumns.length() > 0) {
	                                result.write(",\n    PRIMARY KEY ".getBytes());
	                                if (primaryKeyName != null) { result.write(primaryKeyName.getBytes()); }
	                                result.write( (" ("+primaryKeyColumns.toString()+")").getBytes() );
	                            }
	                            primaryKeys.close();
	                        } catch (SQLException e) {
	                        	log.error("Unable to get primary keys for table "+tableName+".", e);
	                        }

	                        result.write("\n);\n".getBytes());

	                        dumpTable(dbConn, result, tableName);
	                        System.gc();
	                    }
                    }
                } while (rs.next());
                rs.close();
            }
            
            result.flush();
            result.close();
            
            dbConn.close();
            //return result.toString();
        } catch (SQLException e) {
            log.error("An error occured. ", e);  //To change body of catch statement use Options | File Templates.
        } catch (IOException e) {
            log.error("An error occured. ", e);  //To change body of catch statement use Options | File Templates.
        }
        //return null;
    }

    /** dump this particular table to the string buffer */
    //private static void dumpTable(Connection dbConn, StringBuffer result, String tableName) {
	private static void dumpTable(Connection dbConn, FileOutputStream result, String tableName) {
        try {
            // create table sql
            PreparedStatement stmt = dbConn.prepareStatement("SELECT * FROM "+tableName);
            ResultSet rs = stmt.executeQuery();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // data inserts
            result.write( ("\n\n-- Data for "+tableName+"\n").getBytes() );
            while (rs.next()) {
                result.write( ("INSERT INTO "+tableName+" VALUES (").getBytes() );
                for (int i=0; i<columnCount; i++) {
                    if (i > 0) {
                        result.write(", ".getBytes());
                    }
                    Object value = rs.getObject(i+1);
                    if (value == null) {
                        result.write("NULL".getBytes());
                    } else {
                        String outputValue = value.toString();
                        outputValue = outputValue.replaceAll("'","\\'");
                        result.write( ("'"+outputValue+"'").getBytes() );
                    }
                }
                result.write(");\n".getBytes());
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            log.error("Unable to dump table "+tableName+".  "+e);
        } catch (IOException e) {
            log.error("Unable to dump table "+tableName+".  "+e);
        }
    }	


}
