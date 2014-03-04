package rocket;

import java.util.Random;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

/**
 *  InteractiveClock is a FrameLayout with a analog clock showing a minute and hour hand. It does NOT automatically update the time.  
 *  However, the user can change the time by moving (event == ACTION_MOVE) the clock hands. The clock range is 12 hours (720 minutes) 
 *  
 *  <p>Note on class variables:
 *  <br />Hours and minutes are saved separately
 *  <br />- The number value is split in:
 *  <br />- - a long (wholeNumbers) containing the whole numbers
 *  <br />- - a double (decimals) containing the decimal numbers
 *  </p>
 *  
 *  <p>Note on interaction with class:
 *  <br />- the getter methods return Strings
 *  <br />- you can only change the number by adding/removing one digit at a time (use addToNumerals(int) and addToCnNumber(int)) 
 *  </p>
 *  
 *  <p>IMPORTANT RESTRICTION 1:
 *  <br />The class does NOT check if the resource id for the minute and hour hands are valid! 
 *  An invalid resource id will cause unexpected results (i.e. fail).
 *  </p>
 *  
 */
public class Fuse extends View {
	
	private final static String LOG_TAG = "Class Fuse";
	
	// variables for drawing
	private Bitmap rocket;
	private Rect rocketRect;
	private Paint mPaint;
	private Path mFuse;
	private Point mFuseStart;
	private Point mFuseEnd;
	private Point mFlamEnd;
	private Point mBottomRight = new Point(0,0);
	private Random gen;
	private int mRadius = 4;
	private int PARTICLECOUNT = 4;
	private int [] colors = {Color.WHITE, Color.BLUE, Color.GREEN, Color.RED, Color.MAGENTA};
	
	// variables for timer
	private float speed = 1f;
	private long duration = 10000;
	private boolean running = false;
	OnFuseCompleteListener listener;
	
	private Handler mHandler = new Handler();
	private Runnable mUpdateTimeTask = new Runnable() { public void run() {
		final int height = (getMeasuredHeight() > mRadius*2) ? getMeasuredHeight() - mRadius*2 : mRadius*4;
		final int width = (getMeasuredWidth() > mRadius) ? getMeasuredWidth() - mRadius : mRadius*2;
		final int interval = 200; // time between animation updates in milliseconds
		final int segments = 5; // number of "kinks" in the fuse

		// Set the start of the fuse
		if(mBottomRight.x != width || mBottomRight.y == height || rocketRect == null ){
			rocketRect = new Rect(0, 0, width, width*rocket.getHeight()/rocket.getWidth());
			mFuseStart = new Point(rocketRect.centerX(),rocketRect.centerY());
			
			// Initialize the end of the fuse. Note: we set the fuse end to (-1,-1) at start(); 
			if(mFuseEnd.y < 0){
				mFuseEnd.set(mFuseStart.x, height); 
				mFlamEnd.set(mFuseEnd.x, mFuseEnd.y);
			}
		}
		
		// Move the end of the flame
		mFlamEnd.y = mFlamEnd.y-(int)(1+(height-mFuseStart.y)*speed/(duration/interval));
		
		// Move the end of the fuse
		if(mFuseEnd.y - mFlamEnd.y > mRadius*4){
			mFuseEnd.y = mFlamEnd.y;
			mFuse = new Path();
			mFuse.moveTo(mFuseStart.x, mFuseStart.y);
			for(int i = 0; i < segments; i++){
				mFuseEnd.x = mFuseStart.x - i*2 + gen.nextInt(mRadius);
				mFuse.lineTo(mFuseEnd.x, mFuseStart.y + ((mFuseEnd.y - mFuseStart.y)*i)/segments );
			}
			mFuse.lineTo(mFuseEnd.x,mFuseEnd.y-mRadius);
			mFlamEnd.x = mFuseEnd.x;
		}
		
		Log.i(LOG_TAG, "Position is: ("+mFuseStart.x+","+mFuseStart.y+") ("+mFuseEnd.x+","+mFuseEnd.y+") ("+mFlamEnd.x+","+mFlamEnd.y+") speed="+speed);
		invalidate();
		if(mFuseStart.y < mFuseEnd.y && running){
			mHandler.postAtTime(this, SystemClock.uptimeMillis() + interval);
		}else{ 
			running = false;
			if(listener != null) {listener.fuseComplete(!(mFuseStart.y < mFuseEnd.y));}
		}

	} };
	
	@Override protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if(mFuseEnd != null && mFuseStart != null && mFuseEnd.y > mFuseStart.y){
			// draw the fuse
			mPaint.setColor(Color.DKGRAY);
			canvas.drawPath(mFuse, mPaint);
		
			// draw the flame at the of the fuse
			for(int i = 0; i < PARTICLECOUNT; i++){
				mPaint.setColor(colors[gen.nextInt(colors.length)]);
				canvas.drawCircle(mFlamEnd.x+(gen.nextInt(2*mRadius)-mRadius), 
						mFlamEnd.y+gen.nextInt(2*mRadius), 1, mPaint);
			}
		}
		
		if(rocket != null && rocketRect != null)
			canvas.drawBitmap(rocket, null, rocketRect, mPaint);
	}
	
	public void start(){
		speed = 1;
		running = true;
		mFuseEnd = new Point(-1,-1);
		mFlamEnd = new Point(mFuseEnd.x, mFuseEnd.y);

		mHandler.removeCallbacks(mUpdateTimeTask);
        mHandler.postDelayed(mUpdateTimeTask, 100);
	}
	
	/** Stop the fuse */
	public void stop(){ running = false;}
	
	/** Increments the burnRate by ~15%. Minimum is 0.25*/
	public void burnFaster(){ speed += speed/7; speed = (speed < 4) ? speed : 4; }
	
	/** Decrements the burnRate by ~15%). Maximum is 4. */ 
	public void burnSlower(){ speed -= speed/7; speed = (speed > 0.25) ? speed : 0.25f; }
	
	/** Set how fast the fuse should burn. Equals the slope of time progression. 
	 *  <br />Should be between 0.25 and 4. Defaults to 1.*/
	public void setBurnRate(float burnRate){ speed = ( burnRate > 0.25 && burnRate < 4) ? burnRate : 1; }
	
	/** Set how time (in milliseconds) it takes for the fuse to burn out. Minimum duration of 1000 milliseconds.
	 *  <br />Defaults to 1000 milliseconds. */
	public void setDuration(int duration){ duration = ( duration > 1000 ) ? duration : 1000; }

	/** Set the bitmap image of the rocket*/
	public void setListener(OnFuseCompleteListener lstnr) { listener = lstnr; }

	/** Set the bitmap image of the rocket*/
	public void setRocketBitmap(Bitmap bm) { rocket = bm; }

	/** Check if the fuse is lit. */
	public boolean isRunning(){ return running; }
	
	/** Constructor */
	public Fuse(Context context) { 
		super(context);
		mPaint = new Paint();
		mFuse = new Path();
		gen = new Random(SystemClock.uptimeMillis());

		mPaint.setDither(true);
		mPaint.setColor(Color.DKGRAY);
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setStrokeJoin(Paint.Join.ROUND);
		mPaint.setStrokeCap(Paint.Cap.ROUND);
		mPaint.setStrokeWidth(3);
		
	}
	
	/** listener interface for when the fuse stops burning */
	public interface OnFuseCompleteListener {
		public void fuseComplete(boolean burnedOut);
	}
	
}
