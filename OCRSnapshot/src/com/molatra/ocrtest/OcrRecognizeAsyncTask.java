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

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.leptonica.android.WriteFile;
import com.googlecode.tesseract.android.ResultIterator;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.molatra.ocrtest.model.OcrResult;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.util.Pair;

/**
 * Class to send OCR requests to the OCR engine in a separate thread, send a success/failure message,
 * and dismiss the indeterminate progress dialog box. Used for non-continuous mode OCR only.
 */
class OcrRecognizeAsyncTask extends AsyncTask<Void, Void, OcrResult> {

	private static final String TAG = "OcrRecognizeAsyncTask";
	public static File baseDir = new File(Environment.getExternalStorageDirectory().getPath() + "/Android/data/com.molatra.ocrtest/files/img");
	private TessBaseAPI baseApi;
	private Bitmap bitmap;
	private OcrResult ocrResult;

	OcrRecognizeAsyncTask(TessBaseAPI baseApi, Bitmap bitmap) {
		this.baseApi = baseApi;
		this.bitmap = bitmap;
		this.ocrResult = null;
	}

	@Override
	protected OcrResult doInBackground(Void... arg0) {
		
		// Make sure we have access to the API and a bitmap to recognize
		if(baseApi == null || bitmap == null){ return null; }
		
		// Let's get started
		long start = System.currentTimeMillis();
		String textResult;
		ocrResult = null;
		
		try {
			
			// Save the original bitmap to file
//			saveBitmap(bitmap, (start % 1000000000) + "_original.png");
			
			// step 1: Read the bitmap into the API
			baseApi.setImage(ReadFile.readBitmap(bitmap));
			
			// step 2: Get the UTF8 recognized text
			textResult = baseApi.getUTF8Text();
			
			// step 3: Check for failure to recognize text
			if (textResult != null && !textResult.equals("")) {
				
			      // step 4: Get the result iterator
			      ResultIterator r = baseApi.getResultIterator();
			      int level = com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel.RIL_SYMBOL;
			      String allResultText = "";
			      int i = 0;
			      
			      r.begin();
			      do { 
			    	  i++;
			    	  // step 5: Get the top choice for this item in the iterator
					  List<Pair <String, Double>> otherResults = r.getChoicesAndConfidence(level);
			    	  for (Pair <String, Double> element : otherResults) {
			    		  allResultText += element.first + " (" + element.second  + "%) ";
			    	  }
			    	  
			    	  // step 6: New line to indicate a new word
			    	  allResultText += "\n----------------------------------\n"; 
			    	  
			      } while(r.next(level) && i < 100);

				// step 7: Show the thresholded image to the user
				bitmap = WriteFile.writeBitmap(baseApi.getThresholdedImage());
//				saveBitmap(bitmap, (start % 1000000000) + "_thresholded.png");
				
				// step 8: Save the results to a new ocrResult object
				ocrResult = new OcrResult();
				ocrResult.setBitmap(bitmap);
				ocrResult.setText(textResult);
				ocrResult.longtext = allResultText;
				ocrResult.setWordConfidences(baseApi.wordConfidences());
				ocrResult.setMeanConfidence( baseApi.meanConfidence());
				ocrResult.setWordBoundingBoxes(baseApi.getWords().getBoxRects());
				ocrResult.setCharacterBoundingBoxes(baseApi.getConnectedComponents().getBoxRects());
				ocrResult.setRecognitionTimeRequired(System.currentTimeMillis() - start);
			}
			
		} catch (RuntimeException e) {
			
			Log.e(TAG, "Caught RuntimeException in request to Tesseract.");
			e.printStackTrace();
			
		} finally{

			// step 6: Clear the API (free up the resources)
			try { baseApi.clear(); } catch (NullPointerException e1) { Log.e(TAG, "baseApi is null"); }		
		}

		return ocrResult;
	}

	@Override
	protected void onPostExecute(OcrResult result) {
		super.onPostExecute(result);
		Log.i(TAG, "onPostExecute not implemented. Please override this method...");
	}
	
	/** Rotates the image using the provided rotation angle. */
	public Bitmap rotateImage(Bitmap bitmap, int rotationAngle) {
		rotationAngle = rotationAngle % 360;
		if(bitmap != null && rotationAngle != 0) {
				Matrix matrix = new Matrix();
				matrix.postRotate(rotationAngle);
				Log.v("OcrResult", "rotate the image by " + rotationAngle);
			return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true); 
		}
		
		return bitmap;
	}
	
	private boolean saveBitmap(Bitmap bitmap, String filename){
		
		boolean success = false;
		
		// Make sure we have a file and a file name
		success = (bitmap != null) &&(filename != null) && !("".equals(filename));
		
		// Make sure we have a base directory.
		success = success && (baseDir != null) && (baseDir.mkdirs() || baseDir.isDirectory());
		
		// Save the bitmap to file
		if(success){
			
			// give the bitmap a png extension.
			if(!filename.endsWith(".png")){
				filename += ".png";
			}
			
			// Write the bitmap to file
			FileOutputStream out = null;
			try {
				out = new FileOutputStream(new File (baseDir, filename));
				bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
			} catch (Exception e) {
				// Return failure 
				success = false;

				// Just print any encountered errors
				e.printStackTrace();
			} finally {
				// Close the output stream
				if(out != null){ try{ out.close(); } catch(Throwable ignore) { ignore.printStackTrace(); } }
			}
		}
		
		return success;
	}
}
