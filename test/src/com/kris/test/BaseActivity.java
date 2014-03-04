package com.kris.test;

import com.kris.test.LoaderService.LocalBinder;

import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class BaseActivity extends Activity {
	
	public static final int RESULT_FINISH 	= 11;
	
	LoaderService mService;
    boolean mBound = false;
	protected Toast toastMsg;
	private ProgressDialog mProgressDialog;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_button);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        ((TextView) findViewById(R.id.textView1)).setText("Base Activity");
        
        mProgressDialog = ProgressDialog.show(this, "Loading...", "Loading the Audio Recorder and Recognizer", true, false);
        startService(new Intent(BaseActivity.this, LoaderService.class));
        
//        new AsyncLoading(this){
//        	@Override protected Boolean doInBackground(final String... args) {
//            	startService(new Intent(BaseActivity.this, LoaderService.class));
//            	return true;
//            }
//        }.execute();

	}
	
	protected void loadViews(){
	}
	
	
    /** Processes the default button pressed method. By default it assumes it is an unknown buttons. */
	public void onBtnClick(View view) {
		int id = view.getId();
		if(id == R.id.button1)
			startActivityAndFinish(MainActivity.class);
		else
			this.stopService(new Intent(BaseActivity.this, LoaderService.class));
		// postToast(getString(R.string.unknownBtn));
	}
    
	/** Tests to see if the uri is available. Use this method to check if an another app / link works. */
	public boolean isUriAvailable(String uri) {
	    Intent test = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
	    return getPackageManager().resolveActivity(test, 0) != null;
	}
	
	/** Check to see whether or not we should save this as the last activity. 
	 *  Default response is true. Overwrite it for activities that you don't want to save. */
	protected boolean saveAsLastActivity(){ return true; }
	
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

	/** Starts a new activity with FLAG_ACTIVITY_CLEAR_TOP and finishes this activity */
	protected void startActivityAndFinish(Class<?> cls) { 
		// no point in switching to the same activity or to null
		if(cls == null || this.getClass() == cls){ return; }

		// start the activity
        Intent newIntent = new Intent(this, cls);
		setResult(RESULT_FINISH);
		startActivity(newIntent);
		
		// and finish
		finish();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(resultCode == RESULT_FINISH){
			setResult(RESULT_FINISH);
			finish();
		}	
	}

	protected void onPause() { 
    	super.onPause();
    	// transition away using a fade effect.
    	overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    	
    	if (mBound) { unbindService(mConnection); mBound = false; }
    	
    	// save the current activity to the preferences
    	if(saveAsLastActivity()){
            PreferenceManager.getDefaultSharedPreferences(this).edit()
        	.putString("lastActivity", getClass().getName()).commit();
    	}
    	LocalBroadcastManager.getInstance(this).unregisterReceiver(onNotice);
    }
	
    // TODO: Implement life cycle methods
    protected void onResume() { 
    	super.onResume();
    	
    	bindService(new Intent(this, LoaderService.class), mConnection, Context.BIND_AUTO_CREATE);
    	LocalBroadcastManager.getInstance(this).registerReceiver(onNotice,
        		new IntentFilter(LoaderService.BROADCAST));
    	// if this Activity is resuming the user has to press back twice to quit
    }

    protected void onStart() { super.onStart(); }
    protected void onRestart() { super.onRestart(); }
    protected void onStop() { 	super.onStop(); }
    protected void onDestroy() { super.onDestroy(); }

    private BroadcastReceiver onNotice = new BroadcastReceiver() {
    	public void onReceive(Context ctxt, Intent i) {
    		String value = i.getStringExtra("whathappened");
    		Log.i("BaseActivity","received notice - whathappened = "+value);
    		if(value == null || value.equals("finished processing")){
    			((TextView) findViewById(R.id.textView1)).setText("Base Activity - done processing.");	
    		}else if (value.equals("finished loading")){
    			if(mProgressDialog != null && mProgressDialog.isShowing())
    				mProgressDialog.dismiss();
    			((TextView) findViewById(R.id.textView1)).setText("Base Activity - done loading.");
    		}
        	
        }
    };
    

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            mService = ((LocalBinder) service).getService();
            mBound = true;
        }

        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
}