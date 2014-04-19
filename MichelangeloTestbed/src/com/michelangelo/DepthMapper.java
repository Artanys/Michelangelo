package com.michelangelo;

import gnu.trove.list.array.TIntArrayList;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.concurrent.Callable;

import org.opencv.calib3d.Calib3d;
import org.opencv.calib3d.StereoSGBM;
import org.opencv.core.Core;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.features2d.DMatch;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.features2d.KeyPoint;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import android.graphics.Bitmap;
import android.os.StrictMode;
import android.util.Log;

public class DepthMapper implements Callable<Bitmap> {
	public int PIXEL_PRODUCTS[][] = null;

	public enum IMAGE_POSITION {
		LEFT, RIGHT
	};

	public enum WINDOW_SIZE {
		SMALL, MEDIUM, LARGE
	}

	public enum FILTER_MODE {
		MEDIAN, TRIMMED_MEAN, BILATERAL, BILSUB, GAUSSIAN, AVERAGE, NONE;
	}

	private Mat mCameraMatrix = new Mat(3, 3, CvType.CV_32FC1);
	private Mat mDistCoeffs = new Mat(5, 1, CvType.CV_32FC1);
	private Mat mQMatrix = new Mat(4, 4, CvType.CV_32FC1);
	private List<Mat> mRvecs, mTvecs;

	private interface FilterFunc {
		int filter(DepthMapper.Window window);
	}

	private EnumMap<FILTER_MODE, FilterFunc> filters = new EnumMap<FILTER_MODE, FilterFunc>(
			FILTER_MODE.class);

	private static final String TAG = "DepthMapper";
	private int[][] mYDataLeft = null;
	private int[][] mYDataRight = null;
	private int[][] mResult = null;
	private Mat mMatLeft = null;
	private Mat mMatRight = null;
	private int mFocalLength = 0;
	private int mImgWidth = 0;
	private int mImgHeight = 0;
	public Mat pointMid; 
	public Mat colorMat;
	private int mWindowWidth = 0;
	private int mWindowHeight = 0;
	private static boolean first = true;

	private FILTER_MODE mFilterMode = FILTER_MODE.NONE;

	public DepthMapper(int width, int height, Mat matLeft, Mat colorMat) {
		mMatLeft = matLeft;
		mImgWidth = width;
		mImgHeight = height;
		this.colorMat = colorMat;
		
		pointMid = new Mat(1,1,CvType.CV_32FC2);
		
		float xy[] = {matLeft.cols()/2, matLeft.rows()/2};
		
		pointMid.put(0, 0, xy);
		//Point mid = new Point(matLeft.cols()/2,matLeft.rows()/2);
		
		
//		List<Point> temp = new ArrayList<Point>(1);
//		temp.add(mid);
//		(Converters.vector_Point_to_Mat(temp)).convertTo(pointMid, CvType.CV_32F);
		
		mFocalLength = width;
		PIXEL_PRODUCTS = new int[256][256];
		for (int i = 0; i < 256; i++) {
			for (int j = 0; j < 256; j++) {
				PIXEL_PRODUCTS[i][j] = i * j;
			}
		}
		
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy);
		initMatrices();
		

		filters.put(FILTER_MODE.MEDIAN, new FilterFunc() {
			public int filter(Window window) {
				return window.getMedian();
			}
		});
		filters.put(FILTER_MODE.TRIMMED_MEAN, new FilterFunc() {
			public int filter(DepthMapper.Window window) {
				return window.getTrimmedMean();
			}
		});
		filters.put(FILTER_MODE.BILATERAL, new FilterFunc() {
			public int filter(Window window) {
				return window.getBilateral();
			}
		});
		filters.put(FILTER_MODE.BILSUB, new FilterFunc() {
			public int filter(Window window) {
				return window.getBilateralSub();
			}
		});
		filters.put(FILTER_MODE.GAUSSIAN, new FilterFunc() {
			public int filter(Window window) {
				return window.getGaussian();
			}
		});
		filters.put(FILTER_MODE.AVERAGE, new FilterFunc() {
			public int filter(Window window) {
				return window.getAverage();
			}
		});
		filters.put(FILTER_MODE.NONE, new FilterFunc() {
			public int filter(Window window) {
				return window.getCenter();
			}
		});
	}
	
	private void initMatrices() {
		// float[] camMatVals = { 5.25221191e+002f, 0.f, 2.45101059e+002f, 0.f,
		// 5.25221191e+002f, 3.21628296e+002f, 0.f, 0.f, 1.f };
		float[] camMatVals = { 1.f, 0.f, 0.f, 0.f, 1.f, 0.f, 0.f, 0.f, 1.f };
		// float[] distMatVals = { 7.12093562e-002f, 1.53228194e-001f,
		// 3.25737684e-003f, 2.18995404e-003f, -8.89789343e-001f };
		float[] distMatVals = { 0.f, 0.f, 0.f, 0.f, 0.f };
		float[] qMatVals = { 1.f, 0.f, 0.f, -320.f, 0.f, 1.f, 0.f, -439.f, 0.f,
				0.f, 0.f, (float) mFocalLength / 10, 0.f, 0.f, (-1 / 150.f), 0.f };
		mCameraMatrix.put(0, 0, camMatVals);
		mDistCoeffs.put(0, 0, distMatVals);
		mQMatrix.put(0, 0, qMatVals);
	}
	

	private Mat transformMidpoint(Mat midPoint, Mat transform){
		
		Mat dst = new Mat(1,1,CvType.CV_32FC2);
		String output = "";
		Mat m = new Mat();
		transform.convertTo(m, CvType.CV_32F);
		
		Core.perspectiveTransform(midPoint, dst, m);
		
		
		//Log points
		/*for(int i=0; i<midPoint.cols(); i++){
			output+=Arrays.toString(midPoint.get(0, i));
			output+=Arrays.toString(dst.get(0, i));
		}*/
		
		output += Arrays.toString(midPoint.get(0,0));
		output += Arrays.toString(dst.get(0, 0));
				
		Log.i("DepthMapper", output);
		
		return dst;
	}
	
	public Bitmap call() {
		Bitmap result = null;

		if (generateDepthMap()) {
			if(first){
				first = false;
				Server.initClient();
			}
			Server.sendFrame(mMatLeft, 1, -477, -640, 112, -.06666666666, 4);
			//Server.sendColor(colorMat);
			
			Mat temp = Mat.zeros(3,4,CvType.CV_32FC2);
			float[] f00 = new float[2];
			float[] f23 = new float[2];
			float[] f11 = new float[2];
			
			f00[0] = 3;
			f00[1] = (float) 1.7;
			f23[0] = 5;
			f11[0] = (float)(1.4);
			
			temp.put(0, 0, f00);
			temp.put(2,3, f23);
			temp.put(1, 1, f11);
			//Server.send(temp);
			
			// Detect features
			// Imgproc.equalizeHist(mMatLeft, mMatLeft);
			// Imgproc.equalizeHist(mMatRight, mMatRight);

			Mat combineOrig = combineImages(mMatLeft, mMatRight);
			MichelangeloCamera.saveBitmap(
					MichelangeloCamera.grayMatToBitmap(combineOrig),
					"origCombined");

			FeatureDetector orbDetector = FeatureDetector
					.create(FeatureDetector.FAST);
			MatOfKeyPoint leftKP = new MatOfKeyPoint();
			MatOfKeyPoint rightKP = new MatOfKeyPoint();
			orbDetector.detect(mMatLeft, leftKP);
			orbDetector.detect(mMatRight, rightKP);

			// Extract feature descriptors
			DescriptorExtractor extractor = DescriptorExtractor
					.create(DescriptorExtractor.BRIEF);
			Mat leftKPDesc = new Mat();
			Mat rightKPDesc = new Mat();
			extractor.compute(mMatLeft, leftKP, leftKPDesc);
			extractor.compute(mMatRight, rightKP, rightKPDesc);

			// Match features
			DescriptorMatcher matcher = DescriptorMatcher
					.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
			List<MatOfDMatch> featMatchesList = new ArrayList<MatOfDMatch>();
			List<MatOfDMatch> featMatchesListReverse = new ArrayList<MatOfDMatch>();
			featMatchesList.add(new MatOfDMatch());
			featMatchesList.add(new MatOfDMatch());
			featMatchesListReverse.add(new MatOfDMatch());
			featMatchesListReverse.add(new MatOfDMatch());
			MatOfDMatch featMatches = new MatOfDMatch();
			// matcher.match(leftKPDesc, rightKPDesc, featMatches);
			matcher.knnMatch(leftKPDesc, rightKPDesc, featMatchesList, 2);
			matcher.knnMatch(rightKPDesc, leftKPDesc, featMatchesListReverse, 2);

			// Save features to image

			Mat outKPImage1 = new Mat(mMatLeft.size(), CvType.CV_8UC4);
			Mat outKPImage2 = new Mat(mMatLeft.size(), CvType.CV_8UC4);
			Features2d.drawKeypoints(mMatLeft, leftKP, outKPImage1, new Scalar(
					0, 255, 0, 255), 0);
			// MichelangeloCamera.saveBitmap(
			// MichelangeloCamera.colorMatToBitmap(outKPImage), "leftkp");
			Features2d.drawKeypoints(mMatRight, rightKP, outKPImage2,
					new Scalar(255, 0, 0, 255), 0);
			// MichelangeloCamera.saveBitmap(
			// MichelangeloCamera.colorMatToBitmap(outKPImage), "rightkp");
			Mat combineFeatures = combineImages(outKPImage1, outKPImage2);
			MichelangeloCamera.saveBitmap(
					MichelangeloCamera.colorMatToBitmap(combineFeatures),
					"featuresCombined");

			// Calculation of max and min distances between keypoints
			float max_dist = 0;
			float min_dist = 100;
			DMatch[] matchesArray = featMatches.toArray();
			LinkedList<DMatch> goodMatchesList = new LinkedList<DMatch>();
			// for (int i = 0; i < leftKPDesc.rows(); i++) {
			// float dist = matchesArray[i].distance;
			// if (dist < min_dist)
			// min_dist = dist;
			// if (dist > max_dist)
			// max_dist = dist;
			// }
			// for (int i = 0; i < rightKPDesc.rows(); i++) {
			// if (matchesArray[i].distance < Math.max(.02, 5 * min_dist)) {
			// if (matchesArray[i].distance < (.6 * max_dist)) {

			// for (int i = 0; i < featMatchesList.size(); i++) {
			// DMatch[] matchesArray1 = featMatchesList.get(i).toArray();
			// if (matchesArray1[0].distance < (.8 *
			// matchesArray1[1].distance))
			// {
			// goodMatchesList.addLast(matchesArray1[0]);
			// }
			// }

			for (int i = 0; i < featMatchesList.size(); i++) {
				DMatch[] matchesArray1 = featMatchesList.get(i).toArray();
				if (matchesArray1[0].distance < (.65 * matchesArray1[1].distance)) {
					DMatch[] matchesArray2 = featMatchesListReverse.get(
							matchesArray1[0].trainIdx).toArray();
					if (matchesArray2[0].trainIdx == matchesArray1[0].queryIdx) {
						goodMatchesList.addLast(matchesArray1[0]);
					}
				}
			}

			// double tresholdDist = 0.25 * Math
			// .sqrt((double) (mMatLeft.size().height
			// * mMatLeft.size().height + mMatLeft.size().width
			// * mMatLeft.size().width));
			//
			// KeyPoint[] leftKPArray = leftKP.toArray();
			// KeyPoint[] rightKPArray = rightKP.toArray();
			// for (int i = 0; i < featMatchesList.size(); ++i) {
			// DMatch[] matchesArray1 = featMatchesList.get(i).toArray();
			// for (int j = 0; j < matchesArray1.length; j++) {
			// DMatch possibleMatch = matchesArray1[j];
			// Point from = leftKPArray[possibleMatch.queryIdx].pt;
			// Point to = rightKPArray[possibleMatch.trainIdx].pt;
			// // Point to = keypoints_2[matches[i][j].trainIdx].pt;
			//
			// // calculate local distance for each possible match
			// double dist = Math.sqrt((from.x - to.x) * (from.x - to.x)
			// + (from.y - to.y) * (from.y - to.y));
			//
			// // save as best match if local distance is in specified area
			// // and on same height
			// if (dist < tresholdDist && Math.abs(from.y - to.y) < 25) {
			// goodMatchesList.addLast(possibleMatch);
			// // j = matches[i].size();
			// break;
			// }
			// }
			// }

			MatOfDMatch goodMatches = new MatOfDMatch();
			goodMatches.fromList(goodMatchesList);

			// Get keypoints of good matches
			List<KeyPoint> kpListLeft = leftKP.toList();
			List<KeyPoint> kpListRight = rightKP.toList();
			List<KeyPoint> kpListGoodLeft = new ArrayList<KeyPoint>();
			List<KeyPoint> kpListGoodRight = new ArrayList<KeyPoint>();
			LinkedList<Point> goodKPLeft = new LinkedList<Point>();
			LinkedList<Point> goodKPRight = new LinkedList<Point>();
			for (int i = 0; i < goodMatchesList.size(); i++) {
				Point leftPoint = kpListLeft
						.get(goodMatchesList.get(i).queryIdx).pt;
				Point rightPoint = kpListRight
						.get(goodMatchesList.get(i).trainIdx).pt;
				if (Math.abs(leftPoint.y - rightPoint.y) < (mMatLeft.rows() / 8)) {
					goodKPLeft
							.addLast(kpListLeft.get(goodMatchesList.get(i).queryIdx).pt);
					kpListGoodLeft
							.add(kpListLeft.get(goodMatchesList.get(i).queryIdx));
					goodKPRight
							.addLast(kpListRight.get(goodMatchesList.get(i).trainIdx).pt);
					kpListGoodRight
							.add(kpListRight.get(goodMatchesList.get(i).trainIdx));
				}
			}
			
			/* Remove Outliers */
			
			LinkedList<Integer> firstOutlierIndices = removeOutliers(goodKPLeft, goodKPRight);
			
			Log.w(TAG, "FIRST OUTLIER REMOVAL");
			int indices_removed = 0;
			for(int index : firstOutlierIndices) {
				Log.w(TAG, "REMOVING INDEX " + index);
				int index_to_remove = index - indices_removed;
				goodKPLeft.remove(index_to_remove);
				goodKPRight.remove(index_to_remove);
				kpListGoodLeft.remove(index_to_remove);
				kpListGoodRight.remove(index_to_remove);
				indices_removed ++;
			}

			Log.w(TAG, "REMOVED ALL INDICES");
			
			LinkedList<Integer> outlierIndices = NormalFilter(goodKPLeft, goodKPRight);
			indices_removed = 0;
			for(int index : outlierIndices) {
				Log.w(TAG, "REMOVING INDEX " + index);
				int index_to_remove = index - indices_removed;
				goodKPLeft.remove(index_to_remove);
				goodKPRight.remove(index_to_remove);
				kpListGoodLeft.remove(index_to_remove);
				kpListGoodRight.remove(index_to_remove);
				indices_removed ++;
			}

			Log.w(TAG, "REMOVED ALL INDICES");
			
			MatOfPoint2f leftKPf = new MatOfPoint2f();
			MatOfPoint2f rightKPf = new MatOfPoint2f();
			leftKPf.fromList(goodKPLeft);
			rightKPf.fromList(goodKPRight);

			MatOfKeyPoint matGoodKPLeft = new MatOfKeyPoint();
			MatOfKeyPoint matGoodKPRight = new MatOfKeyPoint();
			matGoodKPLeft.fromList(kpListGoodLeft);
			matGoodKPRight.fromList(kpListGoodRight);
			Features2d.drawKeypoints(mMatLeft, matGoodKPLeft, outKPImage1,
					new Scalar(255, 255, 0, 255), 0);
			// MichelangeloCamera.saveBitmap(
			// MichelangeloCamera.colorMatToBitmap(outKPImage),
			// "leftmatches");
			Features2d.drawKeypoints(mMatRight, matGoodKPRight, outKPImage2,
					new Scalar(255, 0, 255, 255), 0);
			// MichelangeloCamera.saveBitmap(
			// MichelangeloCamera.colorMatToBitmap(outKPImage),
			// "rightmatches");
			combineFeatures = combineImages(outKPImage1, outKPImage2);
			MichelangeloCamera.saveBitmap(
					MichelangeloCamera.colorMatToBitmap(combineFeatures),
					"matchesCombined");

			// Undistort feature points
			MatOfPoint2f leftUKPf = new MatOfPoint2f();
			MatOfPoint2f rightUKPf = new MatOfPoint2f();
			Imgproc.undistortPoints(leftKPf, leftUKPf, mCameraMatrix,
					mDistCoeffs);
			Imgproc.undistortPoints(rightKPf, rightUKPf, mCameraMatrix,
					mDistCoeffs);

			// Find fundamental matrix
			Mat fundMat = Calib3d.findFundamentalMat(leftUKPf, rightUKPf,
					Calib3d.RANSAC, 3, 0.99);
			Mat fundMat2 = Calib3d.findFundamentalMat(leftKPf, rightKPf,
					Calib3d.RANSAC, 3, 0.99);
			
			//Mat midFund = transformMidpoint(pointMid, fundMat2);
			
			
			// Compute epilines
			Mat linesLeftU = new Mat();
			Mat linesRightU = new Mat();
			Mat linesLeft = new Mat();
			Mat linesRight = new Mat();
			Calib3d.computeCorrespondEpilines(leftUKPf, 1, fundMat, linesRightU);
			Calib3d.computeCorrespondEpilines(rightUKPf, 2, fundMat, linesLeftU);
			Calib3d.computeCorrespondEpilines(leftKPf, 1, fundMat2, linesRight);
			Calib3d.computeCorrespondEpilines(rightKPf, 2, fundMat2, linesLeft);

			// Draw epilines
			Mat epilineLeftU = drawEpilines(mMatLeft, linesLeftU, leftUKPf);
			Mat epilineRightU = drawEpilines(mMatRight, linesRightU, rightUKPf);
			Mat epilineLeft = drawEpilines(mMatLeft, linesLeft, leftKPf);
			Mat epilineRight = drawEpilines(mMatRight, linesRight, rightKPf);

			Mat combineEpiline = combineImages(epilineLeft, epilineRight);
			MichelangeloCamera.saveBitmap(
					MichelangeloCamera.colorMatToBitmap(combineEpiline),
					"epilineCombine");

			// Rectify
			Mat rectHomog1 = new Mat();
			Mat rectHomog2 = new Mat();
			Mat H1 = new Mat();
			Mat H2 = new Mat();
			Calib3d.stereoRectifyUncalibrated(leftKPf, rightKPf, fundMat2,
					mMatLeft.size(), rectHomog1, rectHomog2, 5);
			Calib3d.stereoRectifyUncalibrated(leftKPf, rightKPf, fundMat2,
					mMatLeft.size(), H1, H2, 5);
			// Calib3d.stereoRectifyUncalibrated(leftUKPf, rightUKPf, fundMat,
			// mMatLeft.size(), rectHomog1, rectHomog2, 5);

			rectHomog1.convertTo(rectHomog1, CvType.CV_32FC1);
			rectHomog2.convertTo(rectHomog2, CvType.CV_32FC1);
			Mat rectMat1 = mCameraMatrix.inv().mul(rectHomog1)
					.mul(mCameraMatrix);
			Mat rectMat2 = mCameraMatrix.inv().mul(rectHomog2)
					.mul(mCameraMatrix);

			Mat newCameraMatrix = new Mat();
			newCameraMatrix = Calib3d.getOptimalNewCameraMatrix(mCameraMatrix,
					mDistCoeffs, mMatLeft.size(), 1);

			// Left remap and rectify
			Mat mapMat1 = new Mat();
			Mat mapMat2 = new Mat();
			// Mat rgbaOrigImage = new Mat(mMatLeft.size(), CvType.CV_8UC4);
			Imgproc.initUndistortRectifyMap(mCameraMatrix, mDistCoeffs,
					rectMat1, mCameraMatrix, mMatLeft.size(), CvType.CV_16SC2,
					mapMat1, mapMat2);
			Mat rectifiedLeftImage = new Mat(mapMat1.size(), CvType.CV_8UC1);
			Mat rectifiedEpilineLeftImage = new Mat(mapMat1.size(),
					CvType.CV_8UC1);
			Mat rectifiedShearLeftImage = new Mat(mapMat1.size(),
					CvType.CV_8UC1);
			Mat rectifiedShearEpilineLeftImage = new Mat(mapMat1.size(),
					CvType.CV_8UC1);
			// Imgproc.cvtColor(mMatLeft, rgbaOrigImage,
			// Imgproc.COLOR_GRAY2RGBA);
			Imgproc.remap(mMatLeft, rectifiedLeftImage, mapMat1, mapMat2,
					Imgproc.INTER_LINEAR);

			// Right remap and rectify
			Mat mapMat3 = new Mat();
			Mat mapMat4 = new Mat();
			Imgproc.initUndistortRectifyMap(mCameraMatrix, mDistCoeffs,
					rectMat2, mCameraMatrix, mMatLeft.size(), CvType.CV_16SC2,
					mapMat3, mapMat4);
			Mat rectifiedRightImage = new Mat(mapMat3.size(), CvType.CV_8UC1);
			Mat rectifiedEpilineRightImage = new Mat(mapMat3.size(),
					CvType.CV_8UC1);
			Mat rectifiedShearRightImage = new Mat(mapMat3.size(),
					CvType.CV_8UC1);
			Mat rectifiedShearEpilineRightImage = new Mat(mapMat3.size(),
					CvType.CV_8UC1);
			Imgproc.remap(mMatRight, rectifiedRightImage, mapMat3, mapMat4,
					Imgproc.INTER_LINEAR);

			// MichelangeloCamera.saveBitmap(
			// MichelangeloCamera.grayMatToBitmap(rectifiedLeftImage),
			// "rectifyleft");
			// MichelangeloCamera.saveBitmap(
			// MichelangeloCamera.colorMatToBitmap(rectifiedLeftImage),
			// "rectifyleft");
			// MichelangeloCamera.saveBitmap(
			// MichelangeloCamera.grayMatToBitmap(rectifiedRightImage),
			// "rectifyright");

			Mat combine = combineImages(mMatLeft, mMatRight);

			for (int i = 0; i < goodKPLeft.size(); i++) {
				Scalar color = new Scalar(randInt(0, 255), randInt(0, 255),
						randInt(0, 255), 255);

				int x0 = (int) goodKPLeft.get(i).x;
				int y0 = (int) goodKPLeft.get(i).y;
				int x1 = (int) goodKPRight.get(i).x + mMatLeft.cols();
				int y1 = (int) goodKPRight.get(i).y;

				Core.line(combine, new Point(x0, y0), new Point(x1, y1), color,
						1);
				Core.circle(combine, new Point(x0, y0), 2, color, -1);
				Core.circle(combine, new Point(x1, y1), 2, color, -1);
			}
			MichelangeloCamera.saveBitmap(
					MichelangeloCamera.grayMatToBitmap(combine),
					"featuresCombined");

			Mat combine2 = combineImages(rectifiedLeftImage,
					rectifiedRightImage);
			MichelangeloCamera.saveBitmap(
					MichelangeloCamera.grayMatToBitmap(combine2),
					"rectCombined");


			Mat colorRect = new Mat(mapMat1.size(), colorMat.type());
			
			Imgproc.warpPerspective(colorMat, colorRect, H1,
					colorMat.size());
			
			Mat midFund = transformMidpoint(pointMid, H1);
			
			Imgproc.warpPerspective(mMatLeft, rectifiedLeftImage, H1,
					mMatLeft.size());
			Imgproc.warpPerspective(mMatRight, rectifiedRightImage, H2,
					mMatRight.size());
			

			Server.sendColor(colorRect);
			
			//Calculate Warped Midpoint
			/*Mat pT = Imgproc.getPerspectiveTransform(mMatLeft, rectifiedLeftImage);
			Mat mid = new Mat(3,1,5,new Scalar(midPoint));
			Core.multiply(pT, mid, mid);*/
			


			
			
			Imgproc.warpPerspective(epilineLeft, rectifiedEpilineLeftImage, H1,
					mMatLeft.size());
			Imgproc.warpPerspective(epilineRight, rectifiedEpilineRightImage,
					H2, mMatRight.size());

			
			combine2 = combineImages(rectifiedEpilineLeftImage,
					rectifiedEpilineRightImage);
			MichelangeloCamera.saveBitmap(
					MichelangeloCamera.grayMatToBitmap(combine2),
					"rectWarpCombined");

			double h1Array[] = new double[9];
			H1.get(0, 0, h1Array);
			Mat H1p = new Mat(3, 3, H1.type());
			double h1pArray[] = { 1, 0, 0, 0, 1, 0, h1Array[6], h1Array[7], 1 };
			H1p.put(0, 0, h1pArray);
			Mat H1r = new Mat(3, 3, H1.type());
			double h1rArray[] = { h1Array[4] - (h1Array[5] * h1Array[7]),
					(h1Array[5] * h1Array[6]) - h1Array[3], 0,
					h1Array[3] - (h1Array[5] * h1Array[6]),
					h1Array[4] - (h1Array[5] * h1Array[7]), h1Array[5], 0, 0, 1 };
			H1r.put(0, 0, h1rArray);
			Mat pointA1 = new Mat(3, 1, H1.type());
			Mat pointB1 = new Mat(3, 1, H1.type());
			Mat pointC1 = new Mat(3, 1, H1.type());
			Mat pointD1 = new Mat(3, 1, H1.type());
			double pointA1Array[] = { (mMatLeft.cols() - 1) / 2, 0, 1 };
			double pointB1Array[] = { mMatLeft.cols() - 1,
					(mMatLeft.rows() - 1) / 2, 1 };
			double pointC1Array[] = { (mMatLeft.cols() - 1) / 2,
					mMatLeft.rows() - 1, 1 };
			double pointD1Array[] = { 0, (mMatLeft.rows() - 1) / 2, 1 };
			pointA1.put(0, 0, pointA1Array);
			pointB1.put(0, 0, pointB1Array);
			pointC1.put(0, 0, pointC1Array);
			pointD1.put(0, 0, pointD1Array);
			Mat pointA1prime = new Mat();
			Mat pointB1prime = new Mat();
			Mat pointC1prime = new Mat();
			Mat pointD1prime = new Mat();
			Mat H1rp = new Mat(3, 3, H1.type());
			Core.gemm(H1r, H1p, 1, new Mat(), 0, H1rp);
			Core.gemm(H1rp, pointA1, 1, new Mat(), 0, pointA1prime);
			Core.gemm(H1rp, pointB1, 1, new Mat(), 0, pointB1prime);
			Core.gemm(H1rp, pointC1, 1, new Mat(), 0, pointC1prime);
			Core.gemm(H1rp, pointD1, 1, new Mat(), 0, pointD1prime);
			double pointA1primeArray[] = new double[3];
			double pointB1primeArray[] = new double[3];
			double pointC1primeArray[] = new double[3];
			double pointD1primeArray[] = new double[3];
			pointA1prime.get(0, 0, pointA1primeArray);
			pointB1prime.get(0, 0, pointB1primeArray);
			pointC1prime.get(0, 0, pointC1primeArray);
			pointD1prime.get(0, 0, pointD1primeArray);
			for (int i = 0; i < 3; i++) {
				pointA1primeArray[i] /= pointA1primeArray[2];
				pointB1primeArray[i] /= pointB1primeArray[2];
				pointC1primeArray[i] /= pointC1primeArray[2];
				pointD1primeArray[i] /= pointD1primeArray[2];
			}
			Mat pointX1 = new Mat(3, 1, H1.type());
			Mat pointY1 = new Mat(3, 1, H1.type());
			pointA1prime.put(0, 0, pointA1primeArray);
			pointB1prime.put(0, 0, pointB1primeArray);
			pointC1prime.put(0, 0, pointC1primeArray);
			pointD1prime.put(0, 0, pointD1primeArray);
			Core.subtract(pointB1prime, pointD1prime, pointX1);
			Core.subtract(pointC1prime, pointA1prime, pointY1);
			double pointX1Array[] = new double[3];
			double pointY1Array[] = new double[3];
			pointX1.get(0, 0, pointX1Array);
			pointY1.get(0, 0, pointY1Array);
			double k1 = ((mMatLeft.rows() * mMatLeft.rows() * pointX1Array[1] * pointX1Array[1]) + (mMatLeft
					.cols() * mMatLeft.cols() * pointY1Array[1] * pointY1Array[1]))
					/ ((mMatLeft.cols() * mMatLeft.rows()) * ((pointX1Array[1] * pointY1Array[0]) - (pointX1Array[0] * pointY1Array[1])));
			double k2 = ((mMatLeft.rows() * mMatLeft.rows() * pointX1Array[0] * pointX1Array[1]) + (mMatLeft
					.cols() * mMatLeft.cols() * pointY1Array[0] * pointY1Array[1]))
					/ ((mMatLeft.cols() * mMatLeft.rows()) * ((pointX1Array[0] * pointY1Array[1]) - (pointX1Array[1] * pointY1Array[0])));
			if (k1 < 0) {
				k1 *= -1;
				k2 *= -1;
			}
			Mat H1s = new Mat(3, 3, H1.type());
			double h1sArray[] = { k1, k2, 0, 0, 1, 0, 0, 0, 1 };
			H1s.put(0, 0, h1sArray);

			double h2Array[] = new double[9];
			H2.get(0, 0, h2Array);
			Mat H2p = new Mat(3, 3, H2.type());
			double h2pArray[] = { 1, 0, 0, 0, 1, 0, h2Array[6], h2Array[7], 1 };
			H2p.put(0, 0, h2pArray);
			Mat H2r = new Mat(3, 3, H2.type());
			double h2rArray[] = { h2Array[4] - (h2Array[5] * h2Array[7]),
					(h2Array[5] * h2Array[6]) - h2Array[3], 0,
					h2Array[3] - (h2Array[5] * h2Array[6]),
					h2Array[4] - (h2Array[5] * h2Array[7]), h2Array[5], 0, 0, 1 };
			H2r.put(0, 0, h2rArray);
			Mat pointA2 = new Mat(3, 1, H2.type());
			Mat pointB2 = new Mat(3, 1, H2.type());
			Mat pointC2 = new Mat(3, 1, H2.type());
			Mat pointD2 = new Mat(3, 1, H2.type());
			double pointA2Array[] = { (mMatLeft.cols() - 1) / 2, 0, 1 };
			double pointB2Array[] = { mMatLeft.cols() - 1,
					(mMatLeft.rows() - 1) / 2, 1 };
			double pointC2Array[] = { (mMatLeft.cols() - 1) / 2,
					mMatLeft.rows() - 1, 1 };
			double pointD2Array[] = { 0, (mMatLeft.rows() - 1) / 2, 1 };
			pointA2.put(0, 0, pointA2Array);
			pointB2.put(0, 0, pointB2Array);
			pointC2.put(0, 0, pointC2Array);
			pointD2.put(0, 0, pointD2Array);
			Mat pointA2prime = new Mat();
			Mat pointB2prime = new Mat();
			Mat pointC2prime = new Mat();
			Mat pointD2prime = new Mat();
			Mat H2rp = new Mat(3, 3, H2.type());
			Core.gemm(H2r, H2p, 1, new Mat(), 0, H2rp);
			Core.gemm(H2rp, pointA2, 1, new Mat(), 0, pointA2prime);
			Core.gemm(H2rp, pointB2, 1, new Mat(), 0, pointB2prime);
			Core.gemm(H2rp, pointC2, 1, new Mat(), 0, pointC2prime);
			Core.gemm(H2rp, pointD2, 1, new Mat(), 0, pointD2prime);
			double pointA2primeArray[] = new double[3];
			double pointB2primeArray[] = new double[3];
			double pointC2primeArray[] = new double[3];
			double pointD2primeArray[] = new double[3];
			pointA2prime.get(0, 0, pointA2primeArray);
			pointB2prime.get(0, 0, pointB2primeArray);
			pointC2prime.get(0, 0, pointC2primeArray);
			pointD2prime.get(0, 0, pointD2primeArray);
			for (int i = 0; i < 3; i++) {
				pointA2primeArray[i] /= pointA2primeArray[2];
				pointB2primeArray[i] /= pointB2primeArray[2];
				pointC2primeArray[i] /= pointC2primeArray[2];
				pointD2primeArray[i] /= pointD2primeArray[2];
			}
			Mat pointX2 = new Mat(3, 1, H2.type());
			Mat pointY2 = new Mat(3, 1, H2.type());
			pointA2prime.put(0, 0, pointA2primeArray);
			pointB2prime.put(0, 0, pointB2primeArray);
			pointC2prime.put(0, 0, pointC2primeArray);
			pointD2prime.put(0, 0, pointD2primeArray);
			Core.subtract(pointB2prime, pointD2prime, pointX2);
			Core.subtract(pointC2prime, pointA2prime, pointY2);
			double pointX2Array[] = new double[3];
			double pointY2Array[] = new double[3];
			pointX2.get(0, 0, pointX2Array);
			pointY2.get(0, 0, pointY2Array);
			double k3 = ((mMatLeft.rows() * mMatLeft.rows() * pointX2Array[1] * pointX2Array[1]) + (mMatLeft
					.cols() * mMatLeft.cols() * pointY2Array[1] * pointY2Array[1]))
					/ ((mMatLeft.cols() * mMatLeft.rows()) * ((pointX2Array[1] * pointY2Array[0]) - (pointX2Array[0] * pointY2Array[1])));
			double k4 = ((mMatLeft.rows() * mMatLeft.rows() * pointX2Array[0] * pointX2Array[1]) + (mMatLeft
					.cols() * mMatLeft.cols() * pointY2Array[0] * pointY2Array[1]))
					/ ((mMatLeft.cols() * mMatLeft.rows()) * ((pointX2Array[0] * pointY2Array[1]) - (pointX2Array[1] * pointY2Array[0])));
			if (k3 < 0) {
				k3 *= -1;
				k4 *= -1;
			}
			Mat H2s = new Mat(3, 3, H2.type());
			double h2sArray[] = { k3, k4, 0, 0, 1, 0, 0, 0, 1 };
			H2s.put(0, 0, h2sArray);


			//Mat colorRectShear = new Mat(mapMat1.size(), colorRect.type());

			/*Imgproc.warpPerspective(colorRect,
					colorRectShear, H1s, colorRect.size());*/
			
			
			Imgproc.warpPerspective(rectifiedLeftImage,
					rectifiedShearLeftImage, H1s, mMatLeft.size());
			Imgproc.warpPerspective(rectifiedRightImage,
					rectifiedShearRightImage, H2s, mMatRight.size());

			Imgproc.warpPerspective(rectifiedEpilineLeftImage,
					rectifiedShearEpilineLeftImage, H1s, mMatLeft.size());
			Imgproc.warpPerspective(rectifiedEpilineRightImage,
					rectifiedShearEpilineRightImage, H2s, mMatRight.size());

			combine2 = combineImages(rectifiedShearEpilineLeftImage,
					rectifiedEpilineRightImage);
			MichelangeloCamera.saveBitmap(
					MichelangeloCamera.grayMatToBitmap(combine2),
					"rectWarpShearCombined");
			

			
			
			
			// Calculate disparities of original
			// StereoBM blockMatcher = new StereoBM(StereoBM.BASIC_PRESET, 96,
			// 13);
			// StereoSGBM sgBlockMatcher = new StereoSGBM(0, 96, 3, 128, 256, 20,
			// 		16, 1, 100, 20, true);
			StereoSGBM sgBlockMatcher = new StereoSGBM(0, 96, 11, 968, 3872, -1,
					20, 5, 100, 20, true);
			Mat disparityBM = new Mat(mMatLeft.rows(), mMatLeft.cols(),
					CvType.CV_32F);
			Mat disparityBMFinal = new Mat(mMatLeft.rows(), mMatLeft.cols(),
					CvType.CV_8U);
			Mat disparityBMFinalRect = new Mat(mMatLeft.rows(),
					mMatLeft.cols(), CvType.CV_8U);
			Mat disparityBMFinalRectShear = new Mat(mMatLeft.rows(),
					mMatLeft.cols(), CvType.CV_8U);
			sgBlockMatcher.compute(mMatLeft, mMatRight, disparityBM);
			MinMaxLocResult minMax = Core.minMaxLoc(disparityBM);
			double minVal = minMax.minVal;
			double maxVal = minMax.maxVal;
			// disparityBM.convertTo(disparityBMFinal, disparityBMFinal.type(),
			// 255.0/(maxVal - minVal), -minVal * 255.0/(maxVal - minVal));
			disparityBM.convertTo(disparityBMFinal, disparityBMFinal.type(),
					255.0 / (96 * 16.));
			
			//Server.sendGray(disparityBMFinal);
			result = MichelangeloCamera.grayMatToBitmap(disparityBMFinal);
			Log.w(TAG, "Disparity map computed (Block Match).");
			// result = getBitmapFromResult();
			// MichelangeloCamera.saveBitmap(result, "disporiginal");

			// Calculate disparities of rectified
			sgBlockMatcher.compute(rectifiedLeftImage, rectifiedRightImage,
					disparityBM);
			minMax = Core.minMaxLoc(disparityBM);
			minVal = minMax.minVal;
			maxVal = minMax.maxVal;									
			
			// disparityBM.convertTo(disparityBMFinal, disparityBMFinal.type(),
			// 255.0/(maxVal - minVal), -minVal * 255.0/(maxVal - minVal));
			disparityBM.convertTo(disparityBMFinalRect,
					disparityBMFinalRect.type(), 255.0 / (96 * 16.));

			Server.sendGray(disparityBMFinalRect);
			result = MichelangeloCamera.grayMatToBitmap(disparityBMFinalRect);
			Log.w(TAG, "Rectified disparity map computed (Block Match).");
			// result = getBitmapFromResult();
			// MichelangeloCamera.saveBitmap(result, "disprectify");

			// Calculate disparities of rectified and sheared
			sgBlockMatcher.compute(rectifiedShearLeftImage,
					rectifiedShearRightImage, disparityBM);
			minMax = Core.minMaxLoc(disparityBM);
			minVal = minMax.minVal;
			maxVal = minMax.maxVal;
			// disparityBM.convertTo(disparityBMFinal, disparityBMFinal.type(),
			// 255.0/(maxVal - minVal), -minVal * 255.0/(maxVal - minVal));
			disparityBM.convertTo(disparityBMFinalRectShear,
					disparityBMFinalRectShear.type(), 255.0 / (96 * 16.));
			
			//Server.send(disparityBMFinalRect);
			result = MichelangeloCamera
					.grayMatToBitmap(disparityBMFinalRectShear);
			Log.w(TAG,
					"Rectified sheared disparity map computed (Block Match).");
			// result = getBitmapFromResult();
			// MichelangeloCamera.saveBitmap(result, "disprectify");

			Mat combineDispRect = combineImages(disparityBMFinal,
					disparityBMFinalRect);
			Mat combineDispRectShear = combineImages(combineDispRect,
					disparityBMFinalRectShear);
			MichelangeloCamera.saveBitmap(
					MichelangeloCamera.grayMatToBitmap(disparityBMFinalRectShear),
					"dispShear");
			MichelangeloCamera.saveBitmap(
					MichelangeloCamera.grayMatToBitmap(combineDispRectShear),
					"dispCombined");

			// Mat pointCloudMat = new Mat ( disparityBMFinalRectShear.size(),
			// CvType.CV_32FC3);
			// Calib3d.reprojectImageTo3D(disparityBMFinalRectShear,
			// pointCloudMat, mQMatrix, true);
		}
		
	
		
		
		return result;
		
	}
	
	
	
	/*
	 * Returns Array of LinkedList of KeyPoints on matrix. idx 0 of return matrix is
	 * left KPs, idx 1 of return matrix is right KPs
	 * 
	 * The return contains indices of the outliers that need to be removed
	 *  
	 * ########################################################
	 *  
	 *       ASSUMES THAT leftKps.size() == rightKps.size()
	 * 
	 * ########################################################
	 *  
	 */
	public LinkedList<Integer> NormalFilter(LinkedList<Point> leftKps, LinkedList<Point> rightKps) {
		
		float slmean = 0;
		float slstddev = 0;
		
		float dstmean = 0;
		float dststddev = 0;
		
		LinkedList<Integer> result = new LinkedList<Integer>();
		
		LinkedList<Float> slopes = new LinkedList<Float>();
		LinkedList<Float> dists = new LinkedList<Float>();
		Iterator<Point> iLeft = leftKps.iterator();
		Iterator<Point> iRight = rightKps.iterator();
		while(iLeft.hasNext()) {
			Point left = iLeft.next();
			Point right = iRight.next();
			float dy = (float) (right.y - left.y);
			float dx = (float) ((right.x + mMatLeft.cols()) - left.x);
			float slope = dy / dx;
			float dist  = dx * dx + dy * dy;
			slmean += slope;
			slopes.add(slope); // slopes will be in the same order as leftKps and rightKps
			dstmean += dist;
			dists.add(dist);
		}
		
		slmean /= leftKps.size(); // get the mean
		dstmean /= leftKps.size();
		
		/* Now calculate the stdDev */
		
		Iterator<Float> iSlopes = slopes.iterator();
		Iterator<Float> iDists = dists.iterator();
		
		while(iSlopes.hasNext()) {
			float val = (iSlopes.next() - slmean);
			slstddev += (val * val);
			float dstval = iDists.next() - dstmean;
			dststddev += (dstval * dstval);
		}
		
		slstddev = (float) Math.sqrt(slstddev /leftKps.size());		
		dststddev = (float) Math.sqrt(dststddev / leftKps.size());
		
		/* Now add the outliers to the result list if they exceed stddev */

		iSlopes = slopes.iterator(); // reset the iterator
		iDists = dists.iterator();
		
		int i = 0;
		int slope_outliers = 0;
		int dist_outliers = 0;
		
		while(iSlopes.hasNext()) {
			i++;
			float slope = iSlopes.next();
			float dist = iDists.next();
			if(Math.abs(slope - slmean) > (2.0 * slstddev)) {
				Log.w(TAG, "slope: " + slope + " mean: " + slmean + " stddev: " + slstddev);
				result.add(i-1);
				slope_outliers ++;
				continue;
			}
			if(Math.abs(dist - dstmean) > (2.0 * dststddev)) {
				Log.w(TAG, "dist: " + dist + " mean: " + dstmean + " stddev: " + dststddev);
				result.add(i-1);
				dist_outliers ++;
			}
		}
		Log.w(TAG, "RESULT HAS " + result.size() + " elements, original had " + leftKps.size());
		Log.w(TAG, "removed " + slope_outliers + " slope outliers and " + dist_outliers + " dist outliers");
		return result;
	}

	public LinkedList<Integer> removeOutliers(LinkedList<Point> leftKps, LinkedList<Point> rightKps) {
		
		LinkedList<Integer> result = new LinkedList<Integer>();
		
		LinkedList<Float> slopes = new LinkedList<Float>();
		LinkedList<Float> dists = new LinkedList<Float>();
		Iterator<Point> iLeft = leftKps.iterator();
		Iterator<Point> iRight = rightKps.iterator();
		while(iLeft.hasNext()) {
			Point left = iLeft.next();
			Point right = iRight.next();
			float dy = (float) (right.y - left.y);
			float dx = (float) ((right.x + mMatLeft.cols()) - left.x);
			float slope = dy / dx;
			float dist  = dx * dx + dy * dy;
			//slmean += slope;
			slopes.add(slope); // slopes will be in the same order as leftKps and rightKps
			//dstmean += dist;
			dists.add(dist);
		}
		
		/* Sort so our calculations are easier */
		
		Collections.sort(slopes);
		Collections.sort(dists);
		
		/* Get the median and quartiles */
		
		//float slmedian = (slopes.get(slopes.size()/2) + slopes.get((slopes.size() + 1) / 2)) / 2;
		//float dstmedian = (dists.get(dists.size()/2) + dists.get((dists.size() + 1) / 2)) / 2;
		
		int upperQuartRoundUp = ((slopes.size() * 3) + 1) / 4;
		int upperQuartRoundDown = (slopes.size() * 3) / 4;
		int lowerQuartRoundUp = (slopes.size() + 1) / 4;
		int lowerQuartRoundDown = slopes.size() / 4;
		
		float slUpperQuartile = (slopes.get(upperQuartRoundUp) + slopes.get(upperQuartRoundDown)) / 2;
		float dstUpperQuartile = (dists.get(upperQuartRoundUp) + dists.get(upperQuartRoundDown)) / 2;
		float slLowerQuartile = (slopes.get(lowerQuartRoundUp) + slopes.get(lowerQuartRoundDown)) / 2;
		float dstLowerQuartile = (dists.get(lowerQuartRoundUp) + dists.get(lowerQuartRoundDown)) / 2;
				
		/* Calculate the valid ranges */
		
		float slMajorQuartileRange = (slUpperQuartile - slLowerQuartile) * 3;
		float dstMajorQuartileRange = (dstUpperQuartile - dstLowerQuartile) * 3; 
		float slUpperMajorFence = slUpperQuartile + slMajorQuartileRange; 
		float dstUpperMajorFence = dstUpperQuartile + dstMajorQuartileRange; 
		float slLowerMajorFence = slLowerQuartile - slMajorQuartileRange; 
		float dstLowerMajorFence = dstLowerQuartile - dstMajorQuartileRange;

		Iterator<Float> iSlopes = slopes.iterator(); // reset the iterator
		Iterator<Float> iDists = dists.iterator();
		
		/* Remove Outliers if they land outside the fences */
		
		int i = 0;
		int slope_outliers = 0;
		int dist_outliers = 0;
		
		while(iSlopes.hasNext()) {
			i++;
			float slope = iSlopes.next();
			float dist = iDists.next();
			if(slope < slLowerMajorFence || slope > slUpperMajorFence) {
				Log.w(TAG, "slope: " + slope + " UppperFence: " + slUpperMajorFence + " LowerFence: " + slLowerMajorFence);
				result.add(i-1);
				slope_outliers ++;
				continue;
			}
			if(dist < dstLowerMajorFence || dist > dstUpperMajorFence) {
				Log.w(TAG, "dist: " + dist + " UppperFence: " + dstUpperMajorFence + " LowerFence: " + dstLowerMajorFence);
				result.add(i-1);
				dist_outliers ++;
			}
		}
		Log.w(TAG, "RESULT HAS " + result.size() + " elements, original had " + leftKps.size());
		Log.w(TAG, "removed " + slope_outliers + " slope outliers and " + dist_outliers + " dist outliers");
		return result;
	}
	
	public Mat combineImages(Mat leftMat, Mat rightMat) {
		Mat combine = new Mat(leftMat.rows(), leftMat.cols() + rightMat.cols(),
				leftMat.type());
		for (int i = 0; i < combine.cols(); i++) {
			if (i < leftMat.cols()) {
				leftMat.col(i).copyTo(combine.col(i));
			} else {
				rightMat.col(i - leftMat.cols()).copyTo(combine.col(i));
			}
		}
		return combine;
	}

	private Mat drawEpilines(Mat img, Mat epilines, MatOfPoint2f points) {
		Mat epilineImage = new Mat(mMatLeft.size(), CvType.CV_8UC1);
		img.copyTo(epilineImage);
		List<Point> pointList = points.toList();
		Scalar color = new Scalar(0, 0, 0, 255);
		for (int i = 0; i < epilines.rows(); i++) {
			double[] lineData = epilines.get(i, 0);
			int x0 = 0;
			int y0 = (int) (-1 * lineData[2] / lineData[1]);
			int x1 = img.cols();
			int y1 = (int) (-1 * (lineData[2] + (lineData[0] * img.cols())) / lineData[1]);
			Core.line(epilineImage, new Point(x0, y0), new Point(x1, y1),
					color, 1);
		}
		return epilineImage;
	}

	public static int randInt(int min, int max) {
		// Usually this can be a field rather than a method variable
		Random rand = new Random();
		// nextInt is normally exclusive of the top value,
		// so add 1 to make it inclusive
		int randomNum = rand.nextInt((max - min) + 1) + min;
		return randomNum;
	}

	private void writeToFile(File file, String data) {
		FileOutputStream stream = null;
		try {
			stream = new FileOutputStream(file);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		OutputStreamWriter outputStreamWriter = new OutputStreamWriter(stream);
		try {
			outputStreamWriter.write(data);
			outputStreamWriter.close();
			stream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private boolean generateDepthMap() {
		if (readyToProcess()) {
			// filter(IMAGE_POSITION.LEFT, mFilterMode);
			// filter(IMAGE_POSITION.RIGHT, mFilterMode);
			// getDepth(IMAGE_POSITION.RIGHT, IMAGE_POSITION.LEFT);
			return true;
		}
		return false;
	}

	public boolean setRightData(int width, int height, Mat matRight) {
		mMatRight = matRight;
		return true;
	}

	public void setWindowSize(WINDOW_SIZE windowSize) {
		switch (windowSize) {
		case SMALL:
			mWindowWidth = 3;
			mWindowHeight = 3;
			break;
		case MEDIUM:
			mWindowWidth = 5;
			mWindowHeight = 5;
			break;
		case LARGE:
			mWindowWidth = 7;
			mWindowHeight = 7;
			break;
		}
	}

	public void setFilterMode(FILTER_MODE filterMode) {
		mFilterMode = filterMode;
	}

	public int getWindowWidth() {
		return mWindowWidth;
	}

	public int getWindowHeight() {
		return mWindowHeight;
	}

	private void filter(IMAGE_POSITION imgPos, FILTER_MODE filterMode) {
		// Debug.startMethodTracing("filter" + filterMode);
		if (mResult == null)
			mResult = new int[mImgHeight][mImgWidth];
		Window window = getWindow(imgPos, 0, 0);
		window.setFilterMode(filterMode);

		int i = 0;
		int j = 0;
		do {
			while (window.canShiftRight()) {
				mResult[i][j++] = filters.get(filterMode).filter(window);
				window.shiftRight();
			}
			mResult[i][j] = filters.get(filterMode).filter(window);
			Log.w(TAG, "Filter Row " + i + " complete");
			if (window.canShiftDown()) {
				window.shiftDown();
				i++;
				while (window.canShiftLeft()) {
					mResult[i][j--] = filters.get(filterMode).filter(window);
					window.shiftLeft();
				}
				mResult[i][j] = filters.get(filterMode).filter(window);
				Log.w(TAG, "Filter Row " + i + " complete");
				if (window.canShiftDown()) {
					window.shiftDown();
					i++;
				} else {
					break;
				}
				// Debug.stopMethodTracing();
			} else {
				break;
			}
		} while (true);
	}

	private void getDepth(IMAGE_POSITION img1Pos, IMAGE_POSITION img2Pos) {
		// Debug.startMethodTracing("getDepth");
		if (mResult == null)
			mResult = new int[mImgHeight][mImgWidth];
		Window window = getWindow(img1Pos, 0, 0);
		Window window2 = getWindow(img2Pos, 0, 0);

		int i = 0;
		int j = 0;
		do {
			while (window.canShiftRight()) {
				mResult[i][j] = window.getNCC(window2, j);
				window.shiftRight();
				j++;
				window2.reset(j, i);
			}
			mResult[i][j] = window.getNCC(window2, j);
			Log.w(TAG, "NCC Row " + i + " complete");
			if (window.canShiftDown()) {
				window.shiftDown();
				i++;
				window2.reset(j, i);
				window.resetNCCRow();
				while (window.canShiftLeft()) {
					mResult[i][j] = window.getNCC(window2, j);
					window.shiftLeft();
					j--;
					window2.reset(j, i);
				}
				mResult[i][j] = window.getNCC(window2, j);
				Log.w(TAG, "NCC Row " + i + " complete");
				if (window.canShiftDown()) {
					window.shiftDown();
					i++;
					window2.reset(0, i);
					window.resetNCCRow();
				} else {
					break;
				}
				// Debug.stopMethodTracing();
			} else {
				break;
			}
		} while (true);
	}

	private Window getWindow(IMAGE_POSITION imagePos, int posX, int posY) {
		Window window;

		if (posX >= mImgWidth || posX < 0 || posY >= mImgHeight || posY < 0) {
			return null;
		}

		switch (imagePos) {
		case LEFT:
			window = new Window(mYDataLeft, mImgWidth, mImgHeight, posX, posY,
					mWindowWidth, mWindowHeight);
			break;
		case RIGHT:
			window = new Window(mYDataRight, mImgWidth, mImgHeight, posX, posY,
					mWindowWidth, mWindowHeight);
			break;
		default:
			return null;
		}
		return window;
	}

	private boolean readyToProcess() {
		// if (mYDataLeft != null && mYDataRight != null && mWindowWidth != 0) {
		// return true;
		// }
		// return false;
		return true;
	}

	public Bitmap getBitmapFromResult() {
		Bitmap bitmap = null;
		if (mResult == null)
			return null;
		int[] pixelData = new int[mImgWidth * mImgHeight];

		Log.w(TAG, "Writing data to rgb buffer.");
		int bitmapIndex = 0;
		for (int i = 0; i < mImgHeight; i++) {
			for (int j = 0; j < mImgWidth; j++, bitmapIndex++) {
				int val = mResult[i][j] & 0xff;
				int argb = (0xff << 24) + (val << 16) + (val << 8) + val;
				pixelData[bitmapIndex] = argb;
			}
		}
		Log.w(TAG, "Setting bitmap pixels from int buffer.");
		bitmap = Bitmap.createBitmap(mImgWidth, mImgHeight,
				Bitmap.Config.ARGB_8888);
		bitmap.setPixels(pixelData, 0, mImgWidth, 0, 0, mImgWidth, mImgHeight);

		return bitmap;
	}

	public Bitmap getBitmapFromGrayScale1D(byte[] grayscaleData, int width,
			int height) {
		Bitmap bitmap = null;
		int len = width * height;
		int[] pixelData = new int[len];

		Log.w(TAG, "Writing byte data to int buffer.");
		for (int i = 0; i < len; i++) {
			int val = grayscaleData[i] & 0xff;
			int argb = (0xff << 24) + (val << 16) + (val << 8) + val;
			pixelData[i] = argb;
		}
		Log.w(TAG, "Setting bitmap pixels to int buffer.");
		bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		bitmap.setPixels(pixelData, 0, width, 0, 0, width, height);

		return bitmap;
	}

	private class Window {
		private ArrayList<TIntArrayList> mWindow = null;
		private TIntArrayList mSortedWindow = null;
		private int[][] mData = null;
		private PriorityQueue<Integer> maxHeap = null;
		private PriorityQueue<Integer> minHeap = null;
		private int mWindowWidth;
		private int mWindowHeight;
		private int mImgWidth;
		private int mImgHeight;
		private int mPosX;
		private int mPosY;
		private int mLeftOffset;
		private int mTopOffset;
		private int mNumPixels;
		private int mSum;
		private int mSumSq;
		private long[] mRowSum = null;
		private FILTER_MODE mFilterMode = FILTER_MODE.NONE;
		private boolean mSorted = false;

		// Filter Coefficients
		private final int mHCoeffs[] = { 204, 180, 124, 66, 28, 9, 2, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		private final int mG3Coeffs[] = { 159, 180, 159, 180, 204, 180, 159,
				180, 159 };
		private final int mG5Coeffs[] = { 75, 109, 124, 109, 75, 109, 159, 180,
				159, 109, 124, 180, 204, 180, 124, 109, 159, 180, 159, 109, 75,
				109, 124, 109, 75 };
		private final int mG7Coeffs[] = { 22, 40, 59, 66, 59, 40, 22, 40, 75,
				109, 124, 109, 75, 40, 59, 109, 159, 180, 159, 109, 59, 66,
				124, 180, 204, 180, 124, 66, 59, 109, 159, 180, 159, 109, 59,
				40, 75, 109, 124, 109, 75, 40, 22, 40, 59, 66, 59, 40, 22 };

		public Window(int[][] yData, int imgWidth, int imgHeight, int posX,
				int posY, int windowWidth, int windowHeight) {
			mWindow = new ArrayList<TIntArrayList>();
			mData = yData;
			mImgWidth = imgWidth;
			mImgHeight = imgHeight;
			mWindowWidth = windowWidth;
			mWindowHeight = windowHeight;
			mPosX = posX;
			mPosY = posY;
			mLeftOffset = windowWidth / 2;
			mTopOffset = windowHeight / 2;
			mNumPixels = windowHeight * windowWidth;
			mSortedWindow = new TIntArrayList(mNumPixels);

			maxHeap = new PriorityQueue<Integer>(mNumPixels / 2,
					new Comparator<Integer>() {
						public int compare(Integer left, Integer right) {
							return right.compareTo(left);
						}
					});
			minHeap = new PriorityQueue<Integer>(mNumPixels / 2,
					new Comparator<Integer>() {
						public int compare(Integer left, Integer right) {
							return left.compareTo(right);
						}
					});

			int mTopOffset = windowHeight / 2;
			int mLeftOffset = windowWidth / 2;
			for (int i = -mTopOffset; i <= mTopOffset; i++) {
				TIntArrayList row = new TIntArrayList(windowWidth);
				for (int j = -mLeftOffset; j <= mLeftOffset; j++) {
					int val;
					int yIndex = posY + i;
					int xIndex = posX + j;
					if (yIndex >= 0 && yIndex < mImgHeight && xIndex >= 0
							&& xIndex < mImgWidth) {
						val = mData[yIndex][xIndex];
					} else {
						if (yIndex < 0 && xIndex < 0) {
							val = mData[0][0];
						} else if (yIndex < 0 && xIndex >= mImgWidth) {
							val = mData[0][mImgWidth - 1];
						} else if (yIndex >= mImgHeight && xIndex < 0) {
							val = mData[mImgHeight - 1][0];
						} else if (yIndex >= mImgHeight && xIndex >= mImgWidth) {
							val = mData[mImgHeight - 1][mImgWidth - 1];
						} else if (yIndex < 0) {
							val = mData[0][xIndex];
						} else if (yIndex >= mImgHeight) {
							val = mData[mImgHeight - 1][xIndex];
						} else if (xIndex < 0) {
							val = mData[yIndex][0];
						} else if (xIndex >= mImgWidth) {
							val = mData[yIndex][mImgWidth - 1];
						} else {
							val = 0;
						}
					}
					row.add(val);
					mSum += val;
					mSumSq += PIXEL_PRODUCTS[val][val];
				}
				mWindow.add(row);
			}
			sortWindow();
			for (int i = 0; i < mSortedWindow.size(); i++) {
				int b = mSortedWindow.get(i);
				addToMedianHeap(b);
			}
			mSorted = true;
		}

		public void reset(int posX, int posY) {
			mPosX = posX;
			mPosY = posY;

			maxHeap.clear();
			minHeap.clear();

			mSum = 0;
			mSumSq = 0;

			for (int i = -mTopOffset; i <= mTopOffset; i++) {
				TIntArrayList row = mWindow.get(i + mTopOffset);
				for (int j = -mLeftOffset; j <= mLeftOffset; j++) {
					int val;
					int yIndex = posY + i;
					int xIndex = posX + j;
					if (yIndex >= 0 && yIndex < mImgHeight && xIndex >= 0
							&& xIndex < mImgWidth) {
						val = mData[yIndex][xIndex];
					} else {
						if (yIndex < 0 && xIndex < 0) {
							val = mData[0][0];
						} else if (yIndex < 0 && xIndex >= mImgWidth) {
							val = mData[0][mImgWidth - 1];
						} else if (yIndex >= mImgHeight && xIndex < 0) {
							val = mData[mImgHeight - 1][0];
						} else if (yIndex >= mImgHeight && xIndex >= mImgWidth) {
							val = mData[mImgHeight - 1][mImgWidth - 1];
						} else if (yIndex < 0) {
							val = mData[0][xIndex];
						} else if (yIndex >= mImgHeight) {
							val = mData[mImgHeight - 1][xIndex];
						} else if (xIndex < 0) {
							val = mData[yIndex][0];
						} else if (xIndex >= mImgWidth) {
							val = mData[yIndex][mImgWidth - 1];
						} else {
							val = 0;
						}
					}
					row.set(j + mLeftOffset, val);
					mSum += val;
					mSumSq += PIXEL_PRODUCTS[val][val];
				}
			}
			if (mFilterMode == FILTER_MODE.TRIMMED_MEAN
					|| mFilterMode == FILTER_MODE.MEDIAN) {
				sortWindow();
				for (int i = 0; i < mSortedWindow.size(); i++) {
					int b = mSortedWindow.get(i);
					addToMedianHeap(b);
				}
				mSorted = true;
			}
		}

		public void setFilterMode(FILTER_MODE filterMode) {
			mFilterMode = filterMode;
		}

		public boolean canShiftRight() {
			if (mPosX + 1 >= mImgWidth) {
				return false;
			}
			return true;
		}

		public boolean canShiftLeft() {
			if (mPosX - 1 < 0) {
				return false;
			}
			return true;
		}

		public boolean canShiftDown() {
			if (mPosY + 1 >= mImgHeight) {
				return false;
			}
			return true;
		}

		public boolean canShiftUp() {
			if (mPosY - 1 < 0) {
				return false;
			}
			return true;
		}

		public void shiftRight() {
			for (int i = 0; i < mWindowHeight; i++) {
				int val, xIndex, yIndex;
				TIntArrayList row = mWindow.get(i);
				yIndex = mPosY + i - mTopOffset;
				xIndex = mPosX + mWindowWidth - mLeftOffset;
				if (yIndex >= mImgHeight || yIndex < 0 || xIndex >= mImgWidth
						|| xIndex < 0) {
					val = row.get(mWindowWidth - 1);
				} else {
					val = mData[yIndex][xIndex];
				}
				int removed = row.removeAt(0);
				row.add(val);
				switch (mFilterMode) {
				case NONE:
					mSumSq -= PIXEL_PRODUCTS[removed][removed];
					mSumSq += PIXEL_PRODUCTS[val][val];
					break;
				case AVERAGE:
					mSum -= removed;
					mSum += val;
					break;
				case TRIMMED_MEAN:
					mSortedWindow.remove(removed);
					mSortedWindow.add(val);
					mSorted = false;
					break;
				case MEDIAN:
					removeFromMedianHeap(removed);
					addToMedianHeap(val);
					break;
				default:
					break;
				}
			}
			mPosX++;
		}

		public void shiftLeft() {
			for (int i = 0; i < mWindowHeight; i++) {
				int val, xIndex, yIndex;
				TIntArrayList row = mWindow.get(i);
				yIndex = mPosY + i - mTopOffset;
				xIndex = mPosX - 1 - mLeftOffset;
				if (yIndex >= mImgHeight || yIndex < 0 || xIndex >= mImgWidth
						|| xIndex < 0) {
					val = row.get(0);
				} else {
					val = mData[yIndex][xIndex];
				}
				int removed = row.removeAt(mWindowWidth - 1);
				row.insert(0, val);
				switch (mFilterMode) {
				case NONE:
					mSumSq -= PIXEL_PRODUCTS[removed][removed];
					mSumSq += PIXEL_PRODUCTS[val][val];
					break;
				case AVERAGE:
					mSum -= removed;
					mSum += val;
					break;
				case TRIMMED_MEAN:
					mSortedWindow.remove(removed);
					mSortedWindow.add(val);
					mSorted = false;
					break;
				case MEDIAN:
					removeFromMedianHeap(removed);
					addToMedianHeap(val);
					break;
				default:
					break;
				}
			}
			mPosX--;
		}

		public void shiftDown() {
			TIntArrayList row = new TIntArrayList(mWindowWidth);

			TIntArrayList removedRow = mWindow.remove(0);
			for (int i = 0; i < removedRow.size(); i++) {
				int removed = removedRow.get(i);
				switch (mFilterMode) {
				case NONE:
					mSumSq -= PIXEL_PRODUCTS[removed][removed];
					break;
				case AVERAGE:
					mSum -= removed;
					break;
				case TRIMMED_MEAN:
					mSortedWindow.remove(removed);
					break;
				case MEDIAN:
					removeFromMedianHeap(removed);
					break;
				default:
					break;
				}
			}
			for (int i = 0; i < mWindowWidth; i++) {
				int val, xIndex, yIndex;
				yIndex = mPosY + mWindowHeight - mTopOffset;
				xIndex = mPosX + i - mLeftOffset;
				if (yIndex >= mImgHeight || yIndex < 0 || xIndex >= mImgWidth
						|| xIndex < 0) {
					val = removedRow.get(i);
				} else {
					val = mData[yIndex][xIndex];
				}
				row.add(val);
				switch (mFilterMode) {
				case NONE:
					mSumSq += PIXEL_PRODUCTS[val][val];
					break;
				case AVERAGE:
					mSum += val;
					break;
				case TRIMMED_MEAN:
					mSortedWindow.add(val);
					mSorted = false;
					break;
				case MEDIAN:
					addToMedianHeap(val);
					break;
				default:
					break;
				}
			}
			mWindow.add(row);
			mPosY++;
		}

		public void shiftUp() {
			TIntArrayList row = new TIntArrayList(mWindowWidth);

			TIntArrayList removedRow = mWindow.remove(mWindowHeight - 1);
			for (int i = 0; i < removedRow.size(); i++) {
				int removed = removedRow.get(i);
				switch (mFilterMode) {
				case NONE:
					mSumSq -= PIXEL_PRODUCTS[removed][removed];
					break;
				case AVERAGE:
					mSum -= removed;
					break;
				case TRIMMED_MEAN:
					mSortedWindow.remove(removed);
					break;
				case MEDIAN:
					removeFromMedianHeap(removed);
					break;
				default:
					break;
				}
			}
			for (int i = 0; i < mWindowWidth; i++) {
				int val, xIndex, yIndex;
				yIndex = mPosY - 1 - mTopOffset;
				xIndex = mPosX + i - mLeftOffset;
				if (yIndex >= mImgHeight || yIndex < 0 || xIndex >= mImgWidth
						|| xIndex < 0) {
					val = removedRow.get(i);
				} else {
					val = mData[yIndex][xIndex];
				}
				row.add(val);
				switch (mFilterMode) {
				case NONE:
					mSumSq += PIXEL_PRODUCTS[val][val];
					break;
				case AVERAGE:
					mSum += val;
					break;
				case TRIMMED_MEAN:
					mSortedWindow.add(val);
					mSorted = false;
					break;
				case MEDIAN:
					addToMedianHeap(val);
					break;
				default:
					break;
				}
			}
			mWindow.add(0, row);
			mPosY--;
		}

		public int get(int x, int y) {
			return mWindow.get(y).get(x);
		}

		public int getSum() {
			return mSum;
		}

		public int getSumSq() {
			return mSumSq;
		}

		public int getCenter() {
			return mWindow.get(mWindowHeight / 2).get(mWindowWidth / 2);
		}

		public TIntArrayList getRow(int rowIndex) {
			return mWindow.get(rowIndex);
		}

		public TIntArrayList getColumn(int columnIndex) {
			TIntArrayList intColumn = new TIntArrayList(mWindowHeight);

			for (int i = 0; i < mWindowHeight; i++) {
				intColumn.add(mWindow.get(i).get(columnIndex));
			}

			return intColumn;
		}

		public TIntArrayList getCorners() {
			TIntArrayList corners = new TIntArrayList(4);

			corners.add(mWindow.get(0).get(0));
			corners.add(mWindow.get(0).get(mWindowWidth - 1));
			corners.add(mWindow.get(mWindowHeight - 1).get(0));
			corners.add(mWindow.get(mWindowHeight - 1).get(mWindowWidth - 1));

			return corners;
		}

		private void sortWindow() {
			mSortedWindow.clear();

			for (int i = 0; i < mWindowHeight; i++) {
				mSortedWindow.addAll(mWindow.get(i));
			}
			mSortedWindow.sort();
			mSorted = true;
		}

		public void addToMedianHeap(int val) {
			if (maxHeap.size() == minHeap.size()) {
				if ((minHeap.peek() != null) && val > maxHeap.peek()) {
					minHeap.offer(val);
				} else {
					maxHeap.offer(val);
				}
			} else {
				if (val < maxHeap.peek()) {
					maxHeap.offer(val);
				} else {
					minHeap.offer(val);
				}
				if (maxHeap.size() > minHeap.size() + 1) {
					minHeap.offer(maxHeap.poll());
				} else if (minHeap.size() > maxHeap.size() + 1) {
					maxHeap.offer(minHeap.poll());
				}
			}
		}

		public boolean removeFromMedianHeap(int val) {
			if (maxHeap.remove(val) || minHeap.remove(val)) {
				if (maxHeap.size() > minHeap.size() + 1) {
					minHeap.offer(maxHeap.poll());
				} else if (minHeap.size() > maxHeap.size() + 1) {
					maxHeap.offer(minHeap.poll());
				}
				return true;
			}
			return false;
		}

		public int getMedian() {
			if (maxHeap.isEmpty())
				return minHeap.peek();
			else if (minHeap.isEmpty())
				return maxHeap.peek();

			if (maxHeap.size() >= minHeap.size()) {
				return maxHeap.peek();
			} else {
				return minHeap.peek();
			}
		}

		public int getTrimmedMean() {
			if (!mSorted) {
				mSortedWindow.sort();
				mSorted = true;
			}
			int numVals = mSortedWindow.size();
			int p = numVals / 2;
			int q = p / 2;
			int sum = 0;
			for (int i = q; i < numVals - q; i++) {
				sum += mSortedWindow.get(i);
			}
			sum /= (p + 1);

			return sum;
		}

		public int getBilateral() {
			int sum = 0;
			int norm = 0;
			int center = getCenter();

			switch (mWindowHeight) {
			case 3:
				for (int i = 0; i < mWindowHeight; i++) {
					TIntArrayList row = mWindow.get(i);
					for (int j = 0; j < mWindowWidth; j++) {
						int val = row.get(j);
						int coeff = mG3Coeffs[i * mWindowHeight + j]
								* mHCoeffs[Math.abs(center - val)];
						sum += val * coeff;
						norm += coeff;
					}
				}
				break;
			case 5:
				for (int i = 0; i < mWindowHeight; i++) {
					TIntArrayList row = mWindow.get(i);
					for (int j = 0; j < mWindowWidth; j++) {
						int val = row.get(j);
						int coeff = mG5Coeffs[i * mWindowHeight + j]
								* mHCoeffs[Math.abs(center - val)];
						sum += val * coeff;
						norm += coeff;
					}
				}
				break;
			case 7:
				for (int i = 0; i < mWindowHeight; i++) {
					TIntArrayList row = mWindow.get(i);
					for (int j = 0; j < mWindowWidth; j++) {
						int val = row.get(j);
						int coeff = mG7Coeffs[i * mWindowHeight + j]
								* mHCoeffs[Math.abs(center - val)];
						sum += val * coeff;
						norm += coeff;
					}
				}
				break;
			}
			sum /= norm;
			return sum;
		}

		public int getBilateralSub() {
			int sum = 0;
			int norm = 0;
			int center = getCenter();

			switch (mWindowHeight) {
			case 3:
				for (int i = 0; i < mWindowHeight; i++) {
					TIntArrayList row = mWindow.get(i);
					for (int j = 0; j < mWindowWidth; j++) {
						int val = row.get(j);
						int coeff = mG3Coeffs[i * mWindowHeight + j]
								* mHCoeffs[Math.abs(center - val)];
						sum += val * coeff;
						norm += coeff;
					}
				}
				break;
			case 5:
				for (int i = 0; i < mWindowHeight; i++) {
					TIntArrayList row = mWindow.get(i);
					for (int j = 0; j < mWindowWidth; j++) {
						int val = row.get(j);
						int coeff = mG5Coeffs[i * mWindowHeight + j]
								* mHCoeffs[Math.abs(center - val)];
						sum += val * coeff;
						norm += coeff;
					}
				}
				break;
			case 7:
				for (int i = 0; i < mWindowHeight; i++) {
					TIntArrayList row = mWindow.get(i);
					for (int j = 0; j < mWindowWidth; j++) {
						int val = row.get(j);
						int coeff = mG7Coeffs[i * mWindowHeight + j]
								* mHCoeffs[Math.abs(center - val)];
						sum += val * coeff;
						norm += coeff;
					}
				}
				break;
			}
			sum /= norm;
			return (center - sum);
		}

		public int getGaussian() {
			int sum = 0;

			switch (mWindowHeight) {
			case 3:
				for (int i = 0; i < mWindowHeight; i++) {
					TIntArrayList row = mWindow.get(i);
					for (int j = 0; j < mWindowWidth; j++) {
						int val = row.get(j);
						sum += val * mG3Coeffs[i * mWindowHeight + j];
					}
				}
				break;
			case 5:
				for (int i = 0; i < mWindowHeight; i++) {
					TIntArrayList row = mWindow.get(i);
					for (int j = 0; j < mWindowWidth; j++) {
						int val = row.get(j);
						sum += val * mG5Coeffs[i * mWindowHeight + j];
					}
				}
				break;
			case 7:
				for (int i = 0; i < mWindowHeight; i++) {
					TIntArrayList row = mWindow.get(i);
					for (int j = 0; j < mWindowWidth; j++) {
						int val = row.get(j);
						sum += val * mG7Coeffs[i * mWindowHeight + j];
					}
				}
				break;
			}
			sum = sum >> 10;
			return sum;
		}

		public int getAverage() {
			int avg = mSum / mNumPixels;
			return avg;
		}

		public int getNCC(Window image2Window, int w1PosX) {
			long numSum = 0;
			long denom = 0;
			double sqrt = 0;
			double corr = 0;
			double maxCorr = 0;
			int curIndex = 0;
			int dispIndex = 0;
			int maxDisp = 51;
			double scale = 255 / maxDisp;

			while (image2Window.canShiftRight() && curIndex <= maxDisp) {
				numSum = 0;
				for (int i = 0; i < mWindowHeight; i++) {
					TIntArrayList row = mWindow.get(i);
					TIntArrayList row2 = image2Window.getRow(i);
					for (int j = 0; j < mWindowWidth; j++) {
						numSum += PIXEL_PRODUCTS[row.get(j)][row2.get(j)];
					}
				}
				denom = (long) mSumSq * image2Window.getSumSq();
				sqrt = Math.sqrt(denom);
				corr = numSum / sqrt;
				if (corr > maxCorr) {
					maxCorr = corr;
					dispIndex = curIndex;
				}
				image2Window.shiftRight();
				curIndex++;
			}

			return (int) (dispIndex * scale);
		}

		public void resetNCCRow() {
			if (mRowSum != null) {
				Arrays.fill(mRowSum, 0);
			}
		}
	}
}
