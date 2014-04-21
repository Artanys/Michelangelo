package com.michelangelo;

import java.util.LinkedList;

import org.opencv.core.Point;

import android.util.Log;

public class NonfreeJNILib {
	static {
		try {
			// Load necessary libraries.
			Log.i("NONFREE", "Trying to load nonfree libraries.");
			System.loadLibrary("opencv_java");
			System.loadLibrary("nonfree");
			System.loadLibrary("nonfree_jni");
			Log.i("NONFREE", "Libraries loaded.");
		} catch (UnsatisfiedLinkError e) {
			System.err.println("Native code library failed to load.\n" + e);
		}
	}

	public static native void runDemo();

	public static native LinkedList<LinkedList<Point>> surfDetect(
			long pImageLeft, long pImageRight);
}
