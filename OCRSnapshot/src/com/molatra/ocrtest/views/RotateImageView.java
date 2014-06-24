package com.molatra.ocrtest.views;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 *  Rotates portrait shots to landscape.
 *  DO NOT use this class outside of this project. It depends on project specific factors 
 *  (such as match_parent layout sizes and fitXY scale)
 * @author Krishna
 *
 */
public class RotateImageView extends ImageView {

	private boolean rotated = false;
	
	/**
	* Constructor inherited from ImageView
	*
	* @param context
	*/
	public RotateImageView(Context context) {
		super(context);
	}

	/**
	* Constructor inherited from ImageView
	*
	* @param context
	* @param attrs
	*/
	public RotateImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	/**
	* Constructor inherited from ImageView
	*
	* @param context
	* @param attrs
	* @param defStyle
	*/
	public RotateImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	/**
	* onDraw override.
	* If animation is "on", view is invalidated after each redraw,
	* to perform recalculation on every loop of UI redraw
	*/
	@Override
	public void onDraw(Canvas canvas){
		canvas.save();
		if(getDrawable() != null && this.getWidth() > 0 && this.getHeight() > 0
				&& getDrawable().getIntrinsicHeight() > getDrawable().getIntrinsicWidth()){

			// Get the scale factor (scaled drawable width should be the same as the image height)
			float scale = (float) getDrawable().getIntrinsicHeight() / (float) getDrawable().getIntrinsicWidth();

			// translate the canvas to keep the current center as center after scaling
			canvas.translate(-(this.getWidth() / 2) * (scale - 1), -(this.getHeight() / 2) * (scale - 1));
			
			// Scale the image (scaled drawable width should be the same as the image height)
			canvas.scale(scale, scale);

			// rotate the canvas to show the image in landscape mode
			canvas.rotate(-90, this.getWidth() / 2, this.getHeight() /2);
			
			// updated the rotated variable
			rotated = true;
		} else {
			// updated the rotated variable
			rotated = false;
		}
		super.onDraw(canvas);
		canvas.restore();
		
	}

	public boolean isRotated(){
		return rotated;
	}
	
}
