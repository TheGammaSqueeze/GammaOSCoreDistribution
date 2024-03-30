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

package com.android.server.nearby.fastpair.cache;

import static com.android.server.nearby.common.bluetooth.fastpair.BluetoothAddress.maskBluetoothAddress;
import static com.android.server.nearby.fastpair.UserActionHandler.EXTRA_FAST_PAIR_SECRET;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

import com.android.server.nearby.common.ble.util.RangingUtils;
import com.android.server.nearby.common.fastpair.IconUtils;
import com.android.server.nearby.common.locator.Locator;
import com.android.server.nearby.common.locator.LocatorContextWrapper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.Objects;

import service.proto.Cache;

/**
 * Wrapper class around StoredDiscoveryItem. A centralized place for methods related to
 * updating/parsing StoredDiscoveryItem.
 */
public class DiscoveryItem implements Comparable<DiscoveryItem> {

    private static final String ACTION_FAST_PAIR =
            "com.android.server.nearby:ACTION_FAST_PAIR";
    private static final int BEACON_STALENESS_MILLIS = 120000;
    private static final int ITEM_EXPIRATION_MILLIS = 20000;
    private static final int APP_INSTALL_EXPIRATION_MILLIS = 600000;
    private static final int ITEM_DELETABLE_MILLIS = 15000;

    private final FastPairCacheManager mFastPairCacheManager;
    private final Clock mClock;

    private Cache.StoredDiscoveryItem mStoredDiscoveryItem;

    /** IntDef for StoredDiscoveryItem.State */
    @IntDef({
            Cache.StoredDiscoveryItem.State.STATE_ENABLED_VALUE,
            Cache.StoredDiscoveryItem.State.STATE_MUTED_VALUE,
            Cache.StoredDiscoveryItem.State.STATE_DISABLED_BY_SYSTEM_VALUE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ItemState {
    }

    public DiscoveryItem(LocatorContextWrapper locatorContextWrapper,
            Cache.StoredDiscoveryItem mStoredDiscoveryItem) {
        this.mFastPairCacheManager =
                locatorContextWrapper.getLocator().get(FastPairCacheManager.class);
        this.mClock =
                locatorContextWrapper.getLocator().get(Clock.class);
        this.mStoredDiscoveryItem = mStoredDiscoveryItem;
    }

    public DiscoveryItem(Context context, Cache.StoredDiscoveryItem mStoredDiscoveryItem) {
        this.mFastPairCacheManager = Locator.get(context, FastPairCacheManager.class);
        this.mClock = Locator.get(context, Clock.class);
        this.mStoredDiscoveryItem = mStoredDiscoveryItem;
    }

    /** @return A new StoredDiscoveryItem with state fields set to their defaults. */
    public static Cache.StoredDiscoveryItem newStoredDiscoveryItem() {
        Cache.StoredDiscoveryItem.Builder storedDiscoveryItem =
                Cache.StoredDiscoveryItem.newBuilder();
        storedDiscoveryItem.setState(Cache.StoredDiscoveryItem.State.STATE_ENABLED);
        return storedDiscoveryItem.build();
    }

    /**
     * Checks if store discovery item support fast pair or not.
     */
    public boolean isFastPair() {
        Intent intent = parseIntentScheme(mStoredDiscoveryItem.getActionUrl());
        if (intent == null) {
            Log.w("FastPairDiscovery", "FastPair: fail to parse action url"
                    + mStoredDiscoveryItem.getActionUrl());
            return false;
        }
        return ACTION_FAST_PAIR.equals(intent.getAction());
    }

    /**
     * Sets the store discovery item mac address.
     */
    public void setMacAddress(String address) {
        mStoredDiscoveryItem = mStoredDiscoveryItem.toBuilder().setMacAddress(address).build();

        mFastPairCacheManager.saveDiscoveryItem(this);
    }

    /**
     * Checks if the item is expired. Expired items are those over getItemExpirationMillis() eg. 2
     * minutes
     */
    public static boolean isExpired(
            long currentTimestampMillis, @Nullable Long lastObservationTimestampMillis) {
        if (lastObservationTimestampMillis == null) {
            return true;
        }
        return (currentTimestampMillis - lastObservationTimestampMillis)
                >= ITEM_EXPIRATION_MILLIS;
    }

    /**
     * Checks if the item is deletable for saving disk space. Deletable items are those over
     * getItemDeletableMillis eg. over 25 hrs.
     */
    public static boolean isDeletable(
            long currentTimestampMillis, @Nullable Long lastObservationTimestampMillis) {
        if (lastObservationTimestampMillis == null) {
            return true;
        }
        return currentTimestampMillis - lastObservationTimestampMillis
                >= ITEM_DELETABLE_MILLIS;
    }

    /** Checks if the item has a pending app install */
    public boolean isPendingAppInstallValid() {
        return isPendingAppInstallValid(mClock.millis());
    }

    /**
     * Checks if pending app valid.
     */
    public boolean isPendingAppInstallValid(long appInstallMillis) {
        return isPendingAppInstallValid(appInstallMillis, mStoredDiscoveryItem);
    }

    /**
     * Checks if the app install time expired.
     */
    public static boolean isPendingAppInstallValid(
            long currentMillis, Cache.StoredDiscoveryItem storedItem) {
        return currentMillis - storedItem.getPendingAppInstallTimestampMillis()
                < APP_INSTALL_EXPIRATION_MILLIS;
    }


    /** Checks if the item has enough data to be shown */
    public boolean isReadyForDisplay() {
        boolean hasUrlOrPopularApp = !mStoredDiscoveryItem.getActionUrl().isEmpty();

        return !TextUtils.isEmpty(mStoredDiscoveryItem.getTitle()) && hasUrlOrPopularApp;
    }

    /** Checks if the action url is app install */
    public boolean isApp() {
        return mStoredDiscoveryItem.getActionUrlType() == Cache.ResolvedUrlType.APP;
    }

    /** Returns true if an item is muted, or if state is unavailable. */
    public boolean isMuted() {
        return mStoredDiscoveryItem.getState() != Cache.StoredDiscoveryItem.State.STATE_ENABLED;
    }

    /**
     * Returns the state of store discovery item.
     */
    public Cache.StoredDiscoveryItem.State getState() {
        return mStoredDiscoveryItem.getState();
    }

    /** Checks if it's device item. e.g. Chromecast / Wear */
    public static boolean isDeviceType(Cache.NearbyType type) {
        return type == Cache.NearbyType.NEARBY_CHROMECAST
                || type == Cache.NearbyType.NEARBY_WEAR
                || type == Cache.NearbyType.NEARBY_DEVICE;
    }

    /**
     * Check if the type is supported.
     */
    public static boolean isTypeEnabled(Cache.NearbyType type) {
        switch (type) {
            case NEARBY_WEAR:
            case NEARBY_CHROMECAST:
            case NEARBY_DEVICE:
                return true;
            default:
                Log.e("FastPairDiscoveryItem", "Invalid item type " + type.name());
                return false;
        }
    }

    /** Gets hash code of UI related data so we can collapse identical items. */
    public int getUiHashCode() {
        return Objects.hash(
                        mStoredDiscoveryItem.getTitle(),
                        mStoredDiscoveryItem.getDescription(),
                        mStoredDiscoveryItem.getAppName(),
                        mStoredDiscoveryItem.getDisplayUrl(),
                        mStoredDiscoveryItem.getMacAddress());
    }

    // Getters below

    /**
     * Returns the id of store discovery item.
     */
    @Nullable
    public String getId() {
        return mStoredDiscoveryItem.getId();
    }

    /**
     * Returns the title of discovery item.
     */
    @Nullable
    public String getTitle() {
        return mStoredDiscoveryItem.getTitle();
    }

    /**
     * Returns the description of discovery item.
     */
    @Nullable
    public String getDescription() {
        return mStoredDiscoveryItem.getDescription();
    }

    /**
     * Returns the mac address of discovery item.
     */
    @Nullable
    public String getMacAddress() {
        return mStoredDiscoveryItem.getMacAddress();
    }

    /**
     * Returns the display url of discovery item.
     */
    @Nullable
    public String getDisplayUrl() {
        return mStoredDiscoveryItem.getDisplayUrl();
    }

    /**
     * Returns the public key of discovery item.
     */
    @Nullable
    public byte[] getAuthenticationPublicKeySecp256R1() {
        return mStoredDiscoveryItem.getAuthenticationPublicKeySecp256R1().toByteArray();
    }

    /**
     * Returns the pairing secret.
     */
    @Nullable
    public String getFastPairSecretKey() {
        Intent intent = parseIntentScheme(mStoredDiscoveryItem.getActionUrl());
        if (intent == null) {
            Log.d("FastPairDiscoveryItem", "FastPair: fail to parse action url "
                    + mStoredDiscoveryItem.getActionUrl());
            return null;
        }
        return intent.getStringExtra(EXTRA_FAST_PAIR_SECRET);
    }

    /**
     * Returns the fast pair info of discovery item.
     */
    @Nullable
    public Cache.FastPairInformation getFastPairInformation() {
        return mStoredDiscoveryItem.hasFastPairInformation()
                ? mStoredDiscoveryItem.getFastPairInformation() : null;
    }

    /**
     * Returns the app name of discovery item.
     */
    @Nullable
    private String getAppName() {
        return mStoredDiscoveryItem.getAppName();
    }

    /**
     * Returns the package name of discovery item.
     */
    @Nullable
    public String getAppPackageName() {
        return mStoredDiscoveryItem.getPackageName();
    }

    /**
     * Returns the action url of discovery item.
     */
    @Nullable
    public String getActionUrl() {
        return mStoredDiscoveryItem.getActionUrl();
    }

    /**
     * Returns the rssi value of discovery item.
     */
    @Nullable
    public Integer getRssi() {
        return mStoredDiscoveryItem.getRssi();
    }

    /**
     * Returns the TX power of discovery item.
     */
    @Nullable
    public Integer getTxPower() {
        return mStoredDiscoveryItem.getTxPower();
    }

    /**
     * Returns the first observed time stamp of discovery item.
     */
    @Nullable
    public Long getFirstObservationTimestampMillis() {
        return mStoredDiscoveryItem.getFirstObservationTimestampMillis();
    }

    /**
     * Returns the last observed time stamp of discovery item.
     */
    @Nullable
    public Long getLastObservationTimestampMillis() {
        return mStoredDiscoveryItem.getLastObservationTimestampMillis();
    }

    /**
     * Calculates an estimated distance for the item, computed from the TX power (at 1m) and RSSI.
     *
     * @return estimated distance, or null if there is no RSSI or no TX power.
     */
    @Nullable
    public Double getEstimatedDistance() {
        // In the future, we may want to do a foreground subscription to leverage onDistanceChanged.
        return RangingUtils.distanceFromRssiAndTxPower(mStoredDiscoveryItem.getRssi(),
                mStoredDiscoveryItem.getTxPower());
    }

    /**
     * Gets icon Bitmap from icon store.
     *
     * @return null if no icon or icon size is incorrect.
     */
    @Nullable
    public Bitmap getIcon() {
        Bitmap icon =
                BitmapFactory.decodeByteArray(
                        mStoredDiscoveryItem.getIconPng().toByteArray(),
                        0 /* offset */, mStoredDiscoveryItem.getIconPng().size());
        if (IconUtils.isIconSizeCorrect(icon)) {
            return icon;
        } else {
            return null;
        }
    }

    /** Gets a FIFE URL of the icon. */
    @Nullable
    public String getIconFifeUrl() {
        return mStoredDiscoveryItem.getIconFifeUrl();
    }

    /**
     * Compares this object to the specified object: 1. By device type. Device setups are 'greater
     * than' beacons. 2. By relevance. More relevant items are 'greater than' less relevant items.
     * 3.By distance. Nearer items are 'greater than' further items.
     *
     * <p>In the list view, we sort in descending order, i.e. we put the most relevant items first.
     */
    @Override
    public int compareTo(DiscoveryItem another) {
        // For items of the same relevance, compare distance.
        Double distance1 = getEstimatedDistance();
        Double distance2 = another.getEstimatedDistance();
        distance1 = distance1 != null ? distance1 : Double.MAX_VALUE;
        distance2 = distance2 != null ? distance2 : Double.MAX_VALUE;
        // Negate because closer items are better ("greater than") further items.
        return -distance1.compareTo(distance2);
    }

    @Nullable
    public String getTriggerId() {
        return mStoredDiscoveryItem.getTriggerId();
    }

    @Override
    public boolean equals(Object another) {
        if (another instanceof DiscoveryItem) {
            return ((DiscoveryItem) another).mStoredDiscoveryItem.equals(mStoredDiscoveryItem);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mStoredDiscoveryItem.hashCode();
    }

    @Override
    public String toString() {
        return String.format(
                "[triggerId=%s], [id=%s], [title=%s], [url=%s], [ready=%s], [macAddress=%s]",
                getTriggerId(),
                getId(),
                getTitle(),
                getActionUrl(),
                isReadyForDisplay(),
                maskBluetoothAddress(getMacAddress()));
    }

    /**
     * Gets a copy of the StoredDiscoveryItem proto backing this DiscoveryItem. Currently needed for
     * Fast Pair 2.0: We store the item in the cloud associated with a user's account, to enable
     * pairing with other devices owned by the user.
     */
    public Cache.StoredDiscoveryItem getCopyOfStoredItem() {
        return mStoredDiscoveryItem;
    }

    /**
     * Gets the StoredDiscoveryItem represented by this DiscoveryItem. This lets tests manipulate
     * values that production code should not manipulate.
     */

    public Cache.StoredDiscoveryItem getStoredItemForTest() {
        return mStoredDiscoveryItem;
    }

    /**
     * Sets the StoredDiscoveryItem represented by this DiscoveryItem. This lets tests manipulate
     * values that production code should not manipulate.
     */
    public void setStoredItemForTest(Cache.StoredDiscoveryItem s) {
        mStoredDiscoveryItem = s;
    }

    /**
     * Parse the intent from item url.
     */
    public static Intent parseIntentScheme(String uri) {
        try {
            return Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
