package com.kris.test.storageoptions;

import java.io.File;

import android.os.Bundle;
import android.os.Environment;
import android.widget.TextView;
import android.app.Activity;
import com.kris.test.R;

public class MainActivity extends Activity {
	
	@Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_table);
		
        StorageOptions.determineStorageOptions();
        
        File sdc = Environment.getExternalStorageDirectory();
		String dir = sdc.getAbsolutePath();
        
        TextView text = (TextView) findViewById(R.id.something);
        text.setText(String.valueOf(StorageOptions.paths[0])+"("+StorageOptions.labels[0]+") \n"+dir);
    }
}
