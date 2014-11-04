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

import java.lang.ref.WeakReference;

import com.molatra.ocrtest.model.PlanarYUVLuminanceSource;
import com.molatra.ocrtest.utils.BitmapTools;

import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

/**
 * Called when the next preview frame is received.
 * 
 * The code for this class was adapted from the ZXing project: http://code.google.com/p/zxing
 */
final class PreviewCallback implements Camera.PreviewCallback {

	private static final String TAG = PreviewCallback.class.getSimpleName();
	
	private final CameraConfigurationManager configManager;
	private WeakReference<Messenger> sendTo;
	private WeakReference<Messenger> replyTo;
	private int previewMessage;
	private int rotation;
	private Rect frame;
	
	PreviewCallback(CameraConfigurationManager configManager) {
		this.configManager = configManager;
	}

	void setVariables(Messenger sendTo, Messenger replyTo, int previewMessage, Rect rect, int rotation){
		this.replyTo = new WeakReference<Messenger>(replyTo);
		this.sendTo = new WeakReference<Messenger>(sendTo);
		this.previewMessage = previewMessage;
		this.frame = fitRectangleToPreview(rect);
		this.rotation = rotation;
	}

	void clearVariables(){
		this.replyTo = null;
		this.sendTo = null;
		this.previewMessage = 0;
		this.frame = null;
		this.rotation = 0;
	}
	
	// Since we're not calling setPreviewFormat(int), the data arrives here in the YCbCr_420_SP (NV21) format.
	@Override public void onPreviewFrame(byte[] data, Camera camera) {
		Point cameraResolution = configManager.getCameraResolution();
		if (cameraResolution != null && replyTo != null && sendTo != null &&
				replyTo.get() != null && sendTo.get() != null) {
			Log.i(TAG, "onPreviewFrame: width x height: " + cameraResolution.x + "x" + cameraResolution.y
					+ ( (frame != null) ? 
					(" (" + frame.left + ", " + frame.top + ") (" + (frame.left + frame.width()) + ", " 
					+ (frame.top + frame.height())+ ")") : " rect is null"));
			try {
				
				Message msg = Message.obtain(null, previewMessage);
				msg.replyTo = replyTo.get();
				if(frame != null){
					msg.obj = BitmapTools.rotateImage(new PlanarYUVLuminanceSource(
									data, 
									cameraResolution.x, cameraResolution.y,
									frame.left, frame.top, 
									frame.width(), frame.height(), 
									configManager.isImageReversed())
						.renderCroppedGreyscaleBitmap(),
						rotation);
				}
				sendTo.get().send(msg);
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (NullPointerException e){
				e.printStackTrace();
			}
		}
	}
	
	
	/**
	 * Resizes the coordinates in the terms of the preview frame, not UI / screen.
	 */
	private Rect fitRectangleToPreview(Rect rect) {
		
		Rect resizedRect = (rect != null) ? new Rect(rect) : (frame != null) ? frame : new Rect();
		
		Point cameraResolution = configManager.getCameraResolution();
		Point screenResolution = configManager.getScreenResolution();

		if (cameraResolution != null && screenResolution != null && rect != null) {
			resizedRect.left = resizedRect.left * cameraResolution.x / screenResolution.x;
			resizedRect.right = resizedRect.right * cameraResolution.x / screenResolution.x;
			resizedRect.top = resizedRect.top * cameraResolution.y / screenResolution.y;
			resizedRect.bottom = resizedRect.bottom * cameraResolution.y / screenResolution.y;
		}

		return resizedRect;
	}

}
