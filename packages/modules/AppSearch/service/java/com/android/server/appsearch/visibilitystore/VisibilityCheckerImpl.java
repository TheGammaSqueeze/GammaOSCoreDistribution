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
package com.android.server.appsearch.visibilitystore;

import static android.Manifest.permission.READ_ASSISTANT_APP_SEARCH_DATA;
import static android.Manifest.permission.READ_CALENDAR;
import static android.Manifest.permission.READ_CONTACTS;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.READ_GLOBAL_APP_SEARCH_DATA;
import static android.Manifest.permission.READ_HOME_APP_SEARCH_DATA;
import static android.Manifest.permission.READ_SMS;
import static android.permission.PermissionManager.PERMISSION_GRANTED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.VisibilityDocument;
import android.content.AttributionSource;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.permission.PermissionManager;

import com.android.server.appsearch.external.localstorage.visibilitystore.CallerAccess;
import com.android.server.appsearch.external.localstorage.visibilitystore.VisibilityChecker;
import com.android.server.appsearch.external.localstorage.visibilitystore.VisibilityStore;
import com.android.server.appsearch.util.PackageUtil;

import java.util.Objects;
import java.util.Set;

/**
 * A platform implementation of {@link VisibilityChecker}.
 *
 * @hide
 */
public class VisibilityCheckerImpl implements VisibilityChecker {
    // Context of the user that the call is being made as.
    private final Context mUserContext;
    private final PermissionManager mPermissionManager;

    public VisibilityCheckerImpl(@NonNull Context userContext) {
        mUserContext = Objects.requireNonNull(userContext);
        mPermissionManager = userContext.getSystemService(PermissionManager.class);
    }

    @Override
    public boolean isSchemaSearchableByCaller(
            @NonNull CallerAccess callerAccess,
            @NonNull String packageName,
            @NonNull String prefixedSchema,
            @NonNull VisibilityStore visibilityStore) {
        Objects.requireNonNull(callerAccess);
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(prefixedSchema);
        if (packageName.equals(VisibilityStore.VISIBILITY_PACKAGE_NAME)) {
            return false; // VisibilityStore schemas are for internal bookkeeping.
        }

        FrameworkCallerAccess frameworkCallerAccess = (FrameworkCallerAccess) callerAccess;
        VisibilityDocument visibilityDocument = visibilityStore.getVisibility(prefixedSchema);
        if (visibilityDocument == null) {
            // The target schema doesn't exist yet. We will treat it as default setting and the only
            // accessible case is that the caller has system access.
            return frameworkCallerAccess.doesCallerHaveSystemAccess();
        }

        if (frameworkCallerAccess.doesCallerHaveSystemAccess() &&
                !visibilityDocument.isNotDisplayedBySystem()) {
            return true;
        }

        if (isSchemaVisibleToPackages(visibilityDocument,
                frameworkCallerAccess.getCallingAttributionSource().getUid())) {
            // The caller is in the allow list and has access to the given schema.
            return true;
        }

        // Checker whether the caller has all required for the given schema.
        return isSchemaVisibleToPermission(visibilityDocument,
                frameworkCallerAccess.getCallingAttributionSource());
    }

    /**
     * Returns whether the schema is accessible by the {@code callerUid}. Checks that the callerUid
     * has one of the allowed PackageIdentifier's package. And if so, that the package also has the
     * matching certificate.
     *
     * <p>This supports packages that have certificate rotation. As long as the specified
     * certificate was once used to sign the package, the package will still be granted access. This
     * does not handle packages that have been signed by multiple certificates.
     */
    private boolean isSchemaVisibleToPackages(@NonNull VisibilityDocument visibilityDocument,
            int callerUid) {
        String[] packageNames = visibilityDocument.getPackageNames();
        byte[][] sha256Certs = visibilityDocument.getSha256Certs();
        if (packageNames.length != sha256Certs.length) {
            // We always set them in pair, So this must has something wrong.
            throw new IllegalArgumentException("Package names and sha 256 certs doesn't match!");
        }
        for (int i = 0; i < packageNames.length; i++) {
            // TODO(b/169883602): Consider caching the UIDs of packages. Looking this up in the
            // package manager could be costly. We would also need to update the cache on
            // package-removals.

            // 'callerUid' is the uid of the caller. The 'user' doesn't have to be the same one as
            // the callerUid since clients can createContextAsUser with some other user, and then
            // make calls to us. So just check if the appId portion of the uid is the same. This is
            // essentially UserHandle.isSameApp, but that's not a system API for us to use.
            int callerAppId = UserHandle.getAppId(callerUid);
            int packageUid = PackageUtil.getPackageUid(mUserContext, packageNames[i]);
            int userAppId = UserHandle.getAppId(packageUid);
            if (callerAppId != userAppId) {
                continue;
            }

            // Check that the package also has the matching certificate
            if (mUserContext
                    .getPackageManager()
                    .hasSigningCertificate(
                            packageNames[i],
                            sha256Certs[i],
                            PackageManager.CERT_INPUT_SHA256)) {
                // The caller has the right package name and right certificate!
                return true;
            }
        }
        // If we can't verify the schema is package accessible, default to no access.
        return false;
    }

    /**
     * Returns whether the caller holds required permissions for the given schema.
     */
    private boolean isSchemaVisibleToPermission(@NonNull VisibilityDocument visibilityDocument,
            @Nullable AttributionSource callerAttributionSource) {
        Set<Set<Integer>> visibleToPermissions = visibilityDocument.getVisibleToPermissions();
        if (visibleToPermissions == null || visibleToPermissions.isEmpty()
                || callerAttributionSource == null) {
            // Provider doesn't set any permissions or there is no caller attribution source,
            // default is not accessible to anyone.
            return false;
        }
        for (Set<Integer> allRequiredPermissions : visibleToPermissions) {
            // User may set multiple required permission sets. Provider need to hold ALL required
            // permission of ANY of the individual value sets.
            if (doesCallerHoldsAllRequiredPermissions(allRequiredPermissions,
                    callerAttributionSource)) {
                // The calling package has all required permissions in this set, return true.
                return true;
            }
        }
        // The calling doesn't hold all required permissions for any individual sets, return false.
        return false;
    }

    /** Returns true if the caller holds all required permission in the given set. */
    private boolean doesCallerHoldsAllRequiredPermissions(
            @NonNull Set<Integer> allRequiredPermissions,
            @NonNull AttributionSource callerAttributionSource) {
        for (int requiredPermission : allRequiredPermissions) {
            String permission;
            switch (requiredPermission) {
                case SetSchemaRequest.READ_SMS:
                    permission = READ_SMS;
                    break;
                case SetSchemaRequest.READ_CALENDAR:
                    permission = READ_CALENDAR;
                    break;
                case SetSchemaRequest.READ_CONTACTS:
                    permission = READ_CONTACTS;
                    break;
                case SetSchemaRequest.READ_EXTERNAL_STORAGE:
                    permission = READ_EXTERNAL_STORAGE;
                    break;
                case SetSchemaRequest.READ_HOME_APP_SEARCH_DATA:
                    permission = READ_HOME_APP_SEARCH_DATA;
                    break;
                case SetSchemaRequest.READ_ASSISTANT_APP_SEARCH_DATA:
                    permission = READ_ASSISTANT_APP_SEARCH_DATA;
                    break;
                default:
                    throw new UnsupportedOperationException(
                            "The required permission is unsupported in AppSearch : "
                                    + requiredPermission);
            }
            if (PERMISSION_GRANTED != mPermissionManager.checkPermissionForDataDelivery(
                    permission, callerAttributionSource, /*message=*/"appsearch")) {
                // The calling package doesn't have this required permission, return false.
                return false;
            }
        }
        // The calling package has all required permissions in this set, return true.
        return true;
    }

    /**
     * Checks whether the given package has access to system-surfaceable schemas.
     *
     * @param callerPackageName Package name of the caller.
     */
    public boolean doesCallerHaveSystemAccess(@NonNull String callerPackageName) {
        Objects.requireNonNull(callerPackageName);
        return mUserContext.getPackageManager()
                .checkPermission(READ_GLOBAL_APP_SEARCH_DATA, callerPackageName)
                == PackageManager.PERMISSION_GRANTED;
    }
}
