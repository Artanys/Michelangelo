package com.michelangelo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class CircleView extends View {
	Paint paint = new Paint();
	
	public CircleView(Context context, AttributeSet attrs) {
		super(context, attrs);
		paint.setColor(Color.LTGRAY);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth((float) 1.5);
	}
	
	public CircleView(Context context) {
		super(context);
		paint.setColor(Color.WHITE);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth((float) 1.5);
	}
	protected void onDraw(Canvas canvas) {
		canvas.drawCircle(this.getWidth()/2, this.getHeight()/2, this.getWidth()/2, paint);
	}
}
