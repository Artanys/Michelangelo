package com.michelangelo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class CaptureSettingsFragment extends DialogFragment {
	
    // Use this instance of the interface to deliver action events
	CaptureSettingsListener mListener;
    
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {    	
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();
        final View view = inflater.inflate(R.layout.fragment_capture_settings, null);
    	
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        
        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(view)
        	   .setTitle(R.string.title_dialog_capture_settings)
               .setPositiveButton(R.string.action_ok, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                	   // Send the positive button event back to the host activity
                	   int numImages = ((SeekBar) view.findViewById(R.id.seekBar)).getProgress();
                	   numImages += 6;
                       mListener.onCaptureSettingsPositiveClick(CaptureSettingsFragment.this, numImages);
                   }
               })
               .setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       // Send the positive button event back to the host activity
                       mListener.onCaptureSettingsNegativeClick(CaptureSettingsFragment.this);
                   }
               });
        
        // Create a listener for the SeekBar
        SeekBar seekBar = (SeekBar) view.findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
        	
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            	// User changed # of images to capture
                if (fromUser) {                    
                	// Get the text to update with the user's new value
                	TextView numText = (TextView) view.findViewById(R.id.textView2);
                	
                	// Add 6 to progress bar to make range from 6 to 10
                	progress += 6;
                	
                	// Update text
                	numText.setText(Integer.toString(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
        
        // Update the seekbar with the last value

    	int prevValue = MichelangeloCamera.NUM_IMAGES;
        seekBar.setProgress(prevValue - 6);
    	((TextView) view.findViewById(R.id.textView2)).setText(Integer.toString(prevValue));

        
        // Create the AlertDialog object and return it
        return builder.create();
    }
    
    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface CaptureSettingsListener {
        public void onCaptureSettingsPositiveClick(DialogFragment dialog, int numImages);
        public void onCaptureSettingsNegativeClick(DialogFragment dialog);
    }
    
    // Override the Fragment.onAttach() method to instantiate the CaptureSettingsListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the CaptureSettingsListener so we can send events to the host
            mListener = (CaptureSettingsListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement CaptureSettingsListener");
        }
    }
}