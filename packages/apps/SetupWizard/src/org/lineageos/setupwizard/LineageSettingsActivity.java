package org.lineageos.setupwizard;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.setupcompat.util.WizardManagerHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class LineageSettingsActivity extends BaseSetupWizardActivity {

    private TextView outputTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setup_lineage_settings);

        // Apply the enter animation when the activity is created
        overridePendingTransition(R.anim.translucent_enter, R.anim.translucent_exit);

        outputTextView = findViewById(R.id.script_output_text_view);
        new ExecuteShellCommand().execute("/system/bin/setup.sh");
    }

    private class ExecuteShellCommand extends AsyncTask<String, String, Void> {
        @Override
        protected Void doInBackground(String... scripts) {
            try {
                // Using 'su' to execute commands as root
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", scripts[0]});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    publishProgress(line + "\n");
                }
                reader.close();
                process.waitFor();
            } catch (Exception e) {
                publishProgress("Error executing script: " + e.getMessage());
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            outputTextView.append(values[0]);
            scrollToBottom();  // Scroll to the bottom after each update
        }

        @Override
        protected void onPostExecute(Void result) {
            outputTextView.append("Script execution completed.\n");
            outputTextView.postDelayed(LineageSettingsActivity.this::proceedToNextActivity, 5000); // 5 seconds delay
        }
    }

    private void scrollToBottom() {
        final ScrollView scrollView = findViewById(R.id.scrollView);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void proceedToNextActivity() {
        Intent intent = WizardManagerHelper.getNextIntent(getIntent(), Activity.RESULT_OK);
        startActivity(intent);
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        // Apply the exit animation when the activity finishes
        overridePendingTransition(R.anim.translucent_enter, R.anim.translucent_exit);
    }

    @Override
    public void onBackPressed() {
        // Prevent back navigation
    }
}
