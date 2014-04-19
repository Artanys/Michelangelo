package com.michelangelo;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import android.util.Log;



public class Server {
	private static final String TAG = "Server";
	private static Socket client;
	private static DataOutputStream ostream;
	private static int scale = 4;

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
		Server.send(left.rows()/scale);
		Server.send(left.cols()/scale);
		Server.send(numImages);
		Server.send(Q03/scale);
		Server.send(Q13/scale);
		Server.send(Q23);
		Server.send(Q32);
		Server.send(Q33);
		flush();
		Log.i("Server","Data sent to server");
	}
	
	
	public synchronized static void sendColor (Mat output){
		Server.send(CvType.CV_8UC4);
		for(int r=0 ; r<output.rows(); r+=scale){
			for (int c=0; c<output.cols(); c+=scale){
				byte[] element = new byte[output.channels()]; 
				output.get(r,c,element);
					Server.send(element[2]);
					Server.send(element[1]);
					Server.send(element[0]);
					Server.send(element[3]);
					//Log.i(TAG,Double.toString(element[dep]));
			}
		}
		flush();
	}
	
	public synchronized static void sendGray (Mat output){
		Server.send(output.type());
		for(int r=0 ; r<output.rows(); r+=scale){
			for (int c=0; c<output.cols(); c+=scale){
				byte[] element = new byte[output.channels()]; 
				output.get(r,c,element);
				
					Server.send(element[0]);
					//Log.i(TAG,Double.toString(element[dep]));
				
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
	public static void send(String message){
		try {
			ostream.writeChars(message);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static void send(int message){
		try {
			ostream.writeInt(message);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static void send(byte message){
		try {
			ostream.writeByte(message);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static void send(double message){
		try {
			ostream.writeDouble(message);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
		public static void send(float message){
			try {
				ostream.writeFloat(message);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		}

	
	}