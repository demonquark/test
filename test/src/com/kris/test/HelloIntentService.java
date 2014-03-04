package com.kris.test;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class HelloIntentService extends IntentService {
	
	static int counter;
	int counter2;

	  /** 
	   * A constructor is required, and must call the super IntentService(String)
	   * constructor with a name for the worker thread.
	   */
	  public HelloIntentService() {
	      super("HelloIntentService");
	  }

	  /**
	   * The IntentService calls this method from the default worker thread with
	   * the intent that started the service. When this method returns, IntentService
	   * stops the service, as appropriate.
	   */
	  @Override
	  protected void onHandleIntent(Intent intent) {
		  int id = counter;
		  counter++;
		  counter2++;
	      // Normally we would do some work here, like download a file.
	      // For our sample, we just sleep for 5 seconds.
	      long endTime = System.currentTimeMillis() + 10*1000;
	      while (System.currentTimeMillis() < endTime) {
	          synchronized (this) {
	              try {
	            	  Log.i("Hello Intent",id+ "|"+counter2+") time "+System.currentTimeMillis()+ " | endtime " + endTime);
	                  wait(1000);
	              } catch (Exception e) {
	              }
	          }
	      }
	  }
	  
	  @Override
	  public void onDestroy() {
	    Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show(); 
	  }

	}
