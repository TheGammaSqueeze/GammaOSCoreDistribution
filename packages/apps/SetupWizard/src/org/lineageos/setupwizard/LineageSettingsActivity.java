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

        outputTextView = findViewById(R.id.script_output_text_view);
        new ExecuteShellCommand().execute("for i in $(seq 1 10); do echo $i; sleep 1; done; whoami; cat /system/bin/customization.sh; mkdir /data/test; touch /data/test/ididit");
    }

    private class ExecuteShellCommand extends AsyncTask<String, String, Void> {
        @Override
        protected Void doInBackground(String... scripts) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", scripts[0]});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    publishProgress(line + "\n");
                }
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
	    // Correctly reference the outer class's method
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
    public void onBackPressed() {
        // Prevent back navigation
    }
}
