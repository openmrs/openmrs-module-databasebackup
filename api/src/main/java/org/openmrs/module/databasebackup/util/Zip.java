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

import java.io.*;
import java.util.zip.*;

/**
* Zip utility to compress a single file.
* 
* Author: Mathias Lin <mathias.lin@metahealthcare.com>
*/
public class Zip {
	
	static final int BUFFER = 2048;
	
	/**
	 * Compresses a given file that resides under path foldername filename.
	 * The compressed file will be stored in the same folder, the extension
	 * .zip added to it's filename.
	 * 
	 * @param folder Folder where the original file resides
	 * @param filename File name of the original uncompressed file
	 */
	public static void zip(String folder, String filename) {
		try {
			BufferedInputStream origin = null;
			FileOutputStream dest = new FileOutputStream(folder + filename + ".zip");
			ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
			byte data[] = new byte[BUFFER];			
			String files[] = { filename };
			
			for (int i = 0; i < files.length; i++) {
				System.out.println("Adding: " + files[i]);
				FileInputStream fi = new FileInputStream(folder + files[i]);
				origin = new BufferedInputStream(fi, BUFFER);
				ZipEntry entry = new ZipEntry(files[i]);
				out.putNextEntry(entry);
				int count;
				while ((count = origin.read(data, 0, BUFFER)) != -1) {
					out.write(data, 0, count);
				}
				origin.close();
			}
			out.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
