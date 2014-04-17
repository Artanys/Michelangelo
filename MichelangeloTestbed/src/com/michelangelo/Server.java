package com.michelangelo;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import org.opencv.core.Mat;

import android.util.Log;



public class Server {
	private static final String TAG = "Server";
	private static Socket client;
	private static DataOutputStream ostream;


	public synchronized static void initClient() {
		try {
		Log.w(TAG, "initClient entered");
		client = new Socket("naumann.cloudapp.net", 3000);
		ostream = new DataOutputStream(client.getOutputStream());
		Log.w(TAG, "initClient finshed");
		} catch (UnknownHostException e){
			e.printStackTrace();
		} catch (IOException e) {
		     e.printStackTrace();
		  }
	}
	
	
	public synchronized static void sendFrame (Mat left, int numImages, double Q03, double Q13, double Q23, double Q32, double Q33){
		Log.i("Server","Sending data to server");
		Server.sendInt(left.rows());
		Server.sendInt(left.cols());
		Server.sendInt(numImages);
		Server.sendDouble(Q03);
		Server.sendDouble(Q13);
		Server.sendDouble(Q23);
		Server.sendDouble(Q32);
		Server.sendDouble(Q33);
		flush();
		Log.i("Server","Data sent to server");
	}
	
	public synchronized static void sendMat (Mat output){
		Server.sendInt(output.type());
		for(int r=0 ; r<output.rows(); r++){
			for (int c=0; c<output.cols(); c++){
				double[] element = output.get(r,c);
				for (int dep = 0; dep<output.channels(); dep++){
					Server.sendDouble(element[dep]);
					Log.i(TAG,Double.toString(element[dep]));
				}
			}
		}
		flush();
	}
	
	private static void flush(){
		try {
			ostream.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public static void sendString(String message){
		try {
			ostream.writeChars(message);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static void sendInt(int message){
		try {
			ostream.writeInt(message);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static void sendDouble(double message){
		try {
			ostream.writeDouble(message);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		
	}

	
	}