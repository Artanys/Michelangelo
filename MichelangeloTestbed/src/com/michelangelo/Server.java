package com.michelangelo;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;

import org.apache.http.util.ByteArrayBuffer;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import android.util.Log;



public class Server {
	private static final String TAG = "Server";
	private static Socket client;
	private static DataOutputStream ostream;
	private static DataInputStream istream;
	private static BufferedReader br;
	private static int scale = 4;

	public synchronized static void initClient() {
		try {
		Log.w(TAG, "initClient entered");
		client = new Socket("naumann.cloudapp.net", 3000);
		br = new BufferedReader(new InputStreamReader(client.getInputStream()));
		ostream = new DataOutputStream(client.getOutputStream());
		Log.w(TAG, "initClient finshed");
		} catch (UnknownHostException e){
			e.printStackTrace();
		} catch (IOException e) {
		     e.printStackTrace();
		  }
	}
	
	
	public synchronized static void sendFrame (Mat left, int numImages, double Q03, double Q13, double Q23, double Q32, double Q33){
		Log.i("Server","Sending frame to server");
		Server.send(left.rows()/scale);
		Server.send(left.cols()/scale);
		Server.send(numImages);
		Server.send(Q03/scale);
		Server.send(Q13/scale);
		Server.send(Q23);
		Server.send(Q32);
		Server.send(Q33);
		flush();
		Log.i("Server","Data frame to server");
	}
	
	
	public synchronized static void sendColor (Mat output){
		Log.i("Server","Sending colorMat to server");
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
		Log.i("Server","colorMat sent to server");
	}
	
	public synchronized static void sendGray (Mat output){
		Log.i("Server","Sending grayMat to server");
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
		Log.i("Server","grayMat sent to server");
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
		
		public static String receive(){
			Log.i("Server","Receive string started");
			String url = "";
			try {
				url = br.readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Log.i("Server","String received from server: " +url );
			return url;
		}

		public static void downloadFromUrl(String DownloadUrl, String fileName) {

			   try {
			           File root = android.os.Environment.getExternalStorageDirectory();               

			           File dir = new File (root.getAbsolutePath() + "/Pictures/Michelangelo/models");
			           if(dir.exists()==false) {
			                dir.mkdirs();
			           }

			           URL url = new URL(DownloadUrl); //you can write here any link
			           File file = new File(dir, fileName);

			           long startTime = System.currentTimeMillis();
			           Log.d("Server", "download begining");
			           Log.d("Server", "download url:" + url);
			           Log.d("Server", "downloaded file name:" + fileName);

			           /* Open a connection to that URL. */
			           URLConnection ucon = url.openConnection();

			           /*
			            * Define InputStreams to read from the URLConnection.
			            */
			           InputStream is = ucon.getInputStream();
			           BufferedInputStream bis = new BufferedInputStream(is);

			           /*
			            * Read bytes to the Buffer until there is nothing more to read(-1).
			            */
			           ByteArrayBuffer baf = new ByteArrayBuffer(5000);
			           int current = 0;
			           while ((current = bis.read()) != -1) {
			              baf.append((byte) current);
			           }


			           /* Convert the Bytes read to a String. */
			           FileOutputStream fos = new FileOutputStream(file);
			           fos.write(baf.toByteArray());
			           fos.flush();
			           fos.close();
			           Log.d("Server", "download ready in" + ((System.currentTimeMillis() - startTime) / 1000) + " sec");

			   } catch (IOException e) {
			       Log.d("Server", "Error: " + e);
			   }

			}
	}