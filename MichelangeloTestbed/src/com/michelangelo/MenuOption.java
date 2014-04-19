package com.michelangelo;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;

public abstract class MenuOption {
	public void executeAction(Context currentContext, Class<?> targetClass) {
		currentContext.startActivity (new Intent ( currentContext, targetClass ));
	}
	public abstract void executeAction(Context currentContext);
	
	/* The following classes are provided for each view. Override executeAction
	 */
	
	public class AboutOption extends MenuOption {

		public void executeAction(Context currentContext) {
			MenuOption.this.executeAction(currentContext, MichelangeloAbout.class);
		}
	}
	
	public class CameraOption extends MenuOption {
		public void executeAction(Context currentContext) {
			MenuOption.this.executeAction(currentContext, MichelangeloCamera.class);
		}
	}
	
	public class GalleryOption extends MenuOption {
		public void executeAction(Context currentContext) {
			MenuOption.this.executeAction(currentContext, MichelangeloGallery.class);
		}
	}
	public class ModelviewerOption extends MenuOption {
		public void executeAction(Context currentContext) {
			MenuOption.this.executeAction(currentContext, MichelangeloModelviewer.class);
		}
	}
	
	public class CaptureSettingsOption extends MenuOption {
		public void executeAction(Context currentContext) {
	        DialogFragment dialog = new CaptureSettingsFragment();
	        dialog.show(((Activity) currentContext).getFragmentManager(), "CaptureSettingsFragment");
		}
	}
}
