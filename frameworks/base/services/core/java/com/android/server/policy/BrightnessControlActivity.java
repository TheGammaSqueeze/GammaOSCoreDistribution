package com.android.server.policy;

import android.app.Activity;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.LinearLayout;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Window;
import android.widget.Toast;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

public class BrightnessControlActivity extends Activity {
    private static final int SEEKBAR_WIDTH = 630;  // Width in pixels
    private static final int SEEKBAR_HEIGHT = 100;  // Height in pixels

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE); // Hide the title bar
        getWindow().setFlags(LayoutParams.FLAG_FULLSCREEN, LayoutParams.FLAG_FULLSCREEN); // Set full-screen flags

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(254); // Max set as 254 to allow for 1-255 range in brightness

        // Set fixed width and height for the SeekBar
        LinearLayout.LayoutParams seekBarParams = new LinearLayout.LayoutParams(
                SEEKBAR_WIDTH,  // Width of the SeekBar
                SEEKBAR_HEIGHT  // Height of the SeekBar
        );
        seekBarParams.gravity = Gravity.CENTER;
        seekBar.setLayoutParams(seekBarParams);

        layout.addView(seekBar);
        setContentView(layout);

        try {
            int currentBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            seekBar.setProgress(currentBrightness - 1); // Compensate for zero-based index of progress
        } catch (Settings.SettingNotFoundException e) {
            Toast.makeText(this, "Failed to get brightness", Toast.LENGTH_SHORT).show();
        }

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int brightnessValue = Math.max(1, progress + 1); // Ensure brightness never goes below 1
                    LayoutParams layoutParams = getWindow().getAttributes();
                    layoutParams.screenBrightness = brightnessValue / 255.0f; // Set the brightness of this window
                    getWindow().setAttributes(layoutParams);

                    Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, brightnessValue);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish(); // Ensure the activity finishes on back press
    }
}
