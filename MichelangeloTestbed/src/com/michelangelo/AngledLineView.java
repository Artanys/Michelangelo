package com.michelangelo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class AngledLineView extends View {

	Paint paint = new Paint();
	float angle = 0;
	boolean radiusSet = false;
	float radius = (float) 0.0;
	
	public AngledLineView(Context context, AttributeSet attrs) {
		super(context, attrs);
		paint.setColor(Color.LTGRAY);
		//paint.setStyle(Paint.Style.STROKE);
		//paint.setStrokeWidth((float) 1.5);
	}

	public AngledLineView(Context context) {
		super(context);
		paint.setColor(Color.LTGRAY);
		//paint.setStyle(Paint.Style.STROKE);
		//paint.setStrokeWidth((float) 1.5);
	}

	protected void onDraw(Canvas canvas) {
		if(!radiusSet) {
			radius = Math.min(this.getWidth(), this.getHeight())/2;
		}
		float stopX = (float) (radius * Math.cos(angle));
		float stopY = (float) (radius * Math.sin(angle));
		
		canvas.drawLine(this.getWidth()/2, this.getHeight()/2, stopX + radius, stopY + radius, paint);
	}
	
	public float getAngle() {
		return angle;
	}
	
	public void setAngle(float angle) {
		this.angle = angle;
		this.invalidate();
	}
}
