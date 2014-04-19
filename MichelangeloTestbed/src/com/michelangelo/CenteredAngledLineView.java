package com.michelangelo;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

public class CenteredAngledLineView extends AngledLineView{

	public CenteredAngledLineView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public CenteredAngledLineView(Context context) {
		super(context);
	}
	
	protected void onDraw(Canvas canvas) {
		if(!radiusSet) {
			radius = Math.min(this.getWidth(), this.getHeight())/2;
			radiusSet = true;
		}
		float X = (float) (radius * Math.cos(angle));
		float Y = (float) (radius * Math.sin(angle));
		
		canvas.drawLine(radius - X, radius - Y, X + radius, Y + radius, paint);
	}
}
