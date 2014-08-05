/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.lang.ref.WeakReference;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.molatra.ocrtest.model.OcrResult;
import com.molatra.ocrtest.utils.FileManager;
import com.molatra.ocrtest.utils.Gegevens;

public class OCRService extends Service {
    
    public enum State {
    	UNINITIALIZED,
    	INITIALIZING,
    	IDLE,
    	BUSY,
    	CONTINUOUS
    }
    
	/** Message ids */
    static final int MSG_INITIALIZE = 1;		// Initialize the OCR engine (if not already initialized).  
    static final int MSG_RELEASE = 2;			// Release all the OCR resources. (inverse of initialize)
    static final int MSG_RECOGNIZE = 3;			// Recognize text from an image.
    static final int MSG_GETSTATE = 4;			// Get the current state of the service (i.e. what it is doing).
    static final int MSG_ERROR = 10;
    static final int MSG_REPLY = 11;
    static final int MSG_RESULT = 12;
    static final int MSG_PROGRESS = 13;
    
	static final int NOTIFICATION_ID = 86000;
    
    static int progCounter = 0;
    static final String TAG = "OCRService";
    
    /** Target we publish to clients. Clients use this messenger to send requests to the Service. */
    final Messenger mMessenger = new Messenger(new IncomingHandler(this));

    /** Currently registered client. The service can have only one client at any time (no queues) */
	private WeakReference <Messenger> currentClient;
	private int currentRequest;
	private Bitmap currentBitmap;
    
    /** Variables used by TessBaseAPI */
    private TessBaseAPI baseApi;
    private State mState;
    private String languageCode = Gegevens.EXTRA_LANGUAGE;
    private File storageRoot;
    
    /** Avoid re-initialization of Tesseract due to accidental unbinding-rebinding by delaying release of resources */
    private static Handler handler = new Handler();
    private Runnable delayStopSelf = new Runnable() {        
		@Override public void run() {
			String wasnull = "baseAPI was " + ((baseApi == null) ? "null" : "not null");
	    	OCRService.this.releaseOCR();
			String isnull = "baseAPI is " + ((baseApi == null) ? "null" : "not null");
	    	Log.v(TAG,"stopping OCR service: " +  wasnull + " and now " + isnull);
	    	OCRService.this.stopForeground(true);
	    	OCRService.this.stopSelf();
		}
    };

    /** Handler of incoming messages from clients. */
	static private class IncomingHandler extends Handler {
	    private final WeakReference<OCRService> mService; 

	    public IncomingHandler(OCRService service) {
	        mService = new WeakReference<OCRService>(service);
	    }
	    
	    @Override
        public void handleMessage(Message msg) {
        	
	    	// Make sure the service exists
	    	if(mService.get() != null && inAllowedState(msg.what)){
	            
	    		// Keep track of the current request
	    		if(msg.what != MSG_GETSTATE)
	    			mService.get().currentRequest = msg.what;
	    		
	    		// Send the message to the service
	    		switch (msg.what) {
                case MSG_INITIALIZE:
                	Log.v(TAG, "initialize client request: " + msg.obj.toString());
                    mService.get().initializeOCR(new WeakReference<Messenger> (msg.replyTo), msg.obj.toString());
                    break;
                case MSG_RELEASE:
                	Log.v(TAG, "release client request");
                	mService.get().releaseOCR();	
                    break;
                case MSG_RECOGNIZE:
                	Log.v(TAG, "recognize client request");
            		mService.get().recognize(new WeakReference<Messenger> (msg.replyTo), 
            				(msg.obj instanceof Bitmap) ? (Bitmap) msg.obj : null);
                    break;
                case MSG_GETSTATE:
                	mService.get().sendMessage(new WeakReference<Messenger> (msg.replyTo),
                			MSG_GETSTATE, mService.get().mState.toString());
                	break;
                default:
                    super.handleMessage(msg);
	            }	    		
	    	} else {
	    		// The service does not exist
	            try {
	            	if(mService.get() != null){
	    	    		Log.e(TAG,"IncomingHandler: Invalid state, namely: " + mService.get().mState + ".");
	            		msg.replyTo.send(Message.obtain(null, MSG_ERROR, 
	            				mService.get().getString(R.string.error_invalid_state)));
	            	} else {
	    	    		Log.e(TAG,"IncomingHandler: Service reference is null.");
	            		msg.replyTo.send(Message.obtain(null, MSG_ERROR, "Service does not exist."));
	            	}
	            } catch (RemoteException e) {
	                // The client is dead. 
		    		Log.e(TAG,"IncomingHandler: The client is dead.");
	            } catch (NullPointerException e) {
	                // The reply to client is does not exist. 
		    		Log.e(TAG,"IncomingHandler: msg.replyTo was null.");
	            }
	    	}
	    }
	    
	    private boolean inAllowedState(int msg){
	    	State s = mService.get().mState;
	    	
	    	return (msg == MSG_INITIALIZE && s == State.UNINITIALIZED)
	    		|| (msg == MSG_RELEASE && s != State.INITIALIZING)
	    		|| (msg == MSG_RECOGNIZE && (s == State.UNINITIALIZED || s == State.INITIALIZING || s == State.IDLE))
	    		|| (msg == MSG_GETSTATE);
	    }
    }
    
    @Override public void onCreate() {
    	// Start in the uninitialized state
    	Log.v(TAG, "onCreate");
    	mState = State.UNINITIALIZED;
    }
    
    @Override public void onDestroy() {
    	Log.v(TAG, "onDestroy");
    	delayedStopSelf();
    	super.onDestroy();
    }
    
    @Override public void onLowMemory() {
    	Log.v(TAG, "onLowMemory");
    	super.onLowMemory();
    }
    
    @Override public boolean onUnbind(Intent intent) {
    	Log.v(TAG, "onUnbind");
    	delayedStopSelf();
		return true;
	}

    @Override public IBinder onBind(Intent intent) {
    	Log.v(TAG, "onBind");
    	handler.removeCallbacks(delayStopSelf);
    	// Start the service. This will keep the service running if the Activity unbinds 
    	// We should stop the service in onUnbind (it's a delayed stop).
		startService(new Intent(this, OCRService.class));
        return mMessenger.getBinder();
    }
    
    @Override public void onRebind(Intent intent) {
    	Log.v(TAG, "onRebind");
    	handler.removeCallbacks(delayStopSelf);
    }
    
    @Override public int onStartCommand(Intent intentee, int flags, int startId) {
    	Log.v(TAG, "onStartCommand");
        
    	// Run the service in the foreground (to avoid having Android prematurely stop the service)
    	startForeground(NOTIFICATION_ID, makeNotification());
    	
    	// Do not restart if we get killed.
        return START_NOT_STICKY;
    }    

    public void initializeOCR(WeakReference<Messenger> client, String language){
    	if(mState == State.UNINITIALIZED || baseApi == null){
    		
    		// Change the state to initializing
    		mState = State.INITIALIZING;

    		// Release the resources if Tesseract is currently running
    		releaseOCR();
    		
    		// Get the StorageRoot
    		storageRoot = FileManager.getApplicationFileDir(this);
    		Log.d(TAG, "storage root: " + storageRoot.getAbsolutePath());
    		
    		// Create a new base Api
    		baseApi = new TessBaseAPI();
    		
    		// Assign this client as current client
    		currentClient = client;
    		
    		// Assign the language
            languageCode = language;
    		
    		// Create a separate thread to initialize Tesseract
    		new OcrInitAsyncTask(this, baseApi, languageCode, storageRoot){
    			@Override protected void onCancelled(Boolean result) {
    				onInitAsyncTaskComplete(false);
    			}
    			@Override protected void onPostExecute(Boolean result) {
    				onInitAsyncTaskComplete(result);
    			}
    		}.execute();
    	} else {
    		sendMessage(client, MSG_ERROR, getString(R.string.error_invalid_state));
    	}
    }

    public void recognize(WeakReference<Messenger> client, Bitmap image){
    	Log.v(TAG,"recognize: (2) Bitmap is " + image);
    	
    	if(image == null){
    		// No image to recognize
    		sendMessage(client, MSG_ERROR, getString(R.string.error_invalid_file_bmp));
    	} else if(mState == State.UNINITIALIZED || baseApi == null){
    		// Save the bitmap while initialize the API
    		currentBitmap = image;
			
    		// Initialize the API first
	    	initializeOCR(client, languageCode);
	    	// We'll reload the bitmap in onInitAsyncTaskComplete()

    	} else if(mState == State.IDLE && baseApi != null){
    		
    		// Change the state to busy
    		mState = State.BUSY;

    		// Assign this client as current client
    		currentClient = client;
    		
    		// Remove references to the currentBitmap the OcrRecognizeAsyncTask will recycle the bitmap
    		currentBitmap = null;
    		
    		// Create separate thread to run the recognition
    		new OcrRecognizeAsyncTask(baseApi, image){
    			@Override protected void onCancelled(OcrResult result) {
    				onRecognizeAsyncTaskComplete(null);
    			}
    			@Override protected void onPostExecute(OcrResult result) {
    				onRecognizeAsyncTaskComplete(result);
    			}
    		}.execute();
    	} else if (mState == State.CONTINUOUS){
    		// TODO: figure out what to do in continuous
    	} else {
    		sendMessage(client, MSG_ERROR, getString(R.string.error_invalid_state));
    	}
    }
    
    public void releaseOCR(){
        // Release the Tesseract resources 
    	if(baseApi != null){ baseApi.end(); baseApi = null; } 
		mState = State.UNINITIALIZED;
    }
    
    @SuppressWarnings("deprecation")
	private Notification makeNotification(){
    	
    	//This constructor is deprecated. 
    	Notification notice = new Notification(R.drawable.ic_action_about, 
    			getString(R.string.notice_ticker), System.currentTimeMillis());
    	notice.setLatestEventInfo(this, getString(R.string.notice_title), getString(R.string.notice_content), 
    			PendingIntent.getActivity(this, 0, new Intent(this, OCRService.class).
    					setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP), 0));

    	notice.flags |= Notification.FLAG_NO_CLEAR;
    	
    	return notice;

    }
    
    private void delayedStopSelf(){
        // Delay stopping the service for 5 seconds
    	handler.removeCallbacks(delayStopSelf);
    	handler.postDelayed(delayStopSelf, 1000L * 10);
    }

    private void sendMessage(WeakReference<Messenger> client, int msgCode, String message){
        try {
    		client.get().send(Message.obtain(null, msgCode, currentRequest, mState.ordinal(), message));
        } catch (RemoteException e) {
            // The client is dead. 
    		Log.e(TAG,"sendMessage: The client is dead. Message was: " + message + " (" + msgCode + ")");
        } catch (NullPointerException e) {
            // The reply to client is does not exist. 
    		Log.e(TAG,"sendMessage: client was null. Message was: " + message + " (" + msgCode + ")");
        }
    }
    
    /**
     * A callback for the asynchronous tasks. 
     * @param what - a message code for a progress update
     * @param message - a String message to send with the progress update
     * @param progress - how far along the asynchronous tasks is (use -1 for indeterminate progress)
     * 
     */
    protected void onAsyncProgress(int what, String message, int progress){
    	progCounter++;
    	String end = " " + ((progress > 0) ? progress + "% " : "") + "(" + progCounter + ")";
    	sendMessage(currentClient, MSG_PROGRESS, message + end);
    }
    
    
    /**
     * Implementation of callback for the OcrInitAsyncTask. Gets called once the initialization is complete. 
     * @param success Whether or not the initialization process was successfully completed
     * 
     */
    private void onInitAsyncTaskComplete(boolean success){
        Log.d(TAG, "onInitAsyncTaskComplete");

        if(success && baseApi != null){
            baseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
            baseApi.setVariable(TessBaseAPI.VAR_SAVE_BLOB_CHOICES, TessBaseAPI.VAR_TRUE);
            baseApi.setVariable(TessBaseAPI.VAR_USE_CJK_FP_MODEL, TessBaseAPI.VAR_TRUE);

    		// If the base API was successfully initialized, put us in the IDLE state (to allow recognition).
        	sendMessage(currentClient, MSG_REPLY, getString(R.string.success_initialization));
    		mState = State.IDLE;

    		if(currentRequest == MSG_RECOGNIZE) {
    	    	Log.v(TAG,"recognize: (3) Bitmap is " + currentBitmap);
    	        recognize(currentClient, currentBitmap);
    			
    		} else {
        		// We're done with the current client. Remove any references to the object.
    	        Log.v(TAG, "onInitAsyncTaskComplete. don't recognize. currentRequest is " + currentRequest);
                currentClient = null;
    		}
        } else {
    		// If the base API was NOT successfully initialized, release the resource.
    		// NOTE: the releaserOCR() method will put us in the UNINITIALIZED state.
    		sendMessage(currentClient, MSG_ERROR, getString(R.string.error_failed_initialize));
    		releaseOCR();

    		// We're done with the current client. Remove any references to the object.
            currentClient = null;
    	}
    }

    /**
     * Implementation of callback for the OcrRecognizeAsyncTask. Gets called once the recognition is complete. 
     * @param result the OCR result from the recognition task (use null for no result)
     * 
     */
    private void onRecognizeAsyncTaskComplete(OcrResult result){
    	
    	if(result != null){
        	Log.v(TAG, "Recognize complete (" + result + "): " + result.getText());
	    	Log.v(TAG,"recognize: (6) Bitmap is " + result.getBitmap());
            try {
            	currentClient.get().send(Message.obtain(null, MSG_RESULT, currentRequest, mState.ordinal(), result));
            } catch (RemoteException e) {
                // The client is dead. 
        		Log.e(TAG,"sendMessage: The client is dead. Message was: " + result + " (" + MSG_RESULT + ")");
            } catch (NullPointerException e) {
                // The reply to client is does not exist. 
        		Log.e(TAG,"sendMessage: client was null. Message was: " + result + " (" + MSG_RESULT + ")");
            }
            
    	} else {
    		// We did not receive an OCR result
        	Log.i(TAG, "Recognize complete: No result");
    		sendMessage(currentClient, MSG_ERROR, getString(R.string.error_failed_recognition));
    	}

    	// Go back to IDLE (or if the baseApi no longer exists go to UNINITIALIZED
    	mState = (baseApi != null) ? State.IDLE : State.UNINITIALIZED;
		
    	// We're done with the current client. Remove any references to the object.
        currentClient = null;
    }
    
    
//  private void keepTrack(){
//		handler.postDelayed(runnable, 1000);
//	}
    
//  private Runnable runnable = new Runnable() {        
//		@Override public void run() {
//			long diff = System.currentTimeMillis() - shutdownTime;
//	    	Log.v(TAG,"keep track: baseAPI is " + ((baseApi == null) ? "null" : "not null") + " | " + progCounter + " | " + diff);
//	    	keepTrack();
//		}
//  };

}
