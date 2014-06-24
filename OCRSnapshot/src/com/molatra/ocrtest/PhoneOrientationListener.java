package com.molatra.ocrtest;

import android.content.Context;
import android.view.OrientationEventListener;
import android.view.Surface;

public class PhoneOrientationListener extends OrientationEventListener{
	
	public int rotation = Surface.ROTATION_90;
	private PhoneRotationListener mListener;

	public PhoneOrientationListener(Context context, int rate, PhoneRotationListener listener) {
		super(context, rate);
		mListener = listener;
	}
	
	@Override public void onOrientationChanged(int orientation) {
		int oldRotation = rotation;
		// For some strange reason, the reported orientation is counterclockwise (inverse of surface rotation) 
		// This means that 270 degrees responds to rotation_90 and 90 degrees corresponds to rotation_270
		if(orientation != ORIENTATION_UNKNOWN && orientation >= 0 && orientation <= 360){
			if(orientation < 45 || orientation >= 315){
				rotation = Surface.ROTATION_0;
			} else if (orientation < 135) {
				rotation = Surface.ROTATION_270;
			} else if (orientation < 225) {
				rotation = Surface.ROTATION_180;
			} else if (orientation < 315) {
				rotation = Surface.ROTATION_90;
			}
			if(oldRotation != rotation){ 
//				Log.v("PhoneOrientationListener", "====== NEW ROTATION ======");
				this.onRotationChanged(rotation);
			}
//			Log.v("PhoneOrientationListener", "onOrientationChanged - Orientation changed to " + rotation + " ("+ orientation + ")");
		}
	}
	
	public int getRotation(){
		return rotation;
	}
	
	/**
	 *  Use this method to perform call backs. 
	 *  The class using the listener can overwrite this method and get an update once the rotation changes.
	 */
	public void onRotationChanged(int rotation){
		mListener.onPhoneRotated(rotation);
	}
	
	public void setPhoneRotationListener(PhoneRotationListener listener){
		mListener = listener;
	}
	
    /** listener interface for PhoneOrientationListener */
    public interface PhoneRotationListener {
		public void onPhoneRotated(int rotation);
    }
}
