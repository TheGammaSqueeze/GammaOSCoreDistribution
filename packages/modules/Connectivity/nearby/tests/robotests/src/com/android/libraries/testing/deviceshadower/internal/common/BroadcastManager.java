/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.libraries.testing.deviceshadower.internal.common;

import android.content.BroadcastReceiver;
import android.content.BroadcastReceiver.PendingResult;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.libraries.testing.deviceshadower.internal.utils.Logger;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * Manager for broadcasting of one virtual Device Shadower device.
 *
 * <p>Inspired by {@link ShadowApplication} and {@link LocalBroadcastManager}.
 * <li>Broadcast permission is not supported until manifest is supported.
 * <li>Send Broadcast is asynchronous.
 */
public class BroadcastManager {

    private static final Logger LOGGER = Logger.create("BroadcastManager");

    private static final Comparator<ReceiverRecord> RECEIVER_RECORD_COMPARATOR =
            new Comparator<ReceiverRecord>() {
                @Override
                public int compare(ReceiverRecord o1, ReceiverRecord o2) {
                    return o2.mIntentFilter.getPriority() - o1.mIntentFilter.getPriority();
                }
            };

    private final Scheduler mScheduler;
    private final Map<String, Intent> mStickyIntents;

    @GuardedBy("mRegisteredReceivers")
    private final Map<BroadcastReceiver, Set<String>> mRegisteredReceivers;

    @GuardedBy("mRegisteredReceivers")
    private final Map<String, List<ReceiverRecord>> mActions;

    public BroadcastManager(Scheduler scheduler) {
        this(
                scheduler,
                new HashMap<String, Intent>(),
                new HashMap<BroadcastReceiver, Set<String>>(),
                new HashMap<String, List<ReceiverRecord>>());
    }

    @VisibleForTesting
    BroadcastManager(
            Scheduler scheduler,
            Map<String, Intent> stickyIntents,
            Map<BroadcastReceiver, Set<String>> registeredReceivers,
            Map<String, List<ReceiverRecord>> actions) {
        this.mScheduler = scheduler;
        this.mStickyIntents = stickyIntents;
        this.mRegisteredReceivers = registeredReceivers;
        this.mActions = actions;
    }

    /**
     * Registers a {@link BroadcastReceiver} with given {@link Context}.
     *
     * @see Context#registerReceiver(BroadcastReceiver, IntentFilter, String, Handler)
     */
    @Nullable
    public Intent registerReceiver(
            @Nullable BroadcastReceiver receiver,
            IntentFilter filter,
            @Nullable String broadcastPermission,
            @Nullable Handler handler,
            Context context) {
        // Ignore broadcastPermission before fully supporting manifest
        Preconditions.checkNotNull(filter);
        Preconditions.checkNotNull(context);
        if (receiver != null) {
            synchronized (mRegisteredReceivers) {
                ReceiverRecord receiverRecord = new ReceiverRecord(receiver, filter, context,
                        handler);
                Set<String> actionSet = mRegisteredReceivers.get(receiver);
                if (actionSet == null) {
                    actionSet = new HashSet<>();
                    mRegisteredReceivers.put(receiver, actionSet);
                }
                for (int i = 0; i < filter.countActions(); i++) {
                    String action = filter.getAction(i);
                    actionSet.add(action);
                    List<ReceiverRecord> receiverRecords = mActions.get(action);
                    if (receiverRecords == null) {
                        receiverRecords = new ArrayList<>();
                        mActions.put(action, receiverRecords);
                    }
                    receiverRecords.add(receiverRecord);
                }
            }
        }
        return processStickyIntents(receiver, filter, context);
    }

    // Broadcast all sticky intents matching the given IntentFilter.
    @SuppressWarnings("FutureReturnValueIgnored")
    @Nullable
    private Intent processStickyIntents(
            @Nullable final BroadcastReceiver receiver,
            IntentFilter intentFilter,
            final Context context) {
        Intent result = null;
        final List<Intent> matchedIntents = new ArrayList<>();
        for (Intent intent : mStickyIntents.values()) {
            if (match(intentFilter, intent)) {
                if (result == null) {
                    result = intent;
                }
                if (receiver == null) {
                    return result;
                }
                matchedIntents.add(intent);
            }
        }
        if (!matchedIntents.isEmpty()) {
            mScheduler.post(
                    NamedRunnable.create(
                            "Broadcast.processStickyIntents",
                            () -> {
                                for (Intent intent : matchedIntents) {
                                    receiver.onReceive(context, intent);
                                }
                            }));
        }
        return result;
    }

    /**
     * Unregisters a {@link BroadcastReceiver}.
     *
     * @see Context#unregisterReceiver(BroadcastReceiver)
     */
    public void unregisterReceiver(BroadcastReceiver broadcastReceiver) {
        synchronized (mRegisteredReceivers) {
            if (!mRegisteredReceivers.containsKey(broadcastReceiver)) {
                LOGGER.w("Receiver not registered: " + broadcastReceiver);
                return;
            }
            Set<String> actionSet = mRegisteredReceivers.remove(broadcastReceiver);
            for (String action : actionSet) {
                List<ReceiverRecord> receiverRecords = mActions.get(action);
                Iterator<ReceiverRecord> iterator = receiverRecords.iterator();
                while (iterator.hasNext()) {
                    if (iterator.next().mBroadcastReceiver == broadcastReceiver) {
                        iterator.remove();
                    }
                }
                if (receiverRecords.isEmpty()) {
                    mActions.remove(action);
                }
            }
        }
    }

    /**
     * Sends sticky broadcast with given {@link Intent}. This call is asynchronous.
     *
     * @see Context#sendStickyBroadcast(Intent)
     */
    public void sendStickyBroadcast(Intent intent) {
        mStickyIntents.put(intent.getAction(), intent);
        sendBroadcast(intent, null /* broadcastPermission */);
    }

    /**
     * Sends broadcast with given {@link Intent}. Receiver permission is not supported. This call is
     * asynchronous.
     *
     * @see Context#sendBroadcast(Intent, String)
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    public void sendBroadcast(final Intent intent, @Nullable String receiverPermission) {
        // Ignore permission matching before fully supporting manifest
        final List<ReceiverRecord> receivers =
                getMatchingReceivers(intent, false /* isOrdered */);
        if (receivers.isEmpty()) {
            return;
        }
        mScheduler.post(
                NamedRunnable.create(
                        "Broadcast.sendBroadcast",
                        () -> {
                            for (ReceiverRecord receiverRecord : receivers) {
                                // Hacky: Call the shadow method, otherwise abort() NPEs after
                                // calling onReceive().
                                // TODO(b/200231384): Sending these, via context.sendBroadcast(),
                                //  won't NPE...but it may not be possible on each simulated
                                //  "device"'s main thread. Check if possible.
                                BroadcastReceiver broadcastReceiver =
                                        receiverRecord.mBroadcastReceiver;
                                Shadows.shadowOf(broadcastReceiver)
                                        .onReceive(receiverRecord.mContext, intent, /*abort=*/
                                                new AtomicBoolean(false));
                            }
                        }));
    }

    /**
     * Sends ordered broadcast with given {@link Intent}. Receiver permission is not supported. This
     * call is asynchronous.
     *
     * @see Context#sendOrderedBroadcast(Intent, String)
     */
    public void sendOrderedBroadcast(Intent intent, @Nullable String receiverPermission) {
        sendOrderedBroadcast(
                intent,
                receiverPermission,
                null /* resultReceiver */,
                null /* handler */,
                0 /* initialCode */,
                null /* initialData */,
                null /* initialExtras */,
                null /* context */);
    }

    /**
     * Sends ordered broadcast with given {@link Intent} and result {@link BroadcastReceiver}.
     * Receiver permission is not supported. This call is asynchronous.
     *
     * @see Context#sendOrderedBroadcast(Intent, String, BroadcastReceiver, Handler, int, String,
     * Bundle)
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    public void sendOrderedBroadcast(
            final Intent intent,
            @Nullable String receiverPermission,
            @Nullable BroadcastReceiver resultReceiver,
            @Nullable Handler handler,
            int initialCode,
            @Nullable String initialData,
            @Nullable Bundle initialExtras,
            @Nullable Context context) {
        // Ignore permission matching before fully supporting manifest
        final List<ReceiverRecord> receivers =
                getMatchingReceivers(intent, true /* isOrdered */);
        if (receivers.isEmpty()) {
            return;
        }
        if (resultReceiver != null) {
            receivers.add(
                    new ReceiverRecord(
                            resultReceiver, null /* intentFilter */, context, handler));
        }
        mScheduler.post(
                NamedRunnable.create(
                        "Broadcast.sendOrderedBroadcast",
                        () -> {
                            postOrderedIntent(
                                    receivers,
                                    intent,
                                    0 /* initialCode */,
                                    null /* initialData */,
                                    null /* initialExtras */);
                        }));
    }

    @VisibleForTesting
    void postOrderedIntent(
            List<ReceiverRecord> receivers,
            final Intent intent,
            int initialCode,
            @Nullable String initialData,
            @Nullable Bundle initialExtras) {
        final AtomicBoolean abort = new AtomicBoolean(false);
        ListenableFuture<BroadcastResult> resultFuture =
                Futures.immediateFuture(
                        new BroadcastResult(initialCode, initialData, initialExtras));

        for (ReceiverRecord receiverRecord : receivers) {
            final BroadcastReceiver receiver = receiverRecord.mBroadcastReceiver;
            final Context context = receiverRecord.mContext;
            resultFuture =
                    Futures.transformAsync(
                            resultFuture,
                            new AsyncFunction<BroadcastResult, BroadcastResult>() {
                                @Override
                                public ListenableFuture<BroadcastResult> apply(
                                        BroadcastResult input) {
                                    PendingResult result = newPendingResult(
                                            input.mCode, input.mData, input.mExtras,
                                            true /* isOrdered */);
                                    ReflectionHelpers.callInstanceMethod(
                                            receiver, "setPendingResult",
                                            ClassParameter.from(PendingResult.class, result));
                                    Shadows.shadowOf(receiver).onReceive(context, intent, abort);
                                    return BroadcastResult.transform(result);
                                }
                            },
                            MoreExecutors.directExecutor());
        }
        Futures.addCallback(
                resultFuture,
                new FutureCallback<BroadcastResult>() {
                    @Override
                    public void onSuccess(BroadcastResult result) {
                        return;
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(t);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private List<ReceiverRecord> getMatchingReceivers(Intent intent, boolean isOrdered) {
        synchronized (mRegisteredReceivers) {
            List<ReceiverRecord> result = new ArrayList<>();
            if (!mActions.containsKey(intent.getAction())) {
                return result;
            }
            Iterator<ReceiverRecord> iterator = mActions.get(intent.getAction()).iterator();
            while (iterator.hasNext()) {
                ReceiverRecord next = iterator.next();
                if (match(next.mIntentFilter, intent)) {
                    result.add(next);
                }
            }
            if (isOrdered) {
                Collections.sort(result, RECEIVER_RECORD_COMPARATOR);
            }
            return result;
        }
    }

    private boolean match(IntentFilter intentFilter, Intent intent) {
        // Action test
        if (!intentFilter.matchAction(intent.getAction())) {
            return false;
        }
        // Category test
        if (intentFilter.matchCategories(intent.getCategories()) != null) {
            return false;
        }
        // Data test
        int matchResult =
                intentFilter.matchData(intent.getType(), intent.getScheme(), intent.getData());
        return matchResult != IntentFilter.NO_MATCH_TYPE
                && matchResult != IntentFilter.NO_MATCH_DATA;
    }

    private static PendingResult newPendingResult(
            int resultCode, String resultData, Bundle resultExtras, boolean isOrdered) {
        ClassParameter<?>[] parameters;
        // PendingResult constructor takes different parameters in different SDK levels.
        if (VERSION.SDK_INT < 17) {
            parameters =
                    ClassParameter.fromComponentLists(
                            new Class<?>[]{
                                    int.class,
                                    String.class,
                                    Bundle.class,
                                    int.class,
                                    boolean.class,
                                    boolean.class,
                                    IBinder.class
                            },
                            new Object[]{
                                    resultCode,
                                    resultData,
                                    resultExtras,
                                    0 /* type */,
                                    isOrdered,
                                    false /* sticky */,
                                    null /* IBinder */
                            });
        } else if (VERSION.SDK_INT < 23) {
            parameters =
                    ClassParameter.fromComponentLists(
                            new Class<?>[]{
                                    int.class,
                                    String.class,
                                    Bundle.class,
                                    int.class,
                                    boolean.class,
                                    boolean.class,
                                    IBinder.class,
                                    int.class
                            },
                            new Object[]{
                                    resultCode,
                                    resultData,
                                    resultExtras,
                                    0 /* type */,
                                    isOrdered,
                                    false /* sticky */,
                                    null /* IBinder */,
                                    0 /* userId */
                            });
        } else {
            parameters =
                    ClassParameter.fromComponentLists(
                            new Class<?>[]{
                                    int.class,
                                    String.class,
                                    Bundle.class,
                                    int.class,
                                    boolean.class,
                                    boolean.class,
                                    IBinder.class,
                                    int.class,
                                    int.class
                            },
                            new Object[]{
                                    resultCode,
                                    resultData,
                                    resultExtras,
                                    0 /* type */,
                                    isOrdered,
                                    false /* sticky */,
                                    null /* IBinder */,
                                    0 /* userId */,
                                    0 /* flags */
                            });
        }
        return ReflectionHelpers.callConstructor(PendingResult.class, parameters);
    }

    /**
     * Holder of broadcast result from previous receiver.
     */
    private static final class BroadcastResult {

        private final int mCode;
        private final String mData;
        private final Bundle mExtras;

        BroadcastResult(int code, String data, Bundle extras) {
            this.mCode = code;
            this.mData = data;
            this.mExtras = extras;
        }

        private static ListenableFuture<BroadcastResult> transform(PendingResult result) {
            return Futures.transform(
                    Shadows.shadowOf(result).getFuture(),
                    new Function<PendingResult, BroadcastResult>() {
                        @Override
                        public BroadcastResult apply(PendingResult input) {
                            return new BroadcastResult(
                                    input.getResultCode(), input.getResultData(),
                                    input.getResultExtras(false));
                        }
                    },
                    MoreExecutors.directExecutor());
        }
    }

    /**
     * Information of a registered BroadcastReceiver.
     */
    @VisibleForTesting
    static final class ReceiverRecord {

        final BroadcastReceiver mBroadcastReceiver;
        final IntentFilter mIntentFilter;
        final Context mContext;
        final Handler mHandler;

        @VisibleForTesting
        ReceiverRecord(
                BroadcastReceiver broadcastReceiver,
                IntentFilter intentFilter,
                Context context,
                Handler handler) {
            this.mBroadcastReceiver = broadcastReceiver;
            this.mIntentFilter = intentFilter;
            this.mContext = context;
            this.mHandler = handler;
        }
    }
}
