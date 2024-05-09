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

#ifndef TUNNEL_RENDERER_H_

#define TUNNEL_RENDERER_H_

#include <gui/Surface.h>
#include <media/stagefright/foundation/AHandler.h>
//#include <media/IStreamSource.h>

namespace android {

struct ABuffer;
class SurfaceComposerClient;
class SurfaceControl;
class Surface;
class IMediaPlayer;
struct IStreamListener;

// This class reassembles incoming RTP packets into the correct order
// and sends the resulting transport stream to a mediaplayer instance
// for playback.
struct TunnelRenderer : public AHandler {
    TunnelRenderer(
            const sp<AMessage> &notifyLost);

    sp<ABuffer> dequeueBuffer();

    void queueBuffer(const sp<ABuffer> &buffer);
	void* doSomeWork();
    enum {
        kWhatQueueBuffer,
    };

    void* ThreadWrapper(void *);
	static void* rec_data(void* me);
protected:
    virtual void onMessageReceived(const sp<AMessage> &msg);
    virtual ~TunnelRenderer();

private:
    struct PlayerClient;
    struct StreamSource;

    mutable Mutex mLock;

    sp<AMessage> mNotifyLost;

    List<sp<ABuffer> > mPackets;
    int64_t mTotalBytesQueued;

    sp<SurfaceComposerClient> mComposerClient;
    sp<SurfaceControl> mSurfaceControl;
    sp<Surface> mSurface;
	sp<SurfaceComposerClient> mComposerClient_back;
    sp<SurfaceControl> mSurfaceControl_back;
    sp<Surface> mSurface_back;
    sp<PlayerClient> mPlayerClient;
    sp<IMediaPlayer> mPlayer;
    sp<StreamSource> mStreamSource;

    int32_t mLastDequeuedExtSeqNo;
    int64_t mFirstFailedAttemptUs;
    bool mRequestedRetransmission;
	int64_t packet_num;
	int64_t packet_num_recent;
	int64_t packet_lost;
	int64_t packet_lost_recent;
	int64_t first_seq_id;
	int64_t last_adjust_time;
	int		mStart;
	int32_t displayWidth ;
	int32_t displayHeight ;
	int32_t screen_dir;
	int32_t	rotation;
	int32_t rotate_displayWidth ;
	int32_t rotate_displayHeight ;
	int32_t rotate_xpos ;
	int32_t rotate_ypos ;
    void initPlayer();
    void destroyPlayer();


    DISALLOW_EVIL_CONSTRUCTORS(TunnelRenderer);
};

}  // namespace android

#endif  // TUNNEL_RENDERER_H_
