//package rocket.copy;
//
//import javax.microedition.khronos.egl.EGLConfig;
//import javax.microedition.khronos.opengles.GL10;
//
//import rocket.*;
//
//import android.opengl.GLSurfaceView;
//import android.opengl.GLU;
//import android.os.Bundle;
//import android.app.Activity;
//import android.content.Context;
//import android.view.ViewGroup;
//import android.widget.LinearLayout;
//import android.widget.TextView;
//
//public class MainActivity extends Activity {
//	protected TextView text;
//	private GLSurfaceView mGLView;
//	
//	@Override public void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_rocket);
//		
//        // get the top level container
//        ViewGroup contentHolder = (ViewGroup) findViewById(R.id.TopLevel);
//        
//        text = (TextView) findViewById(R.id.something);
//        mGLView = new ClearGLSurfaceView(this);
//        mGLView.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.MATCH_PARENT,1));
//        contentHolder.addView(mGLView,0);
//    }
//	
//    @Override
//    protected void onPause() {
//        super.onPause();
//        mGLView.onPause();
//    }
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//        mGLView.onResume();
//    }	
//	
//	class ClearGLSurfaceView extends GLSurfaceView {
//	    ClearRenderer mRenderer;
//
//	    public ClearGLSurfaceView(Context context) {
//	        super(context);
//	        mRenderer = new ClearRenderer();
//	        setRenderer(mRenderer);
//	    }
//	    
//	}
//
//	class ClearRenderer implements GLSurfaceView.Renderer {
//		private ParticleSystem mParticleSystem;
//		
//		public ClearRenderer() {
//			mParticleSystem = new ParticleSystem();
//		}
//
//		public void onSurfaceCreated(GL10 gl, EGLConfig config) {
//	        GLU.gluPerspective(gl, 15.0f, 80.0f/48.0f, 1, 100);
//	        GLU.gluLookAt(gl, 0f, -17f, 5f, 0.0f, 0.0f, 1f, 0.0f, 1.0f, 0.0f);
//	        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
//	    }
//
//	    public void onSurfaceChanged(GL10 gl, int w, int h) {
//	        gl.glViewport(0, 0, w, h);
//	    }
//
//	    public void onDrawFrame(GL10 gl) {
//	    	gl.glClearColor(0, 0, 0, 1.0f);
//	        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
//	        mParticleSystem.update();
//	        mParticleSystem.draw(gl);
//	    }
//	}
//}
