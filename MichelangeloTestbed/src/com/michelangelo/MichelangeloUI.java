package com.michelangelo;

//import com.michelangelo.MichelangeloCamera.DrawerItemClickListener;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

public class MichelangeloUI extends Activity  implements CaptureSettingsFragment.CaptureSettingsListener {

    protected String[] menuOptions;
    protected DrawerLayout mDrawerLayout;
    protected ListView mDrawerList;
    protected ActionBarDrawerToggle mDrawerToggle;
    //protected boolean isMain;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		menuOptions = getResources().getStringArray(R.array.menu_options_camera);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);
        
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer icon to replace 'Up' caret */
                R.string.action_open_drawer,  /* "open drawer" description */
                R.string.action_close_drawer  /* "close drawer" description */
                );
             
        mDrawerToggle.setDrawerIndicatorEnabled(isTaskRoot());
        
        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);
		//setContentView(R.layout.activity_michelangelo_ui);

        mDrawerList.setAdapter(new ArrayAdapter<String>(this,
                R.layout.drawer_list_item, menuOptions));
        
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.michelangelo_ui, menu);
		return true;
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

        return super.onOptionsItemSelected(item);
    }

	/** Performs navigation when a drawer item is clicked */
	private void selectItem(int position) {
		Intent intent = null;
		
		switch ( position ) {
		case 0:
			// Create an instance of the dialog fragment and show it
	        DialogFragment dialog = new CaptureSettingsFragment();
	        dialog.show(getFragmentManager(), "CaptureSettingsFragment");
			break;
		case 1:
			intent = new Intent ( this, MichelangeloGallery.class );
			break;
		case 2:
			intent = new Intent ( this, MichelangeloHelp.class );
			break;
		case 3:
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
    public void onCaptureSettingsPositiveClick(DialogFragment dialog, int numImages) {
        // User touched the dialog's positive button
    	
    	if(MichelangeloCamera.NUM_IMAGES != numImages) {
    		// Update # of photos used to create model, delete previous photos/depth maps, start over    		
    		MichelangeloCamera.NUM_IMAGES = numImages;
    		
    	}
    }

    @Override
    public void onCaptureSettingsNegativeClick(DialogFragment dialog) {
        // User touched the dialog's negative button
    	// User cancelled the dialog, don't update/start over
        
    }

}
