package com.android.traceur;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.preference.PreferenceManager;
import com.android.traceur.Receiver;

public class TraceurBackupAgent extends BackupAgentHelper {

    private static final String PREFS_BACKUP_KEY = "traceur_backup_prefs";

    @Override
    public void onCreate() {
        SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(
                this, PreferenceManager.getDefaultSharedPreferencesName(this));
        addHelper(PREFS_BACKUP_KEY, helper);
    }

    @Override
    public void onRestoreFinished() {
        Receiver.updateQuickSettings(this);
    }
}
