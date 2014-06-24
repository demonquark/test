/*
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
package com.molatra.ocrtest.model;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/**
 * Encapsulates the result of OCR.
 */
public class OcrResult implements Parcelable{
  private Bitmap bitmap;
  private String text;
  public String longtext;
  
  private int[] wordConfidences;
  private int meanConfidence;

  private List<Rect> regionBoundingBoxes;
  private List<Rect> textlineBoundingBoxes;
  private List<Rect> wordBoundingBoxes;
  private List<Rect> stripBoundingBoxes;  
  private List<Rect> characterBoundingBoxes;

  private long timestamp;
  private long recognitionTimeRequired;
  
  private String TAG = "OcrResult";

  
  private OcrResult(Parcel in) {
	  // Note: you need to read the items in the same order that you wrote them
	  this.bitmap 					= in.readParcelable(getClass().getClassLoader());
	  this.text 					= in.readString();
	  this.wordConfidences			= in.createIntArray();
	  this.meanConfidence 			= in.readInt();
	  parcelableArrayToRectList(in.createTypedArray(Rect.CREATOR), regionBoundingBoxes);
	  parcelableArrayToRectList(in.createTypedArray(Rect.CREATOR), this.textlineBoundingBoxes);
	  parcelableArrayToRectList(in.createTypedArray(Rect.CREATOR), this.wordBoundingBoxes);
	  parcelableArrayToRectList(in.createTypedArray(Rect.CREATOR), this.stripBoundingBoxes);
	  parcelableArrayToRectList(in.createTypedArray(Rect.CREATOR), this.characterBoundingBoxes);
	  this.timestamp				= in.readLong();
	  this.recognitionTimeRequired	= in.readLong(); 
  }

  // this is used to regenerate your object.
  public static final Parcelable.Creator<OcrResult> CREATOR = new Parcelable.Creator<OcrResult>() {
      public OcrResult createFromParcel(Parcel in) { return new OcrResult(in); }
      public OcrResult[] newArray(int size) { return new OcrResult[size]; }
  };

  @Override public int describeContents() { return 0; }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
	  // Note: you need to read the items in the same order that you wrote them
	  dest.writeParcelable(bitmap, 0);
	  dest.writeString(text);
	  dest.writeIntArray(wordConfidences != null ? wordConfidences : new int [0]);
	  dest.writeInt(meanConfidence);
	  dest.writeParcelableArray(rectListToParcelableArray(regionBoundingBoxes), 0);
	  dest.writeParcelableArray(rectListToParcelableArray(textlineBoundingBoxes), 0);
	  dest.writeParcelableArray(rectListToParcelableArray(wordBoundingBoxes), 0);
	  dest.writeParcelableArray(rectListToParcelableArray(stripBoundingBoxes), 0);
	  dest.writeParcelableArray(rectListToParcelableArray(characterBoundingBoxes), 0);
	  dest.writeLong(timestamp);
	  dest.writeLong(recognitionTimeRequired);
  }
  
  private void parcelableArrayToRectList(Parcelable [] source, List <Rect> destination){
	  
	  // Make sure we have a list 
	  if(destination == null){ destination = new ArrayList <Rect> (); }
	  
	  // Cast each parcelable to a Rect
	  for(Parcelable t : source){ 
		  try {
			  destination.add((Rect) t);
		  } catch (ClassCastException e){
			  Log.e(TAG, "Could not cast " + t.toString() + " to Rect.");
		  }
	  }
  }
  
  private Rect [] rectListToParcelableArray(List <Rect> source){
	  
	  // Make sure we have a list 
	  if(source == null){ source = new ArrayList <Rect> (); }
	  Rect [] rectArray = new Rect [source.size()];
	  
	  // Copy each Rect to the array
	  int i = 0;
	  for(Rect r : source){ 
		  rectArray[i] = r;
	  }
	  
	  return rectArray;
  }
  
  public OcrResult(Bitmap bitmap,
                   String text,
                   int[] wordConfidences,
                   int meanConfidence,
                   List<Rect> regionBoundingBoxes,
                   List<Rect> textlineBoundingBoxes,
                   List<Rect> wordBoundingBoxes,
                   List<Rect> stripBoundingBoxes,
                   List<Rect> characterBoundingBoxes,
                   long recognitionTimeRequired) {
    this.bitmap = bitmap;
    this.text = text;
    this.wordConfidences = wordConfidences;
    this.meanConfidence = meanConfidence;
    this.regionBoundingBoxes = regionBoundingBoxes;
    this.textlineBoundingBoxes = textlineBoundingBoxes;
    this.wordBoundingBoxes = wordBoundingBoxes;
    this.stripBoundingBoxes = stripBoundingBoxes;
    this.characterBoundingBoxes = characterBoundingBoxes;
    this.recognitionTimeRequired = recognitionTimeRequired;
    this.timestamp = System.currentTimeMillis();
    
  }

  public OcrResult() {
    timestamp = System.currentTimeMillis();
  }

  public Bitmap getBitmap() {
    return getAnnotatedBitmap();
  }
  
  private Bitmap getAnnotatedBitmap() {
    Canvas canvas = new Canvas(bitmap);
    Paint paint = new Paint();
    
    if(wordBoundingBoxes != null  && bitmap != null && characterBoundingBoxes != null){
        // Draw bounding boxes around each word
        for (Rect r : wordBoundingBoxes) {
          paint.setAlpha(0xFF);
          paint.setColor(0xFF00CCFF);
          paint.setStyle(Style.STROKE);
          paint.setStrokeWidth(2);
          canvas.drawRect(r, paint);
        }    
          
        // Draw bounding boxes around each character
        for (Rect r : characterBoundingBoxes) {
          paint.setAlpha(0xA0);
          paint.setColor(0xFF00FF00);
          paint.setStyle(Style.STROKE);
          paint.setStrokeWidth(3);
          canvas.drawRect(r, paint);
        }
    }
    
    return bitmap;
  }
  
  public String getText() {
    return text;
  }

  public int[] getWordConfidences() {
    return wordConfidences;
  }

  public int getMeanConfidence() {
    return meanConfidence;
  }

  public long getRecognitionTimeRequired() {
    return recognitionTimeRequired;
  }

  public Point getBitmapDimensions() {
    return new Point(bitmap.getWidth(), bitmap.getHeight()); 
  }
  
  public List<Rect> getRegionBoundingBoxes() {
    return regionBoundingBoxes;
  }
  
  public List<Rect> getTextlineBoundingBoxes() {
    return textlineBoundingBoxes;
  }
  
  public List<Rect> getWordBoundingBoxes() {
    return wordBoundingBoxes;
  }
  
  public List<Rect> getStripBoundingBoxes() {
  	return stripBoundingBoxes;
  }
  
  public List<Rect> getCharacterBoundingBoxes() {
    return characterBoundingBoxes;
  }
  
  public long getTimestamp() {
    return timestamp;
  }
  
  public void setBitmap(Bitmap bitmap) {
    this.bitmap = bitmap;
  }
  
  public void setText(String text) {
    this.text = text;
  }

  public void setWordConfidences(int[] wordConfidences) {
    this.wordConfidences = wordConfidences;
  }

  public void setMeanConfidence(int meanConfidence) {
    this.meanConfidence = meanConfidence;
  }

  public void setRecognitionTimeRequired(long recognitionTimeRequired) {
    this.recognitionTimeRequired = recognitionTimeRequired;
  }
  
  public void setRegionBoundingBoxes(List<Rect> regionBoundingBoxes) {
    this.regionBoundingBoxes = regionBoundingBoxes;
  }
  
  public void setTextlineBoundingBoxes(List<Rect> textlineBoundingBoxes) {
    this.textlineBoundingBoxes = textlineBoundingBoxes;
  }

  public void setWordBoundingBoxes(List<Rect> wordBoundingBoxes) {
    this.wordBoundingBoxes = wordBoundingBoxes;
  }
  
  public void setStripBoundingBoxes(List<Rect> stripBoundingBoxes) {
  	this.stripBoundingBoxes = stripBoundingBoxes;
  }

  public void setCharacterBoundingBoxes(List<Rect> characterBoundingBoxes) {
    this.characterBoundingBoxes = characterBoundingBoxes;
  }
  
  @Override
  public String toString() {
    return text + " " + meanConfidence + " " + recognitionTimeRequired + " " + timestamp;
  }

}
