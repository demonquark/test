package edu.sfsu.cs.orange.ocr;

import android.content.Context;
import android.view.OrientationEventListener;
import android.view.Surface;

public class CameraOrientationListener extends OrientationEventListener{
	
	public int rotation = Surface.ROTATION_90;

	public CameraOrientationListener(Context context, int rate) {
		super(context, rate);
	}
	
	@Override public void onOrientationChanged(int orientation) {
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
//			Log.v("CameraOrientationListener", "Orientation changed to " + rotation + " ("+ orientation + ")");
		}
	}
	
	public int getRotation(){
		return rotation;
	}

}
