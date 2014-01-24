package com.michelangelo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

public class ModelviewerSettingsFragment extends DialogFragment {
	
    // Use this instance of the interface to deliver action events
	ModelviewerSettingsListener mListener;
    
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {    	
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();
        final View view = inflater.inflate(R.layout.fragment_modelviewer_settings, null);
    	
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        
        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(view)
        	   .setTitle(R.string.title_dialog_modelviewer_settings)
               .setPositiveButton(R.string.action_ok, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                	   // Send the positive button event back to the host activity
                       mListener.onModelviewerSettingsPositiveClick(ModelviewerSettingsFragment.this);
                   }
               })
               .setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       // Send the positive button event back to the host activity
                       mListener.onModelviewerSettingsNegativeClick(ModelviewerSettingsFragment.this);
                   }
               });
        
        // Create the AlertDialog object and return it
        return builder.create();
    }
    
    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface ModelviewerSettingsListener {
        public void onModelviewerSettingsPositiveClick(DialogFragment dialog);
        public void onModelviewerSettingsNegativeClick(DialogFragment dialog);
    }
    
    // Override the Fragment.onAttach() method to instantiate the ModelviewerSettingsListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the ModelviewerSettingsListener so we can send events to the host
            mListener = (ModelviewerSettingsListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement ModelviewerSettingsListener");
        }
    }
}