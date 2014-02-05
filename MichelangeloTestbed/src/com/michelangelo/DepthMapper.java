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
	public enum IMAGE_POSITION {
		LEFT, RIGHT
	};

	public enum WINDOW_SIZE {
		SMALL, MEDIUM, LARGE
	}

	public enum FILTER_MODE {
		MEDIAN, TRIMMED_MEAN, BILATERAL, GAUSSIAN, AVERAGE;
	}

	private interface FilterFunc {
		Byte filter(DepthMapper.Window window);
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
	private FILTER_MODE mFilterMode = FILTER_MODE.MEDIAN;

	public DepthMapper(byte[][] yDataLeft, int width, int height) {
		if (yDataLeft != null) {
			mYDataLeft = yDataLeft;
			mImgWidth = width;
			mImgHeight = height;
		}
		filters.put(FILTER_MODE.MEDIAN, new FilterFunc() {
			public Byte filter(Window window) {
				return window.getMedian();
			}
		});
		filters.put(FILTER_MODE.TRIMMED_MEAN, new FilterFunc() {
			public Byte filter(DepthMapper.Window window) {
				return window.getTrimmedMean();
			}
		});
		filters.put(FILTER_MODE.BILATERAL, new FilterFunc() {
			public Byte filter(Window window) {
				return window.getBilateral();
			}
		});
		filters.put(FILTER_MODE.GAUSSIAN, new FilterFunc() {
			public Byte filter(Window window) {
				return window.getGaussian();
			}
		});
		filters.put(FILTER_MODE.AVERAGE, new FilterFunc() {
			public Byte filter(Window window) {
				return window.getAverage();
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
			filter(IMAGE_POSITION.LEFT, mFilterMode);
			// filter(IMAGE_POSITION.RIGHT, mFilterMode);
			// getDepth (IMAGE_POSITION.LEFT, IMAGE_POSITION.RIGHT);
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
				mResult[i][j++] = filters.get(filterMode).filter(window);
				window.shiftRight();
			}
			Log.w(TAG, "Filter Row " + i + " complete");
			if (window.canShiftDown()) {
				window.shiftDown();
				i++;
				while (window.canShiftLeft()) {
					mResult[i][j--] = filters.get(filterMode).filter(window);
					window.shiftLeft();
				}
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
		private ArrayList<ArrayList<Byte>> mWindow = null;
		private ArrayList<Byte> mSortedWindow = null;
		private byte[][] mData = null;
		private PriorityQueue<Byte> maxHeap = null;
		private PriorityQueue<Byte> minHeap = null;
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
		private FILTER_MODE mFilterMode = FILTER_MODE.MEDIAN;
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
			mWindow = new ArrayList<ArrayList<Byte>>();
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

			maxHeap = new PriorityQueue<Byte>((windowWidth * windowHeight) / 2,
					new Comparator<Byte>() {
						public int compare(Byte left, Byte right) {
							return right.compareTo(left);
						}
					});
			minHeap = new PriorityQueue<Byte>((windowWidth * windowHeight) / 2,
					new Comparator<Byte>() {
						public int compare(Byte left, Byte right) {
							return left.compareTo(right);
						}
					});

			int mTopOffset = windowHeight / 2;
			int mLeftOffset = windowWidth / 2;
			for (int i = -mTopOffset; i <= mTopOffset; i++) {
				ArrayList<Byte> row = new ArrayList<Byte>(windowWidth);
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
					row.add(val);
				}
				mWindow.add(row);
			}
			mSortedWindow = getSortedWindow();
			for (Byte b : mSortedWindow) {
				addToMedianHeap(b);
				mSum += (int) b & 0xff;
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
				byte val;
				try {
					val = mData[mPosY + i - mTopOffset][mPosX + mWindowWidth
							- mLeftOffset];
				} catch (ArrayIndexOutOfBoundsException e) {
					val = mWindow.get(i).get(mWindowWidth - 1);
				}
				Byte removed = mWindow.get(i).remove(0);
				mSortedWindow.remove(removed);
				mWindow.get(i).add(val);
				mSortedWindow.add(val);
				switch (mFilterMode) {
				case MEDIAN:
					removeFromMedianHeap(removed);
					addToMedianHeap(val);
					break;
				case AVERAGE:
					mSum -= (int) removed & 0xff;
					mSum += (int) val & 0xff;
					break;
				default:
					break;
				}
			}
			mSorted = false;
			mPosX++;
		}

		public void shiftLeft() {
			for (int i = 0; i < mWindowHeight; i++) {
				byte val;
				try {
					val = mData[mPosY + i - mTopOffset][mPosX - 1 - mLeftOffset];
				} catch (ArrayIndexOutOfBoundsException e) {
					val = mWindow.get(i).get(0);
				}
				Byte removed = mWindow.get(i).remove(mWindowWidth - 1);
				mSortedWindow.remove(removed);
				mWindow.get(i).add(0, val);
				mSortedWindow.add(val);
				switch (mFilterMode) {
				case MEDIAN:
					removeFromMedianHeap(removed);
					addToMedianHeap(val);
					break;
				case AVERAGE:
					mSum -= (int) removed & 0xff;
					mSum += (int) val & 0xff;
					break;
				default:
					break;
				}
			}
			mSorted = false;
			mPosX--;
		}

		public void shiftDown() {
			ArrayList<Byte> row = new ArrayList<Byte>(mWindowWidth);

			ArrayList<Byte> removedRow = mWindow.remove(0);
			for (Byte removed : removedRow) {
				mSortedWindow.remove(removed);
				switch (mFilterMode) {
				case MEDIAN:
					removeFromMedianHeap(removed);
					break;
				case AVERAGE:
					mSum -= (int) removed & 0xff;
					break;
				default:
					break;
				}
			}
			for (int i = 0; i < mWindowWidth; i++) {
				byte val;
				try {
					val = mData[mPosY + mWindowHeight - mTopOffset][mPosX + i
							- mLeftOffset];
				} catch (ArrayIndexOutOfBoundsException e) {
					val = removedRow.get(i);
				}
				row.add(val);
				mSortedWindow.add(val);
				switch (mFilterMode) {
				case MEDIAN:
					addToMedianHeap(val);
					break;
				case AVERAGE:
					mSum += (int) val & 0xff;
					break;
				default:
					break;
				}
			}
			mWindow.add(row);
			mSorted = false;
			mPosY++;
		}

		public void shiftUp() {
			ArrayList<Byte> row = new ArrayList<Byte>(mWindowWidth);

			ArrayList<Byte> removedRow = mWindow.remove(mWindowHeight - 1);
			for (Byte removed : removedRow) {
				mSortedWindow.remove(removed);
				switch (mFilterMode) {
				case MEDIAN:
					removeFromMedianHeap(removed);
					break;
				case AVERAGE:
					mSum -= (int) removed & 0xff;
					break;
				default:
					break;
				}
			}
			for (int i = 0; i < mWindowWidth; i++) {
				byte val;
				try {
					val = mData[mPosY - 1 - mTopOffset][mPosX + i - mLeftOffset];
				} catch (ArrayIndexOutOfBoundsException e) {
					val = removedRow.get(i);
				}
				row.add(val);
				mSortedWindow.add(val);
				switch (mFilterMode) {
				case MEDIAN:
					addToMedianHeap(val);
					break;
				case AVERAGE:
					mSum += (int) val & 0xff;
					break;
				default:
					break;
				}
			}
			mWindow.add(0, row);
			mSorted = false;
			mPosY--;
		}

		public Byte get(int x, int y) {
			return new Byte(mWindow.get(y).get(x));
		}

		public Byte getCenter() {
			return new Byte(mWindow.get(mWindowHeight / 2)
					.get(mWindowWidth / 2));
		}

		public ArrayList<Byte> getRow(int rowIndex) {
			return new ArrayList<Byte>(mWindow.get(rowIndex));
		}

		public ArrayList<Byte> getColumn(int columnIndex) {
			ArrayList<Byte> byteColumn = new ArrayList<Byte>(mWindowHeight);

			for (int i = 0; i < mWindowHeight; i++) {
				byteColumn.add(mWindow.get(i).get(columnIndex));
			}

			return byteColumn;
		}

		public ArrayList<Byte> getCorners() {
			ArrayList<Byte> corners = new ArrayList<Byte>(4);

			corners.add(mWindow.get(0).get(0));
			corners.add(mWindow.get(0).get(mWindowWidth - 1));
			corners.add(mWindow.get(mWindowHeight - 1).get(0));
			corners.add(mWindow.get(mWindowHeight - 1).get(mWindowWidth - 1));

			return corners;
		}

		private ArrayList<Byte> getSortedWindow() {
			ArrayList<Byte> sorted = new ArrayList<Byte>(mWindowWidth
					* mWindowHeight);

			for (int i = 0; i < mWindowHeight; i++) {
				sorted.addAll(mWindow.get(i));
			}
			Collections.sort(sorted);
			mSorted = true;

			return sorted;
		}

		public void addToMedianHeap(Byte val) {
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

		public boolean removeFromMedianHeap(Byte val) {
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

		public Byte getMedian() {
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

		public Byte getTrimmedMean() {
			if (!mSorted) {
				Collections.sort(mSortedWindow);
				mSorted = true;
			}
			int numVals = mSortedWindow.size();
			int p = numVals / 2;
			int q = p / 2;
			int sum = 0;
			for (int i = q; i < numVals - q; i++) {
				sum += (int) mSortedWindow.get(i) & 0xff;
			}
			sum /= (p + 1);

			return new Byte((byte) sum);
		}

		public Byte getBilateral() {
			int sum = 0;
			int norm = 0;
			int center = (int) getCenter() & 0xff;

			switch (mWindowHeight) {
			case 3:
				for (int i = 0; i < mWindowHeight; i++) {
					for (int j = 0; j < mWindowWidth; j++) {
						int val = (int) mWindow.get(i).get(j) & 0xff;
						int coeff = mG3Coeffs[i * mWindowHeight + j]
								* mHCoeffs[Math.abs(center - val)];
						sum += val * coeff;
						norm += coeff;
					}
				}
				break;
			case 5:
				for (int i = 0; i < mWindowHeight; i++) {
					for (int j = 0; j < mWindowWidth; j++) {
						int val = (int) mWindow.get(i).get(j) & 0xff;
						int coeff = mG5Coeffs[i * mWindowHeight + j]
								* mHCoeffs[Math.abs(center - val)];
						sum += val * coeff;
						norm += coeff;
					}
				}
				break;
			case 7:
				for (int i = 0; i < mWindowHeight; i++) {
					for (int j = 0; j < mWindowWidth; j++) {
						int val = (int) mWindow.get(i).get(j) & 0xff;
						int coeff = mG7Coeffs[i * mWindowHeight + j]
								* mHCoeffs[Math.abs(center - val)];
						sum += val * coeff;
						norm += coeff;
					}
				}
				break;
			}
			sum /= norm;
			return new Byte((byte) sum);
		}

		public Byte getGaussian() {
			int sum = 0;

			switch (mWindowHeight) {
			case 3:
				for (int i = 0; i < mWindowHeight; i++) {
					for (int j = 0; j < mWindowWidth; j++) {
						int val = (int) mWindow.get(i).get(j) & 0xff;
						sum += val * mG3Coeffs[i * mWindowHeight + j];
					}
				}
				break;
			case 5:
				for (int i = 0; i < mWindowHeight; i++) {
					for (int j = 0; j < mWindowWidth; j++) {
						int val = (int) mWindow.get(i).get(j) & 0xff;
						sum += val * mG5Coeffs[i * mWindowHeight + j];
					}
				}
				break;
			case 7:
				for (int i = 0; i < mWindowHeight; i++) {
					for (int j = 0; j < mWindowWidth; j++) {
						int val = (int) mWindow.get(i).get(j) & 0xff;
						sum += val * mG7Coeffs[i * mWindowHeight + j];
					}
				}
				break;
			}
			sum = sum >> 10;
			return new Byte((byte) sum);
		}

		public Byte getAverage() {
			int avg = mSum / mNumPixels;
			return new Byte((byte) avg);
		}
	}
}