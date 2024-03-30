package com.google.snippet;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Pair;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** An example snippet class with a simple Rpc. */
public class WifiAwareSnippet implements Snippet {

  private static class WifiAwareSnippetException extends Exception {
    private static final long SERIAL_VERSION_UID = 1;

    public WifiAwareSnippetException(String msg) {
      super(msg);
    }

    public WifiAwareSnippetException(String msg, Throwable err) {
      super(msg, err);
    }
  }

  private static final String TAG = "WifiAwareSnippet";

  private static final String SERVICE_NAME = "CtsVerifierTestService";
  private static final byte[] MATCH_FILTER_BYTES = "bytes used for matching".getBytes(UTF_8);
  private static final byte[] PUB_SSI = "Extra bytes in the publisher discovery".getBytes(UTF_8);
  private static final byte[] SUB_SSI =
      "Arbitrary bytes for the subscribe discovery".getBytes(UTF_8);
  private static final int LARGE_ENOUGH_DISTANCE = 100000; // 100 meters

  private final WifiAwareManager wifiAwareManager;

  private final Context context;

  private final HandlerThread handlerThread;

  private final Handler handler;

  private WifiAwareSession wifiAwareSession;
  private DiscoverySession wifiAwareDiscoverySession;
  private CallbackUtils.DiscoveryCb discoveryCb;
  private PeerHandle peerHandle;

  public WifiAwareSnippet() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    wifiAwareManager = context.getSystemService(WifiAwareManager.class);
    handlerThread = new HandlerThread("Snippet-Aware");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
  }

  @Rpc(description = "Execute attach.")
  public void attach() throws InterruptedException, WifiAwareSnippetException {
    CallbackUtils.AttachCb attachCb = new CallbackUtils.AttachCb();
    wifiAwareManager.attach(attachCb, handler);
    Pair<CallbackUtils.AttachCb.CallbackCode, WifiAwareSession> results = attachCb.waitForAttach();
    if (results.first != CallbackUtils.AttachCb.CallbackCode.ON_ATTACHED) {
      throw new WifiAwareSnippetException(String.format("executeTest: attach %s", results.first));
    }
    wifiAwareSession = results.second;
    if (wifiAwareSession == null) {
      throw new WifiAwareSnippetException(
          "executeTest: attach callback succeeded but null session returned!?");
    }
  }

  @Rpc(description = "Execute subscribe.")
  public void subscribe(Boolean isUnsolicited, Boolean isRangingRequired)
      throws InterruptedException, WifiAwareSnippetException {
    discoveryCb = new CallbackUtils.DiscoveryCb();

    List<byte[]> matchFilter = new ArrayList<>();
    matchFilter.add(MATCH_FILTER_BYTES);
    SubscribeConfig.Builder builder =
        new SubscribeConfig.Builder()
            .setServiceName(SERVICE_NAME)
            .setServiceSpecificInfo(SUB_SSI)
            .setMatchFilter(matchFilter)
            .setSubscribeType(
                isUnsolicited
                    ? SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE
                    : SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)
            .setTerminateNotificationEnabled(true);

    if (isRangingRequired) {
      // set up a distance that will always trigger - i.e. that we're already in that range
      builder.setMaxDistanceMm(LARGE_ENOUGH_DISTANCE);
    }
    SubscribeConfig subscribeConfig = builder.build();
    Log.d(TAG, "executeTestSubscriber: subscribeConfig=" + subscribeConfig);
    wifiAwareSession.subscribe(subscribeConfig, discoveryCb, handler);

    // wait for results - subscribe session
    CallbackUtils.DiscoveryCb.CallbackData callbackData =
        discoveryCb.waitForCallbacks(
            ImmutableSet.of(
                CallbackUtils.DiscoveryCb.CallbackCode.ON_SUBSCRIBE_STARTED,
                CallbackUtils.DiscoveryCb.CallbackCode.ON_SESSION_CONFIG_FAILED));
    if (callbackData.callbackCode != CallbackUtils.DiscoveryCb.CallbackCode.ON_SUBSCRIBE_STARTED) {
      throw new WifiAwareSnippetException(
          String.format("executeTestSubscriber: subscribe %s", callbackData.callbackCode));
    }
    wifiAwareDiscoverySession = callbackData.subscribeDiscoverySession;
    if (wifiAwareDiscoverySession == null) {
      throw new WifiAwareSnippetException(
          "executeTestSubscriber: subscribe succeeded but null session returned");
    }
    Log.d(TAG, "executeTestSubscriber: subscribe succeeded");

    // 3. wait for discovery
    callbackData =
        discoveryCb.waitForCallbacks(
            ImmutableSet.of(
                isRangingRequired
                    ? CallbackUtils.DiscoveryCb.CallbackCode.ON_SERVICE_DISCOVERED_WITH_RANGE
                    : CallbackUtils.DiscoveryCb.CallbackCode.ON_SERVICE_DISCOVERED));

    if (callbackData.callbackCode == CallbackUtils.DiscoveryCb.CallbackCode.TIMEOUT) {
      throw new WifiAwareSnippetException("executeTestSubscriber: waiting for discovery TIMEOUT");
    }
    peerHandle = callbackData.peerHandle;
    if (!isRangingRequired) {
      Log.d(TAG, "executeTestSubscriber: discovery");
    } else {
      Log.d(TAG, "executeTestSubscriber: discovery with range=" + callbackData.distanceMm);
    }

    if (!Arrays.equals(PUB_SSI, callbackData.serviceSpecificInfo)) {
      throw new WifiAwareSnippetException(
          "executeTestSubscriber: discovery but SSI mismatch: rx='"
              + new String(callbackData.serviceSpecificInfo, UTF_8)
              + "'");
    }
    if (callbackData.matchFilter.size() != 1
        || !Arrays.equals(MATCH_FILTER_BYTES, callbackData.matchFilter.get(0))) {
      StringBuilder sb = new StringBuilder();
      sb.append("size=").append(callbackData.matchFilter.size());
      for (byte[] mf : callbackData.matchFilter) {
        sb.append(", e='").append(new String(mf, UTF_8)).append("'");
      }
      throw new WifiAwareSnippetException(
          "executeTestSubscriber: discovery but matchFilter mismatch: " + sb);
    }
    if (peerHandle == null) {
      throw new WifiAwareSnippetException("executeTestSubscriber: discovery but null peerHandle");
    }
  }

  @Rpc(description = "Send message.")
  public void sendMessage(int messageId, String message)
      throws InterruptedException, WifiAwareSnippetException {
    // 4. send message & wait for send status
    wifiAwareDiscoverySession.sendMessage(peerHandle, messageId, message.getBytes(UTF_8));
    CallbackUtils.DiscoveryCb.CallbackData callbackData =
        discoveryCb.waitForCallbacks(
            ImmutableSet.of(
                CallbackUtils.DiscoveryCb.CallbackCode.ON_MESSAGE_SEND_SUCCEEDED,
                CallbackUtils.DiscoveryCb.CallbackCode.ON_MESSAGE_SEND_FAILED));

    if (callbackData.callbackCode
        != CallbackUtils.DiscoveryCb.CallbackCode.ON_MESSAGE_SEND_SUCCEEDED) {
      throw new WifiAwareSnippetException(
          String.format("executeTestSubscriber: sendMessage %s", callbackData.callbackCode));
    }
    Log.d(TAG, "executeTestSubscriber: send message succeeded");
    if (callbackData.messageId != messageId) {
      throw new WifiAwareSnippetException(
          "executeTestSubscriber: send message message ID mismatch: " + callbackData.messageId);
    }
  }

  @Rpc(description = "Create publish session.")
  public void publish(Boolean isUnsolicited, Boolean isRangingRequired)
      throws WifiAwareSnippetException, InterruptedException {
    discoveryCb = new CallbackUtils.DiscoveryCb();

    // 2. publish
    List<byte[]> matchFilter = new ArrayList<>();
    matchFilter.add(MATCH_FILTER_BYTES);
    PublishConfig publishConfig =
        new PublishConfig.Builder()
            .setServiceName(SERVICE_NAME)
            .setServiceSpecificInfo(PUB_SSI)
            .setMatchFilter(matchFilter)
            .setPublishType(
                isUnsolicited
                    ? PublishConfig.PUBLISH_TYPE_UNSOLICITED
                    : PublishConfig.PUBLISH_TYPE_SOLICITED)
            .setTerminateNotificationEnabled(true)
            .setRangingEnabled(isRangingRequired)
            .build();
    Log.d(TAG, "executeTestPublisher: publishConfig=" + publishConfig);
    wifiAwareSession.publish(publishConfig, discoveryCb, handler);

    //    wait for results - publish session
    CallbackUtils.DiscoveryCb.CallbackData callbackData =
        discoveryCb.waitForCallbacks(
            ImmutableSet.of(
                CallbackUtils.DiscoveryCb.CallbackCode.ON_PUBLISH_STARTED,
                CallbackUtils.DiscoveryCb.CallbackCode.ON_SESSION_CONFIG_FAILED));
    if (callbackData.callbackCode != CallbackUtils.DiscoveryCb.CallbackCode.ON_PUBLISH_STARTED) {
      throw new WifiAwareSnippetException(
          String.format("executeTestPublisher: publish %s", callbackData.callbackCode));
    }
    wifiAwareDiscoverySession = callbackData.publishDiscoverySession;
    if (wifiAwareDiscoverySession == null) {
      throw new WifiAwareSnippetException(
          "executeTestPublisher: publish succeeded but null session returned");
    }
    Log.d(TAG, "executeTestPublisher: publish succeeded");
  }

  @Rpc(description = "Receive message.")
  public String receiveMessage() throws WifiAwareSnippetException, InterruptedException {
    // 3. wait to receive message.
    CallbackUtils.DiscoveryCb.CallbackData callbackData =
        discoveryCb.waitForCallbacks(
            ImmutableSet.of(CallbackUtils.DiscoveryCb.CallbackCode.ON_MESSAGE_RECEIVED));
    peerHandle = callbackData.peerHandle;
    Log.d(TAG, "executeTestPublisher: received message");

    if (peerHandle == null) {
      throw new WifiAwareSnippetException(
          "executeTestPublisher: received message but peerHandle is null!?");
    }
    return new String(callbackData.serviceSpecificInfo, UTF_8);
  }

  @Override
  public void shutdown() {
    if (wifiAwareDiscoverySession != null) {
      wifiAwareDiscoverySession.close();
      wifiAwareDiscoverySession = null;
    }
    if (wifiAwareSession != null) {
      wifiAwareSession.close();
      wifiAwareSession = null;
    }
  }
}
