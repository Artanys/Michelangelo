package com.michelangelo;

import java.util.ArrayList;

import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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

public class MichelangeloGallery extends MichelangeloUI implements ConfirmDeleteFragment.ConfirmDeleteListener{

    public class CheckableLayout extends FrameLayout implements Checkable {
        private boolean mChecked;
 
        public CheckableLayout(Context context) {
            super(context);
        }
 
        @SuppressWarnings("deprecation")
        //@SuppressLint("NewApi")
		public void setChecked(boolean checked) {
            mChecked = checked;
            setBackgroundDrawable(checked ? getResources().getDrawable(
                    R.drawable.ic_action_accept) : null);
            if(checked){
            	this.animate().rotationX(15);
            } else {
            	this.animate().rotationX(0);
            }
        }
 
        public boolean isChecked() {
            return mChecked;
        }
 
        public void toggle() {
            setChecked(!mChecked);
        }
 
    }
	
	public void buttonClicked(View view) {
	    Intent intent = new Intent(this, MichelangeloGallery.class);
	    startActivity(intent);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.activity_michelangelo_gallery);
		super.onCreate(savedInstanceState);
		// Show the Up button in the action bar.
		setupActionBar();
		
		final GridView gridview = (GridView) findViewById(R.id.gridview);
	    gridview.setAdapter(new ImageAdapter(this));

	    gridview.setOnItemClickListener(new OnItemClickListener() {
	        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
	            Toast.makeText(MichelangeloGallery.this, "" + position, Toast.LENGTH_SHORT).show();
	            Intent intent = new Intent(MichelangeloGallery.this, MichelangeloModelviewer.class);
	            startActivity(intent);
	        }
	    });

		gridview.setSelector(R.drawable.ic_action_accept);
		gridview.setDrawSelectorOnTop(true);
		gridview.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE_MODAL);
		gridview.setMultiChoiceModeListener(new MultiChoiceModeListener() {
			private int numSelected = 0;
		
		    @Override
		    public void onItemCheckedStateChanged(ActionMode mode, int position,
		                                          long id, boolean checked) {
		        // Here you can do something when items are selected/de-selected,
		        // such as update the title in the CAB
		    	if ( checked ) {
		    		numSelected ++;
		    		//ImageView imageView = (ImageView) gridview.getAdapter().getItem(position);
		            
		    	}
		    	else {
		    		numSelected --;
		    	}
		    	mode.setTitle(numSelected + " selected");
		    }
		
		    @Override
		    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		        // Respond to clicks on the actions in the CAB
		        switch (item.getItemId()) {
		            case R.id.action_delete:
		    			// Create an instance of the dialog fragment and show it
		    	        DialogFragment dialog = new ConfirmDeleteFragment();
		    	        dialog.show(getFragmentManager(), "ConfirmDeleteFragment");
		    	        numSelected = 0;
		                mode.finish(); // Action picked, so close the CAB
		                return true;
		            case R.id.action_share:
		    	        numSelected = 0;
		                mode.finish(); // Action picked, so close the CAB
		                return true;
		            default:
		                return false;
		        }
		    }
		
		    @Override
		    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		        // Inflate the menu for the CAB
		        MenuInflater inflater = mode.getMenuInflater();
		        inflater.inflate(R.menu.michelangelo_gallery, menu);
		        // Get the menu item.
		        MenuItem menuItem = menu.findItem(R.id.action_share);
		        // Get the provider and hold onto it to set/change the share intent.
		        ShareActionProvider mShareActionProvider = (ShareActionProvider) menuItem.getActionProvider();
		        // Attach an intent to this ShareActionProvider.  You can update this at any time,
		        // like when the user selects a new piece of data they might like to share.
		        mShareActionProvider.setShareIntent(createShareIntent());
		        return true;
		    }
		    
		    private Intent createShareIntent() {
		        Intent shareIntent = new Intent(Intent.ACTION_SEND);
		        shareIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
		        shareIntent.setType("text/plain");
		        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "3D Model(s) Shared");
		        shareIntent.putExtra(Intent.EXTRA_TEXT, "Shared from Michelangelo's Gallery!");
		        return shareIntent;
		    } 
		
		    @Override
		    public void onDestroyActionMode(ActionMode mode) {
		        // Here you can make any necessary updates to the activity when
		        // the CAB is removed. By default, selected items are deselected/unchecked.
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
			//NavUtils.navigateUpFromSameTask(this);
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
	        return mThumbIds.length;
	    }

	    public Object getItem(int position) {
	        return thumbNails.get(position);
	    }

	    public long getItemId(int position) {
	        return 0;
	    }

	    // create a new ImageView for each item referenced by the Adapter
	    public View getView(int position, View convertView, ViewGroup parent) {
	        ImageView imageView;
	        CheckableLayout layout;
	        
	        if (convertView == null) {  // if it's not recycled, initialize some attributes
	            imageView = new ImageView(MichelangeloGallery.this);
	            imageView.setLayoutParams(new GridView.LayoutParams(320, 320));
	            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
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

	        imageView.setImageResource(mThumbIds[position]);
	        thumbNails.add(position, imageView);
	        
	        return layout;
	    }

	    // references to our images
	    private Integer[] mThumbIds = {
	            R.drawable.sample_2, R.drawable.sample_3,
	            R.drawable.sample_4, R.drawable.sample_5,
	            R.drawable.sample_6, R.drawable.sample_7,
	            R.drawable.sample_0, R.drawable.sample_1,
	            R.drawable.sample_2, R.drawable.sample_3,
	            R.drawable.sample_4, R.drawable.sample_5,
	            R.drawable.sample_6, R.drawable.sample_7,
	            R.drawable.sample_0, R.drawable.sample_1,
	            R.drawable.sample_2, R.drawable.sample_3,
	            R.drawable.sample_4, R.drawable.sample_5,
	            R.drawable.sample_6, R.drawable.sample_7
	    };
	}

	// The dialog fragment receives a reference to this Activity through the
    // Fragment.onAttach() callback, which it uses to call the following methods
    // defined by the NoticeDialogFragment.NoticeDialogListener interface
    @Override
    public void onConfirmDeletePositiveClick(DialogFragment dialog) {
        // User touched the dialog's positive button
        //deleteSelectedItems();
    }

    @Override
    public void onConfirmDeleteNegativeClick(DialogFragment dialog) {
        // User touched the dialog's negative button        
    }
}
