package com.android.server.policy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Window;
import android.widget.Toast;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import android.util.Log;

public class PerformanceOptionsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Requesting no title feature for this activity's window
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        final String[] items = new String[] {
            "Max Performance Mode",
            "Normal Performance Mode",
            "Power Saver Mode"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Performance Mode");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String command = getCommand(which);
                executeShellCommand(command);
                // Show a toast message confirming the user's selection
                Toast.makeText(PerformanceOptionsActivity.this, "Selected: " + items[which], Toast.LENGTH_LONG).show();
                dialog.dismiss();
                finish();  // Finish the activity after the selection is made
            }
        });

        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                finish();  // Finish the activity if dialog is cancelled (e.g., back button is pressed)
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    private String getCommand(int mode) {
        switch(mode) {
            case 0: return "/system/bin/setclockvalue_max.sh";
            case 1: return "/system/bin/setclockvalue_stock.sh";
            case 2: return "/system/bin/setclockvalue_powersave.sh";
            default: return "";
        }
    }

    private void executeShellCommand(String command) {
        try {
            // Executes the command
            Process process = Runtime.getRuntime().exec(command);
            // Wait for the command to complete
            process.waitFor();

            // Optionally handle the input and error streams
            InputStream inputStream = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d("Output", line);
            }
            inputStream.close();
            reader.close();

            // Check for errors
            if (process.exitValue() != 0) {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder error = new StringBuilder();
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    error.append(errorLine).append("\n");
                }
                Log.e("ShellCommandError", "Error executing shell command: " + error.toString());
                errorReader.close();
            }
        } catch (IOException e) {
            Log.e("ShellCommandError", "Error executing shell command", e);
        } catch (InterruptedException e) {
            Log.e("ShellCommandError", "Interrupted while waiting for shell command to finish", e);
            Thread.currentThread().interrupt();  // Restore the interrupted status
        }
    }
}
