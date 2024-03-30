/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.sample.testsliceapp;

import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.Fragment;

/**
 * A simple {@link Fragment} subclass. Use the {@link CBS#newInstance} factory method to create an
 * instance of this fragment.
 */
public class CBS extends Fragment {
    Button mRelease, mRequest, mPing;
    Network mNetwork;
    ConnectivityManager mConnectivityManager;
    NetworkCallback mProfileCheckNetworkCallback;

    public CBS() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of this fragment using the provided
     * parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment CBS.
     */
    // TODO: Rename and change types and number of parameters
    public static CBS newInstance(String param1, String param2) {
        CBS fragment = new CBS();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_c_b_s, container, false);
        mProfileCheckNetworkCallback =
                new NetworkCallback() {
            @Override
            public void onAvailable(final Network network) {
                mNetwork = network;
            }
        };
        mRelease = view.findViewById(R.id.releasecbs);
        mRelease.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mConnectivityManager.unregisterNetworkCallback(
                        mProfileCheckNetworkCallback);
            }
        });
        mRequest = view.findViewById(R.id.requestcbs);
        mRequest.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                NetworkCallback mProfileCheckNetworkCallback = new NetworkCallback() {
                    @Override
                    public void onAvailable(final Network network) {
                        mNetwork = network;
                    }
                };
                NetworkRequest.Builder builder = new NetworkRequest.Builder();
                builder.addCapability(NetworkCapabilities.NET_CAPABILITY_CBS);
                mConnectivityManager.requestNetwork(builder.build(), mProfileCheckNetworkCallback);
            }
        });
        mPing = view.findViewById(R.id.pingcbs);
        mPing.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mNetwork != null) {
                    //mNetwork.
                    try {
                        new RequestTask().ping(mNetwork);
                    } catch (Exception e) {
                    }
                }
            }
        });
        return view;
    }
}
