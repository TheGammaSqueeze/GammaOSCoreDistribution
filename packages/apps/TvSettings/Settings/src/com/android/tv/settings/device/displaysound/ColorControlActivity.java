package com.android.tv.settings.device.displaysound;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;
import android.provider.Settings;
import com.android.tv.settings.R;
import android.content.Intent;

public class ColorControlActivity extends Activity {

    private static final int SEEKBAR_WIDTH = 630;  // Width in pixels
    private static final int SEEKBAR_HEIGHT = 100; // Height in pixels
    private static final float MIN_SATURATION = 0.0f;
    private static final float MAX_SATURATION = 2.0f;
    private static final float DEFAULT_SATURATION = 1.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);  // Hide the title bar
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(20);  // Maps to 0.0 to 2.0 range for color saturation
        seekBar.setLayoutParams(new LinearLayout.LayoutParams(SEEKBAR_WIDTH, SEEKBAR_HEIGHT, Gravity.CENTER));
        layout.addView(seekBar);
        setContentView(layout);

        // Set the current saturation level as the default progress
        seekBar.setProgress((int) ((DEFAULT_SATURATION - MIN_SATURATION) * 10));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float saturationLevel = MIN_SATURATION + (progress / 10.0f);
                    setColorSaturation(saturationLevel);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    // Apply color saturation level by sending the appropriate command
    private void setColorSaturation(float saturationLevel) {
        try {
            String command = String.format("service call SurfaceFlinger 1022 f %f", saturationLevel);
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to set saturation level", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();  // Close the popup on back press
    }
}
