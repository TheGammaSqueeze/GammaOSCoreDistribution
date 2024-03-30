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

package com.android.tv.settings.compat;

import android.animation.AnimatorInflater;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.TwoStatePreference;
import androidx.recyclerview.widget.RecyclerView;

import com.android.tv.settings.HasSettingsManager;
import com.android.tv.settings.R;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.SettingsManager;
import com.android.tv.settings.library.State;
import com.android.tv.settings.library.overlay.FlavorUtils;
import com.android.tv.twopanelsettings.TwoPanelSettingsFragment;

import java.util.List;

/** Provide utility class to render settings preferences. */
public abstract class PreferenceControllerFragmentCompat extends LeanbackPreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {
    private SettingsManager mSettingsManager;
    private String mTitle;
    private State mState;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (getActivity() instanceof HasSettingsManager) {
            mSettingsManager = ((HasSettingsManager) getActivity()).getSettingsManager();
            mState = mSettingsManager.createState(getStateIdentifier());
            if (mState != null) {
                mState.onAttach();
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mState != null) {
            mState.onCreate(getArguments());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mState != null) {
            mState.onStart();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getCallbackFragment() instanceof TwoPanelSettingsFragment) {
            TwoPanelSettingsFragment parentFragment =
                    (TwoPanelSettingsFragment) getCallbackFragment();
            parentFragment.addListenerForFragment(this);
        }
        if (mState != null) {
            mState.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getCallbackFragment() instanceof TwoPanelSettingsFragment) {
            TwoPanelSettingsFragment parentFragment =
                    (TwoPanelSettingsFragment) getCallbackFragment();
            parentFragment.removeListenerForFragment(this);
        }
        if (mState != null) {
            mState.onPause();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mState != null) {
            mState.onStop();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mState != null) {
            mState.onDestroy();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (mState != null) {
            mState.onDetach();
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (getActivity() instanceof HasSettingsManager) {
            if (mState == null || (!(preference instanceof HasKeys))) {
                return super.onPreferenceTreeClick(preference);
            }
            boolean handled = mSettingsManager.onPreferenceClick(
                    mState,
                    ((HasKeys) preference).getKeys(),
                    preference instanceof TwoStatePreference
                            && ((TwoStatePreference) preference).isChecked());
            if (handled) {
                return true;
            }
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (mSettingsManager == null || mState == null || !(preference instanceof HasKeys)) {
            return false;
        }
        return mSettingsManager.onPreferenceChange(
                mState,
                ((HasKeys) preference).getKeys(),
                newValue);
    }

    protected Preference findTargetPreference(String[] key) {
        Preference preference = findPreference(key[0]);
        for (int i = 1; i < key.length; i++) {
            if (preference instanceof PreferenceGroup) {
                PreferenceGroup preferenceGroup = (PreferenceGroup) preference;
                preference = preferenceGroup.findPreference(key[i]);
            } else {
                return null;
            }
        }
        return preference;
    }

    public HasKeys updatePref(PreferenceCompat prefCompat) {
        if (prefCompat == null) {
            return null;
        }
        String[] key = prefCompat.getKey();
        Preference preference = findTargetPreference(key);
        if (preference == null) {
            return null;
        }

        RenderUtil.updatePreference(
                getContext(), (HasKeys) preference, prefCompat, preference.getOrder());
        if (prefCompat.hasOnPreferenceChangeListener()) {
            preference.setOnPreferenceChangeListener(this);
        }
        if (prefCompat.getChildPrefCompats() != null && prefCompat.getChildPrefCompats().size() > 0
                && preference instanceof PreferenceGroup) {
            RenderUtil.updatePreferenceGroup((PreferenceGroup) preference,
                    prefCompat.getChildPrefCompats());
        }
        return (HasKeys) preference;
    }

    public void updateAllPref(List<PreferenceCompat> preferenceCompatList) {
        if (preferenceCompatList == null) {
            return;
        }
        preferenceCompatList.stream()
                .forEach(preferenceCompat -> updatePref(preferenceCompat));
    }

    public void updateScreenTitle(String title) {
        setTitle(title);
        mTitle = title;
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
    }

    /**
     * Return the state identifier to be matched with SettingsAPI for the fragment.
     *
     * @return state identifier
     */
    public abstract int getStateIdentifier();

    public State getState() {
        return mState;
    }

    public <T extends State> T getState(Class<T> clazz) {
        return clazz.cast(mState);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (view != null) {
            TextView titleView = view.findViewById(R.id.decor_title);
            // We rely on getResources().getConfiguration().getLayoutDirection() instead of
            // view.isLayoutRtl() as the latter could return false in some complex scenarios even if
            // it is RTL.
            if (titleView != null) {
                if (mTitle != null) {
                    titleView.setText(mTitle);
                }
                if (getResources().getConfiguration().getLayoutDirection()
                        == View.LAYOUT_DIRECTION_RTL) {
                    titleView.setGravity(Gravity.RIGHT);
                }
            }
            if (FlavorUtils.isTwoPanel(getContext())) {
                ViewGroup decor = view.findViewById(R.id.decor_title_container);
                if (decor != null) {
                    decor.setOutlineProvider(null);
                    decor.setBackgroundResource(R.color.tp_preference_panel_background_color);
                }
            }
            removeAnimationClipping(view);
        }
    }

    protected void removeAnimationClipping(View v) {
        if (v instanceof ViewGroup) {
            ((ViewGroup) v).setClipChildren(false);
            ((ViewGroup) v).setClipToPadding(false);
            for (int index = 0; index < ((ViewGroup) v).getChildCount(); index++) {
                View child = ((ViewGroup) v).getChildAt(index);
                removeAnimationClipping(child);
            }
        }
    }

    @Override
    protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
        return new PreferenceGroupAdapter(preferenceScreen) {
            @Override
            @NonNull
            public PreferenceViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                    int viewType) {
                PreferenceViewHolder vh = super.onCreateViewHolder(parent, viewType);
                if (FlavorUtils.isTwoPanel(getContext())) {
                    vh.itemView.setStateListAnimator(AnimatorInflater.loadStateListAnimator(
                            getContext(), R.animator.preference));
                }
                vh.itemView.setOnTouchListener((v, e) -> {
                    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        vh.itemView.requestFocus();
                        v.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_DPAD_CENTER));
                        return true;
                    } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                        v.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
                                KeyEvent.KEYCODE_DPAD_CENTER));
                        return true;
                    }
                    return false;
                });
                vh.itemView.setFocusable(true);
                vh.itemView.setFocusableInTouchMode(true);
                return vh;
            }
        };
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (getActivity() instanceof HasSettingsManager) {
            if (mState == null || (!(preference instanceof HasKeys))) {
                super.onDisplayPreferenceDialog(preference);
                return;
            }
            mSettingsManager.onDisplayPreferenceDialog(mState, ((HasKeys) preference).getKeys());
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }
}

