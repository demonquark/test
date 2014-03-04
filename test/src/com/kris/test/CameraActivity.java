package com.kris.test;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;
import android.content.Intent;

public class CameraActivity extends Activity {
	
	public static final int RESULT_FINISH 	= 11;
	
	protected Toast toastMsg;
	
	@Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_button);
        ((TextView) findViewById(R.id.textView1)).setText("Main Activity");

    }
	
    /** Processes the default button pressed method. By default it assumes it is an unknown buttons. */
	public void onBtnClick(View view) {
		startActivityAndFinish(BaseActivity.class);
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

	

}
