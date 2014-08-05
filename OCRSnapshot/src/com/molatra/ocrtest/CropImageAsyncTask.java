package com.molatra.ocrtest;

import com.molatra.ocrtest.utils.BitmapTools;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.util.Log;
import android.view.Surface;

/**
 * Crops and Rotates the image.
 */
public class CropImageAsyncTask extends AsyncTask<Void, Void, Bitmap> {

	private static final String TAG = "CropImageAsyncTask";
	private String pathToBitmap; 
	private float rotationAngle;
	private Rect rect;
	private boolean rotated;
	private int reqSize;
	private int width;
	private int height;
	
	public CropImageAsyncTask(String pathToBitmap, boolean isBitmapRotated, 
			Rect croppingRect, int imageViewWidth, int imageViewHeight, 
			int phoneRotation, int scaledSize) {
		
		// original bitmap variables
		this.pathToBitmap = pathToBitmap;
		this.rotated = isBitmapRotated;
		
		// cropping rectangle variables
		this.rect = croppingRect;
		this.width = imageViewWidth;
		this.height = imageViewHeight;
		
		// Output bitmap variables
		this.reqSize = scaledSize;
		this.rotationAngle = getRotationAngleForBitmap(phoneRotation) - (rotated ? 90 : 0);
	}

	@Override protected Bitmap doInBackground(Void... arg0) {
		Log.v(TAG, "rotation is " + rotationAngle + " | rotated is " + rotated + " | path: " + pathToBitmap);
		Rect scaledRect = BitmapTools.scaleRectToFitBitmap(pathToBitmap, rect, width, height, rotated);
		return BitmapTools.cropScaledBitmap(pathToBitmap, scaledRect, reqSize, rotationAngle);
	}
	
    public float getRotationAngleForBitmap(int rotation){
    	
		float rotationAngle = 0;
		
		switch(rotation){
		case Surface.ROTATION_90: // 1
			rotationAngle = 0;
			break;
		case Surface.ROTATION_180: // 2
			rotationAngle = 270;
			break;
		case Surface.ROTATION_270: // 3
			rotationAngle = 180;
			break;
		case Surface.ROTATION_0: // 0
			rotationAngle = 90;
			break;
		}
    	
    	return rotationAngle;
    }
}
