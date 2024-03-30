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

package android.security.sts.sts_test_app_package;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class PocReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences sh =
                context.getSharedPreferences(
                        context.getResources().getString(R.string.SHARED_PREFERENCE),
                        Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = sh.edit();
        edit.putInt(
                context.getResources().getString(R.string.RESULT_KEY),
                context.getResources().getInteger(R.integer.FAIL));
        edit.commit();
    }
}
