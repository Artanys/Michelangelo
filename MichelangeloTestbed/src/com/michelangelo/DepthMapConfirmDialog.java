package com.michelangelo;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

public class DepthMapConfirmDialog extends DialogFragment{
	
	public Bitmap bmp;
	private boolean mRemoved;
	private int mBackStackId;

	
/*
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            mListener = (NoticeDialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement NoticeDialogListener");
        }
    }
*/
	
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());       
        LayoutInflater inflater = getActivity().getLayoutInflater();
        ViewGroup vg = (ViewGroup)inflater.inflate(R.layout.depthmap_confirm, null);
        ImageView image = (ImageView) vg.findViewById(R.id.depth_map_image);
        image.setImageBitmap(bmp);
        
        builder.setPositiveButton(R.string.Accept, new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// TODO Auto-generated method stub
				//mListener.onDialogPositiveClick(DepthMapConfirmDialog.this);
			}
		});
        
        
        builder.setNegativeButton(R.string.Reject, new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// TODO Auto-generated method stub
				//mListener.onDialogPositiveClick(DepthMapConfirmDialog.this);
			}
		});
        
        builder.setView(vg);
		return builder.create();
	}	
}
