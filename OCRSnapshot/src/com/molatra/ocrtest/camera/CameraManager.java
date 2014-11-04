/*
 * Copyright (C) 2008 ZXing authors
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
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Messenger;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;

/**
 * This object wraps the Camera service object and expects to be the only one talking to it. The
 * implementation encapsulates the steps needed to take preview-sized images, which are used for
 * both preview and decoding.
 *
 * The code for this class was adapted from the ZXing project: http://code.google.com/p/zxing
 */
public final class CameraManager {

  private static final String TAG = CameraManager.class.getSimpleName();
  
  private final Context context;
  private final CameraConfigurationManager configManager;
  private Camera camera;
  private AutoFocusManager autoFocusManager;
  private boolean initialized;
  private boolean previewing;
  private int rotationAngle;

  /**
   * Preview frames are delivered here, which we pass on to the registered handler. Make sure to
   * clear the handler so it will only receive one message.
   */
  private final PreviewCallback previewCallback;

  public CameraManager(Context context) {
    this.context = context;
    this.configManager = new CameraConfigurationManager(context);
    previewCallback = new PreviewCallback(configManager);
  }

  /**
   * Opens the camera driver and initializes the hardware parameters.
   *
   * @param holder The surface object which the camera will draw preview frames into.
   * @throws IOException Indicates the camera driver failed to open.
   */
  public synchronized void openDriver(SurfaceHolder holder) throws IOException {
    Camera theCamera = camera;
    if (theCamera == null) {
      theCamera = Camera.open();
      if (theCamera == null) {
        throw new IOException();
      }
      camera = theCamera;
    }
    camera.setPreviewDisplay(holder);
    if (!initialized) {
      initialized = true;
      configManager.initFromCameraParameters(theCamera);
    }
    configManager.setDesiredCameraParameters(theCamera);
  }

  /**
   * Closes the camera driver if still in use.
   */
  public synchronized void closeDriver() {
    if (camera != null) {
      camera.release();
      camera = null;
    }
  }

  /**
   * Asks the camera hardware to begin drawing preview frames to the screen.
   */
  public synchronized void startPreview() {
    Camera theCamera = camera;
    if (theCamera != null && !previewing) {
      theCamera.startPreview();
      previewing = true;
      autoFocusManager = new AutoFocusManager(context, camera, true);
    }
  }

  /**
   * Tells the camera to stop drawing preview frames.
   */
  public synchronized void stopPreview() {
    if (autoFocusManager != null) {
    	autoFocusManager.stop();
    	autoFocusManager = null;
    }
  	if (camera != null && previewing) {
      camera.stopPreview();
      previewCallback.clearVariables();
      previewing = false;
    }
  }

  /**
   * A single preview frame will be sent to the specified sendTo messenger with the given decodeCommand (as msg.what). 
   * The data will be cropped to the provided frame and sent as a bitmap (as msg.obj).
   * Assuming that the sendTo messenger is the OCRservice, the service will reply to the replyTo messenger (as msg.replyTo).
   * 
   * @param sendTo - messenger of the OCRService
   * @param replyTo - messenger of the Activity requesting the recognition
   * @param frame - rectangle used to crop the preview
   * @param decodeCommand - command sent to the sendTo messenger as msg.what
   */
  public synchronized void requestOcrDecode(Messenger sendTo, Messenger replyTo, Rect frame, int decodeCommand) {
    Camera theCamera = camera;
    if (theCamera != null && previewing) {
    	Log.v(TAG, "requestOcrDecode. all clear.");
    	previewCallback.setVariables(sendTo, replyTo, decodeCommand, frame, rotationAngle);
    	theCamera.setOneShotPreviewCallback(previewCallback);
    } else {
    	Log.v(TAG, "requestOcrDecode. previewing is " + ((previewing)? "true " : "false ") + ((theCamera != null)? "camera is null." : ""));
    }
  }
  
  /**
   * Asks the camera hardware to perform an autofocus.
   * @param delay Time delay to send with the request
   */
  public synchronized void requestAutoFocus(long delay) {
  	autoFocusManager.start(delay);
  }
  
  public synchronized void setRotationAngle(int rotationAngle){
	  this.rotationAngle = rotationAngle;
  }
  
}
