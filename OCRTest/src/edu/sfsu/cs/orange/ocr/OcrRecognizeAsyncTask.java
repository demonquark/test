/*
 * Copyright 2011 Robert Theis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.sfsu.cs.orange.ocr;

import java.util.List;

import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.tesseract.android.ResultIterator;
import com.googlecode.tesseract.android.TessBaseAPI;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

/**
 * Class to send OCR requests to the OCR engine in a separate thread, send a success/failure message,
 * and dismiss the indeterminate progress dialog box. Used for non-continuous mode OCR only.
 */
final class OcrRecognizeAsyncTask extends AsyncTask<Void, Void, Boolean> {

  //  private static final boolean PERFORM_FISHER_THRESHOLDING = false; 
  //  private static final boolean PERFORM_OTSU_THRESHOLDING = false; 
  //  private static final boolean PERFORM_SOBEL_THRESHOLDING = false; 

  private CaptureActivity activity;
  private TessBaseAPI baseApi;
  private byte[] data;
  private int width;
  private int height;
  private OcrResult ocrResult;
  private long timeRequired;
  private int rotationAngle = 0; // Desired rotation for the OCR result bitmap. (note: rotate before decoding) 

  OcrRecognizeAsyncTask(CaptureActivity activity, TessBaseAPI baseApi, byte[] data, int width, int height) {
    this.activity = activity;
    this.baseApi = baseApi;
    this.data = data;
    this.width = width;
    this.height = height;
    if(activity.mOrientationEventListener != null) { 
    	rotationAngle = determineRotationAngle(activity.mOrientationEventListener.rotation); 
    } 
  }

  @Override
  protected Boolean doInBackground(Void... arg0) {
    long start = System.currentTimeMillis();
    Bitmap bitmap = activity.getCameraManager().buildLuminanceSource(data, width, height).renderCroppedGreyscaleBitmap();
    bitmap = rotateImage(bitmap, rotationAngle);
    String textResult;

    //      if (PERFORM_FISHER_THRESHOLDING) {
    //        Pix thresholdedImage = Thresholder.fisherAdaptiveThreshold(ReadFile.readBitmap(bitmap), 48, 48, 0.1F, 2.5F);
    //        Log.e("OcrRecognizeAsyncTask", "thresholding completed. converting to bmp. size:" + bitmap.getWidth() + "x" + bitmap.getHeight());
    //        bitmap = WriteFile.writeBitmap(thresholdedImage);
    //      }
    //      if (PERFORM_OTSU_THRESHOLDING) {
    //        Pix thresholdedImage = Binarize.otsuAdaptiveThreshold(ReadFile.readBitmap(bitmap), 48, 48, 9, 9, 0.1F);
    //        Log.e("OcrRecognizeAsyncTask", "thresholding completed. converting to bmp. size:" + bitmap.getWidth() + "x" + bitmap.getHeight());
    //        bitmap = WriteFile.writeBitmap(thresholdedImage);
    //      }
    //      if (PERFORM_SOBEL_THRESHOLDING) {
    //        Pix thresholdedImage = Thresholder.sobelEdgeThreshold(ReadFile.readBitmap(bitmap), 64);
    //        Log.e("OcrRecognizeAsyncTask", "thresholding completed. converting to bmp. size:" + bitmap.getWidth() + "x" + bitmap.getHeight());
    //        bitmap = WriteFile.writeBitmap(thresholdedImage);
    //      }

    try {     
      baseApi.setImage(ReadFile.readBitmap(bitmap));
      
      // TODO: REMOVE Kris
      
      // step 1: Set the save blob choices to true. This saves the alternatives
      baseApi.setVariable(TessBaseAPI.VAR_SAVE_BLOB_CHOICES, TessBaseAPI.VAR_TRUE);
      textResult = baseApi.getUTF8Text();
      
      // step 2: Get the result iterator
      ResultIterator r = baseApi.getResultIterator();
      int level = com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel.RIL_SYMBOL;
      String allResultText = "";
      int i = 0;
      
      r.begin();
      do { 
    	  
    	  // step 3: Get the top choice for this item in the iterator
		  List<Pair <String, Double>> otherResults = r.getChoicesAndConfidence(level);
    	  for (Pair <String, Double> element : otherResults) {
    		  allResultText += element.first + " (" + element.second  + "%) ";
    	  }
    	  
    	  // step 4: New line to indicate a new word
    	  allResultText += "\n----------------------------------\n"; 
    	  
      } while(r.next(level) && i < 100);

      textResult = allResultText;
      
      // end REMOVE
      
      timeRequired = System.currentTimeMillis() - start;

      // Check for failure to recognize text
      if (textResult == null || textResult.equals("")) {
        return false;
      }
      ocrResult = new OcrResult();
      ocrResult.setWordConfidences(baseApi.wordConfidences());
      ocrResult.setMeanConfidence( baseApi.meanConfidence());
      ocrResult.setRegionBoundingBoxes(baseApi.getRegions().getBoxRects());
      ocrResult.setTextlineBoundingBoxes(baseApi.getTextlines().getBoxRects());
      ocrResult.setWordBoundingBoxes(baseApi.getWords().getBoxRects());
      ocrResult.setStripBoundingBoxes(baseApi.getStrips().getBoxRects());
      //ocrResult.setCharacterBoundingBoxes(baseApi.getCharacters().getBoxRects());
    } catch (RuntimeException e) {
      Log.e("OcrRecognizeAsyncTask", "Caught RuntimeException in request to Tesseract. Setting state to CONTINUOUS_STOPPED.");
      e.printStackTrace();
      try {
        baseApi.clear();
        activity.stopHandler();
      } catch (NullPointerException e1) {
        // Continue
      }
      return false;
    }
    timeRequired = System.currentTimeMillis() - start;
    ocrResult.setBitmap(bitmap);
    ocrResult.setText(textResult);
    ocrResult.setRecognitionTimeRequired(timeRequired);
    return true;
  }

  @Override
  protected void onPostExecute(Boolean result) {
    super.onPostExecute(result);

    Handler handler = activity.getHandler();
    if (handler != null) {
      // Send results for single-shot mode recognition.
      if (result) {
        Message message = Message.obtain(handler, R.id.ocr_decode_succeeded, ocrResult);
        message.sendToTarget();
      } else {
        Message message = Message.obtain(handler, R.id.ocr_decode_failed, ocrResult);
        message.sendToTarget();
      }
      activity.getProgressDialog().dismiss();
    }
    if (baseApi != null) {
      baseApi.clear();
    }
  }
  
  

  /**
   * By default the camera is in landscape mode (Surface.ROTATION_90). 
   * The rotation angle is the angle needed to go from landscape mode to the rotation indicated in mRotation.
   * FYI: That is ROTATION_0 => 90, ROTATION_90 => 0, ROTATION_180 => 270, ROTATION_270 => 180
   */
  public int determineRotationAngle(int rotation) {
	  // By default the camera is in landscape mode.
	  int degrees = 0;
	  switch (rotation) {
	    case Surface.ROTATION_0:
	        degrees = 90;
	        break;
	    case Surface.ROTATION_90:
	        degrees = 0;
	        break;
	    case Surface.ROTATION_180:
	        degrees = 270;
	        break;
	    case Surface.ROTATION_270:
	        degrees = 180;
	        break;
	    }
	  return degrees;
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


}
