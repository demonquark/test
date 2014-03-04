package com.kris.test;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

/** this class performs all the work, shows dialog before the work and dismiss it after */
public class AsyncLoading extends AsyncTask<String, Integer, Boolean> {

	private ProgressDialog dialog;

    public AsyncLoading(Context c) {
        dialog = new ProgressDialog(c);
        dialog.setCancelable(false);
        dialog.setIndeterminate(true);
        dialog.setMessage("Loading...");
        
    }

    protected void onPreExecute(){
//    	dialog.show();
    }
    @Override protected void onPostExecute(final Boolean success) { 
    	Log.d("asyncloading","ended");
//    	if (dialog.isShowing()) { dialog.dismiss(); } 
    }

    protected Boolean doInBackground(final String... args) {
    	return true;
    }
}