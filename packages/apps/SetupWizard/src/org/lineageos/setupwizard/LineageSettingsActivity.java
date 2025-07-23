package org.lineageos.setupwizard;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemProperties;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.setupcompat.util.WizardManagerHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class LineageSettingsActivity extends BaseSetupWizardActivity {

    private StickTestView leftStickView, rightStickView;
    private ScrollView controlScroll, scrollView;
    private TextView outputTextView, headingTextView;
    private Button continueButton;

    private final int[] sensGroup = {
        R.id.btn_sens_0, R.id.btn_sens_10, R.id.btn_sens_25, R.id.btn_sens_50
    };
    private final int[] deadGroup = {
        R.id.btn_dead_0, R.id.btn_dead_5, R.id.btn_dead_10,
        R.id.btn_dead_15, R.id.btn_dead_20
    };
    private final int[] invLGroup = {
        R.id.btn_invert_left_off, R.id.btn_invert_left_on
    };
    private final int[] invRGroup = {
        R.id.btn_invert_right_off, R.id.btn_invert_right_on
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setup_lineage_settings);

        leftStickView   = findViewById(R.id.left_stick_view);
        rightStickView  = findViewById(R.id.right_stick_view);
        controlScroll   = findViewById(R.id.control_scroll);
        scrollView      = findViewById(R.id.scrollView);
        outputTextView  = findViewById(R.id.script_output_text_view);
        continueButton  = findViewById(R.id.continue_button);
        headingTextView = findViewById(R.id.headingTextView);

        // initial heading
        headingTextView.setText("Configure Analog Sticks");

        overridePendingTransition(R.anim.translucent_enter, R.anim.translucent_exit);

        // RESET
        findViewById(R.id.btn_reset).setOnClickListener(v -> {
            SystemProperties.set("persist.gammaos.analogdeadzone","0");
            SystemProperties.set("persist.gammaos.analogsensitivity","0");
            SystemProperties.set("persist.gammaos.calibrationmode","0");
            SystemProperties.set("persist.gammaos.leftstickinvert","off");
            SystemProperties.set("persist.gammaos.rightstickinvert","off");

            highlightSelection(sensGroup,  R.id.btn_sens_0);
            highlightSelection(deadGroup,  R.id.btn_dead_0);
            highlightSelection(invLGroup,  R.id.btn_invert_left_off);
            highlightSelection(invRGroup,  R.id.btn_invert_right_off);
        });

        // CENTRE with Toast
        findViewById(R.id.btn_centre).setOnClickListener(v -> {
            SystemProperties.set("persist.gammaos.calibrationmode","1");
            Toast.makeText(this, "Sticks are now centered", Toast.LENGTH_SHORT).show();
        });

        // SENSITIVITY
        bindSetting(R.id.btn_sens_0,
            "persist.gammaos.analogsensitivity","0",  sensGroup);
        bindSetting(R.id.btn_sens_10,
            "persist.gammaos.analogsensitivity","1",  sensGroup);
        bindSetting(R.id.btn_sens_25,
            "persist.gammaos.analogsensitivity","2",  sensGroup);
        bindSetting(R.id.btn_sens_50,
            "persist.gammaos.analogsensitivity","3",  sensGroup);

        // DEADZONE
        bindSetting(R.id.btn_dead_0,
            "persist.gammaos.analogdeadzone","0",     deadGroup);
        bindSetting(R.id.btn_dead_5,
            "persist.gammaos.analogdeadzone","1",     deadGroup);
        bindSetting(R.id.btn_dead_10,
            "persist.gammaos.analogdeadzone","2",     deadGroup);
        bindSetting(R.id.btn_dead_15,
            "persist.gammaos.analogdeadzone","3",     deadGroup);
        bindSetting(R.id.btn_dead_20,
            "persist.gammaos.analogdeadzone","4",     deadGroup);

        // INVERT LEFT
        bindSetting(R.id.btn_invert_left_off,
            "persist.gammaos.leftstickinvert","off",  invLGroup);
        bindSetting(R.id.btn_invert_left_on,
            "persist.gammaos.leftstickinvert","on",   invLGroup);

        // INVERT RIGHT
        bindSetting(R.id.btn_invert_right_off,
            "persist.gammaos.rightstickinvert","off", invRGroup);
        bindSetting(R.id.btn_invert_right_on,
            "persist.gammaos.rightstickinvert","on",  invRGroup);

        // CONTINUE: update heading, hide controls, run script
        continueButton.setOnClickListener(v -> {
            headingTextView.setText("Configuring GammaOS Core...");
            controlScroll.setVisibility(View.GONE);
            continueButton.setVisibility(View.GONE);
            scrollView.setVisibility(View.VISIBLE);
            new ExecuteShellCommand().execute("/system/bin/setup.sh");
        });
    }

    private void bindSetting(int btnId, String key, String value, int[] group) {
        Button b = findViewById(btnId);
        b.setOnClickListener(v -> {
            SystemProperties.set(key, value);
            highlightSelection(group, btnId);
        });
    }

    private void highlightSelection(int[] group, int selId) {
        for (int id : group) {
            findViewById(id).setAlpha(id == selId ? 1f : 0.5f);
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent ev) {
        if ((ev.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            && ev.getAction() == MotionEvent.ACTION_MOVE) {

            leftStickView.updateAxes(
                ev.getAxisValue(MotionEvent.AXIS_X),
                ev.getAxisValue(MotionEvent.AXIS_Y)
            );
            rightStickView.updateAxes(
                ev.getAxisValue(MotionEvent.AXIS_Z),
                ev.getAxisValue(MotionEvent.AXIS_RZ)
            );
            return true;
        }
        return super.onGenericMotionEvent(ev);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev) {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL ||
            keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR) {
            return true;
        }
        return super.onKeyDown(keyCode, ev);
    }

    private class ExecuteShellCommand extends AsyncTask<String,String,Void>{
        @Override protected Void doInBackground(String... s){
            try {
                Process p = Runtime.getRuntime()
                  .exec(new String[]{"su","-c",s[0]});
                BufferedReader r = new BufferedReader(
                  new InputStreamReader(p.getInputStream()));
                String line;
                while((line=r.readLine())!=null) publishProgress(line+"\n");
                r.close(); p.waitFor();
            } catch(Exception e){
                publishProgress("Error: "+e.getMessage()+"\n");
            }
            return null;
        }
        @Override protected void onProgressUpdate(String... vals){
            outputTextView.append(vals[0]);
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        }
        @Override protected void onPostExecute(Void v){
            outputTextView.append("Script completed.\n");
            outputTextView.postDelayed(() -> {
                Intent intent = WizardManagerHelper
                  .getNextIntent(getIntent(), Activity.RESULT_OK);
                startActivity(intent);
                finish();
            }, 5000);
        }
    }

    @Override public void finish(){
        super.finish();
        overridePendingTransition(R.anim.translucent_enter, R.anim.translucent_exit);
    }

    @Override public void onBackPressed(){
        // disabled
    }
}
