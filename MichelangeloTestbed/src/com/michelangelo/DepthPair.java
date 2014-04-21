package com.michelangelo;

import org.opencv.core.Mat;

import android.graphics.Bitmap;

public class DepthPair {
	Mat disparity;
	Mat rgb;
	Bitmap disp;
	Bitmap thumbnail;
	
	public DepthPair(Mat disparity, Mat rgb, Bitmap disp, Bitmap thumbnail) {
		this.disparity = disparity;
		this.rgb = rgb;
		this.disp = disp;
		this.thumbnail = thumbnail;
	}	
}
