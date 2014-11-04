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

package com.molatra.ocrtest;

import java.io.IOException;
import java.lang.ref.WeakReference;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.molatra.ocrtest.PhoneOrientationListener.PhoneRotationListener;
import com.molatra.ocrtest.camera.CameraManager;
import com.molatra.ocrtest.model.OcrResult;
import com.molatra.ocrtest.utils.Gegevens;
import com.molatra.ocrtest.views.ResultsOverlayView;
import com.molatra.ocrtest.views.RotateLayout;
import com.molatra.ocrtest.views.ShutterButton;
import com.molatra.ocrtest.views.ViewfinderView;
import com.molatra.ocrtest.views.ShutterButton.OnShutterButtonListener;

public final class CaptureActivity extends OCRBaseActivity 
					implements SurfaceHolder.Callback, OnShutterButtonListener, PhoneRotationListener {

	private static final String TAG = CaptureActivity.class.getSimpleName();

    /** Target we publish for clients to send messages to IncomingHandler. */
    final Messenger mMessenger = new Messenger(new IncomingHandler(this));
	
	private ViewfinderView mViewFinderView;
	private ResultsOverlayView mResultsOverlayView;
	private TextView mResultTextView;
	private RotateLayout mHintRotateView;
	private ProgressBar mProgressBar;
	private ShutterButton mOCRButton;
	private CameraManager cameraManager;
	private SurfaceView surfaceView;
	private boolean hasSurface;
	private boolean isInitialized;
	private boolean isRecognizing;

	private PhoneOrientationListener mOrientationListener;
	
    /** Handler of incoming messages from service. */
    static private class IncomingHandler extends Handler {
	    private final WeakReference<CaptureActivity> mActivity; 

	    IncomingHandler(CaptureActivity activity) {
	    	mActivity = new WeakReference<CaptureActivity>(activity);
	    }

	    @SuppressWarnings("deprecation")
	    @Override public void handleMessage(Message msg) {
	    	
	    	// Make sure the activity exists
	    	if(mActivity.get() != null){
	            switch (msg.what) {
                case OCRService.MSG_ERROR:
                case OCRService.MSG_REPLY:
        	    	Log.v(TAG,"IncomingHandler: ERROR or REPLY message ID. what = " + msg.what);
                	mActivity.get().handleReplyText(msg.what, msg.arg1, msg.arg2, msg.obj.toString());
                    break;
                case OCRService.MSG_PROGRESS:
                	Log.v(TAG,"IncomingHandler: PROGRESS message ID. what = " + msg.what);
                	mActivity.get().mResultTextView.setText(msg.obj.toString());
                    break;
                case OCRService.MSG_RESULT:
                case OCRService.MSG_RESULT_TXT:
                	Log.v(TAG,"IncomingHandler: RESULT or RESULT_TXT message ID. what = " + msg.what);
                	if(msg.obj instanceof OcrResult){
                		OcrResult result = (OcrResult) msg.obj;
                    	Log.i(TAG, "Recognize result (" + result.toString() + "): " + result.getText());
                		mActivity.get().mResultTextView.setText(result.getText());
                		mActivity.get().mResultsOverlayView.setCharacterBoundingBoxes(result.getCharacterBoundingBoxes());
                		mActivity.get().mResultsOverlayView.setWordBoundingBoxes(result.getWordBoundingBoxes());
                		mActivity.get().mResultsOverlayView.invalidate();
                    	result = null;
                	} else {
                		mActivity.get().mResultTextView.setText(msg.obj.toString());
                	}
                	if(mActivity.get().isRecognizing){
                		mActivity.get().mResultsOverlayView.setFramingRect(mActivity.get().mViewFinderView.getFramingRect());
                		mActivity.get().cameraManager.requestOcrDecode(mActivity.get().mService, mActivity.get().mMessenger, 
                				mActivity.get().mViewFinderView.getFramingRect(), OCRService.MSG_RECOGNIZE);
                	} else {
                		// Copy the last result to the clipboard 
                	    ((android.text.ClipboardManager) mActivity.get().getSystemService(CLIPBOARD_SERVICE))
                	    	.setText(mActivity.get().mResultTextView.getText());
                	    mActivity.get().toastMessage(mActivity.get().mResultTextView.getText() 
                	    	+ " " + mActivity.get().getString(R.string.copied), Gravity.BOTTOM);
                	    
                	    // stop recognizing
                		mActivity.get().stopRecognizing();
                	}
                    break;
                default:
    	    		Log.e(TAG,"IncomingHandler: Unexpected message ID. what = " + msg.what);	    		
                    super.handleMessage(msg);
	            }
	    	} else {
	    		Log.e(TAG,"IncomingHandler: Activity reference is null.");	    		
	    	}
        }
    }	

	@Override public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
    	
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.activity_capture);
		setTag(TAG);
	
		loadViews();

		cameraManager = new CameraManager(getApplication());
		mOrientationListener = new PhoneOrientationListener(this, SensorManager.SENSOR_DELAY_NORMAL, this);
		
		// Initialize the 
		isInitialized = false;
		hasSurface = false;
		isRecognizing = false;
	}
	
	@SuppressWarnings("deprecation")
	@Override protected void onResume() {
		super.onResume();
        
		// Set up the camera preview surface.
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (!hasSurface) {
			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		} else {
	        // The activity was paused but not stopped, so the surface still exists. Therefore
	        // surfaceCreated() won't be called, so init the camera here.
	        initCamera(surfaceHolder);
		}
		
		// Bind to the OCR service
		doBindService();

		// enable orientation detection
		if (mOrientationListener != null && mOrientationListener.canDetectOrientation()){ 
			mOrientationListener.enable(); 
		}
	}

	@Override protected void onPause() {
		// Stop using the camera, to avoid conflicting with other camera-based apps
	    if (cameraManager != null) {
	        cameraManager.stopPreview();
			cameraManager.closeDriver();
	    }
	    
		if (!hasSurface) {
			surfaceView.getHolder().removeCallback(this);
		}
		super.onPause();
	}
	
    @Override public void onDestroy() {
    	Log.v(TAG, "onDestroy");
    	doUnbindService();
    	super.onDestroy();
    }
    
    @Override public void onBackPressed(){
    	if(isRecognizing){
    		// Stop the recognition if the user presses the back button
    		stopRecognizing();
    	} else {
			// Cancel ongoing task and go back to the previous Activity (MainActivity)
			Message msg = Message.obtain(null, OCRService.MSG_CANCEL);
			msg.replyTo = mMessenger;
			sendMessage(msg);

			super.onBackPressed();
    	}
    }
    
    private void loadViews(){
    	
		// Load the views
    	mViewFinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
		mResultTextView = (TextView) findViewById(R.id.ocr_result_text_view);
		mHintRotateView = (RotateLayout) findViewById(R.id.hint_rotate);
		mOCRButton = (ShutterButton) findViewById(R.id.ocr_button);
		mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
		surfaceView = (SurfaceView) findViewById(R.id.preview_view);
		mResultsOverlayView = (ResultsOverlayView) findViewById(R.id.results_overlay_view);

		// Set listener to detect a shutter button click
		mOCRButton.setOnShutterButtonListener(this);
		
		// Set listener to change the size of the viewfinder rectangle.
		mViewFinderView.setOnTouchListener(new View.OnTouchListener() {
			
			// Do not allow for more than 4 simulations touch events (i.e. onlly register up to four fingers)
			int [] previousX = new int [4];
			int [] previousY = new int [4];

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						// First touch event set all the pointers to -1
						for(int i =0; i < 4; i++){ previousX[i] = -1; previousY[i] = -1; }
						return true;
					case MotionEvent.ACTION_MOVE:
						for(int i = 0; i < event.getPointerCount(); i++){
							
							// Change the framing rate and 
							mViewFinderView.processMoveEvent(previousX[i], previousY[i],
									(int) event.getX(i), (int) event.getY(i));

							// Reset the x and y values
							previousX[i] = (int) event.getX(i);
							previousY[i] = (int) event.getY(i);
						}

						// redraw the view finder
						v.invalidate();

						return true;
					
					case MotionEvent.ACTION_UP:
						// Last touch event set all the pointers to -1
						for(int i =0; i < 4; i++){ previousX[i] = -1; previousY[i] = -1; }
						return true;
				}
				
				// Ignore other actions
				return false;
			}
		});
    }
    
	private void handleReplyText(int msgCode, int requestCode, int serviceState, String message){
    	if(msgCode == OCRService.MSG_REPLY && (getString(R.string.error_already_initialized).equals(message)
    			|| getString(R.string.success_initialization).equals(message))){
    		
    		// Start the camera after initialization 
    		Log.v(TAG,"handleReplyText: initialization completion");
    		isInitialized = true;
    		mResultTextView.setText(getString(R.string.unknown));
    		initCamera(surfaceView.getHolder());
    		
    		// By default the activity is not recognizing
    		stopRecognizing();
    		
    		// Alert the user 
    		new AlertDialog.Builder(this)
    			.setTitle(R.string.continuous_hint_title)
    			.setMessage(R.string.continuous_hint_text)
    			.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
    			    @Override public void onClick(DialogInterface dialog, int which) {dialog.dismiss();}})
    			.create().show();
    		
    	} else if (msgCode == OCRService.MSG_ERROR && isRecognizing){
    		// Stop the recognition if we encounter an error
    		Log.v(TAG,"handleReplyText: error during recognition.");
    		stopRecognizing();
    	} else if (msgCode == OCRService.MSG_ERROR && !isInitialized){
    		// Finish the activity if we failed to initialize the OCR engine
    		Log.v(TAG,"Failed to initialized");
    		toastMessage(message);
    		finish();
    	} else {
    		toastMessage(message);
    	}
	}
	

    
	/** Initializes the camera and starts the handler to begin previewing. */
	private void initCamera(SurfaceHolder surfaceHolder) {
		Log.v(TAG, "initCamera()");
		if (surfaceHolder == null) {
			throw new IllegalStateException("No SurfaceHolder provided");
		}
		if(isInitialized){
			try {
				// Show the surface and the view finder
				mProgressBar.setVisibility(View.GONE);
				mViewFinderView.setVisibility(View.VISIBLE);
				mOCRButton.setVisibility(View.VISIBLE);

				// Open and initialize the camera
				cameraManager.openDriver(surfaceHolder);
				cameraManager.startPreview();
			 
			} catch (IOException ioe) {
				toastMessage("Could not initialize camera. Please try restarting device.");
			} catch (RuntimeException e) {
				// Barcode Scanner has seen crashes in the wild of this variety:
				toastMessage("Could not initialize camera. Please try restarting device.");
			}   
		} else {
			// Hide the view finder
			mProgressBar.setVisibility(View.VISIBLE);
			mViewFinderView.setVisibility(View.GONE);
			mOCRButton.setVisibility(View.INVISIBLE);
			mResultTextView.setText(getString(R.string.initializing));

			// Initialize the OCR engine
			Message msg = Message.obtain(null, OCRService.MSG_INITIALIZE);
			msg.replyTo = mMessenger;
			msg.arg1 = 0;				// Do NOT reinitialize the engine 
			msg.obj = Gegevens.EXTRA_LANGUAGE;
			sendMessage(msg);
		}
	}
	
	private void startRecognizing(){
		// Set the recognition status to true
		isRecognizing = true;
		
		// Rotate the camera output to fit the current orientation
		int rotation = mOrientationListener.getRotation();
		onPhoneRotated(rotation);
		cameraManager.setRotationAngle(getRotationAngleForBitmap(rotation));
		
		// Hide the OCR button
		mOCRButton.setVisibility(View.INVISIBLE);
		mHintRotateView.setVisibility(View.VISIBLE);

		// fix the orientation of the results
		mResultsOverlayView.setRotation(rotation);
		mResultsOverlayView.setFramingRect(mViewFinderView.getFramingRect());
		
		// Start the recognition
		cameraManager.requestOcrDecode(mService, mMessenger, mViewFinderView.getFramingRect(), OCRService.MSG_RECOGNIZE);
	}
	
	private void stopRecognizing(){
		// Set the recognition status to false
		isRecognizing = false;
		
		// Rotate the camera output to fit the current orientation
		int rotation = mOrientationListener.getRotation();
		onPhoneRotated(rotation);
		cameraManager.setRotationAngle(getRotationAngleForBitmap(rotation));

		// Show the OCR button
		mOCRButton.setVisibility(View.VISIBLE);
		mHintRotateView.setVisibility(View.GONE);

		// Hide the character bounding boxes of the last recognition
		mResultsOverlayView.setFramingRect(null);
		mResultsOverlayView.setWordBoundingBoxes(null);
		mResultsOverlayView.setCharacterBoundingBoxes(null);
		mResultsOverlayView.invalidate();
	}
	
	@Override public void surfaceCreated(SurfaceHolder holder) {
		Log.v(TAG, "surfaceCreated()");
		
		if (holder == null) {
			Log.e(TAG, "surfaceCreated gave us a null surface");
		}
    
		// Only initialize the camera if the OCR engine is ready to go.
	    if (!hasSurface) {
	      Log.v(TAG, "surfaceCreated(): calling initCamera()...");
	      initCamera(holder);
	    }
		hasSurface = true;
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
	}

	@Override public void onPhoneRotated(int rotation) {
		Log.v(TAG, "Current orientation is:" + rotation);
		
		if(!isRecognizing){
			if(mOCRButton != null){ mOCRButton.rotation = rotation; mOCRButton.invalidate(); }
			
			// Get the angle of rotation for the text views
			int rotationAngle = getRotationAngleForBitmap(rotation);

			// Update the layout parameters of the results view
			RotateLayout.LayoutParams y = (RotateLayout.LayoutParams) mResultTextView.getLayoutParams();
			y.setAngle(rotationAngle);
			mResultTextView.setLayoutParams(y);
		}
	}

	@Override public void onShutterButtonFocus(ShutterButton b, boolean pressed) {
		Log.v(TAG, "onShutterButtonFocus");
	}

	@Override public void onShutterButtonClick(ShutterButton b) {
		Log.v(TAG, "onShutterButtonClick");
		startRecognizing();
	}
}