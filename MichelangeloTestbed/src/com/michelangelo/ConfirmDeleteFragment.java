package com.michelangelo;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

public class ConfirmDeleteFragment extends DialogFragment {
	
    // Use this instance of the interface to deliver action events
	ConfirmDeleteListener mListener;
	static ArrayList<Integer> positions;
	
	static ConfirmDeleteFragment newInstance(ArrayList<Integer> selected) {
		ConfirmDeleteFragment f = new ConfirmDeleteFragment();
		positions = (ArrayList<Integer>)selected.clone();

	    // Supply num input as an argument.
	    Bundle args = new Bundle();
	    args.putIntegerArrayList("selected", selected);
	    f.setArguments(args);

	    return f;
	}
	
	public class ConfirmDeleteClickListener implements DialogInterface.OnClickListener {
		private ArrayList<Integer> selected;
		
		public ConfirmDeleteClickListener (ArrayList<Integer> positions){
			selected = positions;
		}
		
        public void onClick(DialogInterface dialog, int id) {
     	   // Send the positive button event back to the host activity
            mListener.onConfirmDeletePositiveClick(ConfirmDeleteFragment.this, selected);
        }
	}
	
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        
        ConfirmDeleteClickListener cl = new ConfirmDeleteClickListener(positions);
        
        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setTitle(R.string.title_dialog_confirm_delete)
               .setPositiveButton(R.string.action_ok, cl)
               .setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       // Send the positive button event back to the host activity
                       mListener.onConfirmDeleteNegativeClick(ConfirmDeleteFragment.this);
                   }
               });
        
        // Change text based on parent activity
        if ( getActivity() instanceof MichelangeloGallery ) {
        	builder.setMessage(R.string.confirm_delete_selected);
        }
        else if ( getActivity() instanceof MichelangeloModelviewer ) {
        	builder.setMessage(R.string.confirm_delete_model);
        }
        
        // Create the AlertDialog object and return it
        return builder.create();
    }
    
    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface ConfirmDeleteListener {
        public void onConfirmDeletePositiveClick(DialogFragment dialog, ArrayList<Integer> selected);
        public void onConfirmDeleteNegativeClick(DialogFragment dialog);
    }
    
    // Override the Fragment.onAttach() method to instantiate the ConfirmDeleteListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the ConfirmDeleteListener so we can send events to the host
            mListener = (ConfirmDeleteListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement ConfirmDeleteListener");
        }
    }
}