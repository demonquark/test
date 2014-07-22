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
    static final int MSG_INITIALIZE = 1;	// Initialize the OCR engine (if not already initialized).  
    static final int MSG_RELEASE = 2;		// Release all the OCR resources. (inverse of initialize)
    static final int MSG_RECOGNIZE = 3;		// Recognize text from an image.
    static final int MSG_GETSTATE = 4;		// Recognize text from an image.
    static final int MSG_PROGRESS = 11;
    static final int MSG_REPLY_INIT = 12;
    static final int MSG_REPLY_RECOGNIZE = 13;
    static final int MSG_REPLY_GENERAL = 14;
    
    static int progCounter = 0;
    
    /** Target we publish for clients to send messages to IncomingHandler. */
    final Messenger mMessenger = new Messenger(new IncomingHandler(this));

    /** Target we publish for clients to send messages to IncomingHandler. */
    static final String TAG = "OCRService";

    // Keeps track of the current registered clients. 
	private WeakReference <Messenger> currentClient;
    
    private TessBaseAPI baseApi;
    private State mState;
    private String languageCode = Gegevens.EXTRA_LANGUAGE;
    private File storageRoot;
    
    private static boolean keeptrack;

    private Handler handler = new Handler();
    private Runnable runnable = new Runnable() {        
		@Override public void run() {
	    	Log.v(TAG,"run: handler");
	    	keepTrack();
		}
    };
    
    
    /**
     * Handler of incoming messages from clients.
     */
	static class IncomingHandler extends Handler {
	    private final WeakReference<OCRService> mService; 

	    IncomingHandler(OCRService service) {
	        mService = new WeakReference<OCRService>(service);
	    }
	    
	    @Override
        public void handleMessage(Message msg) {
        	
	    	// Make sure the service exists
	    	if(mService.get() != null){
	            
	    		// Send the message to the service
	    		switch (msg.what) {
                case MSG_INITIALIZE:
                	Log.v(TAG, "initialize client request: " + msg.obj.toString());
                    mService.get().languageCode = msg.obj.toString();
                    mService.get().initializeOCR(new WeakReference<Messenger> (msg.replyTo));
                    break;
                case MSG_RELEASE:
                	Log.v(TAG, "release client request");
//                    if(mClients.isEmpty()){
//                    	mService.get().releaseOCR();	
//                    }
                    break;
                case MSG_RECOGNIZE:
                	Log.v(TAG, "recognize client request");
                	if(msg.obj instanceof Bitmap){
                		mService.get().recognize(new WeakReference<Messenger> (msg.replyTo), (Bitmap) msg.obj);
                	} else {
                		mService.get().sendMessage(new WeakReference<Messenger> (msg.replyTo), 
                				MSG_REPLY_RECOGNIZE, "Please provide a valid image URI.");
                	}
                    break;
                case MSG_GETSTATE:
                	mService.get().sendMessage(new WeakReference<Messenger> (msg.replyTo),
                			MSG_REPLY_GENERAL, mService.get().mState.toString());
                	break;
                default:
                    super.handleMessage(msg);
	            }	    		
	    	} else {
	    		// Tell the Messenger that the service does not exist
	    		Log.e(TAG,"IncomingHandler: Service reference is null.");
	            try {
	        		msg.replyTo.send(Message.obtain(null, MSG_REPLY_GENERAL, "Service does not exist."));
	            } catch (RemoteException e) {
	                // The client is dead. 
		    		Log.e(TAG,"IncomingHandler: The client is dead.");
	            } catch (NullPointerException e) {
	                // The reply to client is does not exist. 
		    		Log.e(TAG,"IncomingHandler: msg.replyTo was null.");
	            }
	    	}
	    }
    }
    
    @Override public void onCreate() {
    	// Start in the uninitialized state
    	Log.v(TAG, "onCreate");
    	mState = State.UNINITIALIZED;
    }
    
    @Override public void onDestroy() {
    	Log.v(TAG, "onDestroy");
    	keeptrack = false;
    	releaseOCR();
    	super.onDestroy();
    }
    
    @Override public void onLowMemory() {
    	Log.v(TAG, "onLowMemory");
    	super.onLowMemory();
    }
    
    @Override
	public boolean onUnbind(Intent intent) {
    	Log.v(TAG, "onUnbind");
    	releaseOCR();
		return super.onUnbind(intent);
	}

	/**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override public IBinder onBind(Intent intent) {
    	Log.v(TAG, "onBind");
    	keeptrack = true;
//    	keepTrack();
        return mMessenger.getBinder();
    }

    public void initializeOCR(WeakReference<Messenger> client){
    	if(mState == State.UNINITIALIZED || baseApi == null){
    		
    		// Release the resources if Tesseract is currently running
    		releaseOCR();
    		
    		// Get the StorageRoot
    		storageRoot = FileManager.getApplicationFileDir(this);
    		Log.d(TAG, "storage root: " + storageRoot.getAbsolutePath());
    		
    		// Create a new base Api
    		baseApi = new TessBaseAPI();
    		
    		// Assign this client as current client
    		currentClient = client;

    		// Change the state to initializing
    		mState = State.INITIALIZING;
    		
    		// Initialize Tesseract
    		new OcrInitAsyncTask(this, baseApi, languageCode, storageRoot){
    			@Override protected void onCancelled(Boolean result) {
    				super.onPostExecute(result);
    				onInitAsyncTaskComplete(false);
    			}
    			@Override protected void onPostExecute(Boolean result) {
    				super.onPostExecute(result);
    				onInitAsyncTaskComplete(result);
    			}
    		}.execute();
    	} else {
    		// TODO: send back a message ???
    		sendMessage(client, MSG_REPLY_INIT, "Cannot initialize. In wrong state.");
    	}
    }

    public void recognize(WeakReference<Messenger> client, final Bitmap image){
    	if(mState == State.UNINITIALIZED || baseApi == null){
    		
    		// Release the resources if Tesseract is currently running
    		releaseOCR();
    		
    		// Get the StorageRoot
    		storageRoot = FileManager.getApplicationFileDir(this);
    		Log.d(TAG, "storage root: " + storageRoot.getAbsolutePath());
    		
    		// Create a new base Api
    		baseApi = new TessBaseAPI();
    		
    		// Assign this client as current client
    		currentClient = client;

    		// Change the state to initializing
    		mState = State.BUSY;
    		
    		// Initialize Tesseract
    		new OcrInitAsyncTask(this, baseApi, languageCode, storageRoot){
    			@Override protected void onPostExecute(Boolean result) {
    				super.onPostExecute(result);
	    			Log.v(TAG, "After Init from recognize.");
    	    		if(image != null){
    	                baseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
    	                baseApi.setVariable(TessBaseAPI.VAR_SAVE_BLOB_CHOICES, TessBaseAPI.VAR_TRUE);
    	                baseApi.setVariable(TessBaseAPI.VAR_USE_CJK_FP_MODEL, TessBaseAPI.VAR_TRUE);

    	        		// Create separate thread to run the recognition
    	                new OcrRecognizeAsyncTask(baseApi, image){
    	        			@Override protected void onCancelled(OcrResult result) {
    	        				super.onPostExecute(result);
    	        				onRecognizeAsyncTaskComplete(null);
    	        			}
    	        			@Override protected void onPostExecute(OcrResult result) {
    	        				super.onPostExecute(result);
    	        				onRecognizeAsyncTaskComplete(result);
    	        			}
    	        		}.execute();
    	    		} else {
    	    			Log.e(TAG, "Image is null after commit");
    	    		}
    			}
    		}.execute();
    	} else if(mState == State.IDLE && baseApi != null){
    		
    		// Assign this client as current client
    		currentClient = client;

    		// Change the state to busy
    		mState = State.BUSY;
    		
    		if(image != null){
        		// Create separate thread to run the recognition
        		new OcrRecognizeAsyncTask(baseApi, image){
        			@Override protected void onCancelled(OcrResult result) {
        				super.onPostExecute(result);
        				onRecognizeAsyncTaskComplete(null);
        			}
        			@Override protected void onPostExecute(OcrResult result) {
        				super.onPostExecute(result);
        				onRecognizeAsyncTaskComplete(result);
        			}
        		}.execute();
    		} else {
    			sendMessage(client, MSG_REPLY_RECOGNIZE, "Cannot recognize. Invalid uri.");
    		}
    	} else if (mState == State.CONTINUOUS){
    		// TODO: figure out what to do in continuous
    	} else {
    		// TODO: send back a message.
    		sendMessage(client, MSG_REPLY_RECOGNIZE, "Cannot recognize. In wrong state.");
    	}
    }
    
    public void releaseOCR(){
        // Release the Tesseract resources 
    	if(baseApi != null){ baseApi.end(); baseApi = null; } 
		mState = State.UNINITIALIZED;
    }
    
    private void sendMessage(WeakReference<Messenger> client, int msgCode, String message){
        try {
    		client.get().send(Message.obtain(null, msgCode, message));
        } catch (RemoteException e) {
            // The client is dead.  Remove it from the list;
        }
    }
    
    private void keepTrack(){
    	Log.v(TAG,"keep track: baseAPI is " + ((baseApi == null) ? "null" : "not null"));
    	if(keeptrack)
    		handler.postDelayed(runnable, 1000);
    	else
    		Log.v(TAG,"keeptrack is false.");
    }
    
    /**
     * A callback for the asynchronous tasks. 
     * @param what - a message code for a progress update
     * @param message - a String message to send with the progress update
     * @param progress - how far along the asynchronous tasks is (use -1 for indeterminate progress)
     * 
     */
    public void onAsyncProgress(int what, String message, int progress){
        try {
        	progCounter++;
        	String end = " " + ((progress > 0) ? progress + "% " : "") + "(" + progCounter + ")";
    		currentClient.get().send(Message.obtain(null, MSG_PROGRESS, message + end));
        } catch (RemoteException e) {
            // The client is dead.  Remove it from the list;
            currentClient = null;
        }
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
        	sendMessage(currentClient, MSG_REPLY_INIT, "Initialization complete.");
    		mState = State.IDLE;
    	} else {
    		// If the base API was NOT successfully initialized, release the resource.
    		// NOTE: the releaserOCR() method will put us in the UNINITIALIZED state.
    		sendMessage(currentClient, MSG_REPLY_INIT, "Initialization failed.");
    		releaseOCR();
    	}
    	
        // We're done with the current client. Remove any references to the object.
        currentClient = null;
        
    }

    /**
     * Implementation of callback for the OcrRecognizeAsyncTask. Gets called once the recognition is complete. 
     * @param result the OCR result from the recognition task (use null for no result)
     * 
     */
    private void onRecognizeAsyncTaskComplete(OcrResult result){
    	
    	if(result != null){
        	Log.i(TAG, "Recognize complete: " + result.getText());
            try {
            	currentClient.get().send(Message.obtain(null, MSG_REPLY_RECOGNIZE, result));
            } catch (RemoteException e) {
                // The client is dead.  Remove it from the list;
            }
    	} else {
    		// We did not receive an OCR result
        	Log.i(TAG, "Recognize complete: No result");
    		sendMessage(currentClient, MSG_REPLY_RECOGNIZE, "Recognition failed.");
    	}

    	// Go back to IDLE (or if the baseApi no longer exists go to UNINITIALIZED
    	mState = (baseApi != null) ? State.IDLE : State.UNINITIALIZED;
		
    	// We're done with the current client. Remove any references to the object.
        currentClient = null;
    }
}
