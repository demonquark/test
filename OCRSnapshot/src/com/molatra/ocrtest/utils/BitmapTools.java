package com.molatra.ocrtest.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Log;

public class BitmapTools {
	
	private static final String TAG = "BitmapTools";
	
	public static Bitmap decodeSampledBitmapFromResource(String pathName, int reqWidth, int reqHeight) {
		
		// make sure we have valid width and height requirements
		if(reqWidth < 0){ reqWidth = 40; } if(reqHeight < 0){ reqHeight = 40; }
		
	    // First decode with inJustDecodeBounds=true to check dimensions
	    final BitmapFactory.Options options = new BitmapFactory.Options();
	    options.inJustDecodeBounds = true;
	    BitmapFactory.decodeFile(pathName, options);

	    // Calculate inSampleSize
	    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

	    // Decode bitmap with inSampleSize set
	    options.inJustDecodeBounds = false;
	    options.inPurgeable= true;
	    options.inInputShareable = true;
	    return BitmapFactory.decodeFile(pathName, options);
	}
	
	public static int calculateInSampleSize( BitmapFactory.Options options, int reqWidth, int reqHeight) {
	    // Raw height and width of image
	    final int height = options.outHeight;
	    final int width = options.outWidth;
	    int inSampleSize = 1;


		if (height > reqHeight || width > reqWidth) {
	
	        final int halfHeight = height / 2;
	        final int halfWidth = width / 2;

			// Calculate the largest inSampleSize value that is a power of 2 and keeps both
	        // height and width larger than the requested height and width.
	        while ((halfHeight / inSampleSize) > reqHeight
	                && (halfWidth / inSampleSize) > reqWidth) {
	            inSampleSize *= 2;
	        }
	    }

	    return inSampleSize;
	}
	
	
	public static Rect scaleRectToFitBitmap(String pathName, Rect rect, 
			int imageViewWidth, int imageViewHeight, boolean imageIsRotated){
		
	    Rect resizedRect = null;

	    // Decode with inJustDecodeBounds=true to check dimensions
		final BitmapFactory.Options options = new BitmapFactory.Options();
	    options.inJustDecodeBounds = true;
	    BitmapFactory.decodeFile(pathName, options);
	    
	    int outHeight = options.outHeight;
	    int outWidth = options.outWidth;
	    
	    if(imageIsRotated){
			float rScale = (float) options.outWidth / (float) options.outHeight;
	    	float shiftX = (1f - rScale) * rect.width() / 2f; 
	    	float shiftY = (1f - rScale) * rect.height() / 2f;
	    	float centerX = (float) imageViewWidth / 2f;
	    	float centerY = (float) imageViewHeight / 2f;
	    	float shiftCX = (1f - rScale) * (centerX - rect.centerX()); 
	    	float shiftCY = (1f - rScale) * (centerY - rect.centerY());
	    	
	    	Log.v(TAG,"Rotation values: " 
	    			+ "old left: " + rect.left
	    			+ " old top: " + rect.top
	    			+ " old right: " + rect.right
	    			+ " old bottom: " + rect.bottom
	    			+ " | center x: " + centerX
	    			+ " center y: " + centerY
	    			);
	    	Log.v(TAG,"Rotation values: " 
	    			+ "new left: " + (centerY - rect.top + centerX)
	    			+ " new top: " + (imageViewHeight - (centerY - rect.left + centerX))
	    			+ " new right: " + (centerY - rect.bottom + centerX)
	    			+ " new bottom: " + (imageViewHeight - (centerY - rect.right + centerX))
	    			);
	    	
	    	// scale the rectangle
	    	rect = new Rect(
	    			(int) (centerY - (rect.bottom - shiftY + shiftCY) + centerX),
	    			(int) (imageViewHeight - (centerY - (rect.left +  shiftX + shiftCX) + centerX)),
	    			(int) (centerY - (rect.top + shiftY + shiftCY) + centerX),
	    			(int) (imageViewHeight - (centerY - (rect.right - shiftX + shiftCX) + centerX)));

	    }

	    Log.v(TAG, "ImageView: " + imageViewWidth + "x" + imageViewHeight 
	    		+ " | Bitmap: " + outWidth + "x" + outHeight);
	    
	    // Determine the scale for resizing the rectangle
	    float scale = 1;

	    if(imageViewWidth / imageViewHeight >= outWidth / outHeight) {
	    	// The image will be scaled to match the height
    		scale = ((float) outHeight) / ((float) imageViewHeight);
	    } else {
	    	// The image will be scaled to match the width
	    	scale = ((float) outWidth) / ((float) imageViewWidth);
	    }
	    
	    // Scale and translate the rect coordinates to fit the bitmap
	    int left = (int) ((rect.left * scale) - (imageViewWidth * scale - outWidth) / 2);
	    int top = (int) ((rect.top * scale) - (imageViewHeight * scale - outHeight) / 2);
	    int right = (int) ((rect.right * scale) - (imageViewWidth * scale - outWidth) / 2);
	    int bottom = (int) ((rect.bottom * scale) - (imageViewHeight * scale - outHeight) / 2);
	    
	    // Make sure the new coordinates are in the bitmap
	    left = (left >= 0) ? (left <= outWidth) ? left : outWidth : 0; 
	    top = (top >= 0) ? (top <= outHeight) ? top : outHeight : 0; 
	    right = (right >= 0) ? (right <= outWidth) ? right : outWidth : 0; 
	    bottom = (bottom >= 0) ? (bottom <= outHeight) ? bottom : outHeight : 0; 
	    
	    Log.v(TAG + "[scaleRectToFitBitmap]", "Original: (" + rect.left + ", " + rect.top 
	    		+ ", " + rect.right + ", " + rect.bottom  + ") | Resized: (" + left 
	    		+ ", " + top + ", " +  right + ", " + bottom + ") | (left,top,right,bottom)");

	    // Create a new rectangle with the resized coordinates
	    resizedRect = new Rect(left, top, right, bottom);
		    
	    return resizedRect;
	}
	
	public static Bitmap cropScaledBitmap(String pathName, Rect area, int reqSize, float rotationAngle){
		
		// Determine the sample size
	    int inSampleSize = 1;
		int shortestSize = area.width() < area.height() ? area.width() : area.height();
	    while(reqSize > 0 && shortestSize / (inSampleSize * 2) >= reqSize){ inSampleSize *=2; }
	    
	    // Resize the rectangle to fit the required size
	    Rect rArea = new Rect(area.left / inSampleSize, area.top / inSampleSize, 
	    		area.right / inSampleSize, area.bottom / inSampleSize);
		
		// Set the bitmap options for decoding
		BitmapFactory.Options options = new BitmapFactory.Options();
	    options.inJustDecodeBounds = false;
	    options.inSampleSize = inSampleSize;
	    options.inPurgeable= true;
	    options.inInputShareable = true;
	    options.inPreferredConfig = Bitmap.Config.ARGB_4444;
	    
	    // Get the bitmap
	    Bitmap bitmap = BitmapFactory.decodeFile(pathName, options);

	    Log.v(TAG + "[cropScaledBitmap]", "Original rect: (" + area.left + ", " + area.top + ", " 
	    		+  area.right + ", " + area.bottom + ") " + area.width() + "x" + area.height() 
	    		+ " | Resized rect: (" + rArea.left + ", " + rArea.top + ", " +  rArea.right + ", " 
	    		+ rArea.bottom + ")  | (left,top,right,bottom)");
	    Log.v(TAG + "[cropScaledBitmap]", "cropScaledBitmap - Original rect: " + area.width() + "x" + area.height() 
	    		+ " | Resized rect: " + rArea.width() + "x" + rArea.height() 
	    		+ " | Bitmap: " + options.outWidth + "x" + options.outHeight 
	    		+ " | inSampleSize: " + inSampleSize);

	    if(bitmap != null){
	    	
	    	// Crop the image to get a bitmap that is greater than the required size.
	    	bitmap = cropAndRotateBitmap(bitmap, rArea, rotationAngle);
	    	
	    	// Scale the image to get the required size.
	        Matrix matrix = new Matrix();
			shortestSize = bitmap.getWidth() < bitmap.getHeight() ? bitmap.getWidth() : bitmap.getHeight();

	        float scale = ((float) reqSize) / ((float) shortestSize);
	        
	        // RESIZE THE BIT MAP
	        matrix.postScale(scale, scale);

	        // "RECREATE" THE NEW BITMAP
	        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
	    }
	    
	    return bitmap;
	}

	/**
	 * Crop the given bitmap to the coordinates of the provided rectangle and then rotate it according to the rotation angle.
	 * The method will recycle the provided original bitmap (src). 
	 * @param src original bitmap
	 * @param area area to crop to
	 * @param rotationAngle angle to rotate the cropped image
	 * @return the cropped and rotated bitmap
	 */
	private static Bitmap cropAndRotateBitmap(Bitmap src, Rect area, float rotationAngle){
		Log.v(TAG, "cropAndRotateBitmap: rotate (" + area.left + ", " + area.top + ", " +  area.right + ", " 
				+ area.bottom + ") in " + src + "| angle: " + rotationAngle);
		
		if(area.left + area.width() <= src.getWidth() && area.top + area.height() <= src.getHeight() 
				&& area.top >= 0 && area.left >= 0){
			
			// Create a cropped image
			Bitmap cbmp = Bitmap.createBitmap(src, area.left, area.top, area.width(),area.height());

			// Overwrite the original
			src = cbmp;
			
			if(rotationAngle != 0 && ((int)rotationAngle) % 360 != 0){
				// We need to rotate the bitmap before overwriting the original.
		        Matrix matrix = new Matrix();
		        matrix.postRotate(rotationAngle);

		        // Rotate the bitmap and overwrite the original with the rotated bitmap
		        src = Bitmap.createBitmap(cbmp, 0, 0, cbmp.getWidth(), cbmp.getHeight(), matrix, true);
			}
			
		} else {
			// The cropping rectangle is outside of the bitmap. DO NOTHING 
			Log.e(TAG, "Cropping is outside of bitmap. Returned the original instead of throwing IllegalArgumentException.");
		}
		
		return src;
	}
	
	public static Bitmap cropBitmap(Bitmap src, Rect area){
		Log.v(TAG, "Cropping bitmap");
		if(area.left + area.width() <= src.getWidth() && area.top + area.height() <= src.getHeight() 
				&& area.top >= 0 && area.left >= 0){
			
			// Create and return cropped bitmap 
		    return Bitmap.createBitmap(src, area.left, area.top, area.width(),area.height());
		}

		// Something went wrong if we get down here 
		Log.e(TAG, "Cropping is outside of bitmap. Returned the original instead of throwing IllegalArgumentException.");
		return src;
	}
	
	public static Bitmap rotateImage(Bitmap src, float degree) {
		Log.v(TAG, "rotateImage: " + degree);
		if(src != null && ((int)degree) % 360 != 0){
			// We need to rotate the bitmap before overwriting the original. 
	        Matrix matrix = new Matrix();
	        matrix.postRotate(degree);
	        
	        // Return the rotated bitmap
	        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
		}

		// Something went wrong if we get down here 
		Log.i(TAG, "Rotation angle is a multiple of 360 degrees. Returned the original image.");
		return src;
	}

	public static Bitmap getImageFromUri(Context c, Uri uri) {
	    InputStream is = null;
	    Bitmap bitmap = null;
	    try {
	    	// Get the image
			is = c.getContentResolver().openInputStream(uri);
			bitmap = BitmapFactory.decodeStream(is);
		    
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}finally{
			if(is != null){ try { is.close(); } catch (IOException e) { e.printStackTrace(); }}
		}

        return bitmap;
      }
}
