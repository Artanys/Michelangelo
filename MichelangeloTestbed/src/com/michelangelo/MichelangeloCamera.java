package com.michelangelo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MichelangeloCamera extends MichelangeloUI {

	
	private static final String TAG = "MichelangeloCamera";
	public static final int MEDIA_TYPE_IMAGE = 1;
	public static final int MEDIA_TYPE_VIDEO = 2;
	public static int NUM_IMAGES = 8;
	private Camera mCamera = null;
	private CameraPreview mPreview = null;
	private MichelangeloSensor mSensor;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.activity_michelangelo_camera);
		super.onCreate(savedInstanceState);
	
		//View circleView = (View) findViewById(R.id.circle);
		
		//circleView
		
		Button captureButton = (Button) findViewById(R.id.button_capture);
		captureButton.setOnClickListener(
		    new View.OnClickListener() {
		        @Override
		        public void onClick(View v) {
		            // get an image from the camera
		        	mCamera.autoFocus(null);
		            mCamera.takePicture(null, null, mPicture);
		        }
		    }
		);
		captureButton.bringToFront();
		FrameLayout cameraPreview = (FrameLayout) findViewById(R.id.camera_preview);
		cameraPreview.setOnClickListener(
			new View.OnClickListener() {
		        @Override
		        public void onClick(View v) {
		            // get an image from the camera
		        	mCamera.autoFocus(null);
		        }
		    }
		);
		
		final Handler handler = new Handler();
		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				handler.post(new Runnable() {
					public void run() {
						TextView tv = (TextView) findViewById(R.id.button_capture);
						AngledLineView alv = (AngledLineView) findViewById(R.id.circleLine);
						CenteredAngledLineView horizonLine = (CenteredAngledLineView) findViewById(R.id.horizonLine);
						CenteredAngledLineView pitchLine = (CenteredAngledLineView) findViewById(R.id.pitchLine);
						alv.setAngle(mSensor.Rad_orientation[0]);
						pitchLine.setAngle(mSensor.Rad_orientation[1]);
						horizonLine.setAngle(mSensor.Rad_orientation[2]);
				        tv.setText("yaw: " + mSensor.Deg_orientation[0] + " pitch: " + mSensor.Deg_orientation[1] + " roll: " + mSensor.Deg_orientation[2]);
					}
				});
			}
		},125,125);

        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setDisplayShowHomeEnabled(true);
        getActionBar().setHomeButtonEnabled(true);

        releaseCamera();
        grabCamera();
        mSensor = new MichelangeloSensor();
        mSensor.onCreate(this);
       
        Log.d(TAG, "Done creating Camera Page");

	}
	

	/** A safe way to get an instance of the Camera object. */
	/** http://developer.android.com/guide/topics/media/camera.html#custom-camera, January 21, 2014 */
	public static Camera getCameraInstance(){
	    Camera c = null;
	    try {
	        c = Camera.open(); // attempt to get a Camera instance
	    }
	    catch (Exception e){
	        // Camera is not available (in use or does not exist)	
	    }
	    return c; // returns null if camera is unavailable
	}
	
	/** A basic Camera preview class */
	public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
		private SurfaceHolder mHolder;
	    private Camera mCamera;

	    @SuppressWarnings("deprecation")
		public CameraPreview(Context context, Camera camera) {
	        super(context);
	        mCamera = camera;

	        // Install a SurfaceHolder.Callback so we get notified when the
	        // underlying surface is created and destroyed.
	        mHolder = getHolder();
	        mHolder.addCallback(this);
	        // deprecated setting, but required on Android versions prior to 3.0
	        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	    }

	    public void surfaceCreated(SurfaceHolder holder) {
	        // The Surface has been created, now tell the camera where to draw the preview.
	    	
	        try {
	            mCamera.setPreviewDisplay(holder);
	            if (mCamera != null) {
		        	setCameraDisplayOrientation(MichelangeloCamera.this, CameraInfo.CAMERA_FACING_BACK, mCamera);
		        }
	            mCamera.startPreview();
	        } catch (IOException e) {
	            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
	        }
	    }

	    public void surfaceDestroyed(SurfaceHolder holder) {
	        // empty. Take care of releasing the Camera preview in your activity.
	    	if (mCamera != null) {
	            // Call stopPreview() to stop updating the preview surface.
	    		try {
		            mCamera.stopPreview();
		        } catch (Exception e){
		          // ignore: tried to stop a non-existent preview
		        }
	        }
	    }

	    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
	        // If your preview can change or rotate, take care of those events here.
	        // Make sure to stop the preview before resizing or reformatting it.
	    	
	        if (mHolder.getSurface() == null){
	          // preview surface does not exist
	          return;
	        }

	        // stop preview before making changes
	        try {
	            mCamera.stopPreview();
	        } catch (Exception e){
	          // ignore: tried to stop a non-existent preview
	        }

	        // set preview size and make any resize, rotate or
	        // reformatting changes her
	        
	        if (mCamera != null) {
	        	setCameraDisplayOrientation(MichelangeloCamera.this, CameraInfo.CAMERA_FACING_BACK, mCamera);
	        }
	        
	        // start preview with new settings
	        try {
	            mCamera.setPreviewDisplay(mHolder);
	            mCamera.startPreview();

	        } catch (Exception e){
	            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
	        }
	    }
	}
	
	private PictureCallback mPicture = new PictureCallback() {

	    @Override
	    public void onPictureTaken(byte[] data, Camera camera) {
	    	Log.d(TAG, "Taking Picture");
	    	mPreview.setVisibility(View.GONE);
            findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
            findViewById(R.id.loadingPanel).bringToFront();
	        File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
	        if (pictureFile == null){
				Log.d(TAG, "Error creating media file, check storage permissions");
	            return;
	        }

	        try {
	            FileOutputStream fos = new FileOutputStream(pictureFile);
	            fos.write(data);
	            fos.close();
	        } catch (FileNotFoundException e) {
	            Log.d(TAG, "File not found: " + e.getMessage());
	        } catch (IOException e) {
	            Log.d(TAG, "Error accessing file: " + e.getMessage());
	        }
	        findViewById(R.id.loadingPanel).setVisibility(View.GONE);
            mPreview.setVisibility(View.VISIBLE);
	    }
	};
	
	/** Create a file Uri for saving an image or video */
	private static Uri getOutputMediaFileUri(int type){
	      return Uri.fromFile(getOutputMediaFile(type));
	}

	/** Create a File for saving an image or video */
	private static File getOutputMediaFile(int type){
	    // To be safe, you should check that the SDCard is mounted
	    // using Environment.getExternalStorageState() before doing this.

	    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
	              Environment.DIRECTORY_PICTURES), "Michelangelo");
	    // This location works best if you want the created images to be shared
	    // between applications and persist after your app has been uninstalled.

	    // Create the storage directory if it does not exist
	    if (! mediaStorageDir.exists()){
	        if (! mediaStorageDir.mkdirs()){
	            Log.d("MichelangeloCamera", "failed to create directory");
	            return null;
	        }
	    }

	    // Create a media file name
	    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
	    File mediaFile;
	    if (type == MEDIA_TYPE_IMAGE){
	        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
	        "IMG_"+ timeStamp + ".jpg");
	    } else if(type == MEDIA_TYPE_VIDEO) {
	        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
	        "VID_"+ timeStamp + ".mp4");
	    } else {
	        return null;
	    }

	    return mediaFile;
	}
	
	public static void setCameraDisplayOrientation(Activity activity,
	         int cameraId, android.hardware.Camera camera) {
	     android.hardware.Camera.CameraInfo info =
	             new android.hardware.Camera.CameraInfo();
	     android.hardware.Camera.getCameraInfo(cameraId, info);
	     int rotation = activity.getWindowManager().getDefaultDisplay()
	             .getRotation();
	     int degrees = 0;
	     switch (rotation) {
	         case Surface.ROTATION_0: degrees = 0; break;
	         case Surface.ROTATION_90: degrees = 90; break;
	         case Surface.ROTATION_180: degrees = 180; break;
	         case Surface.ROTATION_270: degrees = 270; break;
	     }

	     int result;
//	     if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
//	         result = (info.orientation + degrees) % 360;
//	         result = (360 - result) % 360;  // compensate the mirror
//	     } else {  // back-facing
         result = (info.orientation - degrees + 360) % 360;
//	     }
	     camera.setDisplayOrientation(result);
	 }

	
	@Override
    protected void onPause() {
        super.onPause();
        releaseCamera();              // release the camera immediately on pause event
        mSensor.onPause();
    }
	
	@Override
	protected void onResume() {
		super.onResume();
		grabCamera();
		mCamera.startPreview();
		mSensor.onResume();
	}
	
	@Override
    protected void onDestroy() {
        super.onDestroy();
        releaseCamera();              // release the camera immediately on pause event
        mSensor.onDestroy();
    }
	
	private void releaseCamera(){
        if (mCamera != null){
        	mCamera.stopPreview();
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }
	
	private void grabCamera(){
		if (mCamera == null){
			mCamera = getCameraInstance();
	        // Create our Preview view and set it as the content of our activity.
			FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
			if (mPreview != null) {
				preview.removeView(mPreview);
			}
	        mPreview = new CameraPreview(this, mCamera);
	        preview.addView(mPreview, 1);
	        //LinearLayout button_frame = (LinearLayout) findViewById(R.id.fullscreen_content_controls);
	        //preview.removeView(button_frame);
	        //preview.addView(button_frame);
	        preview.invalidate();
			mCamera.startPreview();
		}
	}
	
	// The dialog fragment receives a reference to this Activity through the
    // Fragment.onAttach() callback, which it uses to call the following methods
    // defined by the NoticeDialogFragment.NoticeDialogListener interface
    @Override
    public void onCaptureSettingsPositiveClick(DialogFragment dialog, int numImages) {
        // User touched the dialog's positive button
    	// Update # of photos used to create model, delete previous photos/depth maps, start over
    	SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
    	SharedPreferences.Editor editor = sharedPref.edit();
    	editor.putInt(getString(R.string.saved_setting_num_images), numImages);
    	editor.commit();
    }

    @Override
    public void onCaptureSettingsNegativeClick(DialogFragment dialog) {
        // User touched the dialog's negative button
    	// User cancelled the dialog, don't update/start over
        
    }

	public class CircleView extends View {
		Paint paint = new Paint();
		public CircleView(Context context) {
			super(context);
			paint.setColor(Color.WHITE);
		}
	
	    public void onDraw(Canvas canvas) {
	            canvas.drawLine(0, 0, 20, 20, paint);
	            canvas.drawLine(20, 0, 0, 20, paint);
	    }
	}
}
