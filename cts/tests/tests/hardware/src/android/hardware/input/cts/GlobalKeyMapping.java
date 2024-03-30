/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.hardware.input.cts;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;
import android.view.KeyEvent;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Loads the global keys from {@code com.android.internal.R.xml.global_keys} the same way like
 * {@code com.android.server.policy.GlobalKeyManager} does.
 * TODO(199182608): Make GlobalKeyManager#shouldHandleGlobalKey() a testApi and remove this class
 */
public class GlobalKeyMapping {
    private static final String TAG = GlobalKeyMapping.class.getSimpleName();
    private static final String TAG_GLOBAL_KEYS = "global_keys";
    private static final String TAG_KEY = "key";
    private static final String ATTR_VERSION = "version";
    private static final String ATTR_KEY_CODE = "keyCode";
    private static final int GLOBAL_KEY_FILE_VERSION = 1;
    private static final Set<Integer> GLOBAL_KEYS = new HashSet<>();


    public GlobalKeyMapping(Context context) {
        loadGlobalKeys(context);
    }

    private void loadGlobalKeys(Context context) {
        try (XmlResourceParser parser = context.getResources().getXml(
                Resources.getSystem().getIdentifier("global_keys", "xml", "android"))) {
            beginDocument(parser, TAG_GLOBAL_KEYS);
            int version = parser.getAttributeIntValue(null, ATTR_VERSION, 0);
            if (GLOBAL_KEY_FILE_VERSION == version) {
                while (true) {
                    nextElement(parser);
                    String element = parser.getName();
                    if (element == null) {
                        break;
                    }
                    if (TAG_KEY.equals(element)) {
                        String keyCodeName = parser.getAttributeValue(null, ATTR_KEY_CODE);
                        int keyCode = KeyEvent.keyCodeFromString(keyCodeName);
                        if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                            GLOBAL_KEYS.add(keyCode);
                        }
                    }
                }
            }
        } catch (Resources.NotFoundException e) {
            Log.w(TAG, "global keys file not found", e);
        } catch (XmlPullParserException e) {
            Log.w(TAG, "XML parser exception reading global keys file", e);
        } catch (IOException e) {
            Log.w(TAG, "I/O exception reading global keys file", e);
        }
    }

    public boolean isGlobalKey(int keyCode) {
        return GLOBAL_KEYS.contains(keyCode);
    }

    /** Ported from com.android.internal.util.XmlUtils */
    private void nextElement(XmlPullParser parser) throws XmlPullParserException, IOException {
        int type;
        while ((type = parser.next()) != parser.START_TAG && type != parser.END_DOCUMENT) {
            // skip
        }
    }

    /** Ported from com.android.internal.util.XmlUtils */
    private void beginDocument(XmlPullParser parser, String firstElementName)
            throws XmlPullParserException, IOException {
        int type;
        while ((type = parser.next()) != parser.START_TAG && type != parser.END_DOCUMENT) {
            // skip
        }

        if (type != parser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }

        if (!parser.getName().equals(firstElementName)) {
            throw new XmlPullParserException("Unexpected start tag: found " + parser.getName()
                    + ", expected " + firstElementName);
        }
    }
}
