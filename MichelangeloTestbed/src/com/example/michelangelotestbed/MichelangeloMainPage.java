package com.example.michelangelotestbed;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;

public class MichelangeloMainPage extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_michelangelo_main_page);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.michelangelo_main_page, menu);
        return true;
    }
    
}
