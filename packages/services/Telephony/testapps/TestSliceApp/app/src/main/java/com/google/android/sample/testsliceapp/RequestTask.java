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

import android.net.Network;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

class RequestTask{
    String ping(Network network) {
        URL url = null;
        try {
            url = new URL("http://www.google.com");
        } catch (Exception e) {
        }
        if (url != null) {
            try {
                return httpGet(network, url);
            } catch (Exception e) {
            }
        }
        return "";
    }

    /**
    * Performs a HTTP GET to the specified URL on the specified Network, and returns
    * the response body decoded as UTF-8.
    */
    private static String httpGet(Network network, URL httpUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) network.openConnection(httpUrl);
        try {
            InputStream inputStream = connection.getInputStream();
            return new BufferedInputStream(inputStream).toString();
        } finally {
            connection.disconnect();
        }
    }
}
