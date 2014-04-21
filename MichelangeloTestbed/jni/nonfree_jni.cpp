#include "jni.h"
#include <opencv2\core\core.hpp>
#include <opencv2\features2d\features2d.hpp>
#include <opencv2\calib3d\calib3d.hpp>
#include <opencv2\imgproc\imgproc.hpp>
#include <opencv2\highgui\highgui.hpp>
#include <opencv2\nonfree\nonfree.hpp>
#include <vector>
#include <iostream>
#include <android/log.h>

#define LOG_INFO(info) __android_log_write(ANDROID_LOG_INFO,"JNI",info)
#define LOG_ERROR(error) __android_log_write(ANDROID_LOG_ERROR,"JNI",error)

using namespace cv;

// The major functions performing the SIFT processing
int run_demo() {
	//const char * imgInFile = "/sdcard/nonfree/img1.jpg";
	//const char * imgOutFile = "/sdcard/nonfree/img1_result.jpg";

	//Mat image;

	//image = imread(imgInFile, CV_LOAD_IMAGE_COLOR);

	//vector < KeyPoint > keypoints;
	//Mat descriptors;

	//Ptr<FeatureDetector> fd = new SurfFeatureDetector(400);
	//detector.detect(image, keypoints);
	//detector.compute(image, keypoints, descriptors);

	/* Some other processing, please check the download package for details. */

	return 0;
}

vector<vector<Point2f> > surfDetect(Mat imageLeft, Mat imageRight) {
	Mat descriptorsLeft, descriptorsRight;
	Ptr<FeatureDetector> fd = new SurfFeatureDetector(400);
	Ptr<DescriptorExtractor> de = new SurfDescriptorExtractor();
	Ptr<DescriptorMatcher> dm = DescriptorMatcher::create("BruteForce");
	vector<KeyPoint> keyPointsLeft, keyPointsRight;
	vector<vector<DMatch> > matchesLeft, matchesRight;
	vector<DMatch> goodMatches;
	vector<Point2f> goodPointsLeft, goodPointsRight;
	vector<vector<Point2f> > goodPoints;

	// Detect features
	LOG_INFO("Detecting features.");
	fd->detect(imageLeft, keyPointsLeft);
	fd->detect(imageRight, keyPointsRight);

	// Compute descriptors
	LOG_INFO("Extracting descriptors.");
	de->compute(imageLeft, keyPointsLeft, descriptorsLeft);
	de->compute(imageRight, keyPointsRight, descriptorsRight);

	// Find closest 2 matches
	LOG_INFO("Finding closest 2 matches.");
	dm->knnMatch(descriptorsLeft, descriptorsRight, matchesLeft, 2);
	dm->knnMatch(descriptorsRight, descriptorsLeft, matchesRight, 2);

	// Do ratio test as per Lowe
	LOG_INFO("Performing ratio test.");
	for (int i = 0; i < matchesLeft.size(); i++) {
		vector<DMatch> leftMatchPair = matchesLeft[i];
		DMatch first = leftMatchPair[0], second = leftMatchPair[1];
		if (first.distance < (.65 * second.distance)) {
			vector<DMatch> rightMatchPair = matchesRight[first.trainIdx];
			DMatch rightFirst = rightMatchPair[0];
			if (rightFirst.trainIdx == first.queryIdx) {
				goodMatches.push_back(first);
			}
		}
	}

	// Get the good keypoints that correspond to the good matches
	LOG_INFO("Retrieving good points.");
	for (int i = 0; i < goodMatches.size(); i++) {
		DMatch match = goodMatches[i];
		KeyPoint keyPointLeft = keyPointsLeft[match.queryIdx], keyPointRight =
				keyPointsRight[match.queryIdx];
		Point2f pointLeft = keyPointLeft.pt, pointRight = keyPointRight.pt;
		if (abs(pointLeft.y - pointRight.y) < (imageLeft.rows / 8)) {
			goodPointsLeft.push_back(pointLeft);
			goodPointsRight.push_back(pointRight);
		} else {
			goodMatches.erase(goodMatches.begin() + i);
			i--;
		}
	}

	// Create vector of vectors
	goodPoints.push_back(goodPointsLeft);
	goodPoints.push_back(goodPointsRight);

	LOG_INFO("Finished.");
	return goodPoints;
}

int surfExtract() {
	Mat imageLeft(300, 300, CV_8UC4), imageRight(300, 300, CV_8UC4),
			descriptorsLeft, descriptorsRight;
	Ptr<FeatureDetector> fd = new SurfFeatureDetector(400);
	Ptr<DescriptorExtractor> de = new SurfDescriptorExtractor();
	vector<KeyPoint> keyPointsLeft, keyPointsRight;

	fd->detect(imageLeft, keyPointsLeft);
	fd->detect(imageRight, keyPointsRight);

	de->compute(imageLeft, keyPointsLeft, descriptorsLeft);
	de->compute(imageRight, keyPointsRight, descriptorsRight);

	return keyPointsLeft.size();
}

// JNI interface functions, be careful about the naming.
extern "C" {
JNIEXPORT void JNICALL Java_com_michelangelo_NonfreeJNILib_runDemo(JNIEnv * env,
		jobject obj);
}
;

JNIEXPORT void JNICALL Java_com_michelangelo_NonfreeJNILib_runDemo(JNIEnv * env,
		jobject obj) {
	run_demo();
}

extern "C" {
JNIEXPORT jobject JNICALL Java_com_michelangelo_NonfreeJNILib_surfDetect(
		JNIEnv * env, jobject obj, jlong pMatLeft, jlong pMatRight);
}
;

JNIEXPORT jobject JNICALL Java_com_michelangelo_NonfreeJNILib_surfDetect(
		JNIEnv * env, jobject obj, jlong pMatLeft, jlong pMatRight) {
	Mat * leftImage = (Mat*) pMatLeft;
	Mat * rightImage = (Mat*) pMatRight;
	vector<vector<Point2f> > goodPoints;
	vector<Point2f> pointsLeft, pointsRight;

	// Detect and match
	LOG_INFO("Starting JNI call.");
	goodPoints = surfDetect(*leftImage, *rightImage);
	pointsLeft = goodPoints[0];
	pointsRight = goodPoints[1];

	// Create java objects
	LOG_INFO("Creating java objects.");
	jclass listClass = (*env).FindClass("java/util/LinkedList");
	jclass point2fClass = (*env).FindClass("org/opencv/core/Point");
	jmethodID pointInitMethod = (*env).GetMethodID(point2fClass, "<init>",
			"(DD)V");
	jobject listLeft = (*env).NewObject(listClass,
			(*env).GetMethodID(listClass, "<init>", "()V"));
	jobject listRight = (*env).NewObject(listClass,
			(*env).GetMethodID(listClass, "<init>", "()V"));
	jobject listBoth = (*env).NewObject(listClass,
			(*env).GetMethodID(listClass, "<init>", "()V"));

	// Fill the linked lists
	LOG_INFO("Filling left and right lists.");
	for (int i = 0; i < pointsLeft.size(); i++) {
		Point2f pointLeft = pointsLeft[i], pointRight = pointsRight[i];
		jobject objLeft = (*env).NewObject(point2fClass, pointInitMethod,
				pointLeft.x, pointLeft.y);
		jobject objRight = (*env).NewObject(point2fClass, pointInitMethod,
				pointRight.x, pointRight.y);
		(*env).CallVoidMethod(listLeft,
				(*env).GetMethodID(listClass, "addLast",
						"(Ljava/lang/Object;)V"), objLeft);
		(*env).CallVoidMethod(listRight,
				(*env).GetMethodID(listClass, "addLast",
						"(Ljava/lang/Object;)V"), objRight);
	}

	// Fill the Linked list with the left and right lists
	LOG_INFO("Filling list of lists.");
	(*env).CallVoidMethod(listBoth,
			(*env).GetMethodID(listClass, "addLast", "(Ljava/lang/Object;)V"),
			listLeft);
	(*env).CallVoidMethod(listBoth,
			(*env).GetMethodID(listClass, "addLast", "(Ljava/lang/Object;)V"),
			listRight);

	LOG_INFO("Finished JNI call.");
	return listBoth;
}

extern "C" {
JNIEXPORT jint JNICALL Java_com_michelangelo_NonfreeJNILib_surfExtract(
		JNIEnv * env, jobject obj, jobject obj2, jobject obj3);
}
;

JNIEXPORT jint JNICALL Java_com_michelangelo_NonfreeJNILib_surfExtract(
		JNIEnv * env, jobject obj, jobject obj2, jobject obj3) {
	int numDescriptors = surfExtract();
	return numDescriptors;
}
