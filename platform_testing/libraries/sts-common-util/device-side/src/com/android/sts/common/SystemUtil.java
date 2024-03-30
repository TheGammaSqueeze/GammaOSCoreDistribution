/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.sts.common;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assume.assumeThat;

import android.app.Instrumentation;

import com.android.compatibility.common.util.SettingsUtils;

import java.io.IOException;
import java.util.Optional;

public class SystemUtil {

    /**
     * Set the value of a device setting and set it back to old value upon closing.
     *
     * @param instrumentation {@link Instrumentation} instance, obtained from a test running in
     *        instrumentation framework
     * @param namespace "system", "secure", or "global"
     * @param key setting key to set
     * @param value setting value to set to
     * @return AutoCloseable that resets the setting back to existing value upon closing.
     */
    public static AutoCloseable withSetting(Instrumentation instrumentation, final String namespace,
            final String key, String value) {
        String getSettingRes = SettingsUtils.get(namespace, key);
        final Optional<String> oldSetting = Optional.ofNullable(getSettingRes);
        SettingsUtils.set(namespace, key, value);

        String getSettingCurrent = SettingsUtils.get(namespace, key);
        Optional<String> currSetting = Optional.ofNullable(getSettingCurrent);
        assumeThat(String.format("Could not set %s:%s to %s", namespace, key, value),
                currSetting.isPresent() ? currSetting.get().trim() : null, equalTo(value));

        return new AutoCloseable() {
            @Override
            public void close() throws Exception {
                if (!oldSetting.isPresent()) {
                    SettingsUtils.delete(namespace, key);
                } else {
                    String oldValue = oldSetting.get().trim();
                    SettingsUtils.set(namespace, key, oldValue);
                    String failMsg =
                            String.format("could not reset '%s' back to '%s'", key, oldValue);
                    String getSettingCurrent = SettingsUtils.get(namespace, key);
                    Optional<String> currSetting = Optional.ofNullable(getSettingCurrent);
                    assumeThat(failMsg, currSetting.isPresent() ? currSetting.get().trim() : null,
                            equalTo(oldValue));
                }
            }
        };
    }
}
