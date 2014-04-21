package com.michelangelo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.DateFormat.Field;
import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Checkable;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ShareActionProvider;
import android.widget.Toast;

public class MichelangeloGallery extends MichelangeloUI implements
		ConfirmDeleteFragment.ConfirmDeleteListener {

	protected ArrayList<Drawable> thumbs = new ArrayList<Drawable>();
	private static final String TAG = "Gallery";

	public class CheckableLayout extends FrameLayout implements Checkable {
		private boolean mChecked;

		public CheckableLayout(Context context) {
			super(context);
		}

		@SuppressWarnings("deprecation")
		// @SuppressLint("NewApi")
		public void setChecked(boolean checked) {
			mChecked = checked;
			setBackgroundDrawable(checked ? getResources().getDrawable(
					R.color.blue) : null);
		}

		public boolean isChecked() {
			return mChecked;
		}

		public void toggle() {
			setChecked(!mChecked);
		}

	}

	public void buttonClicked(View view) {
		Log.d("buttonClicked");
		Intent intent = new Intent(this, MichelangeloGallery.class);
		startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.activity_michelangelo_gallery);
		super.onCreate(savedInstanceState);
		// Show the Up button in the action bar.
		setupActionBar();

		ArrayList<File> filePaths = com.michelangelo.MichelangeloCamera
				.getMediaFiles();
		for (File filePath : filePaths) {
			thumbs.add(Drawable.createFromPath(filePath.toString()));
		}

		final GridView gridview = (GridView) findViewById(R.id.gridview);
		gridview.setAdapter(new ImageAdapter(this));

		gridview.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v,
					int position, long id) {
				// Toast.makeText(MichelangeloGallery.this, "" + position,
				// Toast.LENGTH_SHORT).show();
				Log.d("Thumbnail CLicked");
				// Intent intent = new Intent(MichelangeloGallery.this,
				// MichelangeloModelviewer.class);
				File root = android.os.Environment
						.getExternalStorageDirectory();

				Intent intent = new Intent();
				intent.setAction(android.content.Intent.ACTION_VIEW);

				ArrayList<File> filePaths = com.michelangelo.MichelangeloCamera
						.getMediaFiles();
				File imgFile = filePaths.get(position);

				Log.d("imgFile: " + imgFile.getName());
				String vtkFilepath = imgFile.getName().substring(0,
						imgFile.getName().indexOf(".jpg")); // grab vtk file
															// name from
															// thumbnail name
				Log.d("vtkFile: " + vtkFilepath);

				File file = new File(root.getAbsolutePath()
						+ "/Pictures/Michelangelo/models/" + vtkFilepath); // construct
																			// vtk
																			// file
																			// path
				intent.setDataAndType(Uri.fromFile(file), "doc/*");
				// intent.setData(Uri.parse(vtkFilepath));
				startActivity(intent);

			}
		});

		// gridview.setSelector(R.color.transparent_blue);
		// gridview.setDrawSelectorOnTop(true);
		gridview.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE_MODAL);
		gridview.setMultiChoiceModeListener(new MultiChoiceModeListener() {
			private int numSelected = 0;
			private ArrayList<Integer> positions = new ArrayList<Integer>();
			private Intent shareModelsIntent;
			private ArrayList<Uri> uris = new ArrayList<Uri>();

			@Override
			public void onItemCheckedStateChanged(ActionMode mode,
					int position, long id, boolean checked) {
				// Here you can do something when items are
				// selected/de-selected,
				// such as update the title in the CABFile imgFile =
				// filePaths.get(pos);
				File root = android.os.Environment
						.getExternalStorageDirectory();
				File dir = new File(root.getAbsolutePath()
						+ "/Pictures/Michelangelo/models");
				ArrayList<File> filePaths = com.michelangelo.MichelangeloCamera
						.getMediaFiles();
				File imgFile = filePaths.get(position);
				String vtkFilepath = imgFile.getName().substring(0,
						imgFile.getName().indexOf(".jpg"));
				File file = new File(dir + vtkFilepath);
				Uri uri = Uri.fromFile(file);
				shareModelsIntent.removeExtra(Intent.EXTRA_STREAM);

				if (checked) {
					positions.add(new Integer(position));
					uris.add(uri);
				} else {
					positions.remove(new Integer(position));
					uris.remove(uri);
				}
				
				shareModelsIntent.putParcelableArrayListExtra(
						Intent.EXTRA_STREAM, uris);
				mode.setTitle(gridview.getCheckedItemCount() + " selected");
			}

			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				// Respond to clicks on the actions in the CAB
				boolean retBool = false;
				switch (item.getItemId()) {
				case R.id.action_share:
					retBool = true;
					mode.finish(); // Action picked, so close the CAB
					return true;
				case R.id.action_delete:
					// Create an instance of the dialog fragment and show it
					retBool = true;
					DialogFragment dialog = new ConfirmDeleteFragment();
					dialog.show(getFragmentManager(), "ConfirmDeleteFragment");
					mode.finish(); // Action picked, so close the CAB
					return true;
				default:
					return retBool;
				}
			}

			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				// Inflate the menu for the CAB
				MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.michelangelo_gallery, menu);
				// Get the menu item.
				MenuItem menuItem = menu.findItem(R.id.action_share);
				// Get the provider and hold onto it to set/change the share
				// intent.
				ShareActionProvider mShareActionProvider = (ShareActionProvider) menuItem
						.getActionProvider();
				// Attach an intent to this ShareActionProvider. You can update
				// this at any time,
				// like when the user selects a new piece of data they might
				// like to share.
				mShareActionProvider.setShareIntent(createShareIntent());
				return true;
			}

			private Intent createShareIntent() {
				Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
				ArrayList<Uri> uris = new ArrayList<Uri>();
				File root = android.os.Environment
						.getExternalStorageDirectory();
				File dir = new File(root.getAbsolutePath()
						+ "/Pictures/Michelangelo/models");
				shareIntent
						.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
				shareIntent.setType("text/plain");
				shareIntent
						.putExtra(Intent.EXTRA_SUBJECT, "3D Model(s) Shared");
				shareIntent.putExtra(Intent.EXTRA_TEXT,
						"Shared from Michelangelo's Gallery!");

				ArrayList<File> filePaths = com.michelangelo.MichelangeloCamera
						.getMediaFiles();
				for (int pos : positions) {
					File imgFile = filePaths.get(pos);
					String vtkFilepath = imgFile.getName().substring(0,
							imgFile.getName().indexOf(".jpg"));
					File file = new File(dir + vtkFilepath);
					Uri uri = Uri.fromFile(file);
					uris.add(uri);
				}
				shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,
						uris);
				shareModelsIntent = shareIntent;
				return shareIntent;
			}

			@Override
			public void onDestroyActionMode(ActionMode mode) {
				// Here you can make any necessary updates to the activity when
				// the CAB is removed. By default, selected items are
				// deselected/unchecked.
				positions.clear();
				uris.clear();
			}

			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				// Here you can perform updates to the CAB due to
				// an invalidate() request
				return false;
			}
		});
	}

	/**
	 * Set up the {@link android.app.ActionBar}.
	 */
	private void setupActionBar() {
		getActionBar().setDisplayHomeAsUpEnabled(true);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.michelangelo_gallery, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// This ID represents the Home or Up button. In the case of this
			// activity, the Up button is shown. Use NavUtils to allow users
			// to navigate up one level in the application structure. For
			// more details, see the Navigation pattern on Android Design:
			//
			// http://developer.android.com/design/patterns/navigation.html#up-vs-back
			//
			// NavUtils.navigateUpFromSameTask(this);
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private class ImageAdapter extends BaseAdapter {
		ArrayList<ImageView> thumbNails = new ArrayList<ImageView>();
		private Context mContext;

		public ImageAdapter(Context c) {
			mContext = c;
		}

		public int getCount() {
			return thumbs.size();
		}

		public Object getItem(int position) {
			return thumbNails.get(position);
		}

		public long getItemId(int position) {
			return 0;
		}

		// create a new ImageView for each item referenced by the Adapter
		@SuppressLint("NewApi")
		public View getView(int position, View convertView, ViewGroup parent) {
			ImageView imageView;
			CheckableLayout layout;

			if (convertView == null) { // if it's not recycled, initialize some
										// attributes
				imageView = new ImageView(MichelangeloGallery.this);
				imageView.setLayoutParams(new GridView.LayoutParams(320, 320));
				imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
				imageView.setCropToPadding(true);
				imageView.setPadding(8, 8, 8, 8);
				layout = new CheckableLayout(MichelangeloGallery.this);
				layout.setLayoutParams(new GridView.LayoutParams(
						GridView.LayoutParams.WRAP_CONTENT,
						GridView.LayoutParams.WRAP_CONTENT));
				layout.addView(imageView);
			} else {
				layout = (CheckableLayout) convertView;
				imageView = (ImageView) layout.getChildAt(0);
			}

			// imageView.setImageResource(mThumbIds[position]);

			imageView.setImageDrawable(thumbs.get(position));
			thumbNails.add(position, imageView);

			return layout;
		}

		// references to our images
		private Integer[] mThumbIds = { R.drawable.sample_2,
				R.drawable.sample_3, R.drawable.sample_4, R.drawable.sample_5,
				R.drawable.sample_6, R.drawable.sample_7, R.drawable.sample_0,
				R.drawable.sample_1, R.drawable.sample_2, R.drawable.sample_3,
				R.drawable.sample_4, R.drawable.sample_5, R.drawable.sample_6,
				R.drawable.sample_7, R.drawable.sample_0, R.drawable.sample_1,
				R.drawable.sample_2, R.drawable.sample_3, R.drawable.sample_4,
				R.drawable.sample_5, R.drawable.sample_6, R.drawable.sample_7 };
	}

	// The dialog fragment receives a reference to this Activity through the
	// Fragment.onAttach() callback, which it uses to call the following methods
	// defined by the NoticeDialogFragment.NoticeDialogListener interface
	@Override
	public void onConfirmDeletePositiveClick(DialogFragment dialog) {
		// User touched the dialog's positive button
		// deleteSelectedItems();
	}

	@Override
	public void onConfirmDeleteNegativeClick(DialogFragment dialog) {
		// User touched the dialog's negative button
	}
}
