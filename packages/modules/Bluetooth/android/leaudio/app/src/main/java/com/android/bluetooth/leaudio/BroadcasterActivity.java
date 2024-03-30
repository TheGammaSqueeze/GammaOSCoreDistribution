/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.bluetooth.leaudio;

import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.android.bluetooth.leaudio.R;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class BroadcasterActivity extends AppCompatActivity {
    private BroadcasterViewModel mViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.broadcaster_activity);

        FloatingActionButton fab = findViewById(R.id.broadcast_fab);
        fab.setOnClickListener(view -> {
            if (mViewModel.getBroadcastCount() < mViewModel.getMaximumNumberOfBroadcast()) {
                // Start Dialog with the broadcast input details
                AlertDialog.Builder alert = new AlertDialog.Builder(this);
                LayoutInflater inflater = getLayoutInflater();
                alert.setTitle("Add the Broadcast:");

                View alertView = inflater.inflate(R.layout.broadcaster_add_broadcast_dialog, null);
                final EditText code_input_text = alertView.findViewById(R.id.broadcast_code_input);
                final EditText program_info = alertView.findViewById(R.id.broadcast_program_info_input);
                final NumberPicker contextPicker = alertView.findViewById(R.id.context_picker);

                // Add context type selector
                contextPicker.setMinValue(1);
                contextPicker.setMaxValue(
                        alertView.getResources().getStringArray(R.array.content_types).length - 1);
                contextPicker.setDisplayedValues(
                        alertView.getResources().getStringArray(R.array.content_types));

                alert.setView(alertView).setNegativeButton("Cancel", (dialog, which) -> {
                    // Do nothing
                }).setPositiveButton("Start", (dialog, which) -> {

                    final BluetoothLeAudioContentMetadata.Builder contentBuilder =
                            new BluetoothLeAudioContentMetadata.Builder();
                    final String programInfo = program_info.getText().toString();
                    if (!programInfo.isEmpty()) {
                        contentBuilder.setProgramInfo(programInfo);
                    }

                    // Extract raw metadata
                    byte[] metaBuffer = contentBuilder.build().getRawMetadata();
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    stream.write(metaBuffer, 0 , metaBuffer.length);

                    // Extend raw metadata with context type
                    final int contextValue = 1 << (contextPicker.getValue() - 1);
                    stream.write((byte)0x03); // Length
                    stream.write((byte)0x02); // Type for the Streaming Audio Context
                    stream.write((byte)(contextValue & 0x00FF)); // Value LSB
                    stream.write((byte)((contextValue & 0xFF00) >> 8)); // Value MSB

                    if (mViewModel.startBroadcast(
                                BluetoothLeAudioContentMetadata.fromRawBytes(stream.toByteArray()),
                            code_input_text.getText() == null
                                    || code_input_text.getText().length() == 0 ? null
                                            : code_input_text.getText().toString().getBytes()))
                        Toast.makeText(BroadcasterActivity.this, "Broadcast was created.",
                                Toast.LENGTH_SHORT).show();
                });

                alert.show();
            } else {
                Toast.makeText(BroadcasterActivity.this,
                        "Maximum number of broadcasts reached: " + Integer
                                .valueOf(mViewModel.getMaximumNumberOfBroadcast()).toString(),
                        Toast.LENGTH_SHORT).show();
            }
        });

        RecyclerView recyclerView = findViewById(R.id.broadcaster_recycle_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);

        final BroadcastItemsAdapter itemsAdapter = new BroadcastItemsAdapter();
        itemsAdapter.setOnItemClickListener(broadcastId -> {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle("Broadcast Info:");

            // Load and fill in the metadata layout
            final View metaLayout =
                    getLayoutInflater().inflate(R.layout.broadcast_metadata, null);
            alert.setView(metaLayout);

            BluetoothLeBroadcastMetadata metadata = null;
            for (BluetoothLeBroadcastMetadata b : mViewModel.getAllBroadcastMetadata()) {
                if (b.getBroadcastId() == broadcastId) {
                    metadata = b;
                    break;
                }
            }

            if (metadata != null) {
                TextView addr_text = metaLayout.findViewById(R.id.device_addr_text);
                addr_text.setText("Device Address: " + metadata.getSourceDevice().toString());

                addr_text = metaLayout.findViewById(R.id.adv_sid_text);
                addr_text.setText("Advertising SID: " + metadata.getSourceAdvertisingSid());

                addr_text = metaLayout.findViewById(R.id.pasync_interval_text);
                addr_text.setText("Pa Sync Interval: " + metadata.getPaSyncInterval());

                addr_text = metaLayout.findViewById(R.id.is_encrypted_text);
                addr_text.setText("Is Encrypted: " + metadata.isEncrypted());

                byte[] code = metadata.getBroadcastCode();
                addr_text = metaLayout.findViewById(R.id.broadcast_code_text);
                if (code != null) {
                    addr_text.setText("Broadcast Code: "
                            + new String(code, StandardCharsets.UTF_8));
                } else {
                    addr_text.setVisibility(View.INVISIBLE);
                }

                addr_text = metaLayout.findViewById(R.id.presentation_delay_text);
                addr_text.setText(
                        "Presentation Delay: " + metadata.getPresentationDelayMicros() + " [us]");
            }

            alert.setNeutralButton("Stop", (dialog, which) -> {
                mViewModel.stopBroadcast(broadcastId);
            });
            alert.setPositiveButton("Modify", (dialog, which) -> {
                // Open activity for progam info
                AlertDialog.Builder modifyAlert = new AlertDialog.Builder(this);
                modifyAlert.setTitle("Modify the Broadcast:");

                LayoutInflater inflater = getLayoutInflater();
                View alertView = inflater.inflate(R.layout.broadcaster_add_broadcast_dialog, null);
                EditText program_info_input_text = alertView.findViewById(R.id.broadcast_program_info_input);

                // The Code cannot be changed, so just hide it
                final EditText code_input_text = alertView.findViewById(R.id.broadcast_code_input);
                code_input_text.setVisibility(View.GONE);

                modifyAlert.setView(alertView)
                        .setNegativeButton("Cancel", (modifyDialog, modifyWhich) -> {
                            // Do nothing
                        }).setPositiveButton("Update", (modifyDialog, modifyWhich) -> {
                            if (mViewModel.updateBroadcast(broadcastId,
                                    program_info_input_text.getText().toString()))
                                Toast.makeText(BroadcasterActivity.this, "Broadcast was updated.",
                                        Toast.LENGTH_SHORT).show();
                        });

                modifyAlert.show();
            });

            alert.show();
            Log.d("CC", "Num broadcasts: " + mViewModel.getBroadcastCount());
        });
        recyclerView.setAdapter(itemsAdapter);

        // Get the initial state
        mViewModel = ViewModelProviders.of(this).get(BroadcasterViewModel.class);
        itemsAdapter.updateBroadcastsMetadata(mViewModel.getAllBroadcastMetadata());

        // Put a watch on updates
        mViewModel.getBroadcastUpdateMetadataLive().observe(this, audioBroadcast -> {
            itemsAdapter.updateBroadcastMetadata(audioBroadcast);

            Toast.makeText(BroadcasterActivity.this,
                    "Updated broadcast " + audioBroadcast.getBroadcastId(), Toast.LENGTH_SHORT)
                    .show();
        });

        // Put a watch on any error reports
        mViewModel.getBroadcastStatusMutableLive().observe(this, msg -> {
            Toast.makeText(BroadcasterActivity.this, msg, Toast.LENGTH_SHORT).show();
        });

        // Put a watch on broadcast playback states
        mViewModel.getBroadcastPlaybackStartedMutableLive().observe(this, reasonAndBidPair -> {
            Toast.makeText(BroadcasterActivity.this, "Playing broadcast " + reasonAndBidPair.second
                    + ", reason " + reasonAndBidPair.first, Toast.LENGTH_SHORT).show();

            itemsAdapter.updateBroadcastPlayback(reasonAndBidPair.second, true);
        });

        mViewModel.getBroadcastPlaybackStoppedMutableLive().observe(this, reasonAndBidPair -> {
            Toast.makeText(BroadcasterActivity.this, "Paused broadcast " + reasonAndBidPair.second
                    + ", reason " + reasonAndBidPair.first, Toast.LENGTH_SHORT).show();

            itemsAdapter.updateBroadcastPlayback(reasonAndBidPair.second, false);
        });

        mViewModel.getBroadcastAddedMutableLive().observe(this, broadcastId -> {
            itemsAdapter.addBroadcasts(broadcastId);

            Toast.makeText(BroadcasterActivity.this,
                    "Broadcast was added broadcastId: " + broadcastId, Toast.LENGTH_SHORT).show();
        });

        // Put a watch on broadcast removal
        mViewModel.getBroadcastRemovedMutableLive().observe(this, reasonAndBidPair -> {
            itemsAdapter.removeBroadcast(reasonAndBidPair.second);

            Toast.makeText(
                    BroadcasterActivity.this, "Broadcast was removed " + " broadcastId: "
                            + reasonAndBidPair.second + ", reason: " + reasonAndBidPair.first,
                    Toast.LENGTH_SHORT).show();
        });

        // Prevent destruction when loses focus
        this.setFinishOnTouchOutside(false);
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }
}
