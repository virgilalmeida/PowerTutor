/*
Copyright (C) 2011 The University of Michigan

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Please send inquiries to powertutor@umich.edu
 */

package edu.umich.PowerTutor.service;

import android.content.Context;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.InflaterInputStream;

import edu.umich.PowerTutor.ui.UMLogger;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

/* This class is responsible for implementing all policy decisions on when to
 * send log information back to an external server.  Upon a successful call to
 * shouldUpload() PowerEstimator will then call upload(file).  After the call to
 * upload the file with the passed path may be overwritten.
 *
 * This is a stub implementation not supporting log uploading.
 */
public class LogUploader {

	Context context;
	boolean shouldUpload = false;

	public LogUploader(Context pcontext) {
		context = pcontext;
	}

	/* Returns true if this module supports uploading logs. */
	public static boolean uploadSupported() {
		return true;
	}

	/*
	 * Returns true if the log should be uploaded now. This may depend on log
	 * file size, network conditions, etc.
	 */
	// TODO: This should probably give the file name of the log
	public boolean shouldUpload() {
		return shouldUpload;
	}
	
	public void setshouldUpload(boolean shouldUpload){
		this.shouldUpload = shouldUpload;
	}

	/*
	 * Called when the device is plugged in or unplugged. The intended use of
	 * this is to improve upload policy decisions.
	 */
	public void plug(boolean plugged) {
	}

	/* Initiate the upload of the file with the passed location. */
	public void upload(String origFile) {
		new Thread() {
			public void start() {

				byte[] buffer = new byte[20480];
				
				try {

					// Save atual LOG
					File writeFile = new File(
							Environment.getExternalStorageDirectory(),
							"PowerTrace" + System.currentTimeMillis() + ".log");
					InflaterInputStream logIn = new InflaterInputStream(
							context.openFileInput("PowerTrace.log"));
					BufferedOutputStream logOut = new BufferedOutputStream(
							new FileOutputStream(writeFile));

					for (int ln = logIn.read(buffer); ln != -1; ln = logIn
							.read(buffer)) {
						logOut.write(buffer, 0, ln);
					}
					logIn.close();
					logOut.close();
				} catch (java.io.EOFException e) {

					return;
				} catch (IOException e) {
				}

				java.io.File sdcard;

				sdcard = Environment.getExternalStorageDirectory();

				String[] listFilestoUpload = sdcard.list();

				String fileName = "";

				HttpURLConnection conn = null;
				DataOutputStream dos = null;
				String lineEnd = "\r\n";
				String twoHyphens = "--";
				String boundary = "*****";
				int bytesRead, bytesAvailable, bufferSize;
				int maxBufferSize = 1 * 1024 * 1024;
				File sourceFile;
				int serverResponseCode = 0;
				FileInputStream fileInputStream;
				URL url;
				
				for (String sFile : listFilestoUpload) {
					if (sFile.startsWith("PowerTrace")) {
						
						fileName = Environment.getExternalStorageDirectory()+"/"+sFile;

						sourceFile = new File(fileName);

						if (sourceFile.isFile()) {
							try {

								// open a URL connection to the Servlet
								fileInputStream = new FileInputStream(
										sourceFile);
								url = new URL(UMLogger.SERVER_IP);

								// Open a HTTP connection to the URL
								conn = (HttpURLConnection) url.openConnection();
								conn.setDoInput(true); // Allow Inputs
								conn.setDoOutput(true); // Allow Outputs
								conn.setUseCaches(false); // Don't use a Cached
															// Copy
								conn.setRequestMethod("POST");
								conn.setRequestProperty("Connection",
										"Keep-Alive");
								conn.setRequestProperty("ENCTYPE",
										"multipart/form-data");
								conn.setRequestProperty("Content-Type",
										"multipart/form-data;boundary="
												+ boundary);
								conn.setRequestProperty("uploaded_file",
										fileName);

								dos = new DataOutputStream(
										conn.getOutputStream());

								dos.writeBytes(twoHyphens + boundary + lineEnd);
								dos.writeBytes("Content-Disposition: form-data; name=\"uploaded_file\";filename=\""
										+ fileName + "\"" + lineEnd);

								dos.writeBytes(lineEnd);

								// create a buffer of maximum size
								bytesAvailable = fileInputStream.available();

								bufferSize = Math.min(bytesAvailable,
										maxBufferSize);
								buffer = new byte[bufferSize];

								// read file and write it into form...
								bytesRead = fileInputStream.read(buffer, 0,
										bufferSize);

								while (bytesRead > 0) {

									dos.write(buffer, 0, bufferSize);
									bytesAvailable = fileInputStream
											.available();
									bufferSize = Math.min(bytesAvailable,
											maxBufferSize);
									bytesRead = fileInputStream.read(buffer, 0,
											bufferSize);

								}

								// send multipart form data necesssary after
								// file
								// data...
								dos.writeBytes(lineEnd);
								dos.writeBytes(twoHyphens + boundary
										+ twoHyphens + lineEnd);

								// Responses from the server (code and message)
								serverResponseCode = conn.getResponseCode();
								String serverResponseMessage = conn
										.getResponseMessage();

								Log.i("uploadFile", "HTTP Response is : "
										+ serverResponseMessage + ": "
										+ serverResponseCode);

								// close the streams //
								fileInputStream.close();
								dos.flush();
								dos.close();

							} catch (MalformedURLException ex) {

								// dialog.dismiss();
								ex.printStackTrace();

								Log.e("Upload file to server",
										"error: " + ex.getMessage(), ex);
							} catch (Exception e) {

								// dialog.dismiss();
								e.printStackTrace();

								Log.e("Upload file to server Exception",
										"Exception : " + e.getMessage(), e);
							}

							sourceFile.delete();
							
						} // End else block
					}
				}
			}
		}.start();
	}

	/* Returns true if a file is currently being uploaded. */
	public boolean isUploading() {
		return false;
	}

	/* Interrupt any threads doing upload work. */
	public void interrupt() {
	}

	/* Join any threads that may be performing log upload work. */
	public void join() throws InterruptedException {
	}
}
