package com.michelangelo;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import java.lang.Math;

public class MichelangeloSensor implements SensorEventListener {
	private SensorManager mSensorManager;
	private Sensor mSensor;
	private Context mContext;
	private String TAG="MichelangeloSensor";
	
	private final float DEG = 180 / (float) Math.PI;
	
	private final float[] mRotationMatrix = new float[16];
	private final float[] RadialOrientation = new float[16];
	public final float[] Deg_orientation = new float[3];
	public final float[] Rad_orientation = new float[3];
	public final float[] internal_Deg_Orientation = new float[3];
	
	public float InitialYaw = 0; //degrees
	public int CaptureNumber = 0;
	public int NumberOfCaptures = 1;
	
	private final float PITCHTARGET = 25.0f;
	private final float ROLLTARGET = 0;
	public float YAWTARGET;

	public boolean prevRollReached = false;
	public boolean prevPitchReached = false;
	public boolean prevYawReached = false;
	public boolean PITCHREACHED = false;
	public boolean ROLLREACHED = false;
	public boolean YAWREACHED = true;
	
	public void onCreate(Context ActivityContext) {
		
		this.mContext = ActivityContext;
		
		mRotationMatrix[ 0] = 1;
        mRotationMatrix[ 4] = 1;
        mRotationMatrix[ 8] = 1;
        mRotationMatrix[12] = 1;
         
		mSensorManager = (SensorManager) ActivityContext.getSystemService(ActivityContext.getApplicationContext().SENSOR_SERVICE);
		mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
		start();
	}
	
	private void start() {
        // enable our sensor when the activity is resumed, ask for
        // 125 ms updates.
        mSensorManager.registerListener(this, mSensor, 125000);
    }
	
    private void stop() {
        // make sure to turn our sensor off when the activity is paused
        mSensorManager.unregisterListener(this);
    }

	public void onPause() {
		stop();
	}
	
	public void onResume() {
		start();
	}
	
	public void onDestroy() {
		stop();
	}

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// we received a sensor event. it is a good practice to check
        // that we received the proper event
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            // convert the rotation-vector to a 4x4 matrix. the matrix
            // is interpreted by Open GL as the inverse of the
            // rotation-vector, which is what we want.
            SensorManager.getRotationMatrixFromVector(
                    mRotationMatrix , event.values);
            
            //SensorManager.remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, RadialOrientation);
            	
            
            SensorManager.getOrientation(mRotationMatrix, Rad_orientation);
            
            int PositionNumber = CaptureNumber / 2; 
            int NumberOfPositions = NumberOfCaptures / 2 + NumberOfCaptures % 2;
            
            YAWTARGET = InitialYaw*DEG + ((float) PositionNumber / NumberOfPositions ) * 360;
            
            if(YAWTARGET >= 180){
            	internal_Deg_Orientation[0] -= 360;
            }
            
            if(( YAWTARGET > Rad_orientation[0]*DEG ) && (( Rad_orientation[0]*DEG - (int) YAWTARGET ) >= 360))
            	YAWTARGET -= 360;
            
            if(( YAWTARGET < Rad_orientation[0]*DEG ) && (( Rad_orientation[0]*DEG - YAWTARGET ) <= -360))
            	YAWTARGET += 360;
            
            internal_Deg_Orientation[0] = (int)(Rad_orientation[0]*DEG) - YAWTARGET;
            internal_Deg_Orientation[1] = (int)(Rad_orientation[1]*DEG+90) - PITCHTARGET;
            internal_Deg_Orientation[2] = (int)(Rad_orientation[2]*DEG) - ROLLTARGET;
            
			if( NumberOfCaptures == 1) {
				Deg_orientation[0] = 0;
			} else {
				Deg_orientation[0] = internal_Deg_Orientation[0];
			}
			
			if(Deg_orientation[0] >= 180)
				Deg_orientation[0] -= 360;
			
			if(Deg_orientation[0] < -180)
				Deg_orientation[0] += 360;
			
			Deg_orientation[1] = internal_Deg_Orientation[1];
			Deg_orientation[2] = internal_Deg_Orientation[2];

            if(( -2 <= Deg_orientation[1]) && ( 2 >= Deg_orientation[1] )) {
            	PITCHREACHED = true;
            } else {
            	PITCHREACHED = false;
            }
            
            if(( -2 <= Deg_orientation[2]) && ( 2 >= Deg_orientation[2] )) {
            	ROLLREACHED = true;
            } else {
            	ROLLREACHED = false;
            }
            
            if(( -2 <= Deg_orientation[0]) && ( 2 >= Deg_orientation[0] )) {
            	YAWREACHED = true;
            } else {
            	YAWREACHED = false;
            }
        }
	}
}
