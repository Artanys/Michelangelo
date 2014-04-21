package com.michelangelo;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.MotionEvent;

public class RenderingDialog extends DialogFragment{

	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle("Rendering");
		builder.setMessage("Creating Depth Map...");
		this.setCancelable(false);
        return builder.create();
	}	
}
