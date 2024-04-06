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

//#define LOG_NDEBUG 0
#define LOG_TAG "TunnelRenderer"
#include <utils/Log.h>

#include "include/TunnelRenderer.h"

#include "ATSParser.h"

#include <binder/IMemory.h>
#include <binder/IServiceManager.h>
#include <gui/SurfaceComposerClient.h>
#include <media/mediaplayer.h>
#include <media/IMediaPlayerService.h>
#include <media/IStreamSource.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include "include/MediaKeys.h"

#include <gui/ISurfaceComposer.h>
#include <gui/SurfaceComposerClient.h>
#include <cutils/properties.h>
#include <cutils/memory.h>
#include <utils/CallStack.h>
#include <inttypes.h>
#if (PlATFORM_VERSION == 11)
#include <ui/DisplayConfig.h>
#elif (PLATFORM_VERSION >= 12)
#include <ui/DisplayMode.h>
#else
#include <ui/DisplayInfo.h>
#endif

#define RTP_DEBUG 0

#define UNUSED(x)  (void)(x)

namespace android {
	 int64_t last_tunnelrender_start_time_us;
	 int64_t tunnelrender_start_time_us;
		  int64_t tunnelrender_audio_start_time_us ;

struct TunnelRenderer::PlayerClient : public BnMediaPlayerClient {
    PlayerClient() {}

    virtual void notify(int msg, int ext1, int ext2, const Parcel *obj) {
        UNUSED(obj);
        ALOGI("notify %d, %d, %d", msg, ext1, ext2);
    }

protected:
    virtual ~PlayerClient() {}

private:
    DISALLOW_EVIL_CONSTRUCTORS(PlayerClient);
};
//extern FILE *omx_rs_test;
 
//extern 
FILE* omx_rs_txt ;

struct TunnelRenderer::StreamSource : public BnStreamSource {
    StreamSource(TunnelRenderer *owner);

    virtual void setListener(const sp<IStreamListener> &listener);
    virtual void setBuffers(const Vector<sp<IMemory> > &buffers);

    virtual void onBufferAvailable(size_t index);

    virtual uint32_t flags() const;

    void doSomeWork();
    int get_buffer_num(){return buffer_num;}
protected:
    virtual ~StreamSource();

private:
    mutable Mutex mLock;

    TunnelRenderer *mOwner;

    sp<IStreamListener> mListener;

    Vector<sp<IMemory> > mBuffers;
    List<size_t> mIndicesAvailable;

    size_t mNumDeqeued;
    int	   buffer_num;
    int64_t   last_dequeue_time;
    DISALLOW_EVIL_CONSTRUCTORS(StreamSource);
};

////////////////////////////////////////////////////////////////////////////////

TunnelRenderer::StreamSource::StreamSource(TunnelRenderer *owner)
    : mOwner(owner),
      mNumDeqeued(0) {
	  last_dequeue_time = systemTime(SYSTEM_TIME_MONOTONIC) / 1000;	
}

TunnelRenderer::StreamSource::~StreamSource() {
}

void TunnelRenderer::StreamSource::setListener(
        const sp<IStreamListener> &listener) {
    mListener = listener;
}

void TunnelRenderer::StreamSource::setBuffers(
        const Vector<sp<IMemory> > &buffers) {
    mBuffers = buffers;
}

void TunnelRenderer::StreamSource::onBufferAvailable(size_t index) {
    CHECK_LT(index, mBuffers.size());

    {
        Mutex::Autolock autoLock(mLock);
        mIndicesAvailable.push_back(index);
    }

    doSomeWork();
}

uint32_t TunnelRenderer::StreamSource::flags() const {
    //return kFlagAlignedVideoData | 0x12340000;
    return 0x12340000;   //indicate wfd display video stream
}

void TunnelRenderer::StreamSource::doSomeWork() {
    Mutex::Autolock autoLock(mLock);
#if 0
	int64_t systime = systemTime(SYSTEM_TIME_MONOTONIC) / 1000; 
	if(systime - last_dequeue_time < 5000ll) {
     ALOGD("short time,do nothing audio");
		return;
        }
#endif
    while (!mIndicesAvailable.empty()) {
		#if 0
		sp<ABuffer> srcBuffer = mOwner->dequeueBuffer();
        if (srcBuffer == NULL) {
            break;
        }

        ++mNumDeqeued;

        if (mNumDeqeued == 1) {
            ALOGI("fixing real time now.");

            sp<AMessage> extra = new AMessage;

            extra->setInt32(
                    IStreamListener::kKeyDiscontinuityMask,
                    ATSParser::DISCONTINUITY_ABSOLUTE_TIME);

            extra->setInt64("timeUs", ALooper::GetNowUs());

            mListener->issueCommand(
                    IStreamListener::DISCONTINUITY,
                    false /* synchronous */,
                    extra);
        }

        ALOGV("dequeue TS packet of size %d", srcBuffer->size());

        size_t index = *mIndicesAvailable.begin();
        mIndicesAvailable.erase(mIndicesAvailable.begin());

        sp<IMemory> mem = mBuffers.itemAt(index);
        CHECK_LE(srcBuffer->size(), mem->size());
        CHECK_EQ((srcBuffer->size() % 188), 0u);

        memcpy(mem->pointer(), srcBuffer->data(), srcBuffer->size());
        mListener->queueBuffer(index, srcBuffer->size());
		#else
		
		size_t index = *mIndicesAvailable.begin();
		sp<IMemory> mem = mBuffers.itemAt(index);
		buffer_num = 0;
		do
		{
	        sp<ABuffer> srcBuffer = mOwner->dequeueBuffer();
			
	        if (srcBuffer == NULL) {
	            break;
	        }
	        ++mNumDeqeued;
			if (mNumDeqeued == 1) {
				ALOGD("fixing real time now.");
				sp<AMessage> extra = new AMessage;
#if PLATFORM_SDK_VERSION >= 28
				extra->setInt32(
						kIStreamListenerKeyDiscontinuityMask,
						ATSParser::DISCONTINUITY_ABSOLUTE_TIME);
				#if 0
				extra->setInt64("timeUs", ALooper::GetNowUs());
				#else
				extra->setInt32("first_packet", 1);//ALooper::GetNowUs());
				extra->setInt64("wifidisplay_sys_timeUs", tunnelrender_start_time_us);//ALooper::GetNowUs());
	            extra->setInt64("timeUs", tunnelrender_audio_start_time_us);//ALooper::GetNowUs());
				ALOGD("streaming_sys_start_timeUs %" PRId64 " %" PRId64,tunnelrender_start_time_us,tunnelrender_audio_start_time_us);
				last_tunnelrender_start_time_us = tunnelrender_start_time_us;
				#endif
#else
                                extra->setInt32(
                                                IStreamListener::kKeyDiscontinuityMask,
                                                ATSParser::DISCONTINUITY_ABSOLUTE_TIME);
                                #if 0
                                extra->setInt64("timeUs", ALooper::GetNowUs());
                                #else
                                extra->setInt32("first_packet", 1);//ALooper::GetNowUs());
                                extra->setInt64("wifidisplay_sys_timeUs", tunnelrender_start_time_us);//ALooper::GetNowUs());
                                extra->setInt64("timeUs", tunnelrender_audio_start_time_us);//ALooper::GetNowUs());
                                ALOGD("streaming_sys_start_timeUs %lld %lld",tunnelrender_start_time_us,tunnelrender_audio_start_time_us);
                                last_tunnelrender_start_time_us = tunnelrender_start_time_us;
                                #endif
#endif
				mListener->issueCommand(
						IStreamListener::DISCONTINUITY,
						false /* synchronous */,
						extra);
			}
			else   if (last_tunnelrender_start_time_us > tunnelrender_start_time_us || (last_tunnelrender_start_time_us == 0 && tunnelrender_start_time_us != 0)){//(mNumDeqeued == 1){//(last_tunnelrender_start_time_us < tunnelrender_start_time_us)) {

	            sp<AMessage> extra = new AMessage;
#if PLATFORM_SDK_VERSION >= 28
	            extra->setInt32(
	                    kIStreamListenerKeyDiscontinuityMask,
	                    ATSParser::DISCONTINUITY_ABSOLUTE_TIME);
	            extra->setInt64("wifidisplay_sys_timeUs", tunnelrender_start_time_us);//ALooper::GetNowUs());
	            extra->setInt64("timeUs", tunnelrender_audio_start_time_us);//ALooper::GetNowUs());
		    ALOGD("streaming_sys_start_timeUs %" PRId64 " %" PRId64,tunnelrender_start_time_us,tunnelrender_audio_start_time_us);
		    last_tunnelrender_start_time_us = tunnelrender_start_time_us;
#else
                    extra->setInt32(
                            IStreamListener::kKeyDiscontinuityMask,
                            ATSParser::DISCONTINUITY_ABSOLUTE_TIME);
                    extra->setInt64("wifidisplay_sys_timeUs", tunnelrender_start_time_us);//ALooper::GetNowUs());
                    extra->setInt64("timeUs", tunnelrender_audio_start_time_us);//ALooper::GetNowUs());
                    ALOGD("streaming_sys_start_timeUs %lld %lld",tunnelrender_start_time_us,tunnelrender_audio_start_time_us);
                    last_tunnelrender_start_time_us = tunnelrender_start_time_us;
#endif
	            mListener->issueCommand(
	                    IStreamListener::DISCONTINUITY,
	                    false /* synchronous */,
	                    extra);
	        }

	        //ALOGD_IF(RTP_DEBUG,"dequeue TS packet of size %d", srcBuffer->size());

			if(srcBuffer->size() >= 1880)
				ALOGD("srcBuffer->size() %zu > 1880",srcBuffer->size());
	        CHECK_LE(srcBuffer->size(), mem->size()- buffer_num);
			CHECK_LE(srcBuffer->size(), 1880u);
	        CHECK_EQ((srcBuffer->size() % 188), 0u);
	        memcpy((void *)((int64_t)mem->unsecurePointer() + buffer_num), srcBuffer->data(), srcBuffer->size());

			buffer_num+=srcBuffer->size();
			
		}while(mem->size()- buffer_num > 10 * 188);
		if(buffer_num> 0)
		{
		    mIndicesAvailable.erase(mIndicesAvailable.begin());
	            mListener->queueBuffer(index,buffer_num);// srcBuffer->size());
		}
		else if(buffer_num == 0)
		{
			
			break;
		}
		#endif
    }
    //last_dequeue_time= systime;
}

////////////////////////////////////////////////////////////////////////////////

TunnelRenderer::TunnelRenderer(
        const sp<AMessage> &notifyLost)
    : mNotifyLost(notifyLost),
      mTotalBytesQueued(0ll),
      mLastDequeuedExtSeqNo(-1),
      mFirstFailedAttemptUs(-1ll),
      mRequestedRetransmission(false) ,
      mStart(true){
      tunnelrender_start_time_us = tunnelrender_audio_start_time_us = last_tunnelrender_start_time_us = 0;
	  packet_num = 0;
   	 packet_lost = 0;
   	 packet_num_recent = 0;
   	 packet_lost_recent = 0;
   	 first_seq_id = 0;
	  last_adjust_time = 0;
	  initPlayer();
	  ALOGD("TunnelRenderer");
	  
}

TunnelRenderer::~TunnelRenderer() {
    destroyPlayer();
}

#if 0
static void dump(void)
{
   android::CallStack stack;
   stack.update();
   // stack.dump();
   ALOGD("%s",stack.toString().string());
}
#endif

void TunnelRenderer::queueBuffer(const sp<ABuffer> &buffer) {
    Mutex::Autolock autoLock(mLock);
	int64_t sys_time_before_lock = systemTime(SYSTEM_TIME_MONOTONIC) / 1000;

    mTotalBytesQueued += buffer->size();
	static int last_newExtendedSeqNo = 0;
    int32_t newExtendedSeqNo = buffer->int32Data();
    //dump();
	if(1)
	{
        unsigned char* buff = (unsigned char*)buffer->data();
        int     len = buffer->size();
        int i ;
		int normal_sign = 0;
		if(1)
		{
			int retrtptxt;
			if((retrtptxt = access("/data/test/omx_rs_file",0)) == 0)//test_file!=NULL)
			{	
				
				if(omx_rs_txt == NULL)
					omx_rs_txt = fopen("/data/test/omx_rs_test.ts","wb");
				if(omx_rs_txt != NULL)
				{
					fwrite(buff,buffer->size(),1,omx_rs_txt);
						
					fflush(omx_rs_txt);
				}
				
			}	
	        for(i = 0; i < len-18; i+=188) //lbt
	        {
				int	padding_len = ((buff[i+3] & 0x30) == 0x30)? (buff[i+4] + 1): 0;
		
				int64_t sys_time = systemTime(SYSTEM_TIME_MONOTONIC) / 1000;
				if(buff[i] == 0x47 && ((buff[i+1]&0x40) ) &&((buff[i+3] & 0x10) == 0x10)&&
					buff[i + padding_len + 4] == 0x0&& buff[i + padding_len + 5] == 0x0 && buff[i + padding_len + 6] == 0x1)
				{
					if(buff[i] == 0x47 && buff[i+1] == 0x50 && buff[i+2] == 0x11 )
		            {
	                    uint64_t     PTS;
	                    //long long curtime[2];
			            int64_t curtime[2];
						int  pes_length = 0;
						normal_sign = 1;
	                    buffer->meta()->findInt64("arrivalTimeUs", &curtime[0]);	
	                    PTS = (((uint64_t)buff[i+padding_len+13] ) & 0xe) <<29;
	                    PTS |= buff[i+padding_len+14] <<22;
	                    PTS |= (buff[i+padding_len+15] & 0xfe) <<14;
	                    PTS |= (buff[i+padding_len+16] ) <<7;
	                    PTS |= (buff[i+padding_len+17] >>1);
	       				curtime[1] = (int64_t)(PTS) * 100 /9;
						 
						pes_length = buff[i+padding_len+8] << 8;
						pes_length |= buff[i+padding_len+9];
						
					  int retrtptxt;
                      
                      ALOGD_IF(RTP_DEBUG, " RTP ====== dleta %" PRId64 " timeus %" PRId64, sys_time -curtime[0], curtime[1]);    
	                  if((retrtptxt = access("data/test/omx_rs_txt_file",0)) == 0)//test_file!=NULL)
	  				  {
	  					  
	  					  if(omx_rs_txt == NULL)
	  						  omx_rs_txt = fopen("data/test/omx_rs_txt.txt","ab");

		  if(omx_rs_txt != NULL)
		  {
		      ALOGV("TimeDelayCount: tunnelrender_audio_start_time_us %" PRId64 " tunnelrender_start_time_us" "%" PRId64 "sys_time" " %" PRId64 "cur_Time" " %" PRId64 " %" PRId64 "delta sys" " %" PRId64 " %" PRId64 " %" PRId64 " %" PRId64,
                tunnelrender_audio_start_time_us, tunnelrender_start_time_us, sys_time,curtime[0],curtime[1], sys_time -curtime[0],
                curtime[0] - tunnelrender_start_time_us ,curtime[1] - tunnelrender_audio_start_time_us,
                curtime[0] - tunnelrender_start_time_us - curtime[1] + tunnelrender_audio_start_time_us);
			  fprintf(omx_rs_txt,"TunnelRenderer::queueBuffer Video start time %" PRId64 " %15" PRId64 " adjust %" PRId64 " sys_time %15" PRId64 " %15" PRId64 " cur_Time %15" PRId64 " %15" PRId64 " delta sys %15" PRId64 " %15" PRId64 " %15" PRId64 " %15" PRId64 " %" PRId64 " first_seq_id %15" PRId64 " packet_num %15" PRId64 " packet_lost %15" PRId64 " packet_num_recent %15" PRId64 " packet_lost_recent %15" PRId64 " pes_length %d mPackets.size() %zu newExtendedSeqNo %d last_newExtendedSeqNo %d delta %d buffer->size() %zu PTS %" PRId64 " %2x%2x%2x%2x%2x   data %2x%2x%2x%2x%2x%2x%2x%2x%2x%2x%2x%2x   %2x\n",
					tunnelrender_start_time_us,tunnelrender_audio_start_time_us,last_adjust_time,sys_time_before_lock,sys_time,curtime[0],curtime[1], sys_time -curtime[0],
					curtime[0] - tunnelrender_start_time_us ,curtime[1] - tunnelrender_audio_start_time_us,
					curtime[0] - tunnelrender_start_time_us - curtime[1] + tunnelrender_audio_start_time_us,
					curtime[0]-last_adjust_time,first_seq_id, packet_num,packet_lost,packet_num_recent,packet_lost_recent,
					pes_length,mPackets.size(),newExtendedSeqNo,last_newExtendedSeqNo,newExtendedSeqNo - last_newExtendedSeqNo - 1,buffer->size(),
					PTS,buff[i+padding_len+13],buff[i+padding_len+14],buff[i+padding_len+15],buff[i+padding_len+16],buff[i+padding_len+17],
					buff[i+padding_len+18],buff[i+padding_len+19],buff[i+padding_len+20],buff[i+padding_len+21],
					buff[i+padding_len+22],buff[i+padding_len+23],buff[i+padding_len+24],
					buff[i+padding_len+25],buff[i+padding_len+26],buff[i+padding_len+27],buff[i+padding_len+28],buff[i+padding_len+29]  ,buff[i+3]);
			  fflush(omx_rs_txt);
		  }
						  
	  				  }
	            	}
					else if(buff[i] == 0x47 && buff[i+1] == 0x51 && buff[i+2] == 0x00 )
		            {
	                    uint64_t     PTS;
	                    //long long curtime[2];
			            int64_t curtime[2];
						int pes_length = 0;
						normal_sign = 1;
	                    buffer->meta()->findInt64("arrivalTimeUs", &curtime[0]);	
	                    PTS = (((uint64_t)buff[i+padding_len+13] ) & 0xe) <<29;
	                    PTS |= buff[i+padding_len+14] <<22;
	                    PTS |= (buff[i+padding_len+15] & 0xfe) <<14;
	                    PTS |= (buff[i+padding_len+16] ) <<7;
	                    PTS |= (buff[i+padding_len+17] >>1);
	       				curtime[1] = (int64_t)(PTS) * 100 /9;
						pes_length = buff[i+padding_len+8] << 8;
						pes_length |= buff[i+padding_len+9];
						if((tunnelrender_audio_start_time_us == 0 && tunnelrender_start_time_us == 0))
						{
							last_adjust_time = curtime[0];
							tunnelrender_start_time_us = curtime[0];
							tunnelrender_audio_start_time_us  = curtime[1];
						}
						if(curtime[0] - last_adjust_time > 500000000ll)
						{
							last_adjust_time = curtime[0];
							tunnelrender_start_time_us = curtime[0] - (curtime[1] - tunnelrender_audio_start_time_us); 
							last_tunnelrender_start_time_us = tunnelrender_start_time_us - 1;
						}
                                               if (curtime[1] < tunnelrender_audio_start_time_us) {
                        ALOGD("PTS reverted,use the new audio mediatimeUs .....");
                        tunnelrender_audio_start_time_us = curtime[1];
                        tunnelrender_start_time_us = curtime[0];
                                               } 
						if(tunnelrender_start_time_us >curtime[0] - (curtime[1] - tunnelrender_audio_start_time_us) ) {
        ALOGD("update new start system time Us,%" PRId64 " :%" PRId64 " :%" PRId64 " :%" PRId64,(int64_t)curtime[0],(int64_t)curtime[1], (int64_t)tunnelrender_audio_start_time_us,(int64_t)tunnelrender_start_time_us);
							tunnelrender_start_time_us = curtime[0] - (curtime[1] - tunnelrender_audio_start_time_us);
					}	
						
						int retrtptxt;
					  if((retrtptxt = access("data/test/omx_rs_txt_file2",0)) == 0)//test_file!=NULL)
	  				  {
	  					  
	  					  if(omx_rs_txt == NULL)
	  						  omx_rs_txt = fopen("data/test/omx_rs_txt.txt","ab");
	  					   
	  					
	  					  if(omx_rs_txt != NULL)
	  					  {
	  						  fprintf(omx_rs_txt,"TunnelRenderer::queueBuffer Audio start time %15" PRId64 " %15" PRId64 " adjust %" PRId64 " sys_time %15" PRId64 " %15" PRId64 " cur_Time %15" PRId64  " %15" PRId64 " delta sys %15" PRId64 " %15" PRId64 " %15" PRId64 " %15" PRId64 " %" PRId64 " first_seq_id %15" PRId64 " packet_num %15" PRId64 " packet_lost %15" PRId64 " packet_num_recent %15" PRId64 " packet_lost_recent %15" PRId64 "pes_length %d mPackets.size() %zu newExtendedSeqNo %d last_newExtendedSeqNo %d delta %d buffer->size() %zu PTS %" PRId64 " %2x%2x%2x%2x%2x   data %2x%2x%2x%2x%2x%2x%2x%2x%2x%2x%2x%2x   %2x\n",
					tunnelrender_start_time_us,tunnelrender_audio_start_time_us,last_adjust_time,sys_time_before_lock,sys_time,curtime[0],curtime[1], sys_time -curtime[0],
					curtime[0] - tunnelrender_start_time_us ,curtime[1] - tunnelrender_audio_start_time_us,
					curtime[0] - tunnelrender_start_time_us - curtime[1] + tunnelrender_audio_start_time_us,
					curtime[0]-last_adjust_time,first_seq_id, packet_num,packet_lost,packet_num_recent,packet_lost_recent,
					pes_length,mPackets.size(),newExtendedSeqNo,last_newExtendedSeqNo,newExtendedSeqNo - last_newExtendedSeqNo - 1,buffer->size(),
					PTS,buff[i+padding_len+13],buff[i+padding_len+14],buff[i+padding_len+15],buff[i+padding_len+16],buff[i+padding_len+17],
					buff[i+padding_len+18],buff[i+padding_len+19],buff[i+padding_len+20],buff[i+padding_len+21],
					buff[i+padding_len+22],buff[i+padding_len+23],buff[i+padding_len+24],
					buff[i+padding_len+25],buff[i+padding_len+26],buff[i+padding_len+27],buff[i+padding_len+28],buff[i+padding_len+29]  ,buff[i+3]);
	  						  fflush(omx_rs_txt);
	  					  }
	  				  }
	            	}
				}
				#if 0
				else if(buff[i] == 0x47 && buff[i+1] == 0x40 && buff[i+2] == 0x00 )
	            {
	            	int retrtptxt;
					int64_t curtime[2];
					buffer->meta()->findInt64("arrivalTimeUs", &curtime[0]);	
					normal_sign = 1;
	            	if((retrtptxt = access("data/test/omx_rs_txt_file",0)) == 0)//test_file!=NULL)
	  				  {
	  					  
	  					  if(omx_rs_txt == NULL)
	  						  omx_rs_txt = fopen("data/test/omx_rs_txt.txt","ab");
	  					   
	  					
	  					  if(omx_rs_txt != NULL)
	  					  {
	  						  fprintf(omx_rs_txt,"TunnelRender::queueBuffer pat sys_time %15lld %15lld %15lld mPackets.size() %d newExtendedSeqNo %d last_newExtendedSeqNo %d delta %d ,buffer->getStrongCount() %d\n",
							  	sys_time_before_lock,sys_time,curtime[0],mPackets.size(),newExtendedSeqNo,last_newExtendedSeqNo,newExtendedSeqNo - last_newExtendedSeqNo-1,buffer->getStrongCount());
	  						  fflush(omx_rs_txt);
	  					  }
	  				  }
				}
				else if(buff[i] == 0x47 && buff[i+1] == 0x41 && buff[i+2] == 0x00 )
	            {
	            	int retrtptxt;
					int64_t curtime[2];
					buffer->meta()->findInt64("arrivalTimeUs", &curtime[0]);
					normal_sign = 1;
	            	if((retrtptxt = access("data/test/omx_rs_txt_file",0)) == 0)//test_file!=NULL)
	  				  {
	  					  
	  					  if(omx_rs_txt == NULL)
	  						  omx_rs_txt = fopen("data/test/omx_rs_txt.txt","ab");
	  					   
	  					
	  					  if(omx_rs_txt != NULL)
	  					  {
	  						  fprintf(omx_rs_txt,"TunnelRender::queueBuffer pmt sys_time %15lld %15lld %15lld mPackets.size() %d newExtendedSeqNo %d delta %d\n"
							  	,sys_time_before_lock,sys_time,curtime[0],mPackets.size(),newExtendedSeqNo,newExtendedSeqNo - last_newExtendedSeqNo);
	  						  fflush(omx_rs_txt);
	  					  }
	  				  }
				}
				#endif
				
			}
		}
		if(0)//normal_sign == 0)
        {
        	int retrtptxt;
			normal_sign = 1;
        	if((retrtptxt = access("data/test/omx_rs_txt_file",0)) == 0)//test_file!=NULL)
				  {
					  
					  if(omx_rs_txt == NULL)
						  omx_rs_txt = fopen("data/test/omx_rs_txt.txt","ab");
					   
					
					  if(omx_rs_txt != NULL)
					  {
						  fprintf(omx_rs_txt,"TunnelRender::queueBuffer normal mPackets.size() %zu newExtendedSeqNo %d delta %d\n",mPackets.size(),newExtendedSeqNo,newExtendedSeqNo - last_newExtendedSeqNo);
						  fflush(omx_rs_txt);
					  }
				  }
		}
	}
	else
	{
		unsigned char* buff = (unsigned char*)buffer->data();
		int 	len = buffer->size();

		for(int i = 0; i < len-18; i++) //lbt
        {
			int	padding_len = ((buff[i+3] & 0x30) == 0x30)? (buff[i+4] + 1): 0;
	
			if(buff[i] == 0x47 && ((buff[i+1]&0x40) ) &&((buff[i+3] & 0x10) == 0x10)&&
				buff[i + padding_len + 4] == 0x0&& buff[i + padding_len + 5] == 0x0 && buff[i + padding_len + 6] == 0x1)
			{
				if(buff[i] == 0x47 && buff[i+1] == 0x51 && buff[i+2] == 0x00 )
	            {
                    uint64_t     PTS;
                    //long long curtime[2];
		    int64_t curtime[2];
                    buffer->meta()->findInt64("arrivalTimeUs", &curtime[0]);	
                    PTS = (((uint64_t)buff[i+padding_len+13] ) & 0xe) <<29;
                    PTS |= buff[i+padding_len+14] <<22;
                    PTS |= (buff[i+padding_len+15] & 0xfe) <<14;
                    PTS |= (buff[i+padding_len+16] ) <<7;
                    PTS |= (buff[i+padding_len+17] >>1);
       				curtime[1] = (int64_t)(PTS) * 100 /9;
					if((tunnelrender_audio_start_time_us == 0 && tunnelrender_start_time_us == 0))
					{
						last_adjust_time = curtime[0];
						tunnelrender_start_time_us = curtime[0];
						tunnelrender_audio_start_time_us  = curtime[1];
					}
					if(curtime[0] - last_adjust_time > 500000000ll)
					{
						last_adjust_time = curtime[0];
						tunnelrender_start_time_us = curtime[0] - (curtime[1] - tunnelrender_audio_start_time_us); 
						last_tunnelrender_start_time_us = tunnelrender_start_time_us - 1;
					}
					if(tunnelrender_start_time_us >curtime[0] - (curtime[1] - tunnelrender_audio_start_time_us) )
						tunnelrender_start_time_us = curtime[0] - (curtime[1] - tunnelrender_audio_start_time_us);	
            	}
			}
		}
	}
	last_newExtendedSeqNo = newExtendedSeqNo ;
    if (mPackets.empty()) {
        mPackets.push_back(buffer);
        return;
    }

	

    List<sp<ABuffer> >::iterator firstIt = mPackets.begin();
    List<sp<ABuffer> >::iterator it = --mPackets.end();
    for (;;) {
        int32_t extendedSeqNo = (*it)->int32Data();

        if (extendedSeqNo == newExtendedSeqNo) {
            // Duplicate packet.
            return;
        }

        if (extendedSeqNo < newExtendedSeqNo) {
            // Insert new packet after the one at "it".
            mPackets.insert(++it, buffer);
            return;
        }

        if (it == firstIt) {
            // Insert new packet before the first existing one.
            mPackets.insert(it, buffer);
            return;
        }

        --it;
    }
}
void* TunnelRenderer::doSomeWork()
{
	if (mStreamSource == NULL) {
        if (mTotalBytesQueued > 0ll) {
         //   initPlayer();
        } else {
            ALOGI("Have %" PRId64 " bytes queued...", mTotalBytesQueued);
        }
    } else {
        mStreamSource->doSomeWork();
    }
	return NULL;
}

sp<ABuffer> TunnelRenderer::dequeueBuffer() {
    Mutex::Autolock autoLock(mLock);

    sp<ABuffer> buffer;
    int32_t extSeqNo;
    while (!mPackets.empty()) {
        buffer = *mPackets.begin();
        extSeqNo = buffer->int32Data();

        if (mLastDequeuedExtSeqNo < 0 || extSeqNo > mLastDequeuedExtSeqNo) {
            break;
        }

        // This is a retransmission of a packet we've already returned.

        mTotalBytesQueued -= buffer->size();
        buffer.clear();
        extSeqNo = -1;

        mPackets.erase(mPackets.begin());
    }

    if (mPackets.empty()) {
        if (mFirstFailedAttemptUs < 0ll) {
            mFirstFailedAttemptUs = ALooper::GetNowUs();
            mRequestedRetransmission = false;
        } else {
            ALOGV("no packets available for %.2f secs",
                    (ALooper::GetNowUs() - mFirstFailedAttemptUs) / 1E6);
        }

        return NULL;
    }
	if(first_seq_id != (extSeqNo & 0xffffc00))
	{
		first_seq_id = extSeqNo & 0xffffffffffffc00;
		packet_lost_recent= 0;
		packet_num_recent = 0;
	}

	{
		if(extSeqNo != mLastDequeuedExtSeqNo)
		{
		packet_num++;
		packet_num_recent++;
		packet_lost += extSeqNo - mLastDequeuedExtSeqNo - 1;
		packet_lost_recent += extSeqNo - mLastDequeuedExtSeqNo - 1;
		}
	}
    if (mLastDequeuedExtSeqNo < 0 || extSeqNo == mLastDequeuedExtSeqNo + 1) {
        if (mRequestedRetransmission) {
            ALOGI("Recovered after requesting retransmission of %d",
                  extSeqNo);
        }
       // dump();
		if(0)
		{
            unsigned char* buff = (unsigned char*)buffer->data();
            int     len = buffer->size();
            int i ;
			int	normal_sign = 0;
			
            for(i = 0; i < len-18; i++)
            {

			
				
				int	padding_len = ((buff[i+3] & 0x30) == 0x30)? (buff[i+4] + 1): 0;
		

				if(buff[i] == 0x47 && ((buff[i+1]&0x40) ) &&((buff[i+3] & 0x10) == 0x10)&&
					buff[i + padding_len + 4] == 0x0&& buff[i + padding_len + 5] == 0x0 && buff[i + padding_len + 6] == 0x1)
				{
					normal_sign = 1;
					if(buff[i] == 0x47 && buff[i+1] == 0x50 && buff[i+2] == 0x11 )
		            {
	                    uint64_t     PTS;
	                    //long long curtime[2];
			    int64_t curtime[2];
						int64_t sys_time = systemTime(SYSTEM_TIME_MONOTONIC) / 1000;		
	                    buffer->meta()->findInt64("arrivalTimeUs", &curtime[0]);	
	                    PTS = (((uint64_t)buff[i+padding_len+13] ) & 0xe) <<29;
	                    PTS |= buff[i+padding_len+14] <<22;
	                    PTS |= (buff[i+padding_len+15] & 0xfe) <<14;
	                    PTS |= (buff[i+padding_len+16] ) <<7;
	                    PTS |= (buff[i+padding_len+17] >>1);
	       				curtime[1] = (int64_t)(PTS) * 100 /9;
						 

					  int retrtptxt;
	                  if((retrtptxt = access("data/test/omx_rs_txt_file",0)) == 0)//test_file!=NULL)
	  				  {
	  					  
	  					  if(omx_rs_txt == NULL)
	  						  omx_rs_txt = fopen("data/test/omx_rs_txt.txt","ab");
	  					   
	  					
		  if(omx_rs_txt != NULL)
		  {
		      fprintf(omx_rs_txt,"TunnelRenderer::dequeueBuffer Video start time %15" PRId64  " %15" PRId64 "cur_Time   %15" PRId64  " %15" PRId64 " delta sys %15" PRId64" %15" PRId64 "%15" PRId64 " first_seq_id %16" PRId64 " packet %16" PRId64 "%16" PRId64 " packet_recent %16" PRId64 " %16" PRId64  " mPackets.size() %zu buffer->size() %zu mLastDequeuedExtSeqNo %d  extSeqNo %d delta %d PTS %" PRId64 " %2x%2x%2x%2x%2x   data %2x%2x%2x%2x%2x%2x%2x%2x%2x%2x%2x%2x   %2x\n",
							tunnelrender_start_time_us,tunnelrender_audio_start_time_us,sys_time,curtime[1], 
							curtime[0] - tunnelrender_start_time_us ,curtime[1] - tunnelrender_audio_start_time_us,
							curtime[0] - tunnelrender_start_time_us - curtime[1] + tunnelrender_audio_start_time_us,
							first_seq_id, packet_num,packet_lost,packet_num_recent,packet_lost_recent,
							mPackets.size(),buffer->size(),mLastDequeuedExtSeqNo,extSeqNo,extSeqNo-mLastDequeuedExtSeqNo,
							PTS,buff[i+padding_len+13],buff[i+padding_len+14],buff[i+padding_len+15],buff[i+padding_len+16],buff[i+padding_len+17],
							buff[i+padding_len+18],buff[i+padding_len+19],buff[i+padding_len+20],buff[i+padding_len+21],
							buff[i+padding_len+22],buff[i+padding_len+23],buff[i+padding_len+24],
							buff[i+padding_len+25],buff[i+padding_len+26],buff[i+padding_len+27],buff[i+padding_len+28],buff[i+padding_len+29]  ,buff[i+3]);
			  fflush(omx_rs_txt);
		  }
						  
	  				  }
	            	}
					else if(buff[i] == 0x47 && buff[i+1] == 0x51 && buff[i+2] == 0x00 )
		            {
	                    uint64_t     PTS;
	                    //long long curtime[2];
			    int64_t curtime[2];
						int64_t sys_time = systemTime(SYSTEM_TIME_MONOTONIC) / 1000;		
	                    buffer->meta()->findInt64("arrivalTimeUs", &curtime[0]);	
	                    PTS = (((uint64_t)buff[i+padding_len+13] ) & 0xe) <<29;
	                    PTS |= buff[i+padding_len+14] <<22;
	                    PTS |= (buff[i+padding_len+15] & 0xfe) <<14;
	                    PTS |= (buff[i+padding_len+16] ) <<7;
	                    PTS |= (buff[i+padding_len+17] >>1);
	       				curtime[1] = (int64_t)(PTS) * 100 /9;
					
						
						int retrtptxt;
					  if((retrtptxt = access("data/test/omx_rs_txt_file2",0)) == 0)//test_file!=NULL)
	  				  {
	  					  
	  					  if(omx_rs_txt == NULL)
	  						  omx_rs_txt = fopen("data/test/omx_rs_txt.txt","ab");
	  					   
	  					
	  					  if(omx_rs_txt != NULL)
	  					  {
	  						  fprintf(omx_rs_txt,"TunnelRenderer::dequeueBuffer Audio start time %15" PRId64  " %15" PRId64 " cur_Time   %15" PRId64  " %15" PRId64 " delta sys %15" PRId64 " %15" PRId64 " %15" PRId64  "first_seq_id %16" PRId64 " packet %16" PRId64 " %16" PRId64 " packet_recent %16" PRId64 " %16" PRId64  " mPackets.size() %zu buffer->size() %zu mLastDequeuedExtSeqNo %d  extSeqNo %d delta %d PTS %" PRId64 " %2x%2x%2x%2x%2x   data %2x%2x%2x%2x%2x%2x%2x%2x%2x%2x%2x%2x   %2x\n",
							tunnelrender_start_time_us,tunnelrender_audio_start_time_us,sys_time,curtime[1], 
							curtime[0] - tunnelrender_start_time_us ,curtime[1] - tunnelrender_audio_start_time_us,
							curtime[0] - tunnelrender_start_time_us - curtime[1] + tunnelrender_audio_start_time_us,
							first_seq_id, packet_num,packet_lost,packet_num_recent,packet_lost_recent,
							mPackets.size(),buffer->size(),mLastDequeuedExtSeqNo,extSeqNo,extSeqNo-mLastDequeuedExtSeqNo,
							PTS,buff[i+padding_len+13],buff[i+padding_len+14],buff[i+padding_len+15],buff[i+padding_len+16],buff[i+padding_len+17],
							buff[i+padding_len+18],buff[i+padding_len+19],buff[i+padding_len+20],buff[i+padding_len+21],
							buff[i+padding_len+22],buff[i+padding_len+23],buff[i+padding_len+24],
							buff[i+padding_len+25],buff[i+padding_len+26],buff[i+padding_len+27],buff[i+padding_len+28],buff[i+padding_len+29]  ,buff[i+3]);
	  						  fflush(omx_rs_txt);
	  					  }
	  				  }
	            	}
					
				}
				#if 0
				else if(buff[i] == 0x47 && buff[i+1] == 0x40 && buff[i+2] == 0x00 )
	            {
	            	int retrtptxt;
					normal_sign = 1;
	            	if((retrtptxt = access("data/test/omx_rs_txt_file",0)) == 0)//test_file!=NULL)
	  				  {
	  					  
	  					  if(omx_rs_txt == NULL)
	  						  omx_rs_txt = fopen("data/test/omx_rs_txt.txt","ab");
	  					   
	  					
	  					  if(omx_rs_txt != NULL)
	  					  {
	  						  fprintf(omx_rs_txt,"TunnelRenderer::dequeueBuffer pat mPackets.size() %d mLastDequeuedExtSeqNo %d  extSeqNo  %d delta %d buffer->count %d\n",
							  	mPackets.size(),mLastDequeuedExtSeqNo, extSeqNo,extSeqNo-mLastDequeuedExtSeqNo,buffer->getStrongCount());
	  						  fflush(omx_rs_txt);
	  					  }
	  				  }
				}
				else if(buff[i] == 0x47 && buff[i+1] == 0x41 && buff[i+2] == 0x00 )
	            {
	            	int retrtptxt;
					normal_sign = 1;
	            	if((retrtptxt = access("data/test/omx_rs_txt_file",0)) == 0)//test_file!=NULL)
	  				  {
	  					  
	  					  if(omx_rs_txt == NULL)
	  						  omx_rs_txt = fopen("data/test/omx_rs_txt.txt","ab");
	  					   
	  					
	  					  if(omx_rs_txt != NULL)
	  					  {
	  						  fprintf(omx_rs_txt,"TunnelRenderer::dequeueBuffer pmt mPackets.size() %d mLastDequeuedExtSeqNo %d  extSeqNo %d delta %d\n",mPackets.size(),mLastDequeuedExtSeqNo, extSeqNo,extSeqNo-mLastDequeuedExtSeqNo);
	  						  fflush(omx_rs_txt);
	  					  }
	  				  }
				}
				#endif
    		}
			if(0)//normal_sign == 0)
			{
            	int retrtptxt;
            	if((retrtptxt = access("data/test/omx_rs_txt_file",0)) == 0)//test_file!=NULL)
  				  {
  					  
  					  if(omx_rs_txt == NULL)
  						  omx_rs_txt = fopen("data/test/omx_rs_txt.txt","ab");
  					   
  					
  					  if(omx_rs_txt != NULL)
  					  {
  						  fprintf(omx_rs_txt,"TunnelRenderer::dequeueBuffer normal mPackets.size() %zu mLastDequeuedExtSeqNo %d  extSeqNo %d delta %d\n",mPackets.size(),mLastDequeuedExtSeqNo, extSeqNo,extSeqNo-mLastDequeuedExtSeqNo);
  						  fflush(omx_rs_txt);
  					  }
  				  }
			}
    	}
        mLastDequeuedExtSeqNo = extSeqNo;
        mFirstFailedAttemptUs = -1ll;
        mRequestedRetransmission = false;

        mPackets.erase(mPackets.begin());

        mTotalBytesQueued -= buffer->size();

        return buffer;
    }
#if 0
    if (mFirstFailedAttemptUs < 0ll) {
		#if 0
        mFirstFailedAttemptUs = ALooper::GetNowUs();

        ALOGI("failed to get the correct packet the first time mLastDequeuedExtSeqNo %d.",mLastDequeuedExtSeqNo);
        return NULL;
		#else
		mFirstFailedAttemptUs = ALooper::GetNowUs();
        ALOGI("failed to get the correct packet the first time mLastDequeuedExtSeqNo %d.",mLastDequeuedExtSeqNo);
		mLastDequeuedExtSeqNo = extSeqNo;

        mPackets.erase(mPackets.begin());

        mTotalBytesQueued -= buffer->size();
		
        return buffer;
		#endif
    }

    if (mFirstFailedAttemptUs + 50000ll > ALooper::GetNowUs()) {
        // We're willing to wait a little while to get the right packet.
		if(0)//extSeqNo == mLastDequeuedExtSeqNo + 1 && mPackets.size()> 5) 
		{
	        if (!mRequestedRetransmission) {
	            ALOGI("requesting retransmission of seqNo %d",
	                  (mLastDequeuedExtSeqNo + 1) & 0xffff);

	            sp<AMessage> notify = mNotifyLost->dup();
	            notify->setInt32("seqNo", (mLastDequeuedExtSeqNo + 1) & 0xffff);
	            notify->post();

	            mRequestedRetransmission = true;
	        } else {
	            ALOGI("still waiting for the correct packet to arrive.");
	        }
			int retrtptxt;
			if((retrtptxt = access("data/test/omx_rs_txt_file",0)) == 0)//test_file!=NULL)
			{
			  
			  int64_t sys_time = systemTime(SYSTEM_TIME_MONOTONIC) / 1000;		  
			  if(omx_rs_txt == NULL)
				  omx_rs_txt = fopen("data/test/omx_rs_txt.txt","ab");
			   
								
			  if(omx_rs_txt != NULL)
			  {
				  fprintf(omx_rs_txt,"TunnelRenderer::dequeueBuffer request retransmis start time %15lld  %15lld cur_Time %15lld   mPackets.size() %6d buffer->size() %d mLastDequeuedExtSeqNo %d  extSeqNo %d delta %d \n",
								tunnelrender_start_time_us,tunnelrender_audio_start_time_us,sys_time
								
								,mPackets.size(),buffer->size(),mLastDequeuedExtSeqNo,extSeqNo,extSeqNo-mLastDequeuedExtSeqNo
								);
				  fflush(omx_rs_txt);
			  }

			}
	        return NULL;
		}
		else
		{
			if (!mRequestedRetransmission)  {
	            ALOGI("requesting retransmission of seqNo %d",
	                  (mLastDequeuedExtSeqNo + 1) & 0xffff);
	            mRequestedRetransmission = true;
	        } else {
	            ALOGI("still waiting for the correct packet to arrive.");
	        }
			mLastDequeuedExtSeqNo = extSeqNo;

	        mPackets.erase(mPackets.begin());

	        mTotalBytesQueued -= buffer->size();
			
	        return buffer;
		}
    }
#endif

    ALOGI("dropping packet. extSeqNo %d didn't arrive in time but newSeqNo %d",
            mLastDequeuedExtSeqNo + 1, extSeqNo);

    // Permanent failure, we never received the packet.
    mLastDequeuedExtSeqNo = extSeqNo;
    mFirstFailedAttemptUs = -1ll;
    mRequestedRetransmission = false;

    mTotalBytesQueued -= buffer->size();

    mPackets.erase(mPackets.begin());

    return buffer;
}

void TunnelRenderer::onMessageReceived(const sp<AMessage> &msg) {
    switch (msg->what()) {
        case kWhatQueueBuffer:
        {
            sp<ABuffer> buffer;
            int32_t temp;
            CHECK(msg->findBuffer("buffer", &buffer));
            if (msg->findInt32("resync_rtpseq", &temp)) {
                ALOGD("resync rtpseq %d", temp);
                mLastDequeuedExtSeqNo = -1;
            }

            queueBuffer(buffer);

            if (mStreamSource == NULL) {
                if (mTotalBytesQueued > 0ll) {
                    initPlayer();
                } else {
                    ALOGI("Have %" PRId64 " bytes queued...", mTotalBytesQueued);
                }
            } else {
                mStreamSource->doSomeWork();
            }
            break;
        }

        default:
            TRESPASS();
    }
}

void TunnelRenderer::initPlayer() {
    if (1) {
        mComposerClient = new SurfaceComposerClient;
        CHECK_EQ(mComposerClient->initCheck(), (status_t)OK);

#if PLATFORM_SDK_VERSION >= 29
	sp<IBinder> display(SurfaceComposerClient::getInternalDisplayToken());
#else
    sp<IBinder> display(SurfaceComposerClient::getBuiltInDisplay(
            ISurfaceComposer::eDisplayIdMain));
#endif

#if (PLATFORM_VERSION >= 12)
    CHECK(display != nullptr);
    ui::DisplayMode mode;
    CHECK_EQ(SurfaceComposerClient::getActiveDisplayMode(display, &mode), NO_ERROR);
    const ui::Size& resolution = mode.resolution;
    const ssize_t displayWidth = resolution.getWidth();
    const ssize_t displayHeight = resolution.getHeight();
    ALOGD("display is %zd x %zd\n", displayWidth, displayHeight);
#elif (PLATFORM_VERSION == 11)
    DisplayConfig config;
    CHECK_EQ(SurfaceComposerClient::getActiveDisplayConfig(display, &config), NO_ERROR);
    const ui::Size& resolution = config.resolution;
    int32_t displayWidth = resolution.getWidth();
    int32_t displayHeight = resolution.getHeight();
#else
    DisplayInfo info;
    SurfaceComposerClient::getDisplayInfo(display, &info);
    if(info.w > info.h)
	{
         displayWidth = (int32_t)info.w;
         displayHeight = (int32_t)info.h;
	} else {
         displayWidth = (int32_t)info.h;
         displayHeight = (int32_t)info.w;
	}	
#endif
		rotate_displayWidth 	= displayHeight;
		rotate_displayHeight	= displayHeight * displayHeight / displayWidth;
		rotate_xpos 			= 0;
		rotate_ypos				= (displayWidth - rotate_displayHeight) / 2;
      	screen_dir 				= 0;
      	rotation 				= 0;
		char pro_value[PROPERTY_VALUE_MAX];
		char pro_value_pro[PROPERTY_VALUE_MAX];

		property_get("sys.display.oritation",pro_value,0);
		
		if(pro_value[0] - '0' < 4 && pro_value[0] - '0' >=0)
			screen_dir = pro_value[0] - '0';

		property_get("sys.wfd.rotation",pro_value_pro,0);
		
		if(pro_value_pro[0] - '0' < 4 && pro_value_pro[0] - '0' >=0)
			rotation = pro_value_pro[0] - '0';
	     ALOGE("initPlayer####displayHeight=%zd,displayWidth=%zd rot_w %d rot_h %d x %d y %d screen_dir %d, rotation %d %c %c",
		displayHeight,displayWidth,rotate_displayWidth,rotate_displayHeight,rotate_xpos,rotate_ypos,screen_dir,rotation,
			pro_value[0],pro_value_pro[0]);
#if 0

		{
	

			// create a client to surfaceflinger
			mComposerClient_back = new SurfaceComposerClient();

			mSurfaceControl_back= mComposerClient_back->createSurface(String8("wfd_backgroud"),
			displayWidth, displayHeight, PIXEL_FORMAT_RGB_565, 0);
			mSurface_back  = mSurfaceControl_back->getSurface();
	        CHECK(mSurface_back != NULL);

			SurfaceComposerClient::openGlobalTransaction();
			if((screen_dir == 0 &&( rotation % 2) == 0) ||(screen_dir == 2 &&( rotation % 2) == 1) )
			{
				mSurfaceControl_back->setSize(displayWidth,displayHeight);
				mSurfaceControl_back->setPosition(0,0);
			}
			else if((screen_dir == 0 &&( rotation % 2) == 1) ||(screen_dir == 2 &&( rotation % 2) == 0) )//((rotation %2) == 0)//((screen_dir == 0 &&( rotation % 2) == 1) ||(screen_dir == 2 &&( rotation % 2) == 0) )
			{
				mSurfaceControl_back->setSize(displayHeight,displayWidth);
				mSurfaceControl_back->setPosition(0,0);
				
			}
			CHECK_EQ(mSurfaceControl_back->setLayer(INT_MAX - 1), (status_t)OK);
	        CHECK_EQ(mSurfaceControl_back->show(), (status_t)OK);
			mSurfaceControl_back->setPosition(0,0);
	        SurfaceComposerClient::closeGlobalTransaction();
			Surface::SurfaceInfo info;
			mSurface_back->lock(&info);
			ssize_t bpr = info.s * bytesPerPixel(info.format);
			android_memset16((uint16_t*)info.bits, 0x00, bpr*info.h);
			mSurface_back->unlockAndPost();
		}
#endif
        mSurfaceControl =
            mComposerClient->createSurface(
                    String8("A Surface"),
                    displayWidth,/*displayWidth,*/
                    displayHeight,/*displayHeight,*/
                    PIXEL_FORMAT_RGB_565,
                    0);

        CHECK(mSurfaceControl != NULL);
        CHECK(mSurfaceControl->isValid());

		if((screen_dir == 0 &&( rotation % 2) == 0) ||(screen_dir == 2 &&( rotation % 2) == 1) )//((rotation % 2) == 1)//)((screen_dir == 0 &&( rotation % 2) == 0) ||(screen_dir == 2 &&( rotation % 2) == 1) )
		{
#if PLATFORM_SDK_VERSION >= 28
                        SurfaceComposerClient::Transaction{}
                             .setSize(mSurfaceControl,displayWidth, displayHeight)
                             .setPosition(mSurfaceControl,0, 0)
                             .apply();
#else
                        SurfaceComposerClient::openGlobalTransaction();
                        mSurfaceControl->setSize(displayWidth, displayHeight);
                        mSurfaceControl->setPosition(0, 0);
#endif
		}
		else if((screen_dir == 0 &&( rotation % 2) == 1) ||(screen_dir == 2 &&( rotation % 2) == 0) )//((rotation %2) == 0)//((screen_dir == 0 &&( rotation % 2) == 1) ||(screen_dir == 2 &&( rotation % 2) == 0) )
		{
#if PLATFORM_SDK_VERSION >= 28
                        SurfaceComposerClient::Transaction{}
                             .setSize(mSurfaceControl,rotate_displayWidth, rotate_displayHeight)
                             .setPosition(mSurfaceControl,rotate_xpos, rotate_ypos)
                             .apply();
#else
                        SurfaceComposerClient::openGlobalTransaction();
                        mSurfaceControl->setSize(displayWidth, displayHeight);
                        mSurfaceControl->setPosition(0, 0);
#endif
		}

#if PLATFORM_SDK_VERSION >= 28
        SurfaceComposerClient::Transaction{}
            .setLayer(mSurfaceControl, INT_MAX)
            .show(mSurfaceControl)
            .apply();
#else
        SurfaceComposerClient::openGlobalTransaction();
        mSurfaceControl->setSize(displayWidth, displayHeight);
        mSurfaceControl->setPosition(0, 0);
#endif

        mSurface = mSurfaceControl->getSurface();
        CHECK(mSurface != NULL);
    }
	
    sp<IServiceManager> sm = defaultServiceManager();
    sp<IBinder> binder = sm->getService(String16("media.player"));
    sp<IMediaPlayerService> service = interface_cast<IMediaPlayerService>(binder);
    CHECK(service.get() != NULL);

    mStreamSource = new StreamSource(this);
    mPlayerClient = new PlayerClient;

    mPlayer = service->create(mPlayerClient, AUDIO_SESSION_ALLOCATE);
    CHECK(mPlayer != NULL);
    CHECK_EQ(mPlayer->setDataSource((sp<IStreamSource>)mStreamSource),(status_t)OK);
    mPlayer->setVideoSurfaceTexture(mSurface->getIGraphicBufferProducer());
    mPlayer->start();
}

void TunnelRenderer::destroyPlayer() {
	ALOGD("TunnelRenderer::destroyPlayer");
	mStart = false;
	
    mStreamSource.clear();

    mPlayer->stop();
    mPlayer.clear();

    if (1) {
        mSurface.clear();
        mSurfaceControl.clear();

        mComposerClient->dispose();
        mComposerClient.clear();
    }
}

}  // namespace android

