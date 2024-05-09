/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.documentsui.picker;

import static com.android.documentsui.services.FileOperationService.OPERATION_COMPRESS;
import static com.android.documentsui.services.FileOperationService.OPERATION_COPY;
import static com.android.documentsui.services.FileOperationService.OPERATION_DELETE;
import static com.android.documentsui.services.FileOperationService.OPERATION_EXTRACT;
import static com.android.documentsui.services.FileOperationService.OPERATION_MOVE;
import static com.android.documentsui.services.FileOperationService.OPERATION_UNKNOWN;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.android.documentsui.BaseActivity;
import com.android.documentsui.Injector;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.services.FileOperationService.OpType;
import com.android.documentsui.ui.Snackbars;

import com.google.android.material.snackbar.Snackbar;
import android.view.KeyEvent;

public class PickFragment extends Fragment {
    public static final String TAG = "PickFragment";
    private static final String LOG_TAG = "PickFragment";

    private static final String ACTION_KEY = "action";
    private static final String COPY_OPERATION_SUBTYPE_KEY = "copyOperationSubType";
    private static final String PICK_TARGET_KEY = "pickTarget";
    private static final String RESTRICT_SCOPE_STORAGE_KEY = "restrictScopeStorage";

    private final View.OnClickListener mPickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.d(LOG_TAG, "Pick button clicked");
            if (mPick.isEnabled()) {
                Log.d(LOG_TAG, "Picking document");
                mInjector.actions.pickDocument(getChildFragmentManager(), mPickTarget);
            } else {
                Log.d(LOG_TAG, "Pick button is disabled");
                String msg = getResources().getString(R.string.directory_blocked_header_subtitle);
                Snackbars.makeSnackbar(getActivity(), msg, Snackbar.LENGTH_LONG).show();
            }
        }
    };

    private final View.OnClickListener mCancelListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.d(LOG_TAG, "Cancel button clicked");
            mInjector.pickResult.increaseActionCount();
            final BaseActivity activity = BaseActivity.get(PickFragment.this);
            activity.setResult(FragmentActivity.RESULT_CANCELED);
            activity.finish();
        }
    };

    private Injector<ActionHandler<PickActivity>> mInjector;
    private int mAction;
    private boolean mRestrictScopeStorage;
    private @OpType int mCopyOperationSubType = OPERATION_UNKNOWN;
    private DocumentInfo mPickTarget;
    private View mContainer;
    private View mPickOverlay;
    private Button mPick;
    private Button mCancel;

    public static void show(FragmentManager fm) {
        // Fragment can be restored by FragmentManager automatically.
        if (get(fm) != null) {
            return;
        }

        final PickFragment fragment = new PickFragment();
        final FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.container_save, fragment, TAG);
        ft.commitNowAllowingStateLoss();
    }

    public static PickFragment get(FragmentManager fm) {
        return (PickFragment) fm.findFragmentByTag(TAG);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreateView: Creating view");
        mContainer = inflater.inflate(R.layout.fragment_pick, container, false);

        // Find the 'Pick' button and set the onClick listener.
        mPick = mContainer.findViewById(android.R.id.button1);
        mPick.setOnClickListener(mPickListener);

        // Find the overlay and set the onClick listener.
        mPickOverlay = mContainer.findViewById(R.id.pick_button_overlay);
        mPickOverlay.setOnClickListener(mPickListener);

        // Find the 'Cancel' button and set the onClick listener.
        mCancel = mContainer.findViewById(android.R.id.button2);
        mCancel.setOnClickListener(mCancelListener);

        // Set the container view to be focusable and request focus to handle key events.
        mContainer.setFocusableInTouchMode(true);
        mContainer.requestFocus();

        // Set up the key listener to handle the 'Start' button key event.
        mContainer.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                Log.d(LOG_TAG, "onKey: KeyCode " + keyCode + ", Action " + event.getAction());
                // Check if the key event is a down press of the 'Start' button.
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BUTTON_START) {
                    Log.d(LOG_TAG, "Start button pressed");
                    if (mPick != null && mPick.isEnabled()) {
                        Log.d(LOG_TAG, "Pick button enabled, triggering click");
                        mPick.requestFocus();
                        mPick.performClick(); // Invoke the "Use this folder" action
                        return true; // Indicate that the key event was handled
                    } else {
                        Log.d(LOG_TAG, "Pick button is null or disabled");
                    }
                }
                return false; // Indicate that the key event was not handled
            }
        });

        updateView();
        return mContainer;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.d(LOG_TAG, "onActivityCreated: Activity created");
        if (savedInstanceState != null) {
            // Restore status
            mAction = savedInstanceState.getInt(ACTION_KEY);
            mCopyOperationSubType = savedInstanceState.getInt(COPY_OPERATION_SUBTYPE_KEY);
            mPickTarget = savedInstanceState.getParcelable(PICK_TARGET_KEY);
            mRestrictScopeStorage = savedInstanceState.getBoolean(RESTRICT_SCOPE_STORAGE_KEY);
            updateView();
        }

        mInjector = ((PickActivity) getActivity()).getInjector();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(LOG_TAG, "onSaveInstanceState: Saving instance state");
        outState.putInt(ACTION_KEY, mAction);
        outState.putInt(COPY_OPERATION_SUBTYPE_KEY, mCopyOperationSubType);
        outState.putParcelable(PICK_TARGET_KEY, mPickTarget);
        outState.putBoolean(RESTRICT_SCOPE_STORAGE_KEY, mRestrictScopeStorage);
    }

    public void setPickTarget(int action, @OpType int copyOperationSubType,
                              boolean restrictScopeStorage, DocumentInfo pickTarget) {
        Log.d(LOG_TAG, "setPickTarget: Setting pick target");
        assert(copyOperationSubType != OPERATION_DELETE);

        mAction = action;
        mCopyOperationSubType = copyOperationSubType;
        mRestrictScopeStorage = restrictScopeStorage;
        mPickTarget = pickTarget;
        if (mContainer != null) {
            updateView();
        }
    }

    /**
     * Applies the state of fragment to the view components.
     */
    private void updateView() {
        Log.d(LOG_TAG, "updateView: Updating view with action: " + mAction);
        if (mPickTarget != null && (
                mAction == State.ACTION_OPEN_TREE ||
                        mPickTarget.isCreateSupported())) {
            mContainer.setVisibility(View.VISIBLE);
        } else {
            mContainer.setVisibility(View.GONE);
            return;
        }

        switch (mAction) {
            case State.ACTION_OPEN_TREE:
                mPick.setText(getString(R.string.open_tree_button));
                mPick.setWidth(Integer.MAX_VALUE);
                mCancel.setVisibility(View.GONE);

                // Ensure the button is always enabled
                mPick.setEnabled(true);

                // Always hide the overlay, implying no restrictions
                mPickOverlay.setVisibility(View.GONE);
                break;
            case State.ACTION_PICK_COPY_DESTINATION:
                int titleId;
                switch (mCopyOperationSubType) {
                    case OPERATION_COPY:
                        titleId = R.string.button_copy;
                        break;
                    case OPERATION_COMPRESS:
                        titleId = R.string.button_compress;
                        break;
                    case OPERATION_EXTRACT:
                        titleId = R.string.button_extract;
                        break;
                    case OPERATION_MOVE:
                        titleId = R.string.button_move;
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }
                mPick.setText(titleId);
                mCancel.setVisibility(View.VISIBLE);
                break;
            default:
                mContainer.setVisibility(View.GONE);
                return;
        }
    }

    public Button getPickButton() {
        return mPick;
    }
}
