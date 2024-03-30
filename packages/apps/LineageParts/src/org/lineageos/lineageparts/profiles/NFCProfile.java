/*
 * SPDX-FileCopyrightText: 2012 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.profiles;

import java.util.UUID;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.widget.Toast;

import lineageos.app.Profile;
import lineageos.app.ProfileManager;
import lineageos.providers.LineageSettings;

import org.lineageos.lineageparts.R;

/**
 * This activity handles NDEF_DISCOVERED intents with the "lineage/profile" mime type.
 * Tags should be encoded with the 16-byte UUID of the profile to be activated.
 * Tapping a tag while that profile is already active will select the previously
 * active profile.
 */
public class NFCProfile extends Activity {

    private static final String PREFS_NAME = "NFCProfile";

    private static final String PREFS_PREVIOUS_PROFILE = "previous-profile";

    static final String PROFILE_MIME_TYPE = "lineage/profile";

    private ProfileManager mProfileManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mProfileManager = ProfileManager.getInstance(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = getIntent();
        String action = intent.getAction();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES,
                    Parcelable.class);
            if (rawMsgs != null) {
                NdefMessage[] msgs = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                    for (NdefRecord record : msgs[i].getRecords()) {
                        String type = new String(record.getType());
                        byte[] payload = record.getPayload();
                        if (PROFILE_MIME_TYPE.equals(type) && payload.length == 16) {
                            handleProfileMimeType(payload);
                        }
                    }
                }
            }
        }
        finish();
    }

    private void handleProfileMimeType(byte[] payload) {
        UUID profileUuid = NFCProfileUtils.toUUID(payload);

        boolean enabled = LineageSettings.System.getInt(getContentResolver(),
                LineageSettings.System.SYSTEM_PROFILES_ENABLED, 1) == 1;

        if (enabled) {
            // Only do NFC profile changing if System Profile support is enabled
            Profile currentProfile = mProfileManager.getActiveProfile();
            Profile targetProfile = mProfileManager.getProfile(profileUuid);

            if (targetProfile == null) {
                // show profile selection for unknown tag
                Intent i = new Intent(this, NFCProfileSelect.class);
                i.putExtra(NFCProfileSelect.EXTRA_PROFILE_UUID, profileUuid.toString());
                i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                this.startActivity(i);
            } else {
                // switch to profile
                if (currentProfile == null || !currentProfile.getUuid().equals(profileUuid)) {
                    saveCurrentProfile();
                    switchTo(profileUuid);
                } else {
                    Profile lastProfile = getPreviouslySelectedProfile();
                    if (lastProfile != null) {
                        switchTo(lastProfile.getUuid());
                        clearPreviouslySelectedProfile();
                    }
                }
            }
        }
    }

    private void switchTo(UUID uuid) {
        Profile p = mProfileManager.getProfile(uuid);
        if (p != null) {
            mProfileManager.setActiveProfile(uuid);

            Toast.makeText(
                    this,
                    getString(R.string.profile_selected, p.getName()),
                    Toast.LENGTH_LONG).show();
            NFCProfileUtils.vibrate(this);
        }
    }

    private Profile getPreviouslySelectedProfile() {
        Profile previous = null;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
        String uuid = prefs.getString(PREFS_PREVIOUS_PROFILE, null);
        if (uuid != null) {
            previous = mProfileManager.getProfile(UUID.fromString(uuid));
        }
        return previous;
    }

    private void clearPreviouslySelectedProfile() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, 0).edit();
        editor.remove(PREFS_PREVIOUS_PROFILE);
        editor.commit();
    }

    private void saveCurrentProfile() {
        Profile currentProfile = mProfileManager.getActiveProfile();
        if (currentProfile != null) {
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, 0).edit();
            editor.putString(PREFS_PREVIOUS_PROFILE, currentProfile.getUuid().toString());
            editor.commit();
        }
    }
}
