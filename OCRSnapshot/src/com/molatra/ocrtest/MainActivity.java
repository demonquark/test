package com.molatra.ocrtest;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import com.molatra.ocrtest.PhoneOrientationListener.PhoneRotationListener;
import com.molatra.ocrtest.model.OcrResult;
import com.molatra.ocrtest.utils.BitmapTools;
import com.molatra.ocrtest.utils.FileManager;
import com.molatra.ocrtest.utils.Gegevens;
import com.molatra.ocrtest.views.RotateImageView;
import com.molatra.ocrtest.views.RotateLayout;
import com.molatra.ocrtest.views.ShutterButton;
import com.molatra.ocrtest.views.ShutterButton.OnShutterButtonListener;
import com.molatra.ocrtest.views.ViewfinderView;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.provider.MediaStore;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.hardware.SensorManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends OCRBaseActivity implements OnShutterButtonListener, PhoneRotationListener {
	
	private static final String TAG = MainActivity.class.getSimpleName();
	
    public enum State {
    	NOPICTURE,
    	IDLE,
    	BUSY,
    	DONE
    }
    
    /** Target we publish for clients to send messages to IncomingHandler. */
    final Messenger mMessenger = new Messenger(new IncomingHandler(this));

	private ViewfinderView mViewFinderView;
	private RotateLayout mProgressContainer;
	private LinearLayout mProgressLayout;
	private TextView mProgressText;
	private RotateLayout mResultContainer;
	private ScrollView mScrollView;
	private RotateImageView mSourceImageView;
	private ImageView mResultImageView;
	private TextView mResultTextView;
	private TextView mResultDetailTextView;
	private ShutterButton mCameraButton;
	private ShutterButton mOCRButton;
	private Button mResultDetailButton;
	private PhoneOrientationListener mOrientationListener;
		
	private State mState;
	private String mSourceImagePath;
	private static long start1;
	private boolean installationVerified = false;
	
    /** Handler of incoming messages from service. */
    static private class IncomingHandler extends Handler {
	    private final WeakReference<MainActivity> mActivity; 

	    IncomingHandler(MainActivity activity) {
	    	mActivity = new WeakReference<MainActivity>(activity);
	    }

	    @Override
        public void handleMessage(Message msg) {
	    	
	    	// Make sure the activity exists
	    	if(mActivity.get() != null){
	            switch (msg.what) {
                case OCRService.MSG_ERROR:
                case OCRService.MSG_REPLY:
                	mActivity.get().handleReplyText(msg.what, msg.arg1, msg.arg2, msg.obj.toString());
                    break;
                case OCRService.MSG_PROGRESS:
                	mActivity.get().handleProgressText(msg.what, msg.arg1, msg.arg2, msg.obj.toString());
                    break;
                case OCRService.MSG_RESULT:
                	if(msg.obj instanceof OcrResult){
                		mActivity.get().handleOCRResult((OcrResult) msg.obj);
                	} else {
                		mActivity.get().handleReplyText(OCRService.MSG_ERROR, msg.arg1, msg.arg2, 
                				mActivity.get().getString(R.string.error_invalid_recognition));
                	}
                    break;
                case OCRService.MSG_RESULT_TXT:
                	mActivity.get().mResultImageView.setImageResource(R.drawable.aiqingshenghuo);
            		mActivity.get().mSourceImageView.invalidate();
            		mActivity.get().mSourceImageView.refreshDrawableState();
                	mActivity.get().mResultTextView.setText(String.valueOf(msg.obj));
                	mActivity.get().mResultDetailTextView.setText("");
                	mActivity.get().setState(State.DONE);
                	break;
                default:
    	    		Log.e(TAG,"IncomingHandler: Unexpected message ID. what = " + msg.what);	    		
                    super.handleMessage(msg);
	            }
	    	} else {
	    		Log.e(TAG,"IncomingHandler: Activity reference is null.");	    		
	    	}
        }
    }

	@Override protected void onCreate(Bundle savedInstanceState) {
		Log.v(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_ocr);
		setTag(TAG);
		
		// Load the views (and assign listeners)
		loadViews();
		
		// Assign values to the class variables (This avoids facing null exceptions when creating / saving a Parcelable)
		// convert the saved state to an empty bundle to avoid errors later on
		Bundle savedstate = (savedInstanceState != null) ? savedInstanceState : new Bundle();
		
		// load the image path and state (Note: they can be null)
		mState = (State) savedstate.getSerializable(Gegevens.EXTRA_STATE);
		mSourceImagePath = savedstate.getString(Gegevens.EXTRA_PATH);
		
		// Bind to the OCR service
		doBindService();
		
		// Set the captured image
		if(mSourceImagePath != null){ 
			Log.v(TAG, "onCreate: mSourceImagePath is not null.");
			setSourceImageView(mSourceImagePath);
		} else {
			Log.v(TAG, "onCreate: mSourceImagePath is null.");
			// Set the default state
			if(mState == null){ Log.v(TAG, "onCreate: mState is null."); mState = State.NOPICTURE; }
			setState(mState);
		}
	}
	
	@Override public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Log.v(TAG, "onSaveInstanceState");

		// Save the messenger and the image path
		if(mState != null) { outState.putSerializable(Gegevens.EXTRA_STATE, mState); }
		if(mSourceImagePath != null) { outState.putString(Gegevens.EXTRA_PATH, mSourceImagePath); }
	}

	
	@Override public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.ocr, menu);
		return true;
	}

	@Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		switch(v.getId()){
		case R.id.shutter_button:
			getMenuInflater().inflate(R.menu.context_shutter, menu);
			break; 
		case R.id.ocr_result_text_view:
			getMenuInflater().inflate(R.menu.context_result_text, menu);
			break; 
		}
	}

    /**
     *  Handles the context menu requests. 
     *  Context menu for the shutter button: <br />
     *  - if from gallery, forward an intent call to the gallery <br />
     *  - if from camera, forward an intent call to the camera <br />
     *  - if continuous, open the CaptureActivity <br />
     *  The results are handled in onActivityResult()
     *  Context menu for the result text views:
     *  - if context_result_copy, copy the text to the clip board
     */
	@SuppressWarnings("deprecation")
	@Override public boolean onContextItemSelected(MenuItem item) {
		int id = item.getItemId();
		switch(id){
		case R.id.context_img_from_gallery:
		case R.id.context_img_from_camera:
			handlePicture(id);
			break;
		case R.id.context_img_continuous:
			startActivity(new Intent(this, CaptureActivity.class));
			break;
		case R.id.context_result_copy:
		    ((android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setText(mResultTextView.getText());
		    toastMessage(getString(R.string.text_copied), Gravity.BOTTOM);
		default:
			return super.onContextItemSelected(item);	
		}
		return true;
	}

	@Override protected void onResume() { 
    	Log.v(TAG, "onResume: " + getResources().getConfiguration().orientation);
		super.onResume();
		// enable orientation detection
		if (mOrientationListener != null && mOrientationListener.canDetectOrientation()){ 
			mOrientationListener.enable(); 
		}
	}
	
    @Override public void onDestroy() {
    	Log.v(TAG, "onDestroy");
    	doUnbindService();
    	super.onDestroy();
    }
    
    @Override public void onBackPressed(){
    	if(mState == State.DONE){
    		setState(State.IDLE);
    	} else if (mState == State.BUSY){
			// Cancel ongoing task and go back to the previous Activity (MainActivity)
			Message msg = Message.obtain(null, OCRService.MSG_CANCEL);
			msg.replyTo = mMessenger;
			sendMessage(msg);
    	} else {
        	super.onBackPressed();
    	}
    }
    
	private void loadViews(){
		
		// Load the views
		mSourceImageView = (RotateImageView) findViewById(R.id.image_view);
		mResultImageView = (ImageView) findViewById(R.id.ocr_result_image_view);
		mResultTextView = (TextView) findViewById(R.id.ocr_result_text_view);
		mResultDetailTextView = (TextView) findViewById(R.id.ocr_result_detail_text_view);
		mViewFinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
		mProgressContainer = (RotateLayout) findViewById(R.id.progress_rotate);
		mProgressText = (TextView) findViewById(R.id.progress_text);
		mResultContainer = (RotateLayout) findViewById(R.id.result_rotate);
		mCameraButton = (ShutterButton) findViewById(R.id.shutter_button);
		mOCRButton = (ShutterButton) findViewById(R.id.ocr_button);
		mProgressLayout = (LinearLayout) findViewById(R.id.progress_layout);
		mScrollView = (ScrollView) findViewById(R.id.result_scrollview);
		mResultDetailButton = (Button) findViewById(R.id.ocr_result_detail_button);
		
		// Set listener to detect a shutter button click
		mCameraButton.setOnShutterButtonListener(this);
		mOCRButton.setOnShutterButtonListener(this);
		
		// Set the context menu listeners
		registerForContextMenu(mResultTextView);
		
		mOrientationListener = new PhoneOrientationListener(this, SensorManager.SENSOR_DELAY_NORMAL, this);
		
		// Set listener to change the size of the viewfinder rectangle.
		mViewFinderView.setOnTouchListener(new View.OnTouchListener() {
			
			// Do not allow for more than 4 simulations touch events (i.e. onlly register up to four fingers)
			int [] previousX = new int [4];
			int [] previousY = new int [4];

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						// First touch event set all the pointers to -1
						for(int i =0; i < 4; i++){ previousX[i] = -1; previousY[i] = -1; }
						return true;
					case MotionEvent.ACTION_MOVE:
						for(int i = 0; i < event.getPointerCount(); i++){
							
							// Change the framing rate and 
							mViewFinderView.processMoveEvent(previousX[i], previousY[i],
									(int) event.getX(i), (int) event.getY(i));

							// Reset the x and y values
							previousX[i] = (int) event.getX(i);
							previousY[i] = (int) event.getY(i);
						}

						// redraw the view finder
						v.invalidate();

						return true;
					
					case MotionEvent.ACTION_UP:
						// Last touch event set all the pointers to -1
						for(int i =0; i < 4; i++){ previousX[i] = -1; previousY[i] = -1; }
						return true;
				}
				
				// Ignore other actions
				return false;
			}
		});
	}
    
	private void setState(State newState){
		mState = newState;
		switch(mState){
		case NOPICTURE:
			mViewFinderView.setVisibility(View.INVISIBLE);
			mCameraButton.setVisibility(View.VISIBLE);
			mOCRButton.setVisibility(View.INVISIBLE);
			mSourceImageView.setVisibility(View.INVISIBLE);
			mProgressContainer.setVisibility(View.INVISIBLE);
			mResultContainer.setVisibility(View.INVISIBLE);
			break;
		case IDLE:
			mViewFinderView.setVisibility(View.VISIBLE);
			mCameraButton.setVisibility(View.VISIBLE);
			mOCRButton.setVisibility(View.VISIBLE);
			mSourceImageView.setVisibility(View.VISIBLE);
			mProgressContainer.setVisibility(View.INVISIBLE);
			mResultContainer.setVisibility(View.INVISIBLE);
			break;
		case BUSY:
			mViewFinderView.setVisibility(View.INVISIBLE);
			mCameraButton.setVisibility(View.INVISIBLE);
			mOCRButton.setVisibility(View.INVISIBLE);
			mSourceImageView.setVisibility(View.INVISIBLE);
			mProgressContainer.setVisibility(View.VISIBLE);
			mResultContainer.setVisibility(View.INVISIBLE);
			break;
		case DONE:
			mViewFinderView.setVisibility(View.INVISIBLE);
			mCameraButton.setVisibility(View.INVISIBLE);
			mOCRButton.setVisibility(View.INVISIBLE);
			mSourceImageView.setVisibility(View.INVISIBLE);
			mProgressContainer.setVisibility(View.INVISIBLE);
			mResultContainer.setVisibility(View.VISIBLE);
			break;
		default:
			break;
		}
	}

	private void setSourceImageView(String imagePath){
    	Log.v(TAG, "setSourceImageView: imagePath is " + imagePath);
    	
    	if(imagePath != null) {
    		mSourceImagePath = imagePath;
    		
    		// Decode the image to fit the screen
    		DisplayMetrics dm = getDisplayMetrics();
    		Bitmap sourceImage = BitmapTools.decodeSampledBitmapFromResource(imagePath, dm.widthPixels, dm.heightPixels);

        	// load the image into the image view
    		if(sourceImage != null){
        		mSourceImageView.setImageBitmap(sourceImage);
    			mSourceImageView.invalidate();
    			mSourceImageView.refreshDrawableState();
    			mSourceImagePath = imagePath;
        	}
    	} 
    	
    	// change the view to the correct view
    	setState((imagePath != null) ? State.IDLE : State.NOPICTURE);
    }
    
	private void handleOCR() {
		Log.v(TAG, "handleOCR");
		start1 = System.currentTimeMillis();
		if(mSourceImagePath != null){
			Log.v(TAG, "handleOCR : width: " + mSourceImageView.getWidth() + " | height: " +  mSourceImageView.getHeight());
			new CropImageAsyncTask(mSourceImagePath, mSourceImageView.isRotated(),
					mViewFinderView.getFramingRect(), mSourceImageView.getWidth(), mSourceImageView.getHeight(), 
					mOrientationListener.getRotation(), 100){
				
				@Override protected void onPreExecute() {
					mProgressText.setText("Cropping image...");
					setState(State.BUSY);
				}
				
				@Override protected void onPostExecute(Bitmap result) {
					Log.v(TAG,"recognize: (1) Bitmap is " + result);
					
					boolean success = (result != null);

					if(success){ 
						// Send a message
						Message msg = Message.obtain(null, OCRService.MSG_RECOGNIZE);
						msg.replyTo = mMessenger;
						msg.obj = result;
						success = sendMessage(msg);
						mProgressText.setText(getString(R.string.busy_recognizing));
					}
					
					if(!success){
						setState(State.IDLE);
						toastMessage("Image not cropped.");
					} 
				}
			}.execute();
		} else {
			toastMessage("Image not found");
		}
	}

	private void handlePicture(int id) {
		switch(id){
		case R.id.context_img_from_gallery:
			// Pick an image from the gallery
			Intent i = new Intent(Intent.ACTION_PICK,android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
			startActivityForResult(i, Gegevens.CODE_GALLERY);
			break;
		case R.id.context_img_from_camera:
		    // Take an image with the camera
			try {
				// Create the file URI
				File image = new File(FileManager.getApplicationFileDir(this), Gegevens.FILE_CAMERAIMG + Gegevens.FILE_EXT_PNG);
				if(!image.exists())
					image.createNewFile();
				Uri mCapturedImageURI = Uri.fromFile(image);
				
				// start a camera activity
				Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE); 
				cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCapturedImageURI);
		        startActivityForResult(cameraIntent, Gegevens.CODE_CAMERA); 

			} catch (IOException e) {
				toastMessage("Error. Cannot save camera image to disk.");
				e.printStackTrace();
			}
			break;
		}
	}
	
	private void handlePictureResult(int id, Intent data) {
		// Reset the captured image
		String capturedImagePath = null;
		
		switch(id){
			case Gegevens.CODE_GALLERY:
				// Get the image file path from the data
				if(data != null && data.getData() != null){
					String[] filePathColumn = {MediaStore.Images.Media.DATA};
					Cursor cursor = getContentResolver().query(data.getData(), 
							filePathColumn, null, null, null);
					if(cursor != null){
						try{ 
							if(cursor.moveToFirst()){
								capturedImagePath = cursor.getString(cursor.getColumnIndex(filePathColumn[0]));
								Log.v(TAG, "handlePictureResult: CODE_GALLERY " + capturedImagePath);
							}
						} finally{ 
							cursor.close(); 
						}
					}
				}
				break;
			case Gegevens.CODE_CAMERA:
				toastMessage("Image captured");
				// Use the default image path (created in handlePicture())
				capturedImagePath = new File(FileManager.getApplicationFileDir(this), 
						Gegevens.FILE_CAMERAIMG + Gegevens.FILE_EXT_PNG).getAbsolutePath();
				Log.v(TAG, "handlePictureResult: CODE_CAMERA " + capturedImagePath);
				break;
			default:
				toastMessage("Not sure why that was successful");
				break;
		}
		
		// Update the image view
		setSourceImageView(capturedImagePath);
	}
	
	private void handleReplyText(int msgCode, int requestCode, int serviceState, String message){
		Log.v(TAG, "handleReplyText. messageCode is " + msgCode + ". requestCode is " + requestCode);
		
		if(requestCode == OCRService.MSG_INSTALL_CHECK){
			// Install checks require a different use case path
			handleInstallCheck(msgCode, message);
		} else {

			// Errors and initialization completion should exit the busy state 
			if(msgCode == OCRService.MSG_ERROR || requestCode == OCRService.MSG_INITIALIZE){
				setSourceImageView(mSourceImagePath);
			}
			
			// Everything should toast the reply
			toastMessage(message);
		}
	}
	/**
	 * The installation check is verify that the Tesseract data files are present.
	 * If they are present, continue by showing the default camera button options
	 * If they are not present, ask the user to download the files before continuing 
	 * 
	 * @param msgCode		Should be MSG_ERROR (if verification failed) or MSG_REPLY (if verification succesful)
	 * @param message		Message so we can inform the user of what is happening
	 */
	private void handleInstallCheck(int msgCode, String message){
		switch(msgCode){
		case OCRService.MSG_ERROR:
			// Move to the busy state and show the first time installation text
			setState(State.BUSY);
			findViewById(R.id.progress_bar).setVisibility(View.INVISIBLE);
			mProgressText.setText(getString(R.string.intro_download_warning));
			findViewById(R.id.progress_intro_download_btn).setVisibility(View.VISIBLE);
			
			break;
		case OCRService.MSG_REPLY:
			installationVerified = true;
			onShutterButtonClick(mCameraButton);
			break;
		default:
			break;
		}
		
	}
	
	private void handleOCRResult(OcrResult result){
    	Log.i(TAG, "Recognize result (" + result.toString() + "): " + result.getText());
		
    	// Show an image of the recognized words and symbols
    	if(result.getBitmap() != null){ 
	    	Log.v(TAG,"recognize: (7) Bitmap is " + result.getBitmap());
    		mResultImageView.setImageBitmap(result.getBitmap());
    		mSourceImageView.invalidate();
    		mSourceImageView.refreshDrawableState();
    	}
    	
    	// Show the result text
		mResultTextView.setText(result.getText());
		mResultDetailTextView.setText(result.longtext 
				+ "\n" + result.getRecognitionTimeRequired() + "ms" 
				+ " | " + (System.currentTimeMillis() - start1) + "ms");
    	
		// Hide the detailed text
		mResultDetailButton.setText(getString(R.string.result_detail_show_btn));
		mResultDetailTextView.setVisibility(View.GONE);
		
		setState(State.DONE);
    	result = null;
	}
	
	private void handleProgressText(int msgCode, int requestCode, int serviceState, String message){
		mProgressText.setText(message);
	}

	private DisplayMetrics getDisplayMetrics(){
		DisplayMetrics displaymetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
		return displaymetrics;
	}
	
	@SuppressWarnings("deprecation")
	public void onBtnClick(View view){
		int id = view.getId();
		switch(id){
			case R.id.ocr_result_copy_button:
			    ((android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setText(mResultTextView.getText());
			    toastMessage(getString(R.string.text_copied), Gravity.BOTTOM);
				break;
			case R.id.ocr_result_detail_button:
				if(mResultDetailTextView.getVisibility() == View.GONE){
					mResultDetailButton.setText(getString(R.string.result_detail_hide_btn));
					mResultDetailTextView.setVisibility(View.VISIBLE);
				} else {
					mResultDetailButton.setText(getString(R.string.result_detail_show_btn));
					mResultDetailTextView.setVisibility(View.GONE);
				}
				break;
			case R.id.progress_intro_download_btn:
				// Clean up the busy state views 
				findViewById(R.id.progress_bar).setVisibility(View.VISIBLE);
				mProgressText.setText(getString(R.string.niets));
				findViewById(R.id.progress_intro_download_btn).setVisibility(View.GONE);
				
				// Initialize the OCR engine (will automatically download the Tesseract files)
				Message msg = Message.obtain(null, OCRService.MSG_INITIALIZE);
				msg.replyTo = mMessenger;
				msg.arg1 = 0;				// Do NOT reinitialize the engine 
				msg.obj = Gegevens.EXTRA_LANGUAGE;
				if(!sendMessage(msg)){
					toastMessage("Could not contact the OCR engine. Please restart the app.");	
				}
				break;
		}
	}

	@Override public void onShutterButtonFocus(ShutterButton b, boolean pressed) {
		Log.v(TAG, "onShutterButtonFocus");
	}

	@Override public void onShutterButtonClick(ShutterButton b) {
		Log.v(TAG, "onShutterButtonClick");
		switch(b.getId()){
			case R.id.shutter_button:
				if(installationVerified){
					registerForContextMenu(mCameraButton); 
					mCameraButton.showContextMenu();
				    unregisterForContextMenu(mCameraButton);
				} else {
					// Verify the installation
					Message msg = Message.obtain(null, OCRService.MSG_INSTALL_CHECK);
					msg.replyTo = mMessenger;
					if(!sendMessage(msg)){
						toastMessage("Could not contact the OCR engine. Please restart the app.");
					}
				}
				break;
			case R.id.ocr_button:
				handleOCR();
				break;
		}
	}
	
    @Override public void onPhoneRotated(int rotation) {
    	Log.d(TAG, "Curretion orientation is:" + rotation);
    	
		// update the rotation of the buttons 
		if(mOCRButton != null){ mOCRButton.rotation = rotation; mOCRButton.invalidate(); }
		if(mCameraButton != null){ mCameraButton.rotation = rotation; mCameraButton.invalidate(); }
		
		// Get the angle of rotation for the text views
		int rotationAngle = getRotationAngleForBitmap(rotation);

		// Update the layout parameters of the Progress bar
		RotateLayout.LayoutParams x = (RotateLayout.LayoutParams) mProgressLayout.getLayoutParams();
		x.setAngle(rotationAngle);
		mProgressLayout.setLayoutParams(x);
		
		// Update the layout parameters of the results view
		RotateLayout.LayoutParams y = (RotateLayout.LayoutParams) mScrollView.getLayoutParams();
		y.setAngle(rotationAngle);
		mScrollView.setLayoutParams(y);

	}
	
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.v(TAG, "onActivityResult.");
		if(resultCode == RESULT_OK) {
			handlePictureResult(requestCode, data);
		} else {
			toastMessage("Image not captured");
			setSourceImageView(null);
		}
	}
}