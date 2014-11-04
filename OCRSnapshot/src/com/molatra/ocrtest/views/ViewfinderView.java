/*
 * Copyright (C) 2008 ZXing authors
 * Copyright 2011 Robert Theis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *			http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.molatra.ocrtest.views;

import com.molatra.ocrtest.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

/**
 * This view is overlaid on top of the displayed bitmap. It adds the viewfinder rectangle and partial
 * transparency outside it.
 *
 * The code for this class was adapted from the ZXing project: http://code.google.com/p/zxing
 */
public final class ViewfinderView extends View {

	private static final int MIN_FRAME_WIDTH = 50; // originally 240
	private static final int MIN_FRAME_HEIGHT = 20; // originally 240
	private static final int MAX_FRAME_WIDTH = 800; // originally 480
	private static final int MAX_FRAME_HEIGHT = 600; // originally 360
	private static final int CORNER_SIZE = 15;
	private static final int BUFFER = (3 * CORNER_SIZE) / 4;
	private static final int BIG_BUFFER = 2 * CORNER_SIZE;

	private static final String TAG = "ViewfinderView";

	private final Paint paint;
	private final int maskColor;
	private final int frameColor;
	private final int cornerColor;
	private Point screenResolution;
	private Rect rect;

	// This constructor is used when the class is built from an XML resource.
	public ViewfinderView(Context context, AttributeSet attrs) {
		super(context, attrs);

		// Initialize these once for performance rather than calling them every time in onDraw().
		paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		Resources resources = getResources();
		maskColor = resources.getColor(R.color.viewfinder_mask);
		frameColor = resources.getColor(R.color.viewfinder_frame);
		cornerColor = resources.getColor(R.color.viewfinder_corners);

		// set the screen resolution and framing rectangle
		setScreenResolution(context);
		rect = getFramingRect();
	}

	@Override
	public void onDraw(Canvas canvas) {
		if (rect != null) {
		
			int width = canvas.getWidth();
			int height = canvas.getHeight(); 
	
			// Draw the exterior (i.e. outside the framing rect) darkened
			paint.setColor(maskColor);
			canvas.drawRect(0, 0, width, rect.top, paint);
			canvas.drawRect(0, rect.top, rect.left, rect.bottom + 1, paint);
			canvas.drawRect(rect.right + 1, rect.top, width, rect.bottom + 1, paint);
			canvas.drawRect(0, rect.bottom + 1, width, height, paint);
	
			// Draw a two pixel solid border inside the framing rect
			paint.setAlpha(0);
			paint.setStyle(Style.FILL);
			paint.setColor(frameColor);
			canvas.drawRect(rect.left, rect.top, rect.right + 1, rect.top + 2, paint);
			canvas.drawRect(rect.left, rect.top + 2, rect.left + 2, rect.bottom - 1, paint);
			canvas.drawRect(rect.right - 1, rect.top, rect.right + 1, rect.bottom - 1, paint);
			canvas.drawRect(rect.left, rect.bottom - 1, rect.right + 1, rect.bottom + 1, paint);
	
			// Draw the framing rect corner UI elements
			paint.setColor(cornerColor);
			canvas.drawRect(rect.left - CORNER_SIZE, rect.top - CORNER_SIZE, rect.left + CORNER_SIZE, rect.top, paint);
			canvas.drawRect(rect.left - CORNER_SIZE, rect.top, rect.left, rect.top + CORNER_SIZE, paint);
			canvas.drawRect(rect.right - CORNER_SIZE, rect.top - CORNER_SIZE, rect.right + CORNER_SIZE, rect.top, paint);
			canvas.drawRect(rect.right, rect.top - CORNER_SIZE, rect.right + CORNER_SIZE, rect.top + CORNER_SIZE, paint);
			canvas.drawRect(rect.left - CORNER_SIZE, rect.bottom, rect.left + CORNER_SIZE, rect.bottom + CORNER_SIZE, paint);
			canvas.drawRect(rect.left - CORNER_SIZE, rect.bottom - CORNER_SIZE, rect.left, rect.bottom, paint);
			canvas.drawRect(rect.right - CORNER_SIZE, rect.bottom, rect.right + CORNER_SIZE, rect.bottom + CORNER_SIZE, paint);
			canvas.drawRect(rect.right, rect.bottom - CORNER_SIZE, rect.right + CORNER_SIZE, rect.bottom + CORNER_SIZE, paint);	
		}
	}

	/**
	 * Calculates the framing rect which the UI should draw to show the user where to place the
	 * barcode. This target forces the user to limit the recognition to the page.
	 *
	 * @return The rectangle to draw on screen in window coordinates.
	 */
	public synchronized Rect getFramingRect() {
		if (rect == null && screenResolution != null) {
			
			// Base the default viewer rectangle size on the display metrics (width) 
			int width = screenResolution.x * 3/5;
			if (width < MIN_FRAME_WIDTH) {
				width = MIN_FRAME_WIDTH;
			} else if (width > MAX_FRAME_WIDTH) {
				width = MAX_FRAME_WIDTH;
			}
			
			// Base the default viewer rectangle size on the display metrics (height) 
			int height = screenResolution.y * 1/5;
			if (height < MIN_FRAME_HEIGHT) {
				height = MIN_FRAME_HEIGHT;
			} else if (height > MAX_FRAME_HEIGHT) {
				height = MAX_FRAME_HEIGHT;
			}
			
			// Base the default viewer rectangle size on the display metrics (offset) 
			int leftOffset = (screenResolution.x - width) / 2;
			int topOffset = (screenResolution.y - height) / 2;
	
			// Create a new rectangle
			rect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
		}
		
		return rect;
	}
	
	public void drawViewfinder() {
		invalidate();
	}
	
	public synchronized void setScreenResolution(Context c){
		// Get the display metrics
		DisplayMetrics displayMetrics = new DisplayMetrics();
		WindowManager wm = (WindowManager) c.getApplicationContext().getSystemService(Context.WINDOW_SERVICE); 
		wm.getDefaultDisplay().getMetrics(displayMetrics);
		
		// Set the screen width and height
		screenResolution = new Point(displayMetrics.widthPixels, displayMetrics.heightPixels);
	}

	public synchronized void processMoveEvent(int lastX, int lastY, int currentX, int currentY){
		try { // Try to update the framing rectangle

			if (lastX >= 0) {
				// Adjust the size of the viewfinder rectangle. 
				// Check if the touch event occurs in the corner areas first, because the regions overlap.
				if (((currentX >= rect.left - BIG_BUFFER && currentX <= rect.left + BIG_BUFFER) 
							|| (lastX >= rect.left - BIG_BUFFER && lastX <= rect.left + BIG_BUFFER))
						&& ((currentY <= rect.top + BIG_BUFFER && currentY >= rect.top - BIG_BUFFER) 
							|| (lastY <= rect.top + BIG_BUFFER && lastY >= rect.top - BIG_BUFFER))) {
					
					// Top left corner: adjust both top and left sides
					adjustFramingRect((lastX - currentX), (lastY - currentY), 0, 0);
					
				} else if (((currentX >= rect.right - BIG_BUFFER && currentX <= rect.right + BIG_BUFFER) 
							|| (lastX >= rect.right - BIG_BUFFER && lastX <= rect.right + BIG_BUFFER)) 
						&& ((currentY <= rect.top + BIG_BUFFER && currentY >= rect.top - BIG_BUFFER) 
							|| (lastY <= rect.top + BIG_BUFFER && lastY >= rect.top - BIG_BUFFER))) {
					
					// Top right corner: adjust both top and right sides
					adjustFramingRect(0, (lastY - currentY), (currentX - lastX), 0);
					
				} else if (((currentX >= rect.left - BIG_BUFFER && currentX <= rect.left + BIG_BUFFER) 
							|| (lastX >= rect.left - BIG_BUFFER && lastX <= rect.left + BIG_BUFFER))
						&& ((currentY <= rect.bottom + BIG_BUFFER && currentY >= rect.bottom - BIG_BUFFER) 
							|| (lastY <= rect.bottom + BIG_BUFFER && lastY >= rect.bottom - BIG_BUFFER))) {
					
					// Bottom left corner: adjust both bottom and left sides
					adjustFramingRect((lastX - currentX), 0, 0, (currentY - lastY));
				
				} else if (((currentX >= rect.right - BIG_BUFFER && currentX <= rect.right + BIG_BUFFER) 
							|| (lastX >= rect.right - BIG_BUFFER && lastX <= rect.right + BIG_BUFFER)) 
						&& ((currentY <= rect.bottom + BIG_BUFFER && currentY >= rect.bottom - BIG_BUFFER) 
							|| (lastY <= rect.bottom + BIG_BUFFER && lastY >= rect.bottom - BIG_BUFFER))) {
					
					// Bottom right corner: adjust both bottom and right sides
					adjustFramingRect(0, 0, (currentX - lastX),(currentY - lastY));
					

				} else if (((currentX >= rect.left - BUFFER && currentX <= rect.left + BUFFER) 
							|| (lastX >= rect.left - BUFFER && lastX <= rect.left + BUFFER))
						&& ((currentY <= rect.bottom && currentY >= rect.top) || (lastY <= rect.bottom && lastY >= rect.top))) {
					
					// Adjusting left side: event falls within BUFFER pixels of left side, and between top and bottom side limits
					adjustFramingRect((lastX - currentX), 0, 0, 0);
					
				} else if (((currentX >= rect.right - BUFFER && currentX <= rect.right + BUFFER) 
							|| (lastX >= rect.right - BUFFER && lastX <= rect.right + BUFFER))
						&& ((currentY <= rect.bottom && currentY >= rect.top) || (lastY <= rect.bottom && lastY >= rect.top))) {
					
					// Adjusting right side: event falls within BUFFER pixels of right side, and between top and bottom side limits
					adjustFramingRect(0, 0, (currentX - lastX), 0);
					
				} else if (((currentY <= rect.top + BUFFER && currentY >= rect.top - BUFFER) 
							|| (lastY <= rect.top + BUFFER && lastY >= rect.top - BUFFER))
						&& ((currentX <= rect.right && currentX >= rect.left) || (lastX <= rect.right && lastX >= rect.left))) {
					
					// Adjusting top side: event falls within BUFFER pixels of top side, and between left and right side limits
					adjustFramingRect(0, (lastY - currentY), 0, 0);
				
				} else if (((currentY <= rect.bottom + BUFFER && currentY >= rect.bottom - BUFFER) 
							|| (lastY <= rect.bottom + BUFFER && lastY >= rect.bottom - BUFFER))
						&& ((currentX <= rect.right && currentX >= rect.left) || (lastX <= rect.right && lastX >= rect.left))) {
					
					// Adjusting bottom side: event falls within BUFFER pixels of bottom side, and between left and right side limits
					adjustFramingRect(0, 0, 0, (currentY - lastY));
				}  
			}
		} catch (NullPointerException e) {
			Log.e(TAG, "Framing rect not available", e);
		}

	}
	
	/**
	* Changes the size of the framing rect.
	* 
	* @param deltaLeft Number of pixels to adjust to the left
	* @param deltaTop Number of pixels to adjust to the top
	* @param deltaRight Number of pixels to adjust to the right
	* @param deltaBottom Number of pixels to adjust to the bottom
	*/
	public synchronized void adjustFramingRect(int deltaLeft, int deltaTop, int deltaRight, int deltaBottom) {
		if(rect != null && screenResolution != null){
			// Resize the rectangle
//			Log.d(TAG, "new size? " + ((rect.right + deltaRight) - ( rect.left - deltaLeft)) + "x" 
//			+ ((rect.bottom + deltaBottom) - (rect.top - deltaTop)));
			if(rect.right + deltaRight - ( rect.left - deltaLeft) > MIN_FRAME_WIDTH){
				if(rect.left - deltaLeft > CORNER_SIZE && rect.left - deltaLeft < rect.right ){ rect.left -= deltaLeft; } 
				if(rect.right + deltaRight < screenResolution.x - CORNER_SIZE && rect.right + deltaRight > rect.left){ 
					rect.right += deltaRight; 
				} 
			}
			
			if((rect.bottom + deltaBottom) - (rect.top - deltaTop) > MIN_FRAME_HEIGHT){
				if(rect.top - deltaTop > CORNER_SIZE && rect.top - deltaTop < rect.bottom){ rect.top -= deltaTop; } 
				if(rect.bottom + deltaBottom < screenResolution.y - CORNER_SIZE && rect.bottom + deltaBottom > rect.top) { 
					rect.bottom += deltaBottom; 
				}
			}			
		} else {
			Log.e(TAG, "Cannot adjust framing rect (adjustFramingRect). " + 
					(rect == null ? "rect is null " : "") +
					(screenResolution == null ? "screenResolution is null" : ""));
		}
	}
	
	public synchronized void setFramingRect(Rect newRect){
		rect = newRect;
	}
}
