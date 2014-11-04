package com.molatra.ocrtest;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.widget.Toast;

public class OCRBaseActivity extends Activity {

	private String TAG = OCRBaseActivity.class.getSimpleName();
    protected Messenger mService;
	
    /** Class for interacting with the main interface of the service. */
    protected ServiceConnection mConnection = new ServiceConnection() {
    	public void onServiceConnected(ComponentName className, IBinder service) {
    		// This is called when the connection with the service has been established
        	Log.v(TAG,"onServiceConnected");
            mService = new Messenger(service);
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
        	Log.v(TAG,"onServiceDisconnected");
            mService = null;
        }
    };

    protected void toastMessage(String message){
		Toast toast = Toast.makeText(this, message, Toast.LENGTH_LONG);
		toast.setGravity(Gravity.TOP, 0, 0);
		toast.show();
	}
    
    protected void toastMessage(String message, int gravity){
		Toast toast = Toast.makeText(this, message, Toast.LENGTH_LONG);
		toast.setGravity(gravity, 0, 0);
		toast.show();
    }
	
	protected boolean sendMessage(Message msg){

		boolean msgsent = false;
		
		try {
			mService.send(msg);
			msgsent = true;
		} catch (RemoteException e) {
			e.printStackTrace();
			msgsent = false;
		} catch (NullPointerException e){
			e.printStackTrace();
			msgsent = false;
		}
		
		return msgsent;

	}
	
	protected void doBindService() {
		Log.v(TAG, "doBindService: Bind the service.");
        // Establish a connection with the service. 
        bindService(new Intent(this, OCRService.class), mConnection, Context.BIND_AUTO_CREATE);
    }
    
    protected void doUnbindService() {
		Log.v(TAG, "doUnbindService: Unbind the service.");
    	try{
	        // Detach our existing connection.
	        unbindService(mConnection);
    	}catch(IllegalArgumentException e){ 
    		Log.e(TAG, "doUnbindService: Service not registered.");
    		e.printStackTrace();
    	}
    }
	
    public int getRotationAngleForBitmap(int rotation){
		int rotationAngle = 0;
		switch(rotation){
			case Surface.ROTATION_90:	rotationAngle = 0;		break;
			case Surface.ROTATION_180:	rotationAngle = 270;	break;
			case Surface.ROTATION_270:	rotationAngle = 180;	break;
			case Surface.ROTATION_0:	rotationAngle = 90;		break;
		}
    	return rotationAngle;
    }

    public void setTag(String newTag) { TAG = newTag; }
    
}
