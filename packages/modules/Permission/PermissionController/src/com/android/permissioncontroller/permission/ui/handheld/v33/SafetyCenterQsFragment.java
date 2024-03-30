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

package com.android.permissioncontroller.permission.ui.handheld.v33;

import static android.Manifest.permission_group.CAMERA;
import static android.Manifest.permission_group.LOCATION;
import static android.Manifest.permission_group.MICROPHONE;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.permission.PermissionGroupUsage;
import android.permission.PermissionManager;
import android.util.ArrayMap;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.model.v33.SafetyCenterQsViewModel;
import com.android.permissioncontroller.permission.ui.model.v33.SafetyCenterQsViewModelFactory;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Quick Settings fragment for the safety center. Displays information to the user about the
 * current safety and privacy status of their device, including showing mic/camera usage, and having
 * mic/camera/location toggles.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class SafetyCenterQsFragment extends Fragment {
    private static final ArrayMap<String, Integer> sToggleButtons = new ArrayMap<>();

    private long mSessionId;
    private List<PermissionGroupUsage> mPermGroupUsages;
    private SafetyCenterQsViewModel mViewModel;
    private View mRootView;

    static {
        sToggleButtons.put(CAMERA, R.id.camera_toggle);
        sToggleButtons.put(MICROPHONE, R.id.mic_toggle);
        sToggleButtons.put(LOCATION, R.id.location_toggle);
    }

    /**
     * Create arguments for this package
     *
     * @param sessionId The current session Id
     * @return A bundle with the required arguments
     */
    public static SafetyCenterQsFragment newInstance(long sessionId,
            ArrayList<PermissionGroupUsage> usages) {
        Bundle args = new Bundle();
        args.putLong(EXTRA_SESSION_ID, sessionId);
        args.putParcelableArrayList(PermissionManager.EXTRA_PERMISSION_USAGES, usages);
        SafetyCenterQsFragment frag = new SafetyCenterQsFragment();
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSessionId = INVALID_SESSION_ID;
        if (getArguments() != null) {
            mSessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);
        }

        mPermGroupUsages = getArguments()
                .getParcelableArrayList(PermissionManager.EXTRA_PERMISSION_USAGES);
        if (mPermGroupUsages == null) {
            mPermGroupUsages = new ArrayList<>();
        }

        getActivity().setTheme(R.style.SafetyCenter);

        SafetyCenterQsViewModelFactory factory = new SafetyCenterQsViewModelFactory(
                getActivity().getApplication(), mSessionId, mPermGroupUsages);
        mViewModel = new ViewModelProvider(this, factory).get(SafetyCenterQsViewModel.class);
        mViewModel.getSensorPrivacyLiveData()
                .observe(this, (v) -> setSensorToggleState(v, getView()));
        //LightAppPermGroupLiveDatas are kept track of in the view model,
        // we need to start observing them here
        if (!mPermGroupUsages.isEmpty()) {
            mViewModel.getPermDataLoadedLiveData().observe(this, this::onPermissionGroupsLoaded);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.safety_center_qs, container, false);
        root.findViewById(R.id.security_settings_button).setOnClickListener(
                (v) -> mViewModel.navigateToSecuritySettings(this));
        mRootView = root;
        if (mPermGroupUsages.isEmpty()) {
            mRootView.setVisibility(View.VISIBLE);
            setSensorToggleState(new ArrayMap<>(), mRootView);
        } else {
            mRootView.setVisibility(View.GONE);
        }
        return root;
    }

    private void onPermissionGroupsLoaded(boolean initialized) {
        if (initialized) {
            mRootView.setVisibility(View.VISIBLE);
            setSensorToggleState(new ArrayMap<>(), mRootView);
            addPermissionUsageInformation(mRootView);
        }
    }

    private void addPermissionUsageInformation(View rootView) {
        if (rootView == null) {
            return;
        }
        View permissionSectionTitleView = rootView.findViewById(R.id.permission_section_title);
        if (mPermGroupUsages == null || mPermGroupUsages.isEmpty()) {
            permissionSectionTitleView.setVisibility(View.GONE);
            return;
        }
        permissionSectionTitleView.setVisibility(View.VISIBLE);
        LinearLayout usageLayout = rootView.findViewById(R.id.permission_usage);
        for (PermissionGroupUsage usage : mPermGroupUsages) {
            View cardView = View.inflate(getContext(), R.layout.indicator_card, usageLayout);
            cardView.setId(View.generateViewId());

            RelativeLayout permissionParent = cardView.findViewById(R.id.permission_parent);
            permissionParent.setId(View.generateViewId());
            final int parentIconId = View.generateViewId();
            final int parentTitleId = View.generateViewId();
            final int parentLabelId = View.generateViewId();
            final int parentButtonId = View.generateViewId();
            populatePermissionParent(permissionParent, usage.getPermissionGroupName(),
                    generateUsageLabel(usage), parentIconId, parentTitleId, parentLabelId,
                    parentButtonId);

            if (usage.isPhoneCall()) {
                ImageButton expandButton = permissionParent.findViewById(parentButtonId);
                expandButton.setVisibility(View.GONE);
                continue;
            }

            LinearLayout cardViewGroup = cardView.findViewById(R.id.full_card);
            cardViewGroup.setId(View.generateViewId());

            View expandedView = cardView.findViewById(R.id.expanded_view);
            expandedView.setId(View.generateViewId());

            boolean shouldAllowRevoke = mViewModel.shouldAllowRevoke(usage);
            boolean isSubAttributionUsage = isSubAttributionUsage(usage.getAttributionLabel());
            Intent manageServiceIntent = null;

            if (isSubAttributionUsage) {
                manageServiceIntent = mViewModel.getStartViewPermissionUsageIntent(getContext(),
                        usage);
            }

            boolean canHandleSubAttributionIntent = manageServiceIntent != null;
            int managePermissionIconResId =
                    canHandleSubAttributionIntent || !shouldAllowRevoke ? R.drawable.ic_setting
                            : R.drawable.ic_block;

            int managePermissionLabelResId = getManagePermissionLabel(canHandleSubAttributionIntent,
                    shouldAllowRevoke,
                    usage.getPermissionGroupName());

            RelativeLayout manageParent = populateExpandedPermission(cardView,
                    R.id.manage_parent,
                    managePermissionIconResId, managePermissionLabelResId);

            RelativeLayout usageParent = populateExpandedPermission(cardView, R.id.usage_parent,
                    R.drawable.ic_history,
                    getSeeUsageText(usage.getPermissionGroupName()));

            ImageButton expandButton = permissionParent.findViewById(parentButtonId);

            setExpansionClickListener(permissionParent, expandedView, cardViewGroup,
                    manageParent,
                    usageParent, expandButton);
            setExpansionClickListener(expandButton, expandedView, cardViewGroup, manageParent,
                    usageParent, expandButton);

            MaterialCardView managePermission = cardView.findViewById(R.id.manage_permission);
            managePermission.setId(View.generateViewId());

            if (shouldAllowRevoke) {
                managePermission.setOnClickListener(l -> {
                    permissionParent.callOnClick();
                    permissionParent.setOnClickListener(null);
                    permissionParent.setEnabled(false);
                    expandButton.setEnabled(false);
                    expandButton.setVisibility(View.GONE);
                    revokePermission(permissionParent, parentIconId, parentLabelId, usage);
                });
            } else {
                setManagePermissionClickListener(managePermission, usage, manageServiceIntent);
            }

            MaterialCardView seeUsage = cardView.findViewById(R.id.see_usage);
            seeUsage.setId(View.generateViewId());
            seeUsage.setOnClickListener(l -> {
                mViewModel.navigateToSeeUsage(this, usage.getPermissionGroupName());
            });
        }
    }

    private void setManagePermissionClickListener(MaterialCardView managePermission,
            PermissionGroupUsage usage, Intent manageServiceIntent) {
        if (manageServiceIntent != null) {
            managePermission.setOnClickListener(l -> {
                mViewModel.navigateToManageService(this, manageServiceIntent);
            });
        } else {
            managePermission.setOnClickListener(l -> {
                mViewModel.navigateToManageAppPermissions(this, usage);
            });
        }

    }

    private int getManagePermissionLabel(boolean canHandleIntent,
            boolean shouldAllowRevoke,
            String permissionGroupName) {
        if (canHandleIntent) {
            return R.string.manage_service_qs;
        }
        if (!shouldAllowRevoke) {
            return R.string.manage_permissions_qs;
        }
        return getRemovePermissionText(permissionGroupName);
    }

    private boolean isSubAttributionUsage(@Nullable CharSequence attributionLabel) {
        if (attributionLabel == null || attributionLabel.length() == 0) {
            return false;
        }
        return true;
    }

    private void revokePermission(RelativeLayout permissionParent, int iconId, int labelId,
            PermissionGroupUsage usage) {
        mViewModel.revokePermission(usage);
        ImageView iconView = permissionParent.findViewById(iconId);
        Drawable background = getContext().getDrawable(
                R.drawable.indicator_background_circle).mutate();
        background.setTint(getContext().getColor(R.color.safety_center_done));
        Drawable icon = getContext().getDrawable(R.drawable.ic_check);
        iconView.setImageDrawable(constructIcon(icon, background));
        TextView labelView = permissionParent.findViewById(labelId);
        labelView.setText(R.string.permissions_removed_qs);
    }

    private void setExpansionClickListener(View parentView, View expandedView,
            LinearLayout cardViewGroup, RelativeLayout removeParent, RelativeLayout usageParent,
            ImageButton expandButton) {
        parentView.setOnClickListener(v -> {
            AutoTransition transition = new AutoTransition();
            if (expandedView.getVisibility() == View.VISIBLE) {
                TransitionManager.beginDelayedTransition(cardViewGroup, transition);
                expandedView.setVisibility(View.GONE);
                removeParent.setVisibility(View.GONE);
                usageParent.setVisibility(View.GONE);
                expandButton.setImageDrawable(
                        getContext().getDrawable(R.drawable.ic_expand_more));
            } else {
                TransitionManager.beginDelayedTransition(cardViewGroup, transition);
                expandedView.setVisibility(View.VISIBLE);
                removeParent.setVisibility(View.VISIBLE);
                usageParent.setVisibility(View.VISIBLE);
                expandButton.setImageDrawable(
                        getContext().getDrawable(R.drawable.ic_expand_less));
            }
        });
    }

    private String generateUsageLabel(PermissionGroupUsage usage) {

        Context context = getContext();

        if (usage.isPhoneCall() && usage.isActive()) {
            return context.getString(R.string.active_call_usage_qs);
        } else if (usage.isPhoneCall()) {
            return context.getString(R.string.recent_call_usage_qs);
        }

        return generateAttributionUsageLabel(usage);
    }

    private String generateAttributionUsageLabel(PermissionGroupUsage usage) {

        Context context = getContext();
        CharSequence appLabel = KotlinUtils.INSTANCE.getPackageLabel(
                getActivity().getApplication(), usage.getPackageName(),
                UserHandle.getUserHandleForUid(usage.getUid()));

        final int usageResId =
                usage.isActive() ? R.string.active_app_usage_qs : R.string.recent_app_usage_qs;
        final int singleUsageResId =
                usage.isActive() ? R.string.active_app_usage_1_qs : R.string.recent_app_usage_1_qs;
        final int doubleUsageResId =
                usage.isActive() ? R.string.active_app_usage_2_qs : R.string.recent_app_usage_2_qs;

        CharSequence attributionLabel = usage.getAttributionLabel();
        CharSequence proxyLabel = usage.getProxyLabel();

        if (attributionLabel == null && proxyLabel == null) {
            return context.getString(usageResId, appLabel);
        } else if (attributionLabel != null && proxyLabel != null) {
            return context.getString(doubleUsageResId, appLabel, attributionLabel, proxyLabel);
        } else {
            return context.getString(singleUsageResId, appLabel,
                    attributionLabel == null ? proxyLabel : attributionLabel);
        }
    }

    private void populatePermissionParent(RelativeLayout permissionParent, String permGroupName,
            String usageText, int iconId, int titleId, int labelId, int buttonId) {

        CharSequence permGroupLabel = KotlinUtils.INSTANCE.getPermGroupLabel(getContext(),
                permGroupName);
        ImageView iconView = new ImageView(getContext());
        iconView.setId(iconId);
        RelativeLayout.LayoutParams iconParams = new RelativeLayout.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        iconParams.setMargins(0, 0, convertDpToPixel(10), 0);
        Drawable indicatorIcon = KotlinUtils.INSTANCE.getPermGroupIcon(getContext(),
                permGroupName, Color.BLACK);
        Drawable background = getContext().getDrawable(R.drawable.indicator_background_circle);
        Utils.applyTint(getContext(), background, android.R.attr.colorAccent);
        iconView.setImageDrawable(constructIcon(indicatorIcon, background));
        iconParams.addRule(RelativeLayout.CENTER_VERTICAL);
        permissionParent.addView(iconView, iconParams);

        TextView titleText = new TextView(getContext());
        titleText.setId(titleId);
        titleText.setText(permGroupLabel);
        titleText.setContentDescription(permGroupLabel);
        RelativeLayout.LayoutParams titleParams = new RelativeLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(convertDpToPixel(10), 0, convertDpToPixel(4), convertDpToPixel(4));
        titleParams.addRule(RelativeLayout.RIGHT_OF, iconId);
        permissionParent.addView(titleText, titleParams);

        TextView labelText = new TextView(getContext());
        labelText.setId(labelId);
        labelText.setText(usageText);
        RelativeLayout.LayoutParams textParams = new RelativeLayout.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        textParams.addRule(RelativeLayout.BELOW, titleId);
        textParams.addRule(RelativeLayout.ALIGN_START, titleId);
        textParams.setMargins(0, 0, convertDpToPixel(20), 0);
        permissionParent.addView(labelText, textParams);

        ImageButton expandButton = new ImageButton(getContext());
        expandButton.setId(buttonId);
        expandButton.setBackgroundColor(Color.TRANSPARENT);
        expandButton.setImageDrawable(getContext().getDrawable(R.drawable.ic_expand_more));
        RelativeLayout.LayoutParams buttonParams = new RelativeLayout.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        buttonParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        buttonParams.addRule(RelativeLayout.CENTER_VERTICAL);
        permissionParent.addView(expandButton, buttonParams);
    }

    private RelativeLayout populateExpandedPermission(View indicatorCardView, int expandedCardId,
            int iconId,
            int usageResId) {

        RelativeLayout parentLayout = indicatorCardView.findViewById(expandedCardId);
        parentLayout.setId(View.generateViewId());
        parentLayout.setPadding(convertDpToPixel(8), convertDpToPixel(8), 0, convertDpToPixel(8));
        parentLayout.setVisibility(View.GONE);

        ImageView iconView = new ImageView(getContext());
        iconView.setId(View.generateViewId());
        iconView.setImageResource(iconId);
        RelativeLayout.LayoutParams iconParams = new RelativeLayout.LayoutParams(
                convertDpToPixel(16), convertDpToPixel(16));
        iconParams.setMargins(convertDpToPixel(10), 0, 0, 0);
        iconParams.addRule(RelativeLayout.CENTER_VERTICAL);
        parentLayout.addView(iconView, iconParams);

        TextView labelView = new TextView(getContext());
        labelView.setId(View.generateViewId());
        labelView.setText(usageResId);
        RelativeLayout.LayoutParams labelParams = new RelativeLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(convertDpToPixel(16), 0, 0, 0);
        labelParams.addRule(RelativeLayout.RIGHT_OF, iconView.getId());
        labelParams.addRule(RelativeLayout.CENTER_VERTICAL);
        parentLayout.addView(labelView, labelParams);

        return parentLayout;
    }

    //TODO: Any use of this method should eventually use dimensions defined in resources
    private int convertDpToPixel(float dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private Drawable constructIcon(Drawable icon, Drawable background) {
        LayerDrawable layered = new LayerDrawable(new Drawable[]{background, icon});
        final int bgLayerIndex = 0;
        final int iconLayerIndex = 1;
        int bgSize = (int) getResources().getDimension(R.dimen.ongoing_appops_dialog_circle_size);
        int iconSize = (int) getResources().getDimension(R.dimen.ongoing_appops_dialog_icon_size);
        layered.setLayerHeight(bgLayerIndex, bgSize);
        layered.setLayerWidth(bgLayerIndex, bgSize);
        layered.setLayerHeight(iconLayerIndex, iconSize);
        layered.setLayerWidth(iconLayerIndex, iconSize);
        layered.setLayerGravity(iconLayerIndex, Gravity.CENTER);
        return layered;
    }


    private void setSensorToggleState(Map<String, Boolean> sensorState, View rootView) {
        if (rootView == null) {
            if (getView() == null) {
                return;
            }
            rootView = getView();
            if (rootView == null) {
                return;
            }
        }

        for (int i = 0; i < sToggleButtons.size(); i++) {
            View toggle = rootView.findViewById(sToggleButtons.valueAt(i));
            String groupName = sToggleButtons.keyAt(i);
            if (!toggle.hasOnClickListeners()) {
                toggle.setOnClickListener((v) -> mViewModel.toggleSensor(groupName));
            }

            TextView groupLabel = toggle.findViewById(R.id.toggle_sensor_name);
            groupLabel.setText(KotlinUtils.INSTANCE.getPermGroupLabel(getContext(), groupName));
            TextView blockedStatus = toggle.findViewById(R.id.toggle_sensor_status);
            ImageView iconView = toggle.findViewById(R.id.toggle_sensor_icon);
            boolean sensorEnabled =
                    !sensorState.containsKey(groupName) || sensorState.get(groupName);

            Drawable icon;
            if (sensorEnabled) {
                blockedStatus.setText(R.string.available);
                toggle.setBackgroundResource(R.drawable.safety_center_button_background);
                icon = KotlinUtils.INSTANCE.getPermGroupIcon(getContext(), groupName, Color.BLACK);
                groupLabel.setTextColor(Color.BLACK);
            } else {
                blockedStatus.setText(R.string.blocked);
                toggle.setBackgroundResource(R.drawable.safety_center_button_background_dark);
                icon = getContext().getDrawable(getBlockedIconResId(groupName));
                icon.setTint(Color.LTGRAY);
                groupLabel.setTextColor(Color.LTGRAY);
            }
            iconView.setImageDrawable(icon);
        }
    }

    private static int getRemovePermissionText(String permissionGroup) {
        return CAMERA.equals(permissionGroup) ? R.string.remove_camera_qs
                : R.string.remove_microphone_qs;
    }

    private static int getSeeUsageText(String permissionGroup) {
        return CAMERA.equals(permissionGroup) ? R.string.camera_usage_qs
                : R.string.microphone_usage_qs;
    }

    private static int getBlockedIconResId(String permissionGroup) {
        switch (permissionGroup) {
            case MICROPHONE:
                return R.drawable.ic_mic_blocked;
            case CAMERA:
                return R.drawable.ic_camera_blocked;
            case LOCATION:
                return R.drawable.ic_location_blocked;
        }
        return -1;
    }
}
