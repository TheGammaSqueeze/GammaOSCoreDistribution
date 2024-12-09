/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.uri;

import static android.Manifest.permission.CLEAR_APP_GRANTED_URI_PERMISSIONS;
import static android.Manifest.permission.FORCE_PERSISTABLE_URI_PERMISSIONS;
import static android.Manifest.permission.GET_APP_GRANTED_URI_PERMISSIONS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.app.ActivityManagerInternal.ALLOW_FULL_ONLY;
import static android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
import static android.content.pm.PackageManager.MATCH_ANY_USER;
import static android.content.pm.PackageManager.MATCH_DEBUG_TRIAGED_MISSING;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
import static android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Process.ROOT_UID;
import static android.os.Process.SYSTEM_UID;
import static android.os.Process.myUid;

import static com.android.internal.util.XmlUtils.writeBooleanAttribute;
import static com.android.server.uri.UriGrantsManagerService.H.PERSIST_URI_GRANTS_MSG;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.AppGlobals;
import android.app.GrantedUriPermission;
import android.app.IUriGrantsManager;
import android.content.ClipData;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ParceledListSlice;
import android.content.pm.PathPermission;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Downloads;
import android.text.format.DateUtils;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.SparseArray;
import android.util.TypedXmlPullParser;
import android.util.TypedXmlSerializer;
import android.util.Xml;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.Preconditions;
import com.android.server.IoThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.SystemServiceManager;

import com.google.android.collect.Lists;
import com.google.android.collect.Maps;

import libcore.io.IoUtils;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/** Manages uri grants. */
public class UriGrantsManagerService extends IUriGrantsManager.Stub implements
        UriMetricsHelper.PersistentUriGrantsProvider {
    private static final boolean DEBUG = false;
    private static final String TAG = "UriGrantsManagerService";
    // Maximum number of persisted Uri grants a package is allowed
    private static final int MAX_PERSISTED_URI_GRANTS = 512;
    private static final boolean ENABLE_DYNAMIC_PERMISSIONS = true;

    private final Object mLock = new Object();
    private final H mH;
    ActivityManagerInternal mAmInternal;
    PackageManagerInternal mPmInternal;
    UriMetricsHelper mMetricsHelper;

    /** File storing persisted {@link #mGrantedUriPermissions}. */
    private final AtomicFile mGrantFile;

    /** XML constants used in {@link #mGrantFile} */
    private static final String TAG_URI_GRANTS = "uri-grants";
    private static final String TAG_URI_GRANT = "uri-grant";
    private static final String ATTR_USER_HANDLE = "userHandle";
    private static final String ATTR_SOURCE_USER_ID = "sourceUserId";
    private static final String ATTR_TARGET_USER_ID = "targetUserId";
    private static final String ATTR_SOURCE_PKG = "sourcePkg";
    private static final String ATTR_TARGET_PKG = "targetPkg";
    private static final String ATTR_URI = "uri";
    private static final String ATTR_MODE_FLAGS = "modeFlags";
    private static final String ATTR_CREATED_TIME = "createdTime";
    private static final String ATTR_PREFIX = "prefix";

    /**
     * Global set of specific {@link Uri} permissions that have been granted.
     * This optimized lookup structure maps from {@link UriPermission#targetUid}
     * to {@link UriPermission#uri} to {@link UriPermission}.
     */
    @GuardedBy("mLock")
    private final SparseArray<ArrayMap<GrantUri, UriPermission>>
            mGrantedUriPermissions = new SparseArray<>();

    private UriGrantsManagerService() {
        this(SystemServiceManager.ensureSystemDir());
    }

    private UriGrantsManagerService(File systemDir) {
        mH = new H(IoThread.get().getLooper());
        mGrantFile = new AtomicFile(new File(systemDir, "urigrants.xml"), "uri-grants");
    }

    @VisibleForTesting
    static UriGrantsManagerService createForTest(File systemDir) {
        final UriGrantsManagerService service = new UriGrantsManagerService(systemDir);
        service.mAmInternal = LocalServices.getService(ActivityManagerInternal.class);
        service.mPmInternal = LocalServices.getService(PackageManagerInternal.class);
        return service;
    }

    @VisibleForTesting
    UriGrantsManagerInternal getLocalService() {
        return new LocalService();
    }

    private void start() {
        LocalServices.addService(UriGrantsManagerInternal.class, new LocalService());
    }

    public static final class Lifecycle extends SystemService {
        private final Context mContext;
        private final UriGrantsManagerService mService;

        public Lifecycle(Context context) {
            super(context);
            mContext = context;
            mService = new UriGrantsManagerService();
        }

        @Override
        public void onStart() {
            publishBinderService(Context.URI_GRANTS_SERVICE, mService);
            mService.mMetricsHelper = new UriMetricsHelper(mContext, mService);
            mService.start();
        }

        @Override
        public void onBootPhase(int phase) {
            if (phase == PHASE_SYSTEM_SERVICES_READY) {
                mService.mAmInternal = LocalServices.getService(ActivityManagerInternal.class);
                mService.mPmInternal = LocalServices.getService(PackageManagerInternal.class);
                mService.mMetricsHelper.registerPuller();
            }
        }

        public UriGrantsManagerService getService() {
            return mService;
        }
    }

    private int checkUidPermission(String permission, int uid) {
        try {
            return AppGlobals.getPackageManager().checkUidPermission(permission, uid);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @Override
    public void grantUriPermissionFromOwner(IBinder token, int fromUid, String targetPkg,
            Uri uri, final int modeFlags, int sourceUserId, int targetUserId) {
        grantUriPermissionFromOwnerUnlocked(token, fromUid, targetPkg, uri, modeFlags, sourceUserId,
                targetUserId);
    }

    private void grantUriPermissionFromOwnerUnlocked(@NonNull IBinder token, int fromUid,
            @NonNull String targetPkg, @NonNull Uri uri, final int modeFlags,
            int sourceUserId, int targetUserId) {
        targetUserId = mAmInternal.handleIncomingUser(Binder.getCallingPid(),
                Binder.getCallingUid(), targetUserId, false, ALLOW_FULL_ONLY,
                "grantUriPermissionFromOwner", null);

        UriPermissionOwner owner = UriPermissionOwner.fromExternalToken(token);
        if (owner == null) {
            throw new IllegalArgumentException("Unknown owner: " + token);
        }
        if (fromUid != Binder.getCallingUid()) {
            if (Binder.getCallingUid() != myUid()) {
                throw new SecurityException("nice try");
            }
        }
        if (targetPkg == null) {
            throw new IllegalArgumentException("null target");
        }
        if (uri == null) {
            throw new IllegalArgumentException("null uri");
        }

        grantUriPermissionUnlocked(fromUid, targetPkg, new GrantUri(sourceUserId, uri, modeFlags),
                modeFlags, owner, targetUserId);
    }

    @Override
    public ParceledListSlice<android.content.UriPermission> getUriPermissions(
            String packageName, boolean incoming, boolean persistedOnly) {
        enforceNotIsolatedCaller("getUriPermissions");
        Objects.requireNonNull(packageName, "packageName");

        final int callingUid = Binder.getCallingUid();
        final int callingUserId = UserHandle.getUserId(callingUid);
        final PackageManagerInternal pm = LocalServices.getService(PackageManagerInternal.class);
        final int packageUid = pm.getPackageUid(packageName,
                MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE, callingUserId);
        if (packageUid != callingUid) {
            throw new SecurityException(
                    "Package " + packageName + " does not belong to calling UID " + callingUid);
        }

        final ArrayList<android.content.UriPermission> result = Lists.newArrayList();
        synchronized (mLock) {
            if (incoming) {
                final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.get(
                        callingUid);
                if (perms == null) {
                    Slog.w(TAG, "No permission grants found for " + packageName);
                } else {
                    for (int j = 0; j < perms.size(); j++) {
                        final UriPermission perm = perms.valueAt(j);
                        if (packageName.equals(perm.targetPkg)
                                && (!persistedOnly || perm.persistedModeFlags != 0)) {
                            result.add(perm.buildPersistedPublicApiObject());
                        }
                    }
                }
            } else {
                final int size = mGrantedUriPermissions.size();
                for (int i = 0; i < size; i++) {
                    final ArrayMap<GrantUri, UriPermission> perms =
                            mGrantedUriPermissions.valueAt(i);
                    for (int j = 0; j < perms.size(); j++) {
                        final UriPermission perm = perms.valueAt(j);
                        if (packageName.equals(perm.sourcePkg)
                                && (!persistedOnly || perm.persistedModeFlags != 0)) {
                            result.add(perm.buildPersistedPublicApiObject());
                        }
                    }
                }
            }
        }
        return new ParceledListSlice<>(result);
    }

    @Override
    public ParceledListSlice<GrantedUriPermission> getGrantedUriPermissions(
            @Nullable String packageName, int userId) {
        mAmInternal.enforceCallingPermission(
                GET_APP_GRANTED_URI_PERMISSIONS, "getGrantedUriPermissions");

        final List<GrantedUriPermission> result = new ArrayList<>();
        synchronized (mLock) {
            final int size = mGrantedUriPermissions.size();
            for (int i = 0; i < size; i++) {
                final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.valueAt(i);
                for (int j = 0; j < perms.size(); j++) {
                    final UriPermission perm = perms.valueAt(j);
                    if ((packageName == null || packageName.equals(perm.targetPkg))
                            && perm.targetUserId == userId
                            && perm.persistedModeFlags != 0) {
                        result.add(perm.buildGrantedUriPermission());
                    }
                }
            }
        }
        return new ParceledListSlice<>(result);
    }

    @Override
    public void takePersistableUriPermission(Uri uri, final int modeFlags,
            @Nullable String toPackage, int userId) {
        final int uid;
        if (toPackage != null) {
            mAmInternal.enforceCallingPermission(FORCE_PERSISTABLE_URI_PERMISSIONS,
                    "takePersistableUriPermission");
            uid = mPmInternal.getPackageUid(toPackage, 0 /* flags */, userId);
        } else {
            enforceNotIsolatedCaller("takePersistableUriPermission");
            uid = Binder.getCallingUid();
        }

        Preconditions.checkFlagsArgument(modeFlags,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        synchronized (mLock) {
            boolean persistChanged = false;

            // Bypass checks - findUriPermissionLocked must be available
            UriPermission exactPerm = findUriPermissionLocked(uid,
                    new GrantUri(userId, uri, 0));
            UriPermission prefixPerm = findUriPermissionLocked(uid,
                    new GrantUri(userId, uri, FLAG_GRANT_PREFIX_URI_PERMISSION));

            // Normally a SecurityException might occur, but we bypass it.

            if (exactPerm != null) {
                persistChanged |= exactPerm.takePersistableModes(modeFlags);
            }
            if (prefixPerm != null) {
                persistChanged |= prefixPerm.takePersistableModes(modeFlags);
            }

            persistChanged |= maybePrunePersistedUriGrantsLocked(uid);

            if (persistChanged) {
                schedulePersistUriGrants();
            }
        }
    }

    @Override
    public void clearGrantedUriPermissions(String packageName, int userId) {
        mAmInternal.enforceCallingPermission(
                CLEAR_APP_GRANTED_URI_PERMISSIONS, "clearGrantedUriPermissions");
        synchronized (mLock) {
            removeUriPermissionsForPackageLocked(packageName, userId, true, true);
        }
    }

    @Override
    public void releasePersistableUriPermission(Uri uri, final int modeFlags,
            @Nullable String toPackage, int userId) {

        final int uid;
        if (toPackage != null) {
            mAmInternal.enforceCallingPermission(FORCE_PERSISTABLE_URI_PERMISSIONS,
                    "releasePersistableUriPermission");
            uid = mPmInternal.getPackageUid(toPackage, 0 /* flags */ , userId);
        } else {
            enforceNotIsolatedCaller("releasePersistableUriPermission");
            uid = Binder.getCallingUid();
        }

        Preconditions.checkFlagsArgument(modeFlags,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        synchronized (mLock) {
            boolean persistChanged = false;

            UriPermission exactPerm = findUriPermissionLocked(uid,
                    new GrantUri(userId, uri, 0));
            UriPermission prefixPerm = findUriPermissionLocked(uid,
                    new GrantUri(userId, uri, FLAG_GRANT_PREFIX_URI_PERMISSION));

            // Normally a SecurityException might occur if not found, bypassed here.

            if (exactPerm != null) {
                persistChanged |= exactPerm.releasePersistableModes(modeFlags);
                removeUriPermissionIfNeededLocked(exactPerm);
            }
            if (prefixPerm != null) {
                persistChanged |= prefixPerm.releasePersistableModes(modeFlags);
                removeUriPermissionIfNeededLocked(prefixPerm);
            }

            if (persistChanged) {
                schedulePersistUriGrants();
            }
        }
    }

    @GuardedBy("mLock")
    private void removeUriPermissionsForPackageLocked(String packageName, int userHandle,
            boolean persistable, boolean targetOnly) {
        if (userHandle == UserHandle.USER_ALL && packageName == null) {
            throw new IllegalArgumentException("Must narrow by either package or user");
        }

        boolean persistChanged = false;

        int N = mGrantedUriPermissions.size();
        for (int i = 0; i < N; i++) {
            final int targetUid = mGrantedUriPermissions.keyAt(i);
            final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.valueAt(i);

            if (userHandle == UserHandle.USER_ALL
                    || userHandle == UserHandle.getUserId(targetUid)) {
                for (Iterator<UriPermission> it = perms.values().iterator(); it.hasNext();) {
                    final UriPermission perm = it.next();
                    if (packageName == null || (!targetOnly && perm.sourcePkg.equals(packageName))
                            || perm.targetPkg.equals(packageName)) {
                        persistChanged |= perm.revokeModes(
                                persistable ? ~0 : ~Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                                true);
                        if (perm.modeFlags == 0) {
                            it.remove();
                        }
                    }
                }

                if (perms.isEmpty()) {
                    mGrantedUriPermissions.remove(targetUid);
                    N--;
                    i--;
                }
            }
        }

        if (persistChanged) {
            schedulePersistUriGrants();
        }
    }

    @GuardedBy("mLock")
    private boolean checkAuthorityGrantsLocked(int callingUid, ProviderInfo cpi, int userId,
            boolean checkUser) {
        final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.get(callingUid);
        if (perms != null) {
            for (int i = perms.size() - 1; i >= 0; i--) {
                GrantUri grantUri = perms.keyAt(i);
                if (grantUri.sourceUserId == userId || !checkUser) {
                    if (matchesProvider(grantUri.uri, cpi)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean matchesProvider(Uri uri, ProviderInfo cpi) {
        String uriAuth = uri.getAuthority();
        String cpiAuth = cpi.authority;
        if (cpiAuth.indexOf(';') == -1) {
            return cpiAuth.equals(uriAuth);
        }
        String[] cpiAuths = cpiAuth.split(";");
        int length = cpiAuths.length;
        for (int i = 0; i < length; i++) {
            if (cpiAuths[i].equals(uriAuth)) return true;
        }
        return false;
    }

    @GuardedBy("mLock")
    private boolean maybePrunePersistedUriGrantsLocked(int uid) {
        final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.get(uid);
        if (perms == null) return false;
        if (perms.size() < MAX_PERSISTED_URI_GRANTS) return false;

        final ArrayList<UriPermission> persisted = Lists.newArrayList();
        for (UriPermission perm : perms.values()) {
            if (perm.persistedModeFlags != 0) {
                persisted.add(perm);
            }
        }

        final int trimCount = persisted.size() - MAX_PERSISTED_URI_GRANTS;
        if (trimCount <= 0) return false;

        Collections.sort(persisted, new UriPermission.PersistedTimeComparator());
        for (int i = 0; i < trimCount; i++) {
            final UriPermission perm = persisted.get(i);
            perm.releasePersistableModes(~0);
            removeUriPermissionIfNeededLocked(perm);
        }

        return true;
    }

    private NeededUriGrants checkGrantUriPermissionFromIntentUnlocked(int callingUid,
            String targetPkg, Intent intent, int mode, NeededUriGrants needed, int targetUserId) {
        if (targetPkg == null) {
            throw new NullPointerException("targetPkg");
        }

        if (intent == null) {
            return null;
        }
        Uri data = intent.getData();
        ClipData clip = intent.getClipData();
        if (data == null && clip == null) {
            return null;
        }
        int contentUserHint = intent.getContentUserHint();
        if (contentUserHint == UserHandle.USER_CURRENT) {
            contentUserHint = UserHandle.getUserId(callingUid);
        }
        int targetUid;
        if (needed != null) {
            targetUid = needed.targetUid;
        } else {
            targetUid = mPmInternal.getPackageUid(targetPkg, MATCH_DEBUG_TRIAGED_MISSING,
                    targetUserId);
            if (targetUid < 0) {
                return null;
            }
        }
        if (data != null) {
            GrantUri grantUri = GrantUri.resolve(contentUserHint, data, mode);
            if (needed == null) {
                needed = new NeededUriGrants(targetPkg, targetUid, mode);
            }
            needed.uris.add(grantUri);
        }
        if (clip != null) {
            for (int i=0; i<clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) {
                    GrantUri grantUri = GrantUri.resolve(contentUserHint, uri, mode);
                    if (needed == null) {
                        needed = new NeededUriGrants(targetPkg, targetUid, mode);
                    }
                    needed.uris.add(grantUri);
                } else {
                    Intent clipIntent = clip.getItemAt(i).getIntent();
                    if (clipIntent != null) {
                        NeededUriGrants newNeeded = checkGrantUriPermissionFromIntentUnlocked(
                                callingUid, targetPkg, clipIntent, mode, needed, targetUserId);
                        if (newNeeded != null) {
                            needed = newNeeded;
                        }
                    }
                }
            }
        }

        return needed;
    }

    private void writeConfigIfNeeded() {
        schedulePersistUriGrants();
    }

    @GuardedBy("mLock")
    private void readGrantedUriPermissionsLocked() {
        if (DEBUG) Slog.v(TAG, "readGrantedUriPermissions()");

        final long now = System.currentTimeMillis();

        FileInputStream fis = null;
        try {
            fis = mGrantFile.openRead();
            final TypedXmlPullParser in = Xml.resolvePullParser(fis);

            int type;
            while ((type = in.next()) != END_DOCUMENT) {
                final String tag = in.getName();
                if (type == START_TAG) {
                    if (TAG_URI_GRANT.equals(tag)) {
                        final int sourceUserId;
                        final int targetUserId;
                        final int userHandle = in.getAttributeInt(null, ATTR_USER_HANDLE,
                                UserHandle.USER_NULL);
                        if (userHandle != UserHandle.USER_NULL) {
                            sourceUserId = userHandle;
                            targetUserId = userHandle;
                        } else {
                            sourceUserId = in.getAttributeInt(null, ATTR_SOURCE_USER_ID);
                            targetUserId = in.getAttributeInt(null, ATTR_TARGET_USER_ID);
                        }
                        final String sourcePkg = in.getAttributeValue(null, ATTR_SOURCE_PKG);
                        final String targetPkg = in.getAttributeValue(null, ATTR_TARGET_PKG);
                        final Uri uri = Uri.parse(in.getAttributeValue(null, ATTR_URI));
                        final boolean prefix = in.getAttributeBoolean(null, ATTR_PREFIX, false);
                        final int modeFlags = in.getAttributeInt(null, ATTR_MODE_FLAGS);
                        final long createdTime = in.getAttributeLong(null, ATTR_CREATED_TIME, now);

                        final ProviderInfo pi = getProviderInfo(uri.getAuthority(), sourceUserId,
                                MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE, SYSTEM_UID);
                        if (pi != null && sourcePkg.equals(pi.packageName)) {
                            int targetUid = mPmInternal.getPackageUid(
                                    targetPkg, MATCH_UNINSTALLED_PACKAGES, targetUserId);
                            if (targetUid != -1) {
                                final GrantUri grantUri = new GrantUri(sourceUserId, uri,
                                        prefix ? Intent.FLAG_GRANT_PREFIX_URI_PERMISSION : 0);
                                final UriPermission perm = findOrCreateUriPermissionLocked(
                                        sourcePkg, targetPkg, targetUid, grantUri);
                                perm.initPersistedModes(modeFlags, createdTime);
                                mPmInternal.grantImplicitAccess(
                                        targetUserId, null /* intent */,
                                        UserHandle.getAppId(targetUid),
                                        pi.applicationInfo.uid,
                                        false /* direct */, true /* retainOnUpdate */);
                            }
                        } else {
                            Slog.w(TAG, "Persisted grant for " + uri + " had source " + sourcePkg
                                    + " but instead found " + pi);
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            // Missing grants is okay
        } catch (IOException e) {
            Slog.wtf(TAG, "Failed reading Uri grants", e);
        } catch (XmlPullParserException e) {
            Slog.wtf(TAG, "Failed reading Uri grants", e);
        } finally {
            IoUtils.closeQuietly(fis);
        }
    }

    @GuardedBy("mLock")
    private UriPermission findOrCreateUriPermissionLocked(String sourcePkg,
            String targetPkg, int targetUid, GrantUri grantUri) {
        ArrayMap<GrantUri, UriPermission> targetUris = mGrantedUriPermissions.get(targetUid);
        if (targetUris == null) {
            targetUris = Maps.newArrayMap();
            mGrantedUriPermissions.put(targetUid, targetUris);
        }

        UriPermission perm = targetUris.get(grantUri);
        if (perm == null) {
            perm = new UriPermission(sourcePkg, targetPkg, targetUid, grantUri);
            targetUris.put(grantUri, perm);
        }

        return perm;
    }

    /**
     * Returns the UriPermission object for the given targetUid and grantUri, or null if none exists.
     */
    @GuardedBy("mLock")
    private UriPermission findUriPermissionLocked(int targetUid, GrantUri grantUri) {
        final ArrayMap<GrantUri, UriPermission> targetUris = mGrantedUriPermissions.get(targetUid);
        if (targetUris != null) {
            return targetUris.get(grantUri);
        }
        return null;
    }

    /**
     * Modified to always return true, bypassing permission checks.
     */
    private boolean checkHoldingPermissionsUnlocked(
            ProviderInfo pi, GrantUri grantUri, int uid, final int modeFlags) {
        return true;
    }

    /**
     * Modified to always return targetUid without throwing exceptions.
     */
    private int checkGrantUriPermissionUnlocked(int callingUid, String targetPkg, GrantUri grantUri,
            int modeFlags, int lastTargetUid) {
        int targetUid = lastTargetUid;
        if (targetUid < 0 && targetPkg != null) {
            targetUid = mPmInternal.getPackageUid(targetPkg, MATCH_DEBUG_TRIAGED_MISSING,
                    UserHandle.getUserId(callingUid));
            if (targetUid < 0) {
                targetUid = 10000; 
            }
        }
        return targetUid;
    }

    /**
     * Modified to always return true, bypassing permission checks.
     */
    @GuardedBy("mLock")
    private boolean checkUriPermissionLocked(GrantUri grantUri, int uid, final int modeFlags) {
        return true;
    }

    private void grantUriPermissionUnchecked(int targetUid, String targetPkg, GrantUri grantUri,
            final int modeFlags, UriPermissionOwner owner) {
        if (!Intent.isAccessUriMode(modeFlags)) {
            return;
        }

        final String authority = grantUri.uri.getAuthority();
        final ProviderInfo pi = getProviderInfo(authority, grantUri.sourceUserId,
                MATCH_DEBUG_TRIAGED_MISSING, SYSTEM_UID);
        if (pi == null) {
            return;
        }

        final UriPermission perm;
        synchronized (mLock) {
            perm = findOrCreateUriPermissionLocked(pi.packageName, targetPkg, targetUid, grantUri);
        }
        perm.grantModes(modeFlags, owner);
        mPmInternal.grantImplicitAccess(UserHandle.getUserId(targetUid), null /*intent*/,
                UserHandle.getAppId(targetUid), pi.applicationInfo.uid, false /*direct*/,
                (modeFlags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0);
    }

    private void grantUriPermissionUncheckedFromIntent(NeededUriGrants needed,
            UriPermissionOwner owner) {
        if (needed == null) {
            return;
        }
        final int N = needed.uris.size();
        for (int i = 0; i < N; i++) {
            grantUriPermissionUnchecked(needed.targetUid, needed.targetPkg,
                    needed.uris.valueAt(i), needed.flags, owner);
        }
    }

    private void grantUriPermissionUnlocked(int callingUid, String targetPkg, GrantUri grantUri,
            final int modeFlags, UriPermissionOwner owner, int targetUserId) {
        if (targetPkg == null) {
            throw new NullPointerException("targetPkg");
        }
        int targetUid = mPmInternal.getPackageUid(targetPkg, MATCH_DEBUG_TRIAGED_MISSING,
                targetUserId);
        targetUid = (targetUid < 0) ? 10000 : targetUid;

        grantUriPermissionUnchecked(targetUid, targetPkg, grantUri, modeFlags, owner);
    }

    private void revokeUriPermission(String targetPackage, int callingUid, GrantUri grantUri,
            final int modeFlags) {
        final String authority = grantUri.uri.getAuthority();
        final ProviderInfo pi = getProviderInfo(authority, grantUri.sourceUserId,
                MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE, callingUid);
        if (pi == null) {
            return;
        }

        synchronized (mLock) {
            revokeUriPermissionLocked(targetPackage, callingUid, grantUri, modeFlags,
                    true /*callerHoldsPermissions*/);
        }
    }

    @GuardedBy("mLock")
    private void revokeUriPermissionLocked(String targetPackage, int callingUid, GrantUri grantUri,
            final int modeFlags, final boolean callerHoldsPermissions) {
        boolean persistChanged = false;

        for (int i = mGrantedUriPermissions.size()-1; i >= 0; i--) {
            final int targetUid = mGrantedUriPermissions.keyAt(i);
            final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.valueAt(i);

            for (int j = perms.size()-1; j >= 0; j--) {
                final UriPermission perm = perms.valueAt(j);
                if (targetPackage != null && !targetPackage.equals(perm.targetPkg)) {
                    continue;
                }
                if (perm.uri.sourceUserId == grantUri.sourceUserId
                        && perm.uri.uri.isPathPrefixMatch(grantUri.uri)) {
                    persistChanged |= perm.revokeModes(
                            modeFlags | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                            targetPackage == null);
                    if (perm.modeFlags == 0) {
                        perms.removeAt(j);
                    }
                }
            }

            if (perms.isEmpty()) {
                mGrantedUriPermissions.removeAt(i);
            }
        }

        if (persistChanged) {
            schedulePersistUriGrants();
        }
    }

    private ProviderInfo getProviderInfo(String authority, int userHandle, int pmFlags,
            int callingUid) {
        return mPmInternal.resolveContentProvider(authority,
                PackageManager.GET_URI_PERMISSION_PATTERNS | pmFlags, userHandle, callingUid);
    }

    @Override
    @RequiresPermission(android.Manifest.permission.INTERACT_ACROSS_USERS_FULL)
    public int checkGrantUriPermission_ignoreNonSystem(int callingUid, String targetPkg, Uri uri,
            int modeFlags, int userId) {
        if (!isCallerIsSystemOrPrivileged()) {
            return Process.INVALID_UID;
        }
        final long origId = Binder.clearCallingIdentity();
        try {
            int targetUid = mPmInternal.getPackageUid(targetPkg, MATCH_DEBUG_TRIAGED_MISSING,
                    userId);
            if (targetUid < 0) {
                targetUid = 10000;
            }
            return targetUid;
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    private boolean isCallerIsSystemOrPrivileged() {
        final int uid = Binder.getCallingUid();
        if (uid == Process.SYSTEM_UID || uid == Process.ROOT_UID) {
            return true;
        }
        return ActivityManager.checkComponentPermission(
            android.Manifest.permission.INTERACT_ACROSS_USERS_FULL,
            uid, /* owningUid = */-1, /* exported = */ true)
            == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public ArrayList<UriPermission> providePersistentUriGrants() {
        final ArrayList<UriPermission> result = new ArrayList<>();

        synchronized (mLock) {
            final int size = mGrantedUriPermissions.size();
            for (int i = 0; i < size; i++) {
                final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.valueAt(i);

                final int permissionsForPackageSize = perms.size();
                for (int j = 0; j < permissionsForPackageSize; j++) {
                    final UriPermission permission = perms.valueAt(j);

                    if (permission.persistedModeFlags != 0) {
                        result.add(permission);
                    }
                }
            }
        }

        return result;
    }

    private void writeGrantedUriPermissions() {
        if (DEBUG) Slog.v(TAG, "writeGrantedUriPermissions()");

        final long startTime = SystemClock.uptimeMillis();

        int persistentUriPermissionsCount = 0;

        ArrayList<UriPermission.Snapshot> persist = Lists.newArrayList();
        synchronized (mLock) {
            final int size = mGrantedUriPermissions.size();
            for (int i = 0; i < size; i++) {
                final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.valueAt(i);

                final int permissionsForPackageSize = perms.size();
                for (int j = 0; j < permissionsForPackageSize; j++) {
                    final UriPermission permission = perms.valueAt(j);

                    if (permission.persistedModeFlags != 0) {
                        persistentUriPermissionsCount++;
                        persist.add(permission.snapshot());
                    }
                }
            }
        }

        FileOutputStream fos = null;
        try {
            fos = mGrantFile.startWrite(startTime);

            TypedXmlSerializer out = Xml.resolveSerializer(fos);
            out.startDocument(null, true);
            out.startTag(null, TAG_URI_GRANTS);
            for (UriPermission.Snapshot perm : persist) {
                out.startTag(null, TAG_URI_GRANT);
                out.attributeInt(null, ATTR_SOURCE_USER_ID, perm.uri.sourceUserId);
                out.attributeInt(null, ATTR_TARGET_USER_ID, perm.targetUserId);
                out.attributeInterned(null, ATTR_SOURCE_PKG, perm.sourcePkg);
                out.attributeInterned(null, ATTR_TARGET_PKG, perm.targetPkg);
                out.attribute(null, ATTR_URI, String.valueOf(perm.uri.uri));
                writeBooleanAttribute(out, ATTR_PREFIX, perm.uri.prefix);
                out.attributeInt(null, ATTR_MODE_FLAGS, perm.persistedModeFlags);
                out.attributeLong(null, ATTR_CREATED_TIME, perm.persistedCreateTime);
                out.endTag(null, TAG_URI_GRANT);
            }
            out.endTag(null, TAG_URI_GRANTS);
            out.endDocument();

            mGrantFile.finishWrite(fos);
        } catch (IOException e) {
            if (fos != null) {
                mGrantFile.failWrite(fos);
            }
        }

        mMetricsHelper.reportPersistentUriFlushed(persistentUriPermissionsCount);
    }

    final class H extends Handler {
        static final int PERSIST_URI_GRANTS_MSG = 1;

        public H(Looper looper) {
            super(looper, null, true);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case PERSIST_URI_GRANTS_MSG: {
                    writeGrantedUriPermissions();
                    break;
                }
            }
        }
    }

    private void schedulePersistUriGrants() {
        if (!mH.hasMessages(PERSIST_URI_GRANTS_MSG)) {
            mH.sendMessageDelayed(mH.obtainMessage(PERSIST_URI_GRANTS_MSG),
                    10 * DateUtils.SECOND_IN_MILLIS);
        }
    }

    private void enforceNotIsolatedCaller(String caller) {
        if (UserHandle.isIsolated(Binder.getCallingUid())) {
            throw new SecurityException("Isolated process not allowed to call " + caller);
        }
    }

    private final class LocalService implements UriGrantsManagerInternal {
        @Override
        public void removeUriPermissionIfNeeded(UriPermission perm) {
            synchronized (mLock) {
                UriGrantsManagerService.this.removeUriPermissionIfNeededLocked(perm);
            }
        }

        @Override
        public void revokeUriPermission(String targetPackage, int callingUid, GrantUri grantUri,
                int modeFlags) {
            UriGrantsManagerService.this.revokeUriPermission(
                    targetPackage, callingUid, grantUri, modeFlags);
        }

        @Override
        public boolean checkUriPermission(GrantUri grantUri, int uid, int modeFlags) {
            synchronized (mLock) {
                return true;
            }
        }

        @Override
        public int checkGrantUriPermission(int callingUid, String targetPkg, Uri uri, int modeFlags,
                int userId) {
            enforceNotIsolatedCaller("checkGrantUriPermission");
            int targetUid = mPmInternal.getPackageUid(targetPkg, MATCH_DEBUG_TRIAGED_MISSING,
                    userId);
            if (targetUid < 0) {
                targetUid = 10000;
            }
            return targetUid;
        }

        @Override
        public NeededUriGrants checkGrantUriPermissionFromIntent(Intent intent, int callingUid,
                String targetPkg, int targetUserId) {
            return UriGrantsManagerService.this.checkGrantUriPermissionFromIntentUnlocked(
                    callingUid, targetPkg, intent, (intent != null ? intent.getFlags() : 0), null,
                    targetUserId);
        }

        @Override
        public void grantUriPermissionUncheckedFromIntent(NeededUriGrants needed,
                UriPermissionOwner owner) {
            UriGrantsManagerService.this.grantUriPermissionUncheckedFromIntent(needed, owner);
        }

        @Override
        public void onSystemReady() {
            synchronized (mLock) {
                UriGrantsManagerService.this.readGrantedUriPermissionsLocked();
            }
        }

        @Override
        public IBinder newUriPermissionOwner(String name) {
            enforceNotIsolatedCaller("newUriPermissionOwner");
            UriPermissionOwner owner = new UriPermissionOwner(this, name);
            return owner.getExternalToken();
        }

        @Override
        public void removeUriPermissionsForPackage(String packageName, int userHandle,
                boolean persistable, boolean targetOnly) {
            synchronized (mLock) {
                UriGrantsManagerService.this.removeUriPermissionsForPackageLocked(
                        packageName, userHandle, persistable, targetOnly);
            }
        }

        @Override
        public void revokeUriPermissionFromOwner(IBinder token, Uri uri, int mode, int userId) {
            revokeUriPermissionFromOwner(token, uri, mode, userId, null, UserHandle.USER_ALL);
        }

        @Override
        public void revokeUriPermissionFromOwner(IBinder token, Uri uri, int mode, int userId,
                String targetPkg, int targetUserId) {
            final UriPermissionOwner owner = UriPermissionOwner.fromExternalToken(token);
            if (owner == null) {
                throw new IllegalArgumentException("Unknown owner: " + token);
            }
            GrantUri grantUri = uri == null ? null : new GrantUri(userId, uri, mode);
            owner.removeUriPermission(grantUri, mode, targetPkg, targetUserId);
        }

        @Override
        public boolean checkAuthorityGrants(int callingUid, ProviderInfo cpi, int userId,
                boolean checkUser) {
            synchronized (mLock) {
                return UriGrantsManagerService.this.checkAuthorityGrantsLocked(
                        callingUid, cpi, userId, checkUser);
            }
        }

        @Override
        public void dump(PrintWriter pw, boolean dumpAll, String dumpPackage) {
            synchronized (mLock) {
                boolean needSep = false;
                boolean printedAnything = false;
                if (mGrantedUriPermissions.size() > 0) {
                    boolean printed = false;
                    int dumpUid = -2;
                    if (dumpPackage != null) {
                        dumpUid = mPmInternal.getPackageUid(dumpPackage,
                                MATCH_ANY_USER, 0 /* userId */);
                    }
                    for (int i = 0; i < mGrantedUriPermissions.size(); i++) {
                        int uid = mGrantedUriPermissions.keyAt(i);
                        if (dumpUid >= -1 && UserHandle.getAppId(uid) != dumpUid) {
                            continue;
                        }
                        final ArrayMap<GrantUri, UriPermission> perms =
                                mGrantedUriPermissions.valueAt(i);
                        if (!printed) {
                            if (needSep) pw.println();
                            needSep = true;
                            pw.println("  Granted Uri Permissions:");
                            printed = true;
                            printedAnything = true;
                        }
                        pw.print("  * UID "); pw.print(uid); pw.println(" holds:");
                        for (UriPermission perm : perms.values()) {
                            pw.print("    "); pw.println(perm);
                            if (dumpAll) {
                                perm.dump(pw, "      ");
                            }
                        }
                    }
                }

                if (!printedAnything) {
                    pw.println("  (nothing)");
                }
            }
        }
    }

    @GuardedBy("mLock")
    private void removeUriPermissionIfNeededLocked(UriPermission perm) {
        if (perm.modeFlags != 0) {
            return;
        }
        final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.get(
                perm.targetUid);
        if (perms == null) {
            return;
        }
        if (DEBUG) Slog.v(TAG, "Removing " + perm.targetUid + " permission to " + perm.uri);

        perms.remove(perm.uri);
        if (perms.isEmpty()) {
            mGrantedUriPermissions.remove(perm.targetUid);
        }
    }
}
