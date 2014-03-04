package com.kris.test;

import com.kris.test.camera.CameraPreview;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;

public class CameraActivity extends Activity {
	
	public static final int RESULT_FINISH 	= 11;
	
	protected Toast toastMsg;
    private Camera mCamera;
    private CameraPreview mPreview;

	
	@Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        ((TextView) findViewById(R.id.button_capture)).setText("New text");
        
        if(checkCameraHardware(this)){
        	postToast("The hardware is available");
        	mCamera = CameraActivity.getCameraInstance();

            // Create our Preview view and set it as the content of our activity.
            mPreview = new CameraPreview(this, mCamera);
            FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
            preview.addView(mPreview);

        } else {
        	postToast("The hardware is NOT available");
        }

    }
	
	/** Launch a toast message. */
	protected void postToast(String text){

		// change or create the toast text
		if(toastMsg == null){
			toastMsg = Toast.makeText(this, text, Toast.LENGTH_SHORT);
			toastMsg.setGravity(Gravity.CENTER, 0, 0);
		}else{
			toastMsg.setText(text);
		}
		
		// show the toast message
		toastMsg.show();
	}
	
	
	/** Check if this device has a camera */
	private boolean checkCameraHardware(Context context) {
	    if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
	        // this device has a camera
	        return true;
	    } else {
	        // no camera on this device
	        return false;
	    }
	}
	
	/** A safe way to get an instance of the Camera object. */
	public static Camera getCameraInstance(){
	    Camera c = null;
	    try {
	        c = Camera.open(); // attempt to get a Camera instance
	    }
	    catch (Exception e){
	        // Camera is not available (in use or does not exist)
	    }
	    return c; // returns null if camera is unavailable
	}

	

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();              // release the camera immediately on pause event
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

	

}
