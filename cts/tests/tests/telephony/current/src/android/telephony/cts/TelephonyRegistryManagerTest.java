package android.telephony.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.CarrierPrivilegesCallback;
import android.telephony.TelephonyRegistryManager;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Test TelephonyRegistryManagerTest APIs.
 */
public class TelephonyRegistryManagerTest {
    private TelephonyRegistryManager mTelephonyRegistryMgr;
    private static final long TIMEOUT_MILLIS = 1000;

    @Before
    public void setUp() throws Exception {
        assumeTrue(InstrumentationRegistry.getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TELEPHONY));

        mTelephonyRegistryMgr = (TelephonyRegistryManager) InstrumentationRegistry.getContext()
            .getSystemService(Context.TELEPHONY_REGISTRY_SERVICE);
    }

    /**
     * expect security exception as there is no carrier privilege permission.
     */
    @Test
    public void testNotifyCarrierNetworkChange() {
        try {
            mTelephonyRegistryMgr.notifyCarrierNetworkChange(true);
            fail("Expected SecurityException for notifyCarrierNetworkChange");
        } catch (SecurityException ex) {
            /* Expected */
        }
    }

    /**
     * expect security exception as there is no carrier privilege permission.
     */
    @Test
    public void testNotifyCarrierNetworkChangeWithSubscription() {
        try {
            mTelephonyRegistryMgr.notifyCarrierNetworkChange(
                    SubscriptionManager.getDefaultSubscriptionId(), /*active=*/ true);
            fail("Expected SecurityException for notifyCarrierNetworkChange with subscription");
        } catch (SecurityException expected) {
        }
    }

    @Test
    public void testNotifyCallStateChangedForAllSubscriptions() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Pair<Integer, String>> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onCallStateChanged(int state, String number) {
                queue.offer(Pair.create(state, number));
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_CALL_STATE);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        String dummyNumber = "288124";
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyCallStateChangedForAllSubscriptions(
                        TelephonyManager.CALL_STATE_IDLE, dummyNumber));

        Pair<Integer, String> result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Timed out waiting for phone state change", result);
        assertEquals(TelephonyManager.CALL_STATE_IDLE, result.first.longValue());
        assertTrue(!TextUtils.isEmpty(result.second));
    }

    @Test
    public void testNotifyCallStateChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Pair<Integer, String>> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onCallStateChanged(int state, String number) {
                queue.offer(Pair.create(state, number));
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm = tm.createForSubscriptionId(SubscriptionManager.getDefaultSubscriptionId());
        tm.listen(psl, PhoneStateListener.LISTEN_CALL_STATE);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        String dummyNumber = "288124";
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyCallStateChanged(
                        SubscriptionManager.getSlotIndex(
                                SubscriptionManager.getDefaultSubscriptionId()),
                        SubscriptionManager.getDefaultSubscriptionId(),
                        TelephonyManager.CALL_STATE_IDLE, dummyNumber));

        Pair<Integer, String> result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Timed out waiting for phone state change", result);
        assertEquals(TelephonyManager.CALL_STATE_IDLE, result.first.longValue());
        assertTrue(!TextUtils.isEmpty(result.second));
    }

    @Test
    public void testNotifyServiceStateChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<ServiceState> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onServiceStateChanged(ServiceState ss) {
                queue.offer(ss);
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_SERVICE_STATE);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        ServiceState dummyState = new ServiceState();
        dummyState.setCdmaSystemAndNetworkId(1234, 5678);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyServiceStateChanged(
                        SubscriptionManager.getSlotIndex(
                                SubscriptionManager.getDefaultSubscriptionId()),
                        SubscriptionManager.getDefaultSubscriptionId(),
                        dummyState));

        ServiceState result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Timed out waiting for phone state change", result);
        assertEquals(dummyState, result);
    }

    @Test
    public void testNotifySignalStrengthChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<SignalStrength> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onSignalStrengthsChanged(SignalStrength ss) {
                queue.offer(ss);
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        SignalStrength testValue = new SignalStrength();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifySignalStrengthChanged(
                        SubscriptionManager.getSlotIndex(
                                SubscriptionManager.getDefaultSubscriptionId()),
                        SubscriptionManager.getDefaultSubscriptionId(),
                        testValue));

        SignalStrength result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Timed out waiting for phone state change", result);
        assertEquals(testValue, result);
    }

    @Test
    public void testNotifyMessageWaitingChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onMessageWaitingIndicatorChanged(boolean msgWaitingInd) {
                queue.offer(msgWaitingInd);
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        boolean testValue = true;
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyMessageWaitingChanged(
                        SubscriptionManager.getSlotIndex(
                                SubscriptionManager.getDefaultSubscriptionId()),
                        SubscriptionManager.getDefaultSubscriptionId(),
                        testValue));

        boolean result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(testValue, result);
    }

    @Test
    public void testNotifyCallForwardingChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onCallForwardingIndicatorChanged(boolean callForwarding) {
                queue.offer(callForwarding);
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        boolean testValue = true;
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyCallForwardingChanged(
                        SubscriptionManager.getDefaultSubscriptionId(),
                        testValue));

        boolean result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(testValue, result);
    }

    @Test
    public void testNotifyDataActivityChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onDataActivity(int activity) {
                queue.offer(activity);
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_DATA_ACTIVITY);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        int testValue = TelephonyManager.DATA_ACTIVITY_DORMANT;
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyDataActivityChanged(
                        SubscriptionManager.getDefaultSubscriptionId(),
                        testValue));

        int result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(testValue, result);
    }

    @Test
    public void testCarrierPrivilegesCallback() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Pair<Set<String>, Set<Integer>>> carrierPrivilegesQueue =
                new LinkedBlockingQueue(2);
        LinkedBlockingQueue<Pair<String, Integer>> carrierServiceQueue = new LinkedBlockingQueue(2);

        CarrierPrivilegesCallback cpc = new TestCarrierPrivilegesCallback(carrierPrivilegesQueue,
                carrierServiceQueue);
        CarrierPrivilegesCallback cpc2 = new TestCarrierPrivilegesCallback(carrierPrivilegesQueue,
                carrierServiceQueue);

        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    telephonyManager,
                    tm -> tm.registerCarrierPrivilegesCallback(0, context.getMainExecutor(), cpc));
            // Clear the initial carrierPrivilegesResult from registering the listener. We can't
            // necessarily guarantee this is empty so don't assert on it other than the fact we
            // got _something_. We restore this at the end of the test.
            Pair<Set<String>, Set<Integer>> initialCarrierPrivilegesState =
                    carrierPrivilegesQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull(initialCarrierPrivilegesState);
            Pair<String, Integer> initialCarrierServiceState =
                    carrierServiceQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull(initialCarrierServiceState);

            // Update state
            Set<String> privilegedPackageNames =
                    Set.of("com.carrier.package1", "com.carrier.package2");
            Set<Integer> privilegedUids = Set.of(12345, 54321);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyRegistryMgr,
                    trm -> {
                        trm.notifyCarrierPrivilegesChanged(
                                0, privilegedPackageNames, privilegedUids);
                        trm.notifyCarrierServiceChanged(0, "com.carrier.package1", 12345);
                    });
            Pair<Set<String>, Set<Integer>> carrierPrivilegesResult =
                    carrierPrivilegesQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertEquals(privilegedPackageNames, carrierPrivilegesResult.first);
            assertEquals(privilegedUids, carrierPrivilegesResult.second);

            Pair<String, Integer> carrierServiceResult =
                    carrierServiceQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertEquals("com.carrier.package1", carrierServiceResult.first);
            assertEquals(12345, (long) carrierServiceResult.second);

            // Update the state again, but only notify carrier privileges change this time
            Set<String> newPrivilegedPackageNames = Set.of("com.carrier.package1",
                    "com.carrier.package3");
            Set<Integer> newPrivilegedUids = Set.of(12345, 678910);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyRegistryMgr,
                    trm -> {
                        trm.notifyCarrierPrivilegesChanged(
                                0, newPrivilegedPackageNames, newPrivilegedUids);
                    });
            // The CarrierPrivileges pkgs and UIDs should be updated
            carrierPrivilegesResult =
                    carrierPrivilegesQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertEquals(newPrivilegedPackageNames, carrierPrivilegesResult.first);
            assertEquals(newPrivilegedUids, carrierPrivilegesResult.second);

            // And the CarrierService change notification should NOT be triggered
            assertNull(carrierServiceQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            // Registering cpc2 now immediately gets us the most recent state
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    telephonyManager,
                    tm -> tm.registerCarrierPrivilegesCallback(0, context.getMainExecutor(), cpc2));
            carrierPrivilegesResult = carrierPrivilegesQueue.poll(TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS);
            assertEquals(newPrivilegedPackageNames, carrierPrivilegesResult.first);
            assertEquals(newPrivilegedUids, carrierPrivilegesResult.second);

            carrierServiceResult = carrierServiceQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertEquals("com.carrier.package1", carrierServiceResult.first);
            assertEquals(12345, (long) carrierServiceResult.second);

            // Removing cpc means it won't get the final callback when we restore the original state
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    telephonyManager, tm -> tm.unregisterCarrierPrivilegesCallback(cpc));
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyRegistryMgr,
                    trm -> {
                        trm.notifyCarrierPrivilegesChanged(
                                0, initialCarrierPrivilegesState.first,
                                initialCarrierPrivilegesState.second);
                        trm.notifyCarrierServiceChanged(0, initialCarrierServiceState.first,
                                initialCarrierServiceState.second);
                    });

            carrierPrivilegesResult = carrierPrivilegesQueue.poll(TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS);
            assertEquals(initialCarrierPrivilegesState.first, carrierPrivilegesResult.first);
            assertEquals(initialCarrierPrivilegesState.second, carrierPrivilegesResult.second);

            carrierServiceResult = carrierServiceQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertEquals(initialCarrierServiceState.first, carrierServiceResult.first);
            assertEquals(initialCarrierServiceState.second, carrierServiceResult.second);

            // No further callbacks received
            assertNull(carrierPrivilegesQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertNull(carrierServiceQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    telephonyManager,
                    tm -> {
                        tm.unregisterCarrierPrivilegesCallback(cpc); // redundant, but still allowed
                        tm.unregisterCarrierPrivilegesCallback(cpc2);
                    });
        }
    }

    private class TestCarrierPrivilegesCallback implements CarrierPrivilegesCallback {
        LinkedBlockingQueue<Pair<Set<String>, Set<Integer>>> mCarrierPrivilegesQueue;
        LinkedBlockingQueue<Pair<String, Integer>> mCarrierServiceQueue;

        TestCarrierPrivilegesCallback(
                LinkedBlockingQueue<Pair<Set<String>, Set<Integer>>> carrierPrivilegesQueue,
                LinkedBlockingQueue<Pair<String, Integer>> carrierServiceQueue) {
            mCarrierPrivilegesQueue = carrierPrivilegesQueue;
            mCarrierServiceQueue = carrierServiceQueue;
        }

        @Override
        public void onCarrierPrivilegesChanged(@NonNull Set<String> privilegedPackageNames,
                @NonNull Set<Integer> privilegedUids) {
            mCarrierPrivilegesQueue.offer(new Pair<>(privilegedPackageNames, privilegedUids));
        }

        @Override
        public void onCarrierServiceChanged(@Nullable String carrierServicePackageName,
                int carrierServiceUid) {
            mCarrierServiceQueue.offer(new Pair<>(carrierServicePackageName, carrierServiceUid));
        }
    }
}
