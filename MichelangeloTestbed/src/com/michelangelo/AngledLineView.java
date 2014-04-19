package com.michelangelo;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class AngledLineView extends View {

	Paint paint = new Paint();
	float angle = 0;
	boolean radiusSet = false;
	float cX = 0, cY = 0;
	float radius = (float) 0.0;
	
	public AngledLineView(Context context, AttributeSet attrs) {
		super(context, attrs);
		paint.setColor(Color.LTGRAY);
		paint.setStrokeWidth((float) 2);
		TypedArray array = context.getTheme().obtainStyledAttributes(attrs, R.styleable.LineView, 0, 0);
		
		try {
			angle = array.getFloat(R.styleable.LineView_angle, 0);
			angle = (float) (angle * (Math.PI / 180));
		} finally {
			array.recycle();
		}
		
		//paint.setStyle(Paint.Style.STROKE);
		//paint.setStrokeWidth((float) 1.5);
	}

	public AngledLineView(Context context) {
		super(context);
		paint.setColor(Color.LTGRAY);
		paint.setStrokeWidth((float) 2);
		//paint.setStyle(Paint.Style.STROKE);
		//paint.setStrokeWidth((float) 1.5);
	}

	protected void onDraw(Canvas canvas) {
		if(!radiusSet) {
			cX = this.getWidth() / 2; cY = this.getHeight()/2;
			radius = Math.min(cX, cY);
			radiusSet = true;
		}
		float stopX = (float) (radius * Math.cos(angle));
		float stopY = (float) (radius * Math.sin(angle));
		
		canvas.drawLine(cX, cY, stopX + cX, stopY + cY, paint);
	}
	
	public float getAngle() {
		return angle;
	}
	
	public void setAngle(float angle) {
		this.angle = angle;
		this.invalidate();
	}
}
