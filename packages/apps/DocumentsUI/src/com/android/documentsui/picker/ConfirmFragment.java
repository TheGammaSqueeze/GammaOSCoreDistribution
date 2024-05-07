package com.android.documentsui.picker;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.android.documentsui.BaseActivity;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Shared;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ConfirmFragment extends DialogFragment {

    private static final String TAG = "ConfirmFragment";

    public static final String CONFIRM_TYPE = "type";
    public static final int TYPE_OVERWRITE = 1;
    public static final int TYPE_OEPN_TREE = 2;

    private ActionHandler<PickActivity> mActions;
    private DocumentInfo mTarget;
    private int mType;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        PickActivity pickActivity = (PickActivity) getActivity();
        if (pickActivity != null) {
            mActions = pickActivity.getInjector().actions;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle arg = (getArguments() != null) ? getArguments() : savedInstanceState;

        mTarget = arg.getParcelable(Shared.EXTRA_DOC);
        mType = arg.getInt(CONFIRM_TYPE);
        final PickActivity pickActivity = (PickActivity) getActivity();
        final PickResult pickResult = pickActivity != null ? pickActivity.getInjector().pickResult : null;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
        String message;
        switch (mType) {
            case TYPE_OVERWRITE:
                message = String.format(getString(R.string.overwrite_file_confirmation_message), mTarget.displayName);
                builder.setMessage(message);
                builder.setPositiveButton(android.R.string.ok, (DialogInterface dialog, int id) -> {
                    if (pickResult != null) {
                        pickResult.increaseActionCount();
                    }
                    if (mActions != null) {
                        mActions.finishPicking(mTarget.getDocumentUri());
                    }
                });
                break;
            case TYPE_OEPN_TREE:
                final Uri treeUri = mTarget.getTreeDocumentUri();
                final BaseActivity activity = (BaseActivity) getActivity();
                final String target = activity != null ? activity.getCurrentTitle() : null;
                final String callingAppName = getCallingAppName(activity);
                final String text = getString(R.string.open_tree_dialog_title, callingAppName, target);
                message = getString(R.string.open_tree_dialog_message, callingAppName, target);

                builder.setTitle(text);
                builder.setMessage(message);
                builder.setPositiveButton(R.string.allow, (DialogInterface dialog, int id) -> {
                    if (pickResult != null) {
                        pickResult.increaseActionCount();
                    }
                    if (mActions != null) {
                        mActions.finishPicking(treeUri);
                    }
                });
                break;
        }

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            // Automatically click the positive button when the dialog is shown
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        });

        return dialog;
    }

    private String getCallingAppName(BaseActivity activity) {
        if (activity == null) {
            return "";
        }

        try {
            String packageName = activity.getCallingPackage();
            PackageManager packageManager = activity.getPackageManager();
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            return (String) packageManager.getApplicationLabel(appInfo);
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable(Shared.EXTRA_DOC, mTarget);
        outState.putInt(CONFIRM_TYPE, mType);
    }

    public static void show(FragmentManager fm, DocumentInfo overwriteTarget, int type) {
        Bundle arg = new Bundle();
        arg.putParcelable(Shared.EXTRA_DOC, overwriteTarget);
        arg.putInt(CONFIRM_TYPE, type);

        FragmentTransaction ft = fm.beginTransaction();
        Fragment f = new ConfirmFragment();
        f.setArguments(arg);
        ft.add(f, TAG);
        ft.commitAllowingStateLoss();
    }
}
