/*
 * Copyright 2011 Robert Theis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *			http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.molatra.ocrtest;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.zip.GZIPInputStream;

import org.xeustechnologies.jtar.TarEntry;
import org.xeustechnologies.jtar.TarInputStream;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.molatra.ocrtest.utils.LanguageCodeHelper;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Checks if the phone contains language data and orientation-and-script detection (OSD) data required for OCR.
 * Downloads the necessary language data and orientation-and-script detection (OSD) data. 
 * Initializes the OCR engine using a background thread.
 */
class OcrInitAsyncTask extends AsyncTask<String, String, Boolean> {

	/** Resource to use for data file downloads. */
	static final String OSD_CODE = "osd";
	
	static final String TESSDATA_FOLDERNAME = "tessdata";
	static final String BASE_URL = "http://tesseract-ocr.googlecode.com/files/";

	
	private static final String TAG = "OcrInitAsyncTask";
	private final String languageCode;
	private File storageRoot;
	private Context context;
	private TessBaseAPI baseApi;
	private OCRService service;
	private boolean allowDownload;

	/**
	 * AsyncTask to asynchronously download data and initialize Tesseract.
	 * @param activity				The calling activity
	 * @param baseApi				API to the OCR engine
	 * @param dialog				Dialog box with thermometer progress indicator
	 * @param indeterminateDialog	Dialog box with indeterminate progress indicator
	 * @param languageCode			ISO 639-2 OCR language code
	 * @param languageName			Name of the OCR language, for example, "English"
	 * @param ocrEngineMode			Whether to use Tesseract, Cube, or both
	 */
	OcrInitAsyncTask(OCRService service, TessBaseAPI baseApi, String languageCode, File storageRoot) {
		this.service = service;
		this.context = service.getBaseContext();
		this.baseApi = baseApi;
		this.storageRoot = storageRoot;
		this.languageCode = languageCode;
		this.allowDownload = true;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
	}

	/**
	 * In background thread, perform required setup, and request initialization of
	 * the OCR engine.
	 * Example Cube data filename: "tesseract-ocr-3.01.eng.tar" (Note: not using Cube files in this app)
	 * Example Tesseract data filename: "eng.traineddata"
	 * 
	 * @param params	[0] Pathname for the directory for storing language data files to the SD card
	 */
	protected Boolean doInBackground(String... params) {

		String language = LanguageCodeHelper.getOcrLanguageName(this.context, languageCode);
		
		// Inform the service that we're done downloading (use -1 for indeterminate progress)
		publishProgress(this.context.getString(R.string.init_verifying) + " " + language + "...", "-1");
		
		// Check for, and create if necessary, folder to hold model data
		File tessDataFolder = new File(storageRoot, TESSDATA_FOLDERNAME);
		if (!tessDataFolder.exists() && !tessDataFolder.mkdirs()) {
			Log.e(TAG, "Couldn't make directory " + tessDataFolder);
			return false;
		}
		
		// Verify installation of OSD files. Clean up partial data downloads. Download and install if not already installed.
		boolean dataInstalled = verifyInstallation(tessDataFolder, OSD_CODE) 
				|| (allowDownload && downloadAndInstall(tessDataFolder, OSD_CODE));
		
		// Verify installation of language data. Clean up partial data downloads. Download and install if not already installed.
		dataInstalled = dataInstalled && (verifyInstallation(tessDataFolder, languageCode)
				|| (allowDownload && downloadAndInstall(tessDataFolder,  languageCode)));
		
		// Inform the service that we're done verifying installation (use -1 for indeterminate progress)
		if(dataInstalled){
			publishProgress(this.context.getString(R.string.init_initializing) + " " + language + "...", "-1");
		} else {
			publishProgress(this.context.getString(R.string.init_verification_failed), "-1");
		}

		// Initialize the OCR engine
		return dataInstalled && (baseApi.init(storageRoot + File.separator, languageCode));
		
	}

	/**
	 * Update the dialog box with the latest incremental progress.
	 * 
	 * @param message[0] Text to be displayed
	 * @param message[1] Text to be displayed
	 */
	@Override protected void onProgressUpdate(String... message) {
		super.onProgressUpdate(message);
		int percentComplete = 0;

		if(message.length > 1){
			try{ 
				percentComplete = Integer.parseInt(message[1]);	
			}catch (NumberFormatException e){ 
				// Ignore number format exceptions 
			}
		}
		
		// Inform the service of what we're doing (use -1 for indeterminate progress)
		service.onAsyncProgress(message[0], percentComplete);
	}

	@Override protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);

		Log.v(TAG, "OcrInitAsyncTask is done executing.");
		
		// Inform the service of what we're doing (use -1 for indeterminate progress)
		if(result)
			service.onAsyncProgress(this.context.getString(R.string.init_initialization_success), -1);
		else
			service.onAsyncProgress(this.context.getString(R.string.init_initialization_failed), -1);
	}
	
	/**
	 * Verify the installation/existence of the given dataFileName
	 * First the method deletes any partial downloads (files ending in ".download") and (assumed) partially extracted files. 
	 * 
	 * @param tessDataFolder	Name of folder where the file should be installed.
	 * @param dataFileName		Name of file that should be installed.
	 * @return True				if the file exists 
	 */
	public static boolean verifyInstallation(File tessDataFolder, String code){
		
		// Check for and delete incomplete data. Note that a file ending in ".download" is deemed to be incomplete
		String dataFileName = getDataFileName(code);
		String[] badFiles = { getTempDownloadName(code), getTempUnzippedName(code), dataFileName };
		File badFile = new File(tessDataFolder, badFiles[0]);
		
		// If the ".download" file exists, that means we have the remnants of a partial download and should delete the rest
		if(badFile.exists()){
			for (String filename : badFiles) {
				badFile = new File(tessDataFolder, filename);
				if (badFile.exists()) { badFile.delete(); }
			}
		}	
		
		// If the tesseract data file exists, we can assume that the file is installed
		boolean dataInstalled = new File (tessDataFolder, dataFileName).exists();
		if (!dataInstalled) {
			// TODO: Consider installing from assets.
			Log.e(TAG, "Data for " + dataFileName + " not found in " + tessDataFolder.toString());
		} else {
			Log.v(TAG, "Data for " + dataFileName + " installed in " + tessDataFolder.toString());
		}
		
		return dataInstalled;
	}
	
	private boolean downloadAndInstall(File tessDataFolder, String code) {
		
		// We start with files not being installed.
		boolean installSuccess = false;
		
		try {
			// Get the final filename
			String dataFilename = getDataFileName(code);
			File dataFile = new File(tessDataFolder, dataFilename);
			if(dataFile.exists()) { dataFile.delete(); }
			
			// Download the data to downloaded file
			File downloadedFile = new File (tessDataFolder, getTempDownloadName(code));
			installSuccess = downloadFileHttp(getDownloadURL(code), downloadedFile);
			
			// Gunzip the downloaded file to a tar file
			File unzippedFile = new File(tessDataFolder, getTempUnzippedName(code));
			installSuccess = installSuccess && gunzip(downloadedFile, unzippedFile);	
			
			// Delete the downloaded file
			if(installSuccess && downloadedFile.exists()){ downloadedFile.delete(); }

			// make sure that we should untar
			if(installSuccess && shouldUntarDownloadedFile(code)) {
				
				// Untar the file if necessary
				installSuccess = installSuccess && untar(unzippedFile, tessDataFolder);
				
				// delete the tar file
				if(installSuccess && unzippedFile.exists()){ unzippedFile.delete(); }
			} else {
				// if we don't need to untar the file, just rename it to the data file 
				installSuccess = installSuccess && unzippedFile.renameTo(dataFile);
			}
			
		} catch (MalformedURLException e) {
			Log.e(TAG, "MalformedURLException received in doInBackground. ");
			installSuccess = false;
		} catch (FileNotFoundException e) {
			Log.e(TAG, "FileNotFoundException received in doInBackground. ");
			installSuccess = false;
		} catch (SocketTimeoutException e) {
			Log.e(TAG, "SocketTimeoutException received in doInBackground. Could not connect to server.");
			installSuccess = false;
		} catch (IOException e) {
			Log.e(TAG, "IOException received in doInBackground. Is a network connection available?");
			installSuccess = false;
		}
		
		Log.d(TAG, "Install success is " + installSuccess);
		
		return installSuccess;
	}

	/**
	 * Download a file using an HttpURLConnection to the given destination file. 
	 * 
	 * @param url				URL to download from
	 * @param destinationFile	File to save the download as, including path
	 * @return True 			if response received, destinationFile opened, and unzip successful
	 * @throws SocketTimeoutException
	 * @throws IOException
	 */
	private boolean downloadFileHttp(URL url, File destinationFile) throws SocketTimeoutException, IOException {
		
		// Send an HTTP GET request for the file
		Log.v(TAG, "Sending GET request to " + url + "...");
		publishProgress(this.context.getString(R.string.init_connecting_to) + " tesseract-ocr.googlecode.com ...", "0");
		HttpURLConnection urlConnection = null;
		urlConnection = (HttpURLConnection) url.openConnection();
		urlConnection.setAllowUserInteraction(false);
		urlConnection.setInstanceFollowRedirects(true);
		urlConnection.setRequestMethod("GET");
		urlConnection.setConnectTimeout(15000);
		urlConnection.connect();
		if (urlConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
			Log.e(TAG, "Did not get HTTP_OK response.");
			Log.e(TAG, "Response code: " + urlConnection.getResponseCode());
			Log.e(TAG, "Response message: " + urlConnection.getResponseMessage().toString());
			return false;
		}
		int fileSize = urlConnection.getContentLength();
		InputStream inputStream = urlConnection.getInputStream();

		// Stream the file contents to the destinationFile
		Log.v(TAG, "Streaming download to " + destinationFile.toString() + ".");
		final int BUFFER = 8192;
		FileOutputStream fileOutputStream = null;
		Integer percentComplete;
		int percentCompleteLast = 0;
		try {
			fileOutputStream = new FileOutputStream(destinationFile);
			int downloaded = 0;
			byte[] buffer = new byte[BUFFER];
			int bufferLength = 0;
			while (!isCancelled() && (bufferLength = inputStream.read(buffer, 0, BUFFER)) > 0) {
				fileOutputStream.write(buffer, 0, bufferLength);
				downloaded += bufferLength;
				percentComplete = (int) ((downloaded / (float) fileSize) * 100);
				if (percentComplete > percentCompleteLast) {
					publishProgress(this.context.getString(R.string.init_downloading) 
							+ " " + destinationFile.getName(), percentComplete.toString());
					percentCompleteLast = percentComplete;
				}
			}

		} catch (FileNotFoundException e) {
			Log.e(TAG, "Exception received when opening FileOutputStream.", e);
		}
		
		if(isCancelled()){ 
			Log.e(TAG, "Download was cancelled!!");
		} else { 
			Log.d(TAG, "Download not cancelled!!"); 
		}
		
		fileOutputStream.close();
		if (urlConnection != null) {
			urlConnection.disconnect();
		}

		return !isCancelled();
	}

	/**
	 * Unzips the given Gzipped file to the given destination. Does not delete the original gzipped file.
	 * 
	 * @param zippedFile 	The gzipped file to be uncompressed
	 * @param outFilePath	File to unzip to, including path
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private boolean gunzip(File zippedFile, File outFilePath) throws FileNotFoundException, IOException {
		
		// Get the total size of the uncompressed file
		Log.v(TAG, "Gunzip...");
		int uncompressedFileSize = getGzipSizeUncompressed(zippedFile);
		Integer percentComplete;
		int percentCompleteLast = 0;
		int unzippedBytes = 0;
		final Integer progressMin = 0;
		int progressMax = 100 - progressMin;
		publishProgress(this.context.getString(R.string.init_extracting) 
				+ " " + zippedFile.getName(), progressMin.toString());

		// Create input and output streams
		GZIPInputStream gzipInputStream = new GZIPInputStream(new BufferedInputStream(new FileInputStream(zippedFile)));
		OutputStream outputStream = new FileOutputStream(outFilePath);
		BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);

		// Stream the contents of the zipped file to the output file
		final int BUFFER = 8192;
		byte[] data = new byte[BUFFER];
		int len;
		while (!isCancelled() && (len = gzipInputStream.read(data, 0, BUFFER)) > 0) {
			bufferedOutputStream.write(data, 0, len);
			unzippedBytes += len;
			percentComplete = (int) ((unzippedBytes / (float) uncompressedFileSize) * progressMax) + progressMin;

			if (percentComplete > percentCompleteLast) {
				publishProgress(this.context.getString(R.string.init_extracting) 
						+ " " + zippedFile.getName(), percentComplete.toString());
				percentCompleteLast = percentComplete;
			}
		}
		
		// Close the streams
		gzipInputStream.close();
		bufferedOutputStream.flush();
		bufferedOutputStream.close();
		
		return !isCancelled();
	}


	/**
	 * Untar the contents of a tar file into the given directory, ignoring the relative pathname in the tar file.
	 * Does not delete the original tar file.
	 * 
	 * Uses jtar: http://code.google.com/p/jtar/
	 * 
	 * @param tarFile			The tar file to be untarred
	 * @param destinationDir	The directory to untar into
	 * @return True				If the file was extracted 
	 * 							(technically always turns true, unless it throws an error during the extraction process)
	 * @throws IOException
	 */
	private boolean untar(File tarFile, File destinationDir) throws IOException {

		// Get the total size of the uncompressed file
		Log.v(TAG, "Untarring...");
		final int uncompressedSize = getTarSizeUncompressed(tarFile);
		Integer percentComplete;
		int percentCompleteLast = 0;
		int unzippedBytes = 0;
		final Integer progressMin = 50;
		final int progressMax = 100 - progressMin;
		publishProgress(this.context.getString(R.string.init_installing) 
				+ " " + tarFile.getName(), progressMin.toString());

		// Extract all the files
		TarInputStream tarInputStream = new TarInputStream(new BufferedInputStream(new FileInputStream(tarFile)));
		TarEntry entry;
		while (!isCancelled() && (entry = tarInputStream.getNextEntry()) != null) {
			// Get the next entry in the tar
			int len;
			final int BUFFER = 8192;
			byte data[] = new byte[BUFFER];
			String pathName = entry.getName();
			String fileName = pathName.substring(pathName.lastIndexOf('/'), pathName.length());
			
			// Create and output file in the destination directory
			OutputStream outputStream = new FileOutputStream(destinationDir + fileName);
			BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);

			// Write to the output file
			while (!isCancelled() && (len = tarInputStream.read(data, 0, BUFFER)) != -1) {
				bufferedOutputStream.write(data, 0, len);
				unzippedBytes += len;
				percentComplete = (int) ((unzippedBytes / (float) uncompressedSize) * progressMax) + progressMin;
				if (percentComplete > percentCompleteLast) {
					publishProgress(this.context.getString(R.string.init_installing)
							+ " " + tarFile.getName(), percentComplete.toString());
					percentCompleteLast = percentComplete;
				}
			}
			
			// Close your output stream
			bufferedOutputStream.flush();
			bufferedOutputStream.close();
		}
		
		// close the input stream
		tarInputStream.close();
		
		return !isCancelled();
	}
	
	/**
	 * Returns the uncompressed size for a Gzipped file.
	 * 
	 * @param file		Gzipped file to get the size for
	 * @return Size 	when uncompressed, in bytes
	 * @throws IOException
	 */
	private int getGzipSizeUncompressed(File zipFile) throws IOException {
		RandomAccessFile raf = new RandomAccessFile(zipFile, "r");
		raf.seek(raf.length() - 4);
		int b4 = raf.read();
		int b3 = raf.read();
		int b2 = raf.read();
		int b1 = raf.read();
		raf.close();
		return (b1 << 24) | (b2 << 16) + (b3 << 8) + b4;
	}

	/**
	 * Return the uncompressed size for a Tar file.
	 * 
	 * @param tarFile
	 *					The Tarred file
	 * @return Size when uncompressed, in bytes
	 * @throws IOException
	 */
	private int getTarSizeUncompressed(File tarFile) throws IOException {
		int size = 0;
		TarInputStream tis = new TarInputStream(new BufferedInputStream(
				new FileInputStream(tarFile)));
		TarEntry entry;
		while ((entry = tis.getNextEntry()) != null) {
			if (!entry.isDirectory()) {
				size += entry.getSize();
			}
		}
		tis.close();
		return size;
	}
	
	private URL getDownloadURL(String code) throws MalformedURLException{
		
		String downloadURL = "";
		
		if(code != null){
			if(code.equals(OSD_CODE)){
				// We need to download the OSD data file
				downloadURL = BASE_URL + "tesseract-ocr-3.01.osd.tar.gz";
			} else {
				// We need to download a language data file
				downloadURL = BASE_URL + "tesseract-ocr-3.02." + code + ".tar.gz";
			}
		}
		
		return new URL(downloadURL);
	}
	
	private static String getTempDownloadName(String dataFileName){
		return dataFileName + ".tar.gz.tmp";
	}
	
	private static String getTempUnzippedName(String dataFileName){
		return dataFileName + ".tar";
	}
	
	private static String getDataFileName(String code){
		// Note right now the extension for the OSD file is the same as that for language files
		// eg: osd.traineddata and eng.traineddata
		return (OSD_CODE.equals(code)) ? code + ".traineddata" : code + ".traineddata";
	}
	
	private boolean shouldUntarDownloadedFile(String code){
		return !code.startsWith("cube_"); 
	}
}