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

import java.util.List;

import com.molatra.ocrtest.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.View;

/**
 * This view is overlaid on top of the displayed bitmap. It adds the viewfinder rectangle and partial
 * transparency outside it.
 *
 * The code for this class was adapted from the ZXing project: http://code.google.com/p/zxing
 */
public final class ResultsOverlayView extends View {

	private static final String TAG = "ViewfinderView";

	private final Paint paint;
	private final int wordColor;
	private final int characterColor;
	private Rect frame;
	private int rotation;
	private List<Rect> wordBoundingBoxes;
	private List<Rect> characterBoundingBoxes;

	// This constructor is used when the class is built from an XML resource.
	public ResultsOverlayView(Context context, AttributeSet attrs) {
		super(context, attrs);

		// Initialize these once for performance rather than calling them every time in onDraw().
		paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		Resources resources = getResources();
		wordColor = resources.getColor(R.color.blue);
		characterColor = resources.getColor(R.color.green);
	}

	@Override
	public void onDraw(Canvas canvas) {
		Log.d(TAG, "onDraw");
		if (frame != null) {		
			if(wordBoundingBoxes != null){
				Log.v(TAG,"draw word bounding box");
				paint.setAlpha(0xA0);
				paint.setColor(wordColor);
				drawBoxes(canvas, paint, wordBoundingBoxes);
			}
			
			if(characterBoundingBoxes != null){
				Log.v(TAG,"draw character bounding box");
				paint.setAlpha(0xA0);
				paint.setColor(characterColor);
				drawBoxes(canvas, paint, characterBoundingBoxes);
			}
		}
	}
	
	private void drawBoxes(Canvas canvas, Paint paint, List<Rect> boxes){
		Rect rect;
		for (int i = 0; i < boxes.size(); i++) {
			paint.setStyle(Style.STROKE);
			paint.setStrokeWidth(1);
			rect = boxes.get(i);
			rect = rotateBox(rect);
//			Log.v(TAG,"original: (" + boxes.get(i).left + "," + boxes.get(i).top 
//					+ ") (" + boxes.get(i).right + "," + boxes.get(i).bottom + ")" 
//					+ " | frame: (" + frame.left + "," + frame.top + ")"
//					+ " | drawing: (" + (frame.left + rect.left) + "," + (frame.top + rect.top) + ") (" 
//					+ (frame.left + rect.right) + "," + (frame.top + rect.bottom) + ")");
			canvas.drawRect(frame.left + rect.left, frame.top + rect.top, 
				frame.left + rect.right,  frame.top + rect.bottom, paint);
		}
	}
	
	private Rect rotateBox(Rect box){
		
		int width = frame.width();
		int height = frame.height();
		
		switch(rotation){
			case Surface.ROTATION_90:	// landscape
				return box;
			case Surface.ROTATION_180:	// upside down portrait
				return new Rect((width - box.bottom), box.left, (width - box.top), box.right);
			case Surface.ROTATION_270:	// upside down landscape
				return new Rect((width - box.right), (height - box.bottom), (width - box.left), (height - box.top));
			case Surface.ROTATION_0:	// portrait
				return new Rect(box.top, (height - box.right), box.bottom, (height - box.left));
		}

		Log.e(TAG, "drawBoxes: Unknown rotation. provided box not rotated.");
		return box;
	}
	
	public synchronized Rect getFramingRect() {
		return frame;
	}
	
	public void drawViewfinder() {
		invalidate();
	}
	
	public synchronized void setRotation(int rotation){
		this.rotation = rotation;
	}
	
	public synchronized void setFramingRect(Rect newRect){
		frame = newRect;
	}
	
	public void setWordBoundingBoxes(List<Rect> boxes){
		this.wordBoundingBoxes = boxes;
	}

	public void setCharacterBoundingBoxes(List<Rect> boxes){
		this.characterBoundingBoxes = boxes;
	}
}
