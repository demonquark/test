/*
 * Copyright (C) 2010 ZXing authors
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

package com.molatra.ocrtest.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.hardware.Camera;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.molatra.ocrtest.utils.Gegevens;

/**
 * A class which deals with reading, parsing, and setting the camera parameters which are used to
 * configure the camera hardware.
 * 
 * The code for this class was adapted from the ZXing project: http://code.google.com/p/zxing
 */
final class CameraConfigurationManager {

	private static final String TAG = "CameraConfiguration";
	
	// This is bigger than the size of a small screen, which is still supported. The routine
	// below will still select the default (presumably 320x240) size for these. This prevents
	// accidental selection of very low resolution on some devices.
	private static final int MIN_PREVIEW_PIXELS = 470 * 320; // normal screen
	private static final int MAX_PREVIEW_PIXELS = 800 * 600; // more than large/HD screen
	private static final boolean DEFAULT_PREF_AUTOFOCUS = true; // Use auto focus by default
	private static final boolean DEFAULT_PREF_TORCH = false; // Do NOT use the torch by default
	private static final boolean DEFAULT_PREF_REVERSE_IMG = false; // Do NOT reverse the image by default
	private final Context context;
	private Point screenResolution;
	private Point cameraResolution;
	
	public CameraConfigurationManager(Context context) {
		this.context = context;
	}

	/**
	 * Reads, one time, values from the camera that are needed by the app.
	 */
	@SuppressWarnings("deprecation")
	protected void initFromCameraParameters(Camera camera) {
		
		Camera.Parameters parameters = camera.getParameters();
		WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		Display display = manager.getDefaultDisplay();
		int width = display.getWidth();
		int height = display.getHeight();
		
		// We're landscape-only, and have apparently seen issues with display thinking it's portrait 
		// when waking from sleep. If it's not landscape, assume it's mistaken and reverse them:
		if (width < height) {
			Log.i(TAG, "Display reports portrait orientation; assuming this is incorrect");
			int temp = width;
			width = height;
			height = temp;
		}
		
		// Save the screen and camera resolutions
		screenResolution = new Point(width, height);
		cameraResolution = findBestPreviewSizeValue(parameters, screenResolution);
		Log.i(TAG, "Screen resolution: " + screenResolution + " | Camera resolution: " + cameraResolution);
	}

	protected void setDesiredCameraParameters(Camera camera) {
		
		// Get the camera parameters
		Camera.Parameters parameters = camera.getParameters();
		if (parameters == null) {
			Log.w(TAG, "Device error: no camera parameters are available. Proceeding without configuration.");
			return;
		}

		// Turn on the torch (flashlight) if requested
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		doSetTorch(parameters, prefs.getBoolean(Gegevens.PREF_TORCH, DEFAULT_PREF_TORCH));
		
		// Get the focus mode
		String focusMode = null;
		if(prefs.getBoolean(Gegevens.PREF_AUTOFOCUS, DEFAULT_PREF_AUTOFOCUS)){
			focusMode = findSettableValue(parameters.getSupportedFocusModes(),
					"continuous-video", // Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO in 4.0+
					"continuous-picture", // Camera.Paramters.FOCUS_MODE_CONTINUOUS_PICTURE in 4.0+
					Camera.Parameters.FOCUS_MODE_AUTO);
		}

		// Maybe selected auto-focus but not available, so fall through here:
		if (focusMode == null) {
			focusMode = findSettableValue(parameters.getSupportedFocusModes(),
			Camera.Parameters.FOCUS_MODE_MACRO, "edof"); // Camera.Parameters.FOCUS_MODE_EDOF in 2.2+
		}

		// Set the focus mode
		if (focusMode != null) {
			parameters.setFocusMode(focusMode);
		}

		// Set the preview size
		parameters.setPreviewSize(cameraResolution.x, cameraResolution.y);
		camera.setParameters(parameters);
	}

	protected Point getCameraResolution() {
		return cameraResolution;
	}

	protected Point getScreenResolution() {
		return screenResolution;
	}
	
	protected boolean isTorchSet(){
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Gegevens.PREF_TORCH, DEFAULT_PREF_TORCH);
	}
	
	protected boolean isAutoFocusSet(){
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Gegevens.PREF_AUTOFOCUS, DEFAULT_PREF_AUTOFOCUS);
	}
	
	protected boolean isImageReversed(){
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Gegevens.PREF_REVERSE_IMG, DEFAULT_PREF_REVERSE_IMG);
	}

	protected void setTorch(Camera camera, boolean newSetting) {
		// Turn the torch on/off 
		Camera.Parameters parameters = camera.getParameters();
		doSetTorch(parameters, newSetting);
		camera.setParameters(parameters);
		
		// Write the new torch value to the preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		boolean currentSetting = prefs.getBoolean(Gegevens.PREF_TORCH, DEFAULT_PREF_TORCH);
		if (currentSetting != newSetting) {
			SharedPreferences.Editor editor = prefs.edit();
			editor.putBoolean(Gegevens.PREF_TORCH, newSetting);
			editor.commit();
		}
	}

	private static void doSetTorch(Camera.Parameters parameters, boolean useTorch) {
		String flashMode;
		if (useTorch) {
			flashMode = findSettableValue(parameters.getSupportedFlashModes(),
					Camera.Parameters.FLASH_MODE_TORCH,
					Camera.Parameters.FLASH_MODE_ON);
		} else {
			flashMode = findSettableValue(parameters.getSupportedFlashModes(),
					Camera.Parameters.FLASH_MODE_OFF);
		}
		
		if (flashMode != null) {
			parameters.setFlashMode(flashMode);
		}
	}

	private Point findBestPreviewSizeValue(Camera.Parameters parameters, Point screenResolution) {
	
		// Sort by size, descending
		List<Camera.Size> supportedPreviewSizes = new ArrayList<Camera.Size>(parameters.getSupportedPreviewSizes());
		Collections.sort(supportedPreviewSizes, new Comparator<Camera.Size>() {
			@Override public int compare(Camera.Size a, Camera.Size b) {
				int aPixels = a.height * a.width;
				int bPixels = b.height * b.width;
				if (bPixels < aPixels) {	return -1; }
				if (bPixels > aPixels) { 	return 1; }
				return 0;
			}
		});
		
		// Log the available sizes
		if (Log.isLoggable(TAG, Log.INFO)) {
			StringBuilder previewSizesString = new StringBuilder();
			for (Camera.Size supportedPreviewSize : supportedPreviewSizes) {
				previewSizesString.append(supportedPreviewSize.width).append('x')
					.append(supportedPreviewSize.height).append(' ');
			}
			Log.i(TAG, "Supported preview sizes: " + previewSizesString);
		}
		
		// Get the aspect ratio
		Point bestSize = null;
		float screenAspectRatio = (float) screenResolution.x / (float) screenResolution.y;
		
		// Get the best fitting size 
		float diff = Float.POSITIVE_INFINITY;
		for (Camera.Size supportedPreviewSize : supportedPreviewSizes) {
			// Skip if too obviously too large
			int realWidth = supportedPreviewSize.width;
			int realHeight = supportedPreviewSize.height;
			int pixels = realWidth * realHeight;
			if (pixels < MIN_PREVIEW_PIXELS || pixels > MAX_PREVIEW_PIXELS) {
				continue;
			}
			
			// Get the width and height
			boolean isCandidatePortrait = realWidth < realHeight;
			int maybeFlippedWidth = isCandidatePortrait ? realHeight : realWidth;
			int maybeFlippedHeight = isCandidatePortrait ? realWidth : realHeight;

			// Check for an exact fit
			if (maybeFlippedWidth == screenResolution.x && maybeFlippedHeight == screenResolution.y) {
				Point exactPoint = new Point(realWidth, realHeight);
				Log.i(TAG, "Found preview size exactly matching screen size: " + exactPoint);
				return exactPoint;
			}
			
			// Check for a difference 
			float aspectRatio = (float) maybeFlippedWidth / (float) maybeFlippedHeight;
			float newDiff = Math.abs(aspectRatio - screenAspectRatio);
			if (newDiff < diff) {
				bestSize = new Point(realWidth, realHeight);
				diff = newDiff;
			}
		}
		
		// Could not find a proper fit. Just use the default values.
		if (bestSize == null) {
			Camera.Size defaultSize = parameters.getPreviewSize();
			bestSize = new Point(defaultSize.width, defaultSize.height);
			Log.i(TAG, "No suitable preview sizes, using default: " + bestSize);
		}
		
		Log.i(TAG, "Found best approximate preview size: " + bestSize);
		return bestSize;
	}

	private static String findSettableValue(Collection<String> supportedValues, String... desiredValues) {
		String result = null;
		if (supportedValues != null) {
			for (String desiredValue : desiredValues) {
				// Exit the loop once we find a suitable value
				if (supportedValues.contains(desiredValue)) {
					result = desiredValue;
					break;
				}
			}
		}
		
		Log.i(TAG, "Supported values: " + supportedValues + " | Set to: " + result);
		return result;
	}
}
