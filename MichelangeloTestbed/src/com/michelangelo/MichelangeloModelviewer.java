package com.michelangelo;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ShareActionProvider;

public class MichelangeloModelviewer extends MichelangeloUI implements ModelviewerSettingsFragment.ModelviewerSettingsListener,
	ConfirmDeleteFragment.ConfirmDeleteListener {

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		setContentView(R.layout.activity_michelangelo_modelviewer);		
		super.onCreate(savedInstanceState);
   	
		setupActionBar();
        getActionBar().setDisplayShowHomeEnabled(true);
        getActionBar().setHomeButtonEnabled(true);

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
		getMenuInflater().inflate(R.menu.michelangelo_modelviewer, menu);
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
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "3D Model Shared");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Shared from Michelangelo's Modelviewer!");
        return shareIntent;
    } 
	
	private class DrawerItemClickListener implements ListView.OnItemClickListener {
	    @Override
	    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
	        selectItem(position);
	    }
	}
	
	@Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }
	
	@Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle.onOptionsItemSelected(item)) {
          return true;
        }
        // Handle your other action bar items...
        switch (item.getItemId()) {
			case R.id.action_delete:
				// Create an instance of the dialog fragment and show it
		        DialogFragment dialog = new ConfirmDeleteFragment();
		        dialog.show(getFragmentManager(), "ConfirmDeleteFragment");
				return true;
			case R.id.action_share:
                //shareDisplayedModel();
                return true;
		}

        return super.onOptionsItemSelected(item);
    }

	/** Performs navigation when a drawer item is clicked */
	private void selectItem(int position) {
		Intent intent = null;
		
		switch ( position ) {
		case 0:
			// Create an instance of the dialog fragment and show it
	        DialogFragment dialog = new ModelviewerSettingsFragment();
	        dialog.show(getFragmentManager(), "ModelviewerSettingsFragment");
			break;
		case 1:
			intent = new Intent ( this, MichelangeloCamera.class );
			break;
		case 2:
			intent = new Intent ( this, MichelangeloGallery.class );
			break;
		case 3:
			intent = new Intent ( this, MichelangeloHelp.class );
			break;
		case 4:
			intent = new Intent ( this, MichelangeloAbout.class );
			break;
		}

	    // Highlight the selected item, update the title, and close the drawer
	    //mDrawerList.setItemChecked(position, true);
	    mDrawerLayout.closeDrawer(mDrawerList);
		
		if ( position != 0 ) startActivity ( intent );
	}
	
	// The dialog fragment receives a reference to this Activity through the
    // Fragment.onAttach() callback, which it uses to call the following methods
    // defined by the NoticeDialogFragment.NoticeDialogListener interface
	@Override
    public void onModelviewerSettingsPositiveClick(DialogFragment dialog) {
        // User touched the dialog's positive button
    }

    @Override
    public void onModelviewerSettingsNegativeClick(DialogFragment dialog) {
        // User touched the dialog's negative button        
    }
    
    @Override
    public void onConfirmDeletePositiveClick(DialogFragment dialog) {
        // User touched the dialog's positive button
    }

    @Override
    public void onConfirmDeleteNegativeClick(DialogFragment dialog) {
        // User touched the dialog's negative button        
    }
}
