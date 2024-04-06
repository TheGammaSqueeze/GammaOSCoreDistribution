/*
 * Copyright 2012, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef WIFI_DISPLAY_SINK_H_

#define WIFI_DISPLAY_SINK_H_


#include <gui/Surface.h>
#include <media/stagefright/foundation/AHandler.h>
#include "ANetworkSession.h"

namespace android {
#ifdef WFD_HDCP_SUPPORT
struct IHDCP;
#endif
struct ParsedMessage;
struct RTPSink;
// Represents the RTSP client acting as a wifi display sink.
// Connects to a wifi display source and renders the incoming
// transport stream using a MediaPlayer instance.
struct WifiDisplaySink : public AHandler {
	static const unsigned kHDCPDefaultPort = 58877;
    WifiDisplaySink(
            const sp<ANetworkSession> &netSession );

    void start(const char *sourceHost, int32_t sourcePort);
    void start(const char *uri);

protected:
    virtual ~WifiDisplaySink();
    virtual void onMessageReceived(const sp<AMessage> &msg);

private:
#ifdef WFD_HDCP_SUPPORT
    struct HDCPObserver;
#endif
    enum State {
        UNDEFINED,
        CONNECTING,
        CONNECTED,
        PAUSED,
        PLAYING,
        TEARDOWN,
    };

    enum {
        kWhatStart,
        kWhatRTSPNotify,
        kWhatHDCPNotify,
        kWhatStop,
    };

    struct ResponseID {
        int32_t mSessionID;
        int32_t mCSeq;

        bool operator<(const ResponseID &other) const {
            return mSessionID < other.mSessionID
                || (mSessionID == other.mSessionID
                        && mCSeq < other.mCSeq);
        }
    };

    bool mUsingHDCP;
    bool mHDCPInitializationComplete;
#ifdef WFD_HDCP_SUPPORT	
    // HDCP specific section >>>>
    sp<IHDCP> mHDCP;
    sp<HDCPObserver> mHDCPObserver;
    status_t makeHDCP();
    // <<<< HDCP specific section
#endif

    typedef status_t (WifiDisplaySink::*HandleRTSPResponseFunc)(
            int32_t sessionID, const sp<ParsedMessage> &msg);

    static const bool sUseTCPInterleaving = false;

    State mState;
    sp<ANetworkSession> mNetSession;
    AString mSetupURI;
    AString mRTSPHost;
	int32_t mRTSPPort;  // add by lance 2014.07.23
	uint32_t connectionCount;  // count of connetion  add by lance 2014.07.30
    int32_t mSessionID;

    int32_t mNextCSeq;

    KeyedVector<ResponseID, HandleRTSPResponseFunc> mResponseHandlers;

    sp<RTPSink> mRTPSink;
    AString mPlaybackSessionID;
    int32_t mPlaybackSessionTimeoutSecs;

	AString* mUrl;
	int		end_flag;
    pthread_t mThread;
    void* ThreadWrapper(void *);
	static void* rec_data(void* me);
	void sendIDR(int32_t sessionID, const char *uri);
    status_t sendM2(int32_t sessionID);
    status_t sendDescribe(int32_t sessionID, const char *uri);
    status_t sendSetup(int32_t sessionID, const char *uri);
    status_t sendPlay(int32_t sessionID, const char *uri);

    status_t sendTearDown(int32_t sessionID, const char *uri);
    status_t onReceiveM2Response(
            int32_t sessionID, const sp<ParsedMessage> &msg);

    status_t onReceiveDescribeResponse(
            int32_t sessionID, const sp<ParsedMessage> &msg);

    status_t onReceiveSetupResponse(
            int32_t sessionID, const sp<ParsedMessage> &msg);

    status_t configureTransport(const sp<ParsedMessage> &msg);

    status_t onReceiveTearDownResponse(int32_t sessionID, const sp<ParsedMessage> &msg);
    status_t onReceivePlayResponse(
            int32_t sessionID, const sp<ParsedMessage> &msg);
	status_t onReceiveKeepAliveResponse(
            int32_t sessionID, const sp<ParsedMessage> &msg);
	status_t onReceiveIdrResponse(
            int32_t sessionID, const sp<ParsedMessage> &msg);

    void registerResponseHandler(
            int32_t sessionID, int32_t cseq, HandleRTSPResponseFunc func);

    void onReceiveClientData(const sp<AMessage> &msg);

    void onOptionsRequest(
            int32_t sessionID,
            int32_t cseq,
            const sp<ParsedMessage> &data);

    void onGetParameterRequest(
            int32_t sessionID,
            int32_t cseq,
            const sp<ParsedMessage> &data);

    void onSetParameterRequest(
            int32_t sessionID,
            int32_t cseq,
            const sp<ParsedMessage> &data);

    void sendErrorResponse(
            int32_t sessionID,
            const char *errorDetail,
            int32_t cseq);

	void finishStop();

	char* getFirstLocalAddress();

    static void AppendCommonResponse(AString *response, int32_t cseq);
	uint8_t vauleParsedByResolution(int32_t displayWidth, int32_t displayHeight, float displayFps);
	void getParameterString(char *parameterByte, uint8_t parsedValue);
	
	void resolutionToParameterByte(char *parameterByte, int32_t displayWidth, int32_t displayHeight, float displayFps);

    bool ParseURL(
            const char *url, AString *host, int32_t *port, AString *path,
            AString *user, AString *pass);

    DISALLOW_EVIL_CONSTRUCTORS(WifiDisplaySink);
};

}  // namespace android

#endif  // WIFI_DISPLAY_SINK_H_
