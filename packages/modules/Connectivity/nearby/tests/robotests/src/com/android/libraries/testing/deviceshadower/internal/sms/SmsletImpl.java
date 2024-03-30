/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.libraries.testing.deviceshadower.internal.sms;

import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;

import com.android.libraries.testing.deviceshadower.Smslet;
import com.android.libraries.testing.deviceshadower.internal.common.ContentDatabase;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of SMS functionality.
 */
public class SmsletImpl implements Smslet {

    private final Map<Uri, ContentDatabase> mUriToDataMap;

    public SmsletImpl() {
        mUriToDataMap = new HashMap<>();
        mUriToDataMap.put(
                Telephony.Sms.Inbox.CONTENT_URI, new ContentDatabase(Telephony.Sms.Inbox.BODY));
        mUriToDataMap.put(Telephony.Sms.Sent.CONTENT_URI,
                new ContentDatabase(Telephony.Sms.Inbox.BODY));
        // TODO(b/200231384): implement Outbox, Intents, Conversations.
    }

    @Override
    public Smslet addSms(Uri contentUri, String body) {
        mUriToDataMap.get(contentUri).addData(body);
        return this;
    }

    public Cursor getCursor(Uri uri) {
        return mUriToDataMap.get(uri).getCursor();
    }
}
