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
		
		// make sure we have valide width and height requirements
		if(reqWidth < 0){ reqWidth = 40; } if(reqHeight < 0){ reqHeight = 40; }
		
	    // First decode with inJustDecodeBounds=true to check dimensions
	    final BitmapFactory.Options options = new BitmapFactory.Options();
	    options.inJustDecodeBounds = true;
	    BitmapFactory.decodeFile(pathName, options);

	    // Calculate inSampleSize
	    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

	    // Decode bitmap with inSampleSize set
	    options.inJustDecodeBounds = false;
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
	
	
	public static Rect scaleRectToFitBitmap(Context c, Uri imgUri, Rect rect, int imageViewWidth, int imageViewHeight, boolean imageIsRotated){
		
	    InputStream is = null;
	    Rect resizedRect = null;
		
	    try {
	    	
	    	// Get the image
			is = c.getContentResolver().openInputStream(imgUri);

		    // Decode with inJustDecodeBounds=true to check dimensions
			final BitmapFactory.Options options = new BitmapFactory.Options();
		    options.inJustDecodeBounds = true;
		    BitmapFactory.decodeStream(is, null, options);
		    
		    
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
		    	
		    	// undo the scaling done by the image view
		    	float sLeft = (rect.left +  shiftX + shiftCX);
		    	float sTop = (rect.top + shiftY + shiftCY);
		    	float sRight = (rect.right - shiftX + shiftCX);
		    	float sBottom = (rect.bottom - shiftY + shiftCY);
		    	
		    	Log.i(TAG,"Rotation values: " 
		    			+ "old left: " + rect.left
		    			+ " old top: " + rect.top
		    			+ " old right: " + rect.right
		    			+ " old bottom: " + rect.bottom
		    			+ " | center x: " + centerX
		    			+ " center y: " + centerY
		    			);
		    	Log.i(TAG,"Rotation values: " 
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
//		    	Rect srect = new Rect(
//		    			rect.left + (int) shiftX, 
//		    			rect.top + (int) shiftY, 
//		    			rect.right - (int) shiftX, 
//		    			rect.bottom - (int) shiftY);

		    	// rotate the rectangle
//		    	rect = new Rect(imageViewHeight - srect.bottom, srect.left, imageViewHeight - srect.top, srect.right);
		    	
//		    	return srect;
//		    	rect = new Rect(imageViewHeight - srect.bottom, srect.left, imageViewHeight - srect.top, srect.right);

		    }

		    
		    Log.d(TAG, "ImageView: " + imageViewWidth + "x" + imageViewHeight 
		    		+ " | Bitmap: " + outHeight + "x" + outHeight);
		    
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
		    
		    Log.d(TAG, "Original: (" + rect.left + "," + rect.top + ") to (" +  rect.right + "," + rect.bottom +
		    		") | Resized: (" + left + "," + top + ") to (" +  right + "," + bottom + ")");

		    // Create a new rectangle with the resized coordinates
		    resizedRect = new Rect(left, top, right, bottom);
		    
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}finally{
			if(is != null){ try { is.close(); } catch (IOException e) { e.printStackTrace(); }}
		}
	    
	    return resizedRect;
	}
	
	
	public static Bitmap cropScaledBitmap(Context c, Uri imgUri, Rect area, int reqSize, boolean rotate){
		
		// Determine the sample size
	    int inSampleSize = 1;
		int shortestSize = area.width() < area.height() ? area.width() : area.height();
	    while(reqSize > 0 && shortestSize / (inSampleSize * 2) >= reqSize){ inSampleSize *=2; }
	    
	    // Resize the rectangle to fit the required size
	    Rect rArea = new Rect(area.left / inSampleSize, area.top / inSampleSize, 
	    		area.right / inSampleSize, area.bottom / inSampleSize);
		
	    InputStream is = null;
	    Bitmap bitmap = null;
	    
	    try {
	    	// Get the image
			is = c.getContentResolver().openInputStream(imgUri);

			// Get the image
			final BitmapFactory.Options options = new BitmapFactory.Options();
		    options.inJustDecodeBounds = false;
		    options.inSampleSize = inSampleSize;
			bitmap = BitmapFactory.decodeStream(is, null, options);
			Log.d(TAG, "Original: (" + area.left + "," + area.top + ") to (" +  area.right + "," + area.bottom
		    		+ ") " + area.width() + "x" + area.height() 
		    		+ " | Resized: (" + rArea.left + "," + rArea.top + ") to (" +  rArea.right + "," + rArea.bottom 
		    		+ ") " + rArea.width() + "x" + rArea.height() 
		    		+ " | Bitmap: " + options.outWidth + "x" + options.outHeight + " | inSampleSize: " + inSampleSize);
		    
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}finally{
			if(is != null){ try { is.close(); } catch (IOException e) { e.printStackTrace(); }}
		}

	    if(bitmap != null){
	    	
	    	// Crop the image to get a bitmap that is greater than the required size.
	    	bitmap = cropBitmap(bitmap, rArea, rotate);
	    	
	    	// Scale the image to get the required size.
	        Matrix matrix = new Matrix();
			shortestSize = bitmap.getWidth() < bitmap.getHeight() ? bitmap.getWidth() : bitmap.getHeight();

	        float scale = ((float) reqSize) / ((float) shortestSize);
	        
	        // RESIZE THE BIT MAP
	        matrix.postScale(scale, scale);

	        // "RECREATE" THE NEW BITMAP
	        bitmap = Bitmap.createBitmap(bitmap, 0, 0, 
	        		bitmap.getWidth(), bitmap.getHeight(), matrix, false);
	    }
	    
	    return bitmap;
	}

	public static Bitmap cropBitmap(Bitmap src, Rect area, boolean rotate){
		Log.d(TAG, "Cropping bitmap");
		if(area.left + area.width() <= src.getWidth() && area.top + area.height() <= src.getHeight() 
				&& area.top >= 0 && area.left >= 0){
		    return rotateImage(Bitmap.createBitmap(src, area.left, area.top, area.width(),area.height()),
		    		rotate ? -90 : 0);
		}

		Log.e(TAG, "Cropping is outside of bitmap");
		return src;
	}
	
	public static Bitmap rotateImage(Bitmap src, float degree) {
		Bitmap bmp = null;
		Log.d(TAG, "Rotating bitmap: " + degree);
		if(degree != 0){
	        // create new matrix
	        Matrix matrix = new Matrix();
	        // setup rotation degree
	        matrix.postRotate(degree);
	        bmp = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
		}

		Log.d(TAG, "Rotating bitmap: the rotated image exists? " + (bmp != null));
		
		return bmp != null ? bmp : src;
	}
	
//	public static Uri getImageUri(Context inContext, Bitmap inImage) {
//        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
//        inImage.compress(Bitmap.CompressFormat.PNG, 90, bytes);
//        String path = Images.Media.insertImage(inContext.getContentResolver(), inImage, Gegevens.FILE_INPUTIMG, null);
//        return Uri.parse(path);
//      }

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
