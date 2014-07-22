package com.molatra.ocrtest;

import java.lang.ref.WeakReference;

import com.molatra.ocrtest.PhoneOrientationListener.PhoneRotationListener;
import com.molatra.ocrtest.model.OcrResult;
import com.molatra.ocrtest.utils.BitmapTools;
import com.molatra.ocrtest.utils.Gegevens;
import com.molatra.ocrtest.views.RotateImageView;
import com.molatra.ocrtest.views.RotateLayout;
import com.molatra.ocrtest.views.ShutterButton;
import com.molatra.ocrtest.views.ShutterButton.OnShutterButtonListener;
import com.molatra.ocrtest.views.ViewfinderView;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class OCRActivity extends Activity implements OnShutterButtonListener, PhoneRotationListener {
	
	private static final String TAG = "OCRActivity";
	
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
	private PhoneOrientationListener mOrientationListener;
	
	private Uri mCapturedImageURI;
	private State mState;
    private Messenger mService = null;
	
    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
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
    
    /**
     * Handler of incoming messages from service.
     */
    static class IncomingHandler extends Handler {
	    private final WeakReference<OCRActivity> mActivity; 

	    IncomingHandler(OCRActivity activity) {
	    	mActivity = new WeakReference<OCRActivity>(activity);
	    }

	    @Override
        public void handleMessage(Message msg) {
	    	
	    	// Make sure the activity exists
	    	if(mActivity.get() != null){
	            switch (msg.what) {
                case OCRService.MSG_PROGRESS:
                	mActivity.get().mProgressText.setText(msg.obj.toString());
                    break;
                case OCRService.MSG_REPLY_INIT:
                	mActivity.get().setState(mActivity.get().mCapturedImageURI == null ? State.NOPICTURE : State.IDLE);
                	mActivity.get().toastMessage(msg.obj.toString());
                    break;
                case OCRService.MSG_REPLY_RECOGNIZE:
                	if(msg.obj instanceof OcrResult){
                		OcrResult result = (OcrResult) msg.obj;
                		mActivity.get().mResultImageView.setImageBitmap(result.getBitmap());
                		mActivity.get().mSourceImageView.invalidate();
                		mActivity.get().mSourceImageView.refreshDrawableState();
                		mActivity.get().mResultTextView.setText(result.getText());
                		mActivity.get().mResultDetailTextView.setText(result.longtext);
                    	mActivity.get().setState(State.DONE);
                	} else {
                    	mActivity.get().toastMessage("Invalid reply from recognize");
                    	mActivity.get().setState(mActivity.get().mCapturedImageURI == null ? State.NOPICTURE : State.IDLE);
                	}
                    break;
                case OCRService.MSG_REPLY_GENERAL:
                	mActivity.get().toastMessage("State is: " + msg.obj.toString());
                	mActivity.get().setState(mActivity.get().mCapturedImageURI == null ? State.NOPICTURE : State.IDLE);
                	break;
                default:
                    super.handleMessage(msg);
	            }
	    	} else {
	    		Log.e(TAG,"IncomingHandler: Activity reference is null.");	    		
	    	}
        }
    }
    
    private class CropImage extends AsyncTask<Uri, Void, Bitmap> {

    	private Context context;
    	private int rotationAngle;
        public CropImage(Context context, int rotationAngle) {
        	this.context = context;
        	this.rotationAngle = rotationAngle;
        }

        @Override
        protected void onPreExecute() {
        	setState(State.BUSY);
        	mProgressText.setText("Cropping image...");
        }
        
		@Override
		protected Bitmap doInBackground(Uri... arg0) {
			
			// TODO Auto-generated method stub
			Log.d(TAG, "Image view is: " + mSourceImageView.getWidth() + " | " + mSourceImageView.getHeight());
			Log.d(TAG, "Bitmap uri is: " + mCapturedImageURI.getPath());
			Bitmap inputImage = null;
			
			try {
				inputImage = BitmapTools.cropScaledBitmap(context, mCapturedImageURI, 
						BitmapTools.scaleRectToFitBitmap(context,
								mCapturedImageURI,
								mViewFinderView.getFramingRect(), 
								mSourceImageView.getWidth(), 
								mSourceImageView.getHeight(),
								mSourceImageView.isRotated()), 100, false);
			    
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			return inputImage;
		}

		@Override
        protected void onPostExecute(Bitmap result) {
			boolean success = false;
			
			if(result != null){

				// Get the rotation angle from the phone orientation
				float angle = getRotationAngleForBitmap(rotationAngle);
				
				// Add the rotation angle from the image view
				if(mSourceImageView.isRotated()){ angle -= 90; }
				
				// Rotate the bitmap according to requested angle
				result = BitmapTools.rotateImage(result, angle);
				
				// Recognize the text in the image
                Message msg = Message.obtain(null, OCRService.MSG_RECOGNIZE);
                msg.replyTo = mMessenger;
                msg.obj = result;
                
                try {
					mService.send(msg);
					success = true;
				} catch (RemoteException e) {
					e.printStackTrace();
					success = false;
				} catch (NullPointerException e){
					e.printStackTrace();
					success = false;
				}
			} 
			
			if(!success) {
				setState(State.IDLE);
				toastMessage("Image not cropped");
			}
        }

    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_ocr);
		
		// Load the views (and assign listeners)
		loadViews();
		
		// Assign values to the class variables (This avoids facing null exceptions when creating / saving a Parcelable)
		// convert the saved state to an empty bundle to avoid errors later on
		Bundle savedstate = (savedInstanceState != null) ? savedInstanceState : new Bundle();
		
		// load the user (Note: user cannot remain null if it is neither in the saved state nor the arguments)
		mService = savedstate.getParcelable(Gegevens.EXTRA_MESSENGER);
		mCapturedImageURI = savedstate.getParcelable(Gegevens.EXTRA_URI);
		mState = (State) savedstate.getSerializable(Gegevens.EXTRA_STATE);
		
		// Set the default state
		if(mState == null){ Log.v(TAG, "onCreate: mState is null."); mState = State.NOPICTURE; }
		
		// Bind to the OCR service
		if(mService == null){ Log.d(TAG, "onCreate: mService is null."); doBindService();
		} else {
			// Test your connection to the service.
			Log.d(TAG, "onCreate: mService is not null.");
            Message msg = Message.obtain(null, OCRService.MSG_GETSTATE);
            msg.replyTo = mMessenger;
            
            try {
				mService.send(msg);
			} catch (RemoteException e) {
				e.printStackTrace();
				doBindService();
			} catch (NullPointerException e){
				e.printStackTrace();
				doBindService();
			}
		}

		// Set the captured image
		if(mCapturedImageURI != null){ 
			Log.d(TAG, "onCreate: CapturedImageURI is not null.");
			setSourceURI(mCapturedImageURI);
		} else {
			Log.d(TAG, "onCreate: CapturedImageURI is null.");
			mState = State.NOPICTURE;
		}

		setState(mState);
	}
	
	@Override public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Log.v(TAG, "Saving OCRActivity instance state.");

		// Save the messenger and the image uri
		if(mService != null) { outState.putParcelable(Gegevens.EXTRA_MESSENGER, mService); }
		if(mCapturedImageURI != null) { outState.putParcelable(Gegevens.EXTRA_URI, mCapturedImageURI); }
		if(mState != null){ outState.putSerializable(Gegevens.EXTRA_STATE, mState); }
	}

	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.ocr, menu);
		return true;
	}

	@Override
	protected void onResume() { 
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
		
		// Set listener to detect a shutter button click
		mCameraButton.setOnShutterButtonListener(this);
		mOCRButton.setOnShutterButtonListener(this);
		
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
							mViewFinderView.processMoveEvent(previousX[i], previousY[i],(int) event.getX(i), (int) event.getY(i));

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

	@Override public void onShutterButtonFocus(ShutterButton b, boolean pressed) {
		Log.v(TAG, "onShutterButtonFocus");
	}

	@Override public void onShutterButtonClick(ShutterButton b) {
		int id = b.getId();
		
		switch(id){
			case R.id.shutter_button:
				handlePicture();
				break;
			case R.id.ocr_button:
				handleOCR();
				break;
		}
	}
	
	private void handleOCR() {
		new CropImage(this, mOrientationListener.getRotation()).execute(mCapturedImageURI);
	}

	private void handlePicture() {		
		// TODO: build in the settings the option to default to one of the context items
		// if default:
		Log.i(TAG+"[handlePicture]", "showcontextMenu");
		registerForContextMenu(mCameraButton); 
		mCameraButton.showContextMenu();
	    unregisterForContextMenu(mCameraButton);
		// else if take picture: redirect to take picture
		// else if from gallery: redirect to from gallery
	}
	
	private void handlePicture(int id) {
		switch(id){
		case R.id.context_img_from_gallery:
			Intent i = new Intent(Intent.ACTION_PICK,android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
			startActivityForResult(i, Gegevens.CODE_GALLERY);
			break;
		case R.id.context_img_from_camera:
			// TODO: consider saving the file to a folder instead of the gallery
		    // make a URI for the capture images
		    ContentValues values = new ContentValues();
		    values.put(MediaStore.Images.Media.TITLE, Gegevens.FILE_CAMERAIMG);
		    mCapturedImageURI = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
			Log.i(TAG,"Save picture to: "+ 	mCapturedImageURI.toString());

			// start a camera activity
			Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE); 
			cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCapturedImageURI);
	        startActivityForResult(cameraIntent, Gegevens.CODE_CAMERA); 
			break;
		}
	}
	
	@Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		if(v.getId() == R.id.shutter_button){
			getMenuInflater().inflate(R.menu.context_shutter, menu);
		}
	}

    /**
     *  Handles the context menu requests. 
     *  Currently there is just one context menu namely the context menu for the shutter button. <br />
     *  - if from gallery, forward an intent call to the gallery <br />
     *  - if from camera, forward an intent call to the camera <br />
     *  The results are handled in onActivityResult()
     */
	@Override public boolean onContextItemSelected(MenuItem item) {
		int id = item.getItemId();
		switch(id){
		case R.id.context_img_from_gallery:
		case R.id.context_img_from_camera:
			handlePicture(id);
			break;
		default:
			return super.onContextItemSelected(item);	
		}
		return true;
	}
	
	private void toastMessage(String message){
		Toast toast = Toast.makeText(this, message, Toast.LENGTH_LONG);
		toast.setGravity(Gravity.TOP, 0, 0);
		toast.show();
	}

	private void doBindService() {
        // Establish a connection with the service. 
		Log.v(TAG, "doBindService: Bind the service.");
        bindService(new Intent(this, OCRService.class), mConnection, Context.BIND_AUTO_CREATE);
    }
    
    private void doUnbindService() {
		Log.v(TAG, "doUnbindService: Unbind the service.");
    	try{
	        // Detach our existing connection.
	        unbindService(mConnection);
    	}catch(IllegalArgumentException e){ 
    		Log.e(TAG, "doUnbindService: Service not registered.");
    		e.printStackTrace();
    	}
    }
    
    private void setSourceURI(Uri sourceUri){
    	if(sourceUri != null) {
			mSourceImageView.setImageURI(sourceUri);
			mSourceImageView.invalidate();
			mSourceImageView.refreshDrawableState();
			setState(State.IDLE);
    	}
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
	
    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.v(TAG, "onActivityResult.");
		if(resultCode == RESULT_OK){
			switch(requestCode){
			case Gegevens.CODE_GALLERY:
				mCapturedImageURI = (data != null) ? data.getData() : null;
			case Gegevens.CODE_CAMERA:
				toastMessage("Image captured");
				setSourceURI(mCapturedImageURI);
				break;
			default:
				toastMessage("Not sure why that was successful");
				break;
			}
		} else {
			toastMessage("Image not captured");
		}
	}

    @Override
    public void onBackPressed(){
    	if(mState == State.DONE){
    		setState(State.IDLE);
    	} else {
        	super.onBackPressed();
    	}
    }
    
    public float getRotationAngleForBitmap(int rotation){
    	
		float rotationAngle = 0;
		
		switch(rotation){
		case Surface.ROTATION_90: // 1
			rotationAngle = 0;
			break;
		case Surface.ROTATION_180: // 2
			rotationAngle = 270;
			break;
		case Surface.ROTATION_270: // 3
			rotationAngle = 180;
			break;
		case Surface.ROTATION_0: // 0
			rotationAngle = 90;
			break;
		}
    	
    	return rotationAngle;
    }

    
    @Override
	public void onPhoneRotated(int rotation) {
    	
    	Log.d(TAG, "Curretion orientation is:" + rotation);
    	
		// update the rotation of the buttons 
		if(mOCRButton != null){ mOCRButton.rotation = rotation; mOCRButton.invalidate(); }
		if(mCameraButton != null){ mCameraButton.rotation = rotation; mCameraButton.invalidate(); }
		
		// Get the angle of rotation for the text views
		int rotationAngle = 90;
		switch(rotation){
		case Surface.ROTATION_90:
			rotationAngle = 0;
			break;
		case Surface.ROTATION_180:
			rotationAngle = 270;
			break;
		case Surface.ROTATION_270:
			rotationAngle = 180;
			break;
		case Surface.ROTATION_0:
			rotationAngle = 90;
			break;
		}

		// Update the layout parameters of the Progress bar
		RotateLayout.LayoutParams x = (RotateLayout.LayoutParams) mProgressLayout.getLayoutParams();
		x.setAngle(rotationAngle);
		mProgressLayout.setLayoutParams(x);
		
		// Update the layout parameters of the results view
		RotateLayout.LayoutParams y = (RotateLayout.LayoutParams) mScrollView.getLayoutParams();
		y.setAngle(rotationAngle);
		mScrollView.setLayoutParams(y);

	}
}