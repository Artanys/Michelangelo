package com.michelangelo;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.lang.Math;;

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
	
	private final float PITCHTARGET = 25.0f;
	private final float ROLLTARGET = 0;
	private float YAWTARGET;

	public boolean prevRollReached = false;
	public boolean prevPitchReached = false;
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
            
            Deg_orientation[0] = (int)(Rad_orientation[0]*DEG);
            Deg_orientation[1] = (int)(Rad_orientation[1]*DEG+90);
            Deg_orientation[2] = (int)(Rad_orientation[2]*DEG);
            
            if(( ( PITCHTARGET - 1 ) < Deg_orientation[1]) && ( ( PITCHTARGET + 1 ) > Deg_orientation[1] )) {
            	PITCHREACHED = true;
            } else {
            	PITCHREACHED = false;
            }
            
            if(( ( ROLLTARGET - 1 ) < Deg_orientation[2]) && ( ( ROLLTARGET + 1 ) > Deg_orientation[2] )) {
            	ROLLREACHED = true;
            } else {
            	ROLLREACHED = false;
            }
        }
	}
}
