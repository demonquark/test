package com.kris.test;

import java.util.List;

import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

public class TableContract_TableProvider_Activity extends Activity {
	private Uri mUri;
	protected TextView text;
    private static final String[] PROJECTION = new String[] {
		    	TableContract.Words._ID,
		    	TableContract.Words.COLUMN_WORDID,
		    	TableContract.Words.COLUMN_TRADITIONAL_CHINESE,
		    	TableContract.Words.COLUMN_SIMPLIFIED_CHINESE,
		    	TableContract.Words.COLUMN_COUNT
        	};
	
	
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_table);
        
        text = (TextView) findViewById(R.id.something);
        
        if(getContentResolver() != null){
        	mUri = TableContract.Words.CONTENT_URI;
        	mUri = updateNote(4,"a simple5 text", "a traditional5 text");
        	
        	// text.setText(mUri.toString());
        	mUri = Uri.parse(TableContract.SCHEME + TableContract.AUTHORITY + TableContract.Words.PATH_ID + "6");
        	Cursor cursor = null;
        	try {
        	    cursor = getContentResolver().query(mUri, PROJECTION, null, null, null);
        	    String outputString = "";
        	    for(String name : cursor.getColumnNames())
        	    	outputString += " | "+name;
        	    if (cursor != null && cursor.moveToFirst()){
        	    	outputString = "" + cursor.getString(2);
        	    	outputString += " | " + cursor.getString(3);
        	    }
        	    text.setText(outputString);
        	    cursor.close();
        	} catch(Exception e) {
        	    e.printStackTrace();
        	}
        }
        
        
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    private final Uri updateNote(int wordid, String text, String title) {
        // Sets up a map to contain values to be updated in the provider.
        ContentValues values = new ContentValues();
        values.put(TableContract.Words.COLUMN_WORDID, wordid);
        values.put(TableContract.Words.COLUMN_TRADITIONAL_CHINESE, title);
        values.put(TableContract.Words.COLUMN_SIMPLIFIED_CHINESE, text);
        values.put(TableContract.Words.COLUMN_COUNT, 1);
        
        return getContentResolver().insert( mUri, values );
    }
    
    public String findApplicationPackage( String sAppName ){
    	String sPackageName = "";
    	PackageManager pm = getPackageManager();
    	//get a list of installed apps.
    	List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
    	ApplicationInfo ap = new ApplicationInfo();
    	ap.loadLabel(pm);
    	for (ApplicationInfo packageInfo : packages) {
    		String sApp = packageInfo.loadLabel( pm ).toString();
			Log.d("Class", "Installed AppName = " + sApp + "  ,package :" + packageInfo.packageName );
			//Log.d(TAG,   "Launch Activity :" + pm.getLaunchIntentForPackage(packageInfo.packageName)); 
    	   }// the getLaunchIntentForPackage  
    	   return sPackageName;
    	}    
}
