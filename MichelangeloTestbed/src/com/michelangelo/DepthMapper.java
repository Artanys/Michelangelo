package com.michelangelo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;

import android.graphics.Bitmap;
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

	private interface FilterFunc {
		Integer filter(DepthMapper.Window window);
	}

	private EnumMap<FILTER_MODE, FilterFunc> filters = new EnumMap<FILTER_MODE, FilterFunc>(
			FILTER_MODE.class);

	private static final String TAG = "DepthMapper";
	private byte[][] mYDataLeft = null;
	private byte[][] mYDataRight = null;
	private byte[][] mResult = null;
	private int mImgWidth = 0;
	private int mImgHeight = 0;
	private int mWindowWidth = 0;
	private int mWindowHeight = 0;
	private FILTER_MODE mFilterMode = FILTER_MODE.NONE;

	public DepthMapper(byte[][] yDataLeft, int width, int height) {
		if (yDataLeft != null) {
			mYDataLeft = yDataLeft;
			mImgWidth = width;
			mImgHeight = height;
			PIXEL_PRODUCTS = new int[256][256];
			for ( int i = 0; i < 256; i ++ ) {
				for ( int j = 0; j < 256; j ++ ) {
					PIXEL_PRODUCTS[i][j] = i * j;
				}
			}
		}
		filters.put(FILTER_MODE.MEDIAN, new FilterFunc() {
			public Integer filter(Window window) {
				return window.getMedian();
			}
		});
		filters.put(FILTER_MODE.TRIMMED_MEAN, new FilterFunc() {
			public Integer filter(DepthMapper.Window window) {
				return window.getTrimmedMean();
			}
		});
		filters.put(FILTER_MODE.BILATERAL, new FilterFunc() {
			public Integer filter(Window window) {
				return window.getBilateral();
			}
		});
		filters.put(FILTER_MODE.BILSUB, new FilterFunc() {
			public Integer filter(Window window) {
				return window.getBilateralSub();
			}
		});
		filters.put(FILTER_MODE.GAUSSIAN, new FilterFunc() {
			public Integer filter(Window window) {
				return window.getGaussian();
			}
		});
		filters.put(FILTER_MODE.AVERAGE, new FilterFunc() {
			public Integer filter(Window window) {
				return window.getAverage();
			}
		});
		filters.put(FILTER_MODE.NONE, new FilterFunc() {
			public Integer filter(Window window) {
				return window.getCenter();
			}
		});
	}

	public Bitmap call() {
		Bitmap result = null;

		if (generateDepthMap()) {
			result = getBitmapFromResult();
			MichelangeloCamera.saveBitmap(result);
		}

		return result;
	}

	private boolean generateDepthMap() {
		if (readyToProcess()) {
			//filter(IMAGE_POSITION.LEFT, mFilterMode);
			// filter(IMAGE_POSITION.RIGHT, mFilterMode);
			getDepth (IMAGE_POSITION.RIGHT, IMAGE_POSITION.LEFT);
			return true;
		}
		return false;
	}

	public boolean setRightData(byte[][] yDataRight, int width, int height) {
		if (yDataRight == null || width != mImgWidth || height != mImgHeight) {
			return false;
		}
		mYDataRight = yDataRight;
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
			mResult = new byte[mImgHeight][mImgWidth];
		Window window = getWindow(imgPos, 0, 0);
		window.setFilterMode(filterMode);

		int i = 0;
		int j = 0;
		do {
			while (window.canShiftRight()) {
				mResult[i][j++] = filters.get(filterMode).filter(window).byteValue();
				window.shiftRight();
			}
			mResult[i][j] = filters.get(filterMode).filter(window).byteValue();
			Log.w(TAG, "Filter Row " + i + " complete");
			if (window.canShiftDown()) {
				window.shiftDown();
				i++;
				while (window.canShiftLeft()) {
					mResult[i][j--] = filters.get(filterMode).filter(window).byteValue();
					window.shiftLeft();
				}
				mResult[i][j] = filters.get(filterMode).filter(window).byteValue();
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
			mResult = new byte[mImgHeight][mImgWidth];
		Window window = getWindow(img1Pos, 0, 0);
		Window window2 = getWindow(img2Pos, 0, 0);

		int i = 0;
		int j = 0;
		do {
			while (window.canShiftRight()) {
				mResult[i][j] = window.getNCC(window2, j).byteValue();
				window.shiftRight();
				j++;
				window2.reset(j, i);
			}
			mResult[i][j] = window.getNCC(window2, j).byteValue();
			Log.w(TAG, "Filter Row " + i + " complete");
			if (window.canShiftDown()) {
				window.shiftDown();
				i++;
				window2.reset(j, i);
				while (window.canShiftLeft()) {
					mResult[i][j] = window.getNCC(window2, j).byteValue();
					window.shiftLeft();
					j--;
					window2.reset(j, i);
				}
				mResult[i][j] = window.getNCC(window2, j).byteValue();
				Log.w(TAG, "Filter Row " + i + " complete");
				if (window.canShiftDown()) {
					window.shiftDown();
					i++;
					window2.reset(0, i);
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
		if (mYDataLeft != null && mYDataRight != null && mWindowWidth != 0) {
			return true;
		}
		return false;
	}

	public Bitmap getBitmapFromResult() {
		Bitmap bitmap = null;
		if (mResult == null)
			return null;
		int[] pixelData = new int[mImgWidth * mImgHeight];

		Log.w(TAG, "Writing byte data to int buffer.");
		int bitmapIndex = 0;
		for (int i = 0; i < mImgHeight; i++) {
			for (int j = 0; j < mImgWidth; j++, bitmapIndex++) {
				int val = mResult[i][j] & 0xff;
				int argb = (0xff << 24) + (val << 16) + (val << 8) + val;
				pixelData[bitmapIndex] = argb;
			}
		}
		Log.w(TAG, "Setting bitmap pixels to int buffer.");
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
		private ArrayList<ArrayList<Integer>> mWindow = null;
		private ArrayList<ArrayList<Integer>> mWindowSq = null;
		private ArrayList<Integer> mSortedWindow = null;
		private byte[][] mData = null;
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

		public Window(byte[][] yData, int imgWidth, int imgHeight, int posX,
				int posY, int windowWidth, int windowHeight) {
			mWindow = new ArrayList<ArrayList<Integer>>();
			mWindowSq = new ArrayList<ArrayList<Integer>>();
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
				ArrayList<Integer> row = new ArrayList<Integer>(windowWidth);
				ArrayList<Integer> rowSq = new ArrayList<Integer>(windowWidth);
				for (int j = -mLeftOffset; j <= mLeftOffset; j++) {
					byte val;
					int yIndex = posY + i;
					int xIndex = posX + j;
					try {
						val = yData[yIndex][xIndex];
					} catch (ArrayIndexOutOfBoundsException e) {
						do {
							if (yIndex < 0 && xIndex < 0) {
								val = yData[0][0];
								break;
							}
							if (yIndex < 0 && xIndex >= imgWidth) {
								val = yData[0][imgWidth - 1];
								break;
							}
							if (yIndex >= imgHeight && xIndex < 0) {
								val = yData[imgHeight - 1][0];
								break;
							}
							if (yIndex >= imgHeight && xIndex >= imgWidth) {
								val = yData[imgHeight - 1][imgWidth - 1];
								break;
							}
							if (yIndex < 0) {
								val = yData[0][xIndex];
								break;
							}
							if (yIndex >= imgHeight) {
								val = yData[imgHeight - 1][xIndex];
								break;
							}
							if (xIndex < 0) {
								val = yData[yIndex][0];
								break;
							}
							if (xIndex >= imgWidth) {
								val = yData[yIndex][imgWidth - 1];
								break;
							}
							val = 0;
						} while (false);
					}
					int intVal = (int) val & 0xff;
					row.add(intVal);
					rowSq.add(PIXEL_PRODUCTS[intVal][intVal]);
				}
				mWindow.add(row);
				mWindowSq.add(rowSq);
			}
			mSortedWindow = getSortedWindow();
			for (Integer b : mSortedWindow) {
				addToMedianHeap(b);
				mSum += b;
				mSumSq += PIXEL_PRODUCTS[b][b];
			}
			mSorted = true;
		}
		
		public void reset ( int posX, int posY ) {
			mPosX = posX;
			mPosY = posY;

			mWindow.clear();
			mWindowSq.clear();
			maxHeap.clear();
			minHeap.clear();
			
			for (int i = -mTopOffset; i <= mTopOffset; i++) {
				ArrayList<Integer> row = new ArrayList<Integer>(mWindowWidth);
				ArrayList<Integer> rowSq = new ArrayList<Integer>(mWindowWidth);
				for (int j = -mLeftOffset; j <= mLeftOffset; j++) {
					byte val;
					int yIndex = posY + i;
					int xIndex = posX + j;
					try {
						val = mData[yIndex][xIndex];
					} catch (ArrayIndexOutOfBoundsException e) {
						do {
							if (yIndex < 0 && xIndex < 0) {
								val = mData[0][0];
								break;
							}
							if (yIndex < 0 && xIndex >= mImgWidth) {
								val = mData[0][mImgWidth - 1];
								break;
							}
							if (yIndex >= mImgHeight && xIndex < 0) {
								val = mData[mImgHeight - 1][0];
								break;
							}
							if (yIndex >= mImgHeight && xIndex >= mImgWidth) {
								val = mData[mImgHeight - 1][mImgWidth - 1];
								break;
							}
							if (yIndex < 0) {
								val = mData[0][xIndex];
								break;
							}
							if (yIndex >= mImgHeight) {
								val = mData[mImgHeight - 1][xIndex];
								break;
							}
							if (xIndex < 0) {
								val = mData[yIndex][0];
								break;
							}
							if (xIndex >= mImgWidth) {
								val = mData[yIndex][mImgWidth - 1];
								break;
							}
							val = 0;
						} while (false);
					}
					int intVal = (int) val & 0xff;
					row.add(intVal);
					rowSq.add(PIXEL_PRODUCTS[intVal][intVal]);
				}
				mWindow.add(row);
				mWindowSq.add(rowSq);
			}
			mSortedWindow = getSortedWindow();
			mSum = 0;
			mSumSq = 0;
			for (Integer b : mSortedWindow) {
				addToMedianHeap(b);
				mSum += b;
				mSumSq += PIXEL_PRODUCTS[b][b];
			}
			mSorted = true;
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
				int val, valSq;
				ArrayList<Integer> row = mWindow.get(i);
				ArrayList<Integer> rowSq = mWindowSq.get(i);
				try {
					val = (int) mData[mPosY + i - mTopOffset][mPosX + mWindowWidth
							- mLeftOffset] & 0xff;
				} catch (ArrayIndexOutOfBoundsException e) {
					val = row.get(mWindowWidth - 1);
				}
				valSq = PIXEL_PRODUCTS[val][val];
				Integer removed = row.remove(0);
				Integer removedSq = rowSq.remove(0);
				row.add(val);
				rowSq.add(valSq);
				switch (mFilterMode) {
				case NONE:
					mSumSq -= removedSq;
					mSumSq += valSq;
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
				int val, valSq;
				ArrayList<Integer> row = mWindow.get(i);
				ArrayList<Integer> rowSq = mWindowSq.get(i);
				try {
					val = (int) mData[mPosY + i - mTopOffset][mPosX - 1 - mLeftOffset] & 0xff;
				} catch (ArrayIndexOutOfBoundsException e) {
					val = row.get(0);
				}
				valSq = PIXEL_PRODUCTS[val][val];
				Integer removed = row.remove(mWindowWidth - 1);
				Integer removedSq = rowSq.remove(mWindowWidth - 1);
				row.add(0, val);
				rowSq.add(0, valSq);
				switch (mFilterMode) {
				case NONE:
					mSumSq -= removedSq;
					mSumSq += valSq;
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
			ArrayList<Integer> row = new ArrayList<Integer>(mWindowWidth);
			ArrayList<Integer> rowSq = new ArrayList<Integer>(mWindowWidth);

			ArrayList<Integer> removedRow = mWindow.remove(0);
			mWindowSq.remove(0);
			for (Integer removed : removedRow) {
				switch (mFilterMode) {
				case NONE:
					mSumSq -= PIXEL_PRODUCTS[removed][removed];
					break;
				case AVERAGE:
					mSum -= removed;
					mSumSq -= PIXEL_PRODUCTS[removed][removed];
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
				int val, valSq;
				try {
					val = (int) mData[mPosY + mWindowHeight - mTopOffset][mPosX + i
							- mLeftOffset] & 0xff;
				} catch (ArrayIndexOutOfBoundsException e) {
					val = removedRow.get(i);
				}
				valSq = PIXEL_PRODUCTS[val][val];
				row.add(val);
				rowSq.add(valSq);
				switch (mFilterMode) {
				case NONE:
					mSumSq += valSq;
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
			mWindowSq.add(rowSq);
			mPosY++;
		}

		public void shiftUp() {
			ArrayList<Integer> row = new ArrayList<Integer>(mWindowWidth);
			ArrayList<Integer> rowSq = new ArrayList<Integer>(mWindowWidth);

			ArrayList<Integer> removedRow = mWindow.remove(mWindowHeight - 1);
			mWindowSq.remove(mWindowHeight - 1);
			for (Integer removed : removedRow) {
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
				int val, valSq;
				try {
					val = (int) mData[mPosY - 1 - mTopOffset][mPosX + i - mLeftOffset] & 0xff;
				} catch (ArrayIndexOutOfBoundsException e) {
					val = removedRow.get(i);
				}
				valSq = PIXEL_PRODUCTS[val][val];
				row.add(val);
				rowSq.add(valSq);
				switch (mFilterMode) {
				case NONE:
					mSumSq += valSq;
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
			mWindowSq.add(0, rowSq);
			mPosY--;
		}

		public Integer get(int x, int y) {
			return mWindow.get(y).get(x);
		}
		
		public Integer getSq(int x, int y) {
			return mWindowSq.get(y).get(x);
		}
		
		public int getSum() {
			return mSum;
		}
		
		public int getSumSq() {
			return mSumSq;
		}

		public Integer getCenter() {
			return mWindow.get(mWindowHeight / 2).get(mWindowWidth / 2);
		}

		public ArrayList<Integer> getRow(int rowIndex) {
			return mWindow.get(rowIndex);
		}

		public ArrayList<Integer> getColumn(int columnIndex) {
			ArrayList<Integer> intColumn = new ArrayList<Integer>(mWindowHeight);

			for (int i = 0; i < mWindowHeight; i++) {
				intColumn.add(mWindow.get(i).get(columnIndex));
			}

			return intColumn;
		}

		public ArrayList<Integer> getCorners() {
			ArrayList<Integer> corners = new ArrayList<Integer>(4);

			corners.add(mWindow.get(0).get(0));
			corners.add(mWindow.get(0).get(mWindowWidth - 1));
			corners.add(mWindow.get(mWindowHeight - 1).get(0));
			corners.add(mWindow.get(mWindowHeight - 1).get(mWindowWidth - 1));

			return corners;
		}

		private ArrayList<Integer> getSortedWindow() {
			ArrayList<Integer> sorted = new ArrayList<Integer>(mNumPixels);

			for (int i = 0; i < mWindowHeight; i++) {
				sorted.addAll(mWindow.get(i));
			}
			Collections.sort(sorted);
			mSorted = true;

			return sorted;
		}

		public void addToMedianHeap(Integer val) {
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

		public boolean removeFromMedianHeap(Integer val) {
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

		public Integer getMedian() {
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

		public Integer getTrimmedMean() {
			if (!mSorted) {
				Collections.sort(mSortedWindow);
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

			return Integer.valueOf(sum);
		}

		public Integer getBilateral() {
			int sum = 0;
			int norm = 0;
			int center = getCenter();

			switch (mWindowHeight) {
			case 3:
				for (int i = 0; i < mWindowHeight; i++) {
					ArrayList<Integer> row = mWindow.get(i);
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
					ArrayList<Integer> row = mWindow.get(i);
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
					ArrayList<Integer> row = mWindow.get(i);
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
			return Integer.valueOf(sum);
		}

		public Integer getBilateralSub() {
			int sum = 0;
			int norm = 0;
			int center = getCenter();

			switch (mWindowHeight) {
			case 3:
				for (int i = 0; i < mWindowHeight; i++) {
					ArrayList<Integer> row = mWindow.get(i);
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
					ArrayList<Integer> row = mWindow.get(i);
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
					ArrayList<Integer> row = mWindow.get(i);
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
			return Integer.valueOf(center - sum);
		}

		public Integer getGaussian() {
			int sum = 0;

			switch (mWindowHeight) {
			case 3:
				for (int i = 0; i < mWindowHeight; i++) {
					ArrayList<Integer> row = mWindow.get(i);
					for (int j = 0; j < mWindowWidth; j++) {
						int val = row.get(j);
						sum += val * mG3Coeffs[i * mWindowHeight + j];
					}
				}
				break;
			case 5:
				for (int i = 0; i < mWindowHeight; i++) {
					ArrayList<Integer> row = mWindow.get(i);
					for (int j = 0; j < mWindowWidth; j++) {
						int val = row.get(j);
						sum += val * mG5Coeffs[i * mWindowHeight + j];
					}
				}
				break;
			case 7:
				for (int i = 0; i < mWindowHeight; i++) {
					ArrayList<Integer> row = mWindow.get(i);
					for (int j = 0; j < mWindowWidth; j++) {
						int val = row.get(j);
						sum += val * mG7Coeffs[i * mWindowHeight + j];
					}
				}
				break;
			}
			sum = sum >> 10;
			return Integer.valueOf(sum);
		}

		public Integer getAverage() {
			int avg = mSum / mNumPixels;
			return Integer.valueOf(avg);
		}
		
		public Integer getNCC( Window image2Window, int w1PosX ) {
			long numSum = 0;
			long denom = 0;
			double sqrt = 0;
			double corr = 0;
			double maxCorr = 0;
			int curIndex = 0;
			int dispIndex = 0;
			int maxDisp = mImgWidth / 5;
			double scale = 255 / maxDisp;
			
			while ( image2Window.canShiftRight() && curIndex < maxDisp ) {
				numSum = 0;
				for (int i = 0; i < mWindowHeight; i++) {
					ArrayList<Integer> row = mWindow.get(i);
					ArrayList<Integer> row2 = image2Window.getRow(i);
					for (int j = 0; j < mWindowWidth; j++) {
						numSum += PIXEL_PRODUCTS[row.get(j)][row2.get(j)];
					}
				}
				denom = (long) mSumSq * image2Window.getSumSq();
				sqrt = Math.sqrt(denom);
				corr = numSum / sqrt;
				if ( corr > maxCorr ) {
					maxCorr = corr;
					dispIndex = curIndex;
				}
				image2Window.shiftRight();
				curIndex ++;
			}
			
			return Integer.valueOf((int)(dispIndex * scale));
			
		}
	}
}
