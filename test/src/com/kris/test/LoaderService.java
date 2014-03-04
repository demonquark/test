package com.kris.test;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;
import android.os.Process;

public class LoaderService extends Service {

	enum State {IDLE, LISTENING};
	private ServiceHandler mServiceHandler;
	public static final String BROADCAST = "com.commonsware.android.localcast.NoticeService.BROADCAST";
	private final IBinder mBinder = new LocalBinder();
	static int counter;
	int counter2;
	int counter3;
	boolean started;
	State state;
	  
	// Handler that receives messages from the thread
	private final class ServiceHandler extends Handler {
		public ServiceHandler(Looper looper) { super(looper); }
		
		@Override public void handleMessage(Message msg) {
	  		state = State.LISTENING;

			if(!started){
				Log.d("LoaderSErvice","Started is false");
				init();
			}
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
	          LocalBroadcastManager.getInstance(LoaderService.this).sendBroadcast(new Intent(BROADCAST));
	          state = State.IDLE;

//	          stopSelf(msg.arg1);
	          if(counter > 4){
	        	  Log.e("LoaderSErvice","Counter is greater than 3.");
	        	  System.exit(-1); }
	      }

		  private void init() {
	          synchronized (this) {
	              try {
	            	  Log.d("Hello Intent","Initializing... Wait for 5 seconds.");
	                  wait(5000);
	            	  Log.d("Hello Intent","Initializing... Done waiting.");
	          		started = true;
	              } catch (Exception e) { }
	              
            	  Log.d("Hello Intent","Initializing... Done waiting.");
	              Intent intent = new Intent(BROADCAST);
	              intent.putExtra("whathappened", "finished loading");
		          LocalBroadcastManager.getInstance(LoaderService.this).sendBroadcast(intent);
	          }
		}
	  }

	@Override public void onCreate() {
		Log.d("Hello Intent","Service created.");
		state = State.IDLE;
		started = false;
//		ProgressDialog.show(this, "Loading...", "Loading from service", true, false);
		
		// Get the HandlerThread's Looper and use it for our Handler 
		HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();
		mServiceHandler = new ServiceHandler(thread.getLooper());
	}

	@Override public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d("LoaderSErvice","Started... " + counter3);
		counter3++;
		
		if(state == State.IDLE){
			Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();

			// For each start request, send a message to start a job and deliver the
			// start ID so we know which request we're stopping when we finish the job
			Message msg = mServiceHandler.obtainMessage();
			msg.arg1 = startId;
			mServiceHandler.sendMessage(msg);
		}
		
	    // If we get killed, after returning from here, restart
	    return START_NOT_STICKY;
	  }

	@Override public IBinder onBind(Intent intent) { return mBinder; }
	@Override public void onDestroy() {
		Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
		Log.d("Hello Intent","SERVICE DONE.");
	}

    public class LocalBinder extends Binder { LoaderService getService() { return LoaderService.this; } }
}