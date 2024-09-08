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

public class ControllerOptionsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Requesting no title feature for this activity's window
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        final String[] items = new String[] {
            "Nintendo Switch Controller Layout",
            "XBOX Controller Layout",
            "Swap Left Analog / DPAD [ON]",
            "Swap Left Analog / DPAD [OFF]",
            "Invert Left Analog Stick [ON]",
            "Invert Left Analog Stick [OFF]",
            "Invert Right Analog Stick [ON]",
            "Invert Right Analog Stick [OFF]"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Controller Options");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String command = getCommand(which);
                executeShellCommand(command);
                // Show a toast message confirming the user's selection
                Toast.makeText(ControllerOptionsActivity.this, "Selected: " + items[which], Toast.LENGTH_LONG).show();
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
            case 0: return "/system/bin/setabxyvalue_default.sh";
            case 1: return "/system/bin/setabxyvalue_swapped.sh";
            case 2: return "/system/bin/setdpadanalogtoggle_on.sh";
            case 3: return "/system/bin/setdpadanalogtoggle_off.sh";
            case 4: return "/system/bin/setanalogaxisvalue_swapped.sh";
            case 5: return "/system/bin/setanalogaxisvalue_default.sh";
            case 6: return "/system/bin/setrightanalogaxisvalue_swapped.sh";
            case 7: return "/system/bin/setrightanalogaxisvalue_default.sh";
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
