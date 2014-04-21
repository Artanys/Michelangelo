package com.michelangelo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MichelangeloCamera extends MichelangeloUI implements
		CaptureSettingsFragment.CaptureSettingsListener {

	boolean new_picture = false;
	private static final String TAG = "MichelangeloCamera";
	public static final int MEDIA_TYPE_IMAGE = 1;
	public static final int MEDIA_TYPE_VIDEO = 2;
	public static int NUM_IMAGES = 16;

	public static final long pulse_duration = 100;
	public static final long pulse_off_duration = 600;

	private Camera mCamera = null;
	private CameraPreview mPreview = null;
	private ExecutorService mExecutor = null;
	private ArrayList<Future<DepthPair>> mTaskList = null;
	private ArrayList<DepthMapper> mDMList = null;
	private Handler mHandler = null;
	private MichelangeloSensor mSensor;
	private Bitmap bitmapLast;
	int cameraTimeCount;

	public RenderingDialog rend = new RenderingDialog();
	private static boolean first = true;

	// public Vibrator vibe;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.activity_michelangelo_camera);
		super.onCreate(savedInstanceState);
		// vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		// if(vibe.hasVibrator()) vibe.vibrate(new long[]{0, pulse_duration,
		// pulse_off_duration}, 0);

		Button captureButton = (Button) findViewById(R.id.button_capture);
		captureButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// get an image from the camera
				mSensor.firstImage = !mSensor.firstImage;
				boolean temp = mSensor.firstImage;
				mCamera.autoFocus(null);
				mCamera.takePicture(null, null, mPicture);
				if(mSensor.CaptureNumber == 0){
					mSensor.InitialYaw = mSensor.Rad_orientation[0];
					mSensor.NumberOfCaptures = 1;//MichelangeloCamera.NUM_IMAGES;
				}
				mSensor.incrementCaptureNumber();
				if(!mSensor.firstImage){
					ImageView lastImage = (ImageView) findViewById(R.id.last_image);
					lastImage.setVisibility(View.VISIBLE);
				} else {
					ImageView lastImage = (ImageView) findViewById(R.id.last_image);
					lastImage.setVisibility(View.GONE);
				}
				if(mSensor.getCaptureNumber() > mSensor.NumberOfCaptures){
					Context context = getApplicationContext();
					CharSequence text = "Finished Image Capture Series!";
					int duration = Toast.LENGTH_SHORT;

					mSensor.setCaptureNumber(0);
					mSensor.InitialYaw = 0;
					mSensor.NumberOfCaptures = 1;

					ImageView lastImage = (ImageView) findViewById(R.id.last_image);
					lastImage.setVisibility(View.GONE);
					mSensor.firstImage = true;
				}
			}
		});

		getActionBar().setDisplayHomeAsUpEnabled(true);
		getActionBar().setDisplayShowHomeEnabled(true);
		getActionBar().setHomeButtonEnabled(true);

		FrameLayout cameraPreview = (FrameLayout) findViewById(R.id.camera_window);
		cameraPreview.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// get an image from the camera
				mCamera.autoFocus(null);
			}
		});

		Log.i(TAG, "Trying to load OpenCV library");
		if (!OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_8, this,
				mOpenCVCallback)) {
			Log.e(TAG, "Cannot connect to OpenCV Manager");
		}

		final Handler handler = new Handler();
		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				handler.post(new Runnable() {
					public void run() {
						AngledLineView alv = (AngledLineView) findViewById(R.id.circleLine);
						CenteredAngledLineView horizonLine = (CenteredAngledLineView) findViewById(R.id.horizonLine);
						CenteredAngledLineView pitchLine = (CenteredAngledLineView) findViewById(R.id.pitchLine);
						CircleView alvCircle = (CircleView) findViewById(R.id.circle);
						TextView yawText = (TextView) findViewById(R.id.yaw_text);
						TextView pitchText = (TextView) findViewById(R.id.pitch_text);
						TextView rollText = (TextView) findViewById(R.id.roll_text);
						yawText.setText((int)mSensor.Deg_orientation[0] + "°");
						pitchText.setText((int)mSensor.Deg_orientation[1] + "°");
						rollText.setText((int)mSensor.Deg_orientation[2] + "°"); 
						float yaw = mSensor.Rad_orientation[0];
						float pitch = mSensor.Rad_orientation[1];
						float roll = mSensor.Rad_orientation[2];
						alv.setAngle(yaw);
						pitchLine.setAngle(pitch);
						horizonLine.setAngle(roll);

						if (mSensor.PITCHREACHED) {
							pitchLine.paint.setColor(Color.GREEN);
							pitchText.setTextColor(Color.GREEN);
						} else {
							pitchLine.paint.setColor(Color.LTGRAY);
							pitchText.setTextColor(Color.LTGRAY);
						}

						if (mSensor.ROLLREACHED) {
							horizonLine.paint.setColor(Color.GREEN);
							rollText.setTextColor(Color.GREEN);
						} else {
							horizonLine.paint.setColor(Color.LTGRAY);
							rollText.setTextColor(Color.LTGRAY);
						}

						if (mSensor.YAWREACHED) {
							alv.paint.setColor(Color.GREEN);
							yawText.setTextColor(Color.GREEN);
							alvCircle.paint.setColor(Color.GREEN);
							alvCircle.invalidate();
						} else {
							alv.paint.setColor(Color.LTGRAY);
							yawText.setTextColor(Color.LTGRAY);
							alvCircle.paint.setColor(Color.LTGRAY);
							alvCircle.invalidate();
						}
						/*
						if(mSensor.YAWREACHED && mSensor.ROLLREACHED && mSensor.PITCHREACHED ){
							cameraTimeCount += 1;
						} else {
							cameraTimeCount = 0;
							mSensor.allReached = false;
						}
						
						if(cameraTimeCount == 12 && mSensor.firstImage && !mSensor.allReached){
							takePicture();
							cameraTimeCount = 0;
						}
						
						if( cameraTimeCount == 28 && !mSensor.firstImage && !mSensor.allReached){
							takePicture();
							cameraTimeCount = 0;
						}*/

					}
				});
			}
		}, 125, 125);

		releaseCamera();
		grabCamera();
		mSensor = new MichelangeloSensor();
		mSensor.onCreate(this);

		Log.d(TAG, "Done creating Camera Page");

	}
	
	public void takePicture() {
		// get an image from the camera
		mSensor.firstImage = !mSensor.firstImage;
		//mCamera.autoFocus(null);
		mCamera.takePicture(null, null, mPicture);
		if(mSensor.getCaptureNumber() == 0){
			mSensor.InitialYaw = mSensor.Rad_orientation[0];
			mSensor.NumberOfCaptures = 1;//MichelangeloCamera.NUM_IMAGES;
		}

		mSensor.incrementCaptureNumber();
		
		if(mSensor.firstImage){
			ImageView lastImage = (ImageView) findViewById(R.id.last_image);
			lastImage.setVisibility(View.VISIBLE);
		} else {
			ImageView lastImage = (ImageView) findViewById(R.id.last_image);
			lastImage.setVisibility(View.GONE);
		}

		if(mSensor.getCaptureNumber() == mSensor.NumberOfCaptures){
			mSensor.setCaptureNumber(0);
			mSensor.InitialYaw = 0;
			mSensor.NumberOfCaptures = 1;

			ImageView lastImage = (ImageView) findViewById(R.id.last_image);
			lastImage.setVisibility(View.GONE);
			mSensor.firstImage = true;
			//mPreview.setVisibility(View.GONE);
		}
		//cameraTimeCount = 0;
	}
	


	/** A safe way to get an instance of the Camera object. */
	/**
	 * http://developer.android.com/guide/topics/media/camera.html#custom-camera
	 * , January 21, 2014
	 */
	public static Camera getCameraInstance() {
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
		} catch (Exception e) {
			// Camera is not available (in use or does not exist)
		}
		return c; // returns null if camera is unavailable
	}

	/** A basic Camera preview class */
	public class CameraPreview extends SurfaceView implements
			SurfaceHolder.Callback {
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
			// The Surface has been created, now tell the camera where to draw
			// the preview.

			try {
				mCamera.setPreviewDisplay(holder);
				if (mCamera != null) {
					setCameraDisplayOrientation(MichelangeloCamera.this,
							CameraInfo.CAMERA_FACING_BACK, mCamera);
				}
				mCamera.startPreview();
			} catch (IOException e) {
				Log.d(TAG, "Error setting camera preview: " + e.getMessage());
			}
		}

		public void surfaceDestroyed(SurfaceHolder holder) {
			// empty. Take care of releasing the Camera preview in your
			// activity.
			if (mCamera != null) {
				// Call stopPreview() to stop updating the preview surface.
				try {
					mCamera.stopPreview();
				} catch (Exception e) {
					// ignore: tried to stop a non-existent preview
				}
			}
		}

		public void surfaceChanged(SurfaceHolder holder, int format, int w,
				int h) {
			// If your preview can change or rotate, take care of those events
			// here.
			// Make sure to stop the preview before resizing or reformatting it.

			if (mHolder.getSurface() == null) {
				// preview surface does not exist
				return;
			}

			// stop preview before making changes
			try {
				mCamera.stopPreview();
			} catch (Exception e) {
				// ignore: tried to stop a non-existent preview
			}

			// set preview size and make any resize, rotate or
			// reformatting changes her

			if (mCamera != null) {
				setCameraDisplayOrientation(MichelangeloCamera.this,
						CameraInfo.CAMERA_FACING_BACK, mCamera);
			}

			// start preview with new settings
			try {
				mCamera.setPreviewDisplay(mHolder);
				mCamera.startPreview();

			} catch (Exception e) {
				Log.d(TAG, "Error starting camera preview: " + e.getMessage());
			}
		}
	}

	private PictureCallback mPicture = new PictureCallback() {

		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			Log.d(TAG, "Taking Picture");
			ImageView lastImage = (ImageView) findViewById(R.id.last_image);
			RectangleView guideBox = (RectangleView) findViewById(R.id.guideBox);
			FrameLayout lastImageBox = (FrameLayout) findViewById(R.id.last_image_window);
			FrameLayout imageBox = (FrameLayout) findViewById(R.id.camera_window);
			LinearLayout overlayBox = (LinearLayout) findViewById(R.id.overlay);
			mPreview.setVisibility(View.GONE);
			File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE, "original");
			if (pictureFile == null) {
				Log.d(TAG,
						"Error creating media file, check storage permissions");
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
			mPreview.setVisibility(View.VISIBLE);

			Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
			//thumbnail
			Bitmap thumbnail = ThumbnailUtils.extractThumbnail(bitmap, 100, 100);
			saveBitmap(thumbnail,"THUMB_TEST");
			
			int width = imageBox.getWidth();
			int height = imageBox.getHeight();

			Matrix mat = new Matrix();
			mat.postRotate(90);
			Bitmap bitmapRot = Bitmap.createBitmap(bitmap, 0, 0,bitmap.getWidth(),bitmap.getHeight(), mat, true);
			Bitmap bitmapResized = Bitmap.createScaledBitmap(bitmapRot, width, height, true);
			bitmapLast = Bitmap.createBitmap(bitmapResized, guideBox.getLeft() + overlayBox.getLeft(), guideBox.getTop() + lastImageBox.getTop() 
					+ overlayBox.getTop(), lastImage.getWidth(), lastImage.getHeight());
			lastImage.setImageBitmap(bitmapLast);
			
			if(mSensor.firstImage){
				guideBox.setX(getPixels(100));			
			} else {
				
				guideBox.setX(getPixels(0));
			}
			
			int bmWidth = bitmapRot.getWidth();
			int bmHeight = bitmapRot.getHeight();
			Mat ImageMat = bitmapToMat(bitmapRot);
			Mat grayMat = colorMatToGrayscale(ImageMat);
			// saveBitmap(grayMatToBitmap(grayMat));

			if (mHandler == null)
				mHandler = new Handler();
			if (mExecutor == null)
				mExecutor = Executors.newCachedThreadPool();
			if (mDMList == null)
				mDMList = new ArrayList<DepthMapper>();
			if (mTaskList == null)
				mTaskList = new ArrayList<Future<DepthPair>>();
			Parameters params = mCamera.getParameters();
			float focalLength = params.getFocalLength();
			
			//promptIntents();
			DepthMapper dm = new DepthMapper((int) (focalLength * 10),
					bmHeight, grayMat, ImageMat, thumbnail);
			dm.setWindowSize(DepthMapper.WINDOW_SIZE.MEDIUM);
			dm.setFilterMode(DepthMapper.FILTER_MODE.NONE);
			mDMList.add(dm);
			
			if (mDMList.size() == 2) {
				mDMList.get(0).setRightData(bmWidth, bmHeight,
						grayMat);
				mTaskList
						.add(mExecutor.submit(mDMList.get(0)));
				mDMList.clear();
				new_picture = true;
			}
			
			if(new_picture) {
				new_picture = false;
//				AlertDialog alertDialog = new AlertDialog.Builder(MichelangeloCamera.this).create();
//				alertDialog.setTitle("Loading");
//				alertDialog.setMessage("Creating Depth Map...");
//				alertDialog.show();
				
				rend.show(getFragmentManager(), "message");
				
				Runnable dialog = new Runnable() {
					public void run() {

						DepthPair result = null;
						try {
							//Thread.sleep(1000);
							result = mTaskList.get(mTaskList.size() - 1).get();
							rend.dismiss();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (ExecutionException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
			 			DepthMapConfirmDialog dispFrag = new DepthMapConfirmDialog();	 			
			 			dispFrag.depthpair = result;	 			
			 			dispFrag.show(getFragmentManager(), "message");
					}
				};
				Thread myThread = new Thread(dialog);
				myThread.start();
			}
		}
	};
	
	private int getPixels(int dipValue){ 
	     Resources r = getResources();
	     int px = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue,   r.getDisplayMetrics());
	     return px; 
	}

	/** Create a File for saving an image or video */
	private static File getOutputMediaFile(int type, String tag) {
		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.

		File mediaStorageDir = new File(
				Environment
						.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
				"Michelangelo");
		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.

		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				Log.d("MichelangeloCamera", "failed to create directory");
				return null;
			}
		}

		// Create a media file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
				.format(new Date());
		File mediaFile;
		if (type == MEDIA_TYPE_IMAGE) {
			mediaFile = new File(mediaStorageDir.getPath() + File.separator
					+ "IMG_" + timeStamp + tag + ".jpg");
		} else if (type == MEDIA_TYPE_VIDEO) {
			mediaFile = new File(mediaStorageDir.getPath() + File.separator
					+ "VID_" + timeStamp + ".mp4");
		} else {
			return null;
		}

		return mediaFile;
	}

	/** Create a File for saving an image or video */
	public static ArrayList<File> getMediaFiles() {
		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.

		ArrayList<File> matchingFiles = new ArrayList<File>();

        File root = android.os.Environment.getExternalStorageDirectory();               

        File mediaStorageDir = new File (root.getAbsolutePath() + "/Pictures/Michelangelo/models");
        if(mediaStorageDir.exists()==false) {
        	mediaStorageDir.mkdirs();
        }
		
		/*File mediaStorageDir = new File(
				Environment
						.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
				"Michelangelo");*/
		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.

		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			Log.d("MichelangeloCamera", "media Directory doesn't exist!");
			return matchingFiles;
		}

		File[] files = mediaStorageDir.listFiles();

		for (File file : files) {
			if (file.getName().endsWith(".jpg")) {
				matchingFiles.add(file);
			}
		}

		return matchingFiles;
	}

	public static void setCameraDisplayOrientation(Activity activity,
			int cameraId, android.hardware.Camera camera) {
		android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
		android.hardware.Camera.getCameraInfo(cameraId, info);
		int rotation = activity.getWindowManager().getDefaultDisplay()
				.getRotation();
		int degrees = 0;
		switch (rotation) {
		case Surface.ROTATION_0:
			degrees = 0;
			break;
		case Surface.ROTATION_90:
			degrees = 90;
			break;
		case Surface.ROTATION_180:
			degrees = 180;
			break;
		case Surface.ROTATION_270:
			degrees = 270;
			break;
		}

		int result;
		// if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
		// result = (info.orientation + degrees) % 360;
		// result = (360 - result) % 360; // compensate the mirror
		// } else { // back-facing
		result = (info.orientation - degrees + 360) % 360;
		// }
		camera.setDisplayOrientation(result);
	}

	@Override
	protected void onPause() {
		super.onPause();
		// vibe.cancel(); //turn off the vibration
		releaseCamera(); // release the camera immediately on pause event
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
		// vibe.cancel(); //turn off the vibration
		releaseCamera(); // release the camera immediately on pause event
		mSensor.onDestroy();
	}

	private void releaseCamera() {
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.release(); // release the camera for other applications
			mCamera = null;
		}
	}

	private void grabCamera() {
		if (mCamera == null) {
			mCamera = getCameraInstance();
			Parameters params = mCamera.getParameters();
			List<Size> sizes = params.getSupportedPictureSizes();
			int maxSize = Integer.MAX_VALUE;
			Size minSize = null;
			Size medSize = sizes.get(sizes.size() / 2);
			Size maxSizeIndex = sizes.get(0);
			for (Size size : sizes) {
				if (size.height * size.width < maxSize) {
					minSize = size;
				}
			}

			// params.setPictureSize(minSize.width, minSize.height);
			params.setPictureSize(medSize.width, medSize.height);
			// params.setPictureSize(maxSizeIndex.width, maxSizeIndex.height);
			// params.setPreviewSize(params.getPictureSize().width,
			// params.getPictureSize().height);
			params.setPreviewSize(minSize.width, minSize.height);
			mCamera.setParameters(params);
			Log.w(TAG, "Picture size: width = " + params.getPreviewSize().width
					+ " height = " + params.getPreviewSize().height);
			// Create our Preview view and set it as the content of our
			// activity.
			FrameLayout preview = (FrameLayout) findViewById(R.id.camera_window);
			if (mPreview != null) {
				preview.removeView(mPreview);
			}
			mPreview = new CameraPreview(this, mCamera);
			preview.addView(mPreview, 0);
			mCamera.startPreview();
			preview.invalidate();
		}
	}

	public Bitmap toGrayscale(Bitmap bmpOriginal) {
		final int height = bmpOriginal.getHeight();
		final int width = bmpOriginal.getWidth();

		final Bitmap bmpGrayscale = Bitmap.createBitmap(width, height,
				Bitmap.Config.ARGB_8888);
		final Canvas c = new Canvas(bmpGrayscale);
		final Paint paint = new Paint();
		final ColorMatrix cm = new ColorMatrix();
		cm.setSaturation(0);
		final ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
		paint.setColorFilter(f);
		c.drawBitmap(bmpOriginal, 0, 0, paint);
		return bmpGrayscale;
	}

	public Mat bitmapToMat(Bitmap bmpOriginal) {
		Mat bmpMat = new Mat(bmpOriginal.getHeight(), bmpOriginal.getWidth(),
				CvType.CV_8U, new Scalar(4));
		Utils.bitmapToMat(bmpOriginal, bmpMat);
		return bmpMat;
	}

	public Mat colorMatToGrayscale(Mat matColor) {
		Mat grayMat = new Mat(matColor.rows(), matColor.cols(), CvType.CV_8UC1);
		Imgproc.cvtColor(matColor, grayMat, Imgproc.COLOR_RGBA2GRAY);
		return grayMat;
	}

	public static Bitmap grayMatToBitmap(Mat matOrig) {
		Bitmap bmpResult = Bitmap.createBitmap(matOrig.cols(), matOrig.rows(),
				Bitmap.Config.ARGB_8888);
		Mat temp = new Mat(matOrig.rows(), matOrig.cols(), CvType.CV_8UC4);
		Imgproc.cvtColor(matOrig, temp, Imgproc.COLOR_GRAY2RGBA, 4);
		Utils.matToBitmap(matOrig, bmpResult);
		return bmpResult;
	}

	public static Bitmap colorMatToBitmap(Mat matOrig) {
		Bitmap bmpResult = Bitmap.createBitmap(matOrig.cols(), matOrig.rows(),
				Bitmap.Config.ARGB_8888);
		Utils.matToBitmap(matOrig, bmpResult);
		return bmpResult;
	}

	public static void saveBitmap(Bitmap bitmap, String tag) {
		File resultFile = getOutputMediaFile(MEDIA_TYPE_IMAGE, tag);
		if (resultFile == null) {
			Log.d(TAG, "Error creating media file, check storage permissions");
			return;
		}

		try {
			Log.d(TAG, "Storing bitmap file.");
			FileOutputStream fosResult = new FileOutputStream(resultFile);
			Log.d(TAG, "Compressing bitmap.");
			bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fosResult);
			Log.d(TAG, "Finished compressing.");
			Log.d(TAG, "Writing to file.");
			fosResult.flush();
			fosResult.close();
			Log.d(TAG, "Bitmap written.");
			Thread.sleep(500);
		} catch (FileNotFoundException e) {
			Log.d(TAG, "File not found: " + e.getMessage());
		} catch (IOException e) {
			Log.d(TAG, "Error accessing file: " + e.getMessage());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unused")
	public static void saveThumbnail(Bitmap bitmap, String name) {
		 File root = android.os.Environment.getExternalStorageDirectory();               
         File dir = new File (root.getAbsolutePath() + "/Pictures/Michelangelo/models");
         if(dir.exists()==false) {
              dir.mkdirs();
         }
		File resultFile = new File(dir, name);;
		if (resultFile == null) {
			Log.d(TAG, "Error creating media file, check storage permissions");
			return;
		}

		try {
			Log.d(TAG, "Storing bitmap file.");
			FileOutputStream fosResult = new FileOutputStream(resultFile);
			Log.d(TAG, "Compressing bitmap.");
			bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fosResult);
			Log.d(TAG, "Finished compressing.");
			Log.d(TAG, "Writing to file.");
			fosResult.flush();
			fosResult.close();
			Log.d(TAG, "Bitmap written.");
			Thread.sleep(500);
		} catch (FileNotFoundException e) {
			Log.d(TAG, "File not found: " + e.getMessage());
		} catch (IOException e) {
			Log.d(TAG, "Error accessing file: " + e.getMessage());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	// The dialog fragment receives a reference to this Activity through the
	// Fragment.onAttach() callback, which it uses to call the following methods
	// defined by the NoticeDialogFragment.NoticeDialogListener interface
	@Override
	public void onCaptureSettingsPositiveClick(DialogFragment dialog,
			int numImages) {
		// User touched the dialog's positive button
		// Update # of photos used to create model, delete previous photos/depth
		// maps, start over
		SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putInt(getString(R.string.saved_setting_num_images), numImages);
		NUM_IMAGES = 2 * numImages;
		editor.commit();
	}

	@Override
	public void onCaptureSettingsNegativeClick(DialogFragment dialog) {
		// User touched the dialog's negative button
		// User cancelled the dialog, don't update/start over

	}

	private BaseLoaderCallback mOpenCVCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			if (status == LoaderCallbackInterface.SUCCESS) {
				Log.i(TAG, "OpenCV loaded successfully");
			} else {
				super.onManagerConnected(status);
			}
		}
	};
}
