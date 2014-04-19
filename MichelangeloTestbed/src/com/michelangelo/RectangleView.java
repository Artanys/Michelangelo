package com.michelangelo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class RectangleView extends View {

	Paint paint = new Paint();
	boolean dimensionsSet = false;
	float width  = (float) 0.0;
	float height = (float) 0.0; 
	
	public RectangleView(Context context, AttributeSet attrs) {
		super(context, attrs);
		paint.setColor(Color.LTGRAY);
		//paint.setStrokeWidth((float) 2);
		
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth((float) 4);
	}

	public RectangleView(Context context) {
		super(context);
		paint.setColor(Color.LTGRAY);
		//paint.setStrokeWidth((float) 2);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth((float) 4);
	}

	protected void onDraw(Canvas canvas) {
		if(!dimensionsSet) {
			width = this.getWidth();
			height = this.getHeight();
		}
		
		canvas.drawRect(0, 0, width, height, paint);
		
		/*float stopX = (float) (radius * Math.cos(angle));
		float stopY = (float) (radius * Math.sin(angle));
		
		canvas.drawLine(this.getWidth()/2, this.getHeight()/2, stopX + radius, stopY + radius, paint);*/
	}
	
}

