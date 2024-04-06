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
#define LOG_TAG "wfd_jni"
#include <utils/Log.h>

#include "include/WifiDisplaySink.h"

#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>
//#include <gui/SurfaceComposerClient.h>
#include <media/AudioSystem.h>
#include <media/IMediaPlayerService.h>
//#include <media/IRemoteDisplay.h>
#//include <media/IRemoteDisplayClient.h>
#include <media/stagefright/foundation/ADebug.h>
#include <stdio.h>
#include <stdlib.h>
#include<string.h>
#include <cutils/properties.h> // for property_get
#include <nativehelper/JNIHelp.h>
#include <assert.h>
#include <android_runtime/AndroidRuntime.h>

using namespace android;

#define TRY_ARP_COUNT 36

#define UNUSED(x)  (void)(x)

static int Search_ipAddr(const char *hwSrcAddr,char *IpAddr)
{
	//FILE *fp = fopen("/data/misc/dhcp/dnsmasq.leases", "rb");
        UNUSED(hwSrcAddr);
	FILE *fp = fopen("/proc/net/arp", "rb");
	ALOGI("##############");
	int iRet =-1;
	if (fp)
		{
			char *save_ptr;
			char *hwAddr,*ipAddr,*hwType,*Flag,*Mash,*Device;
			char line[255];				
			while(fgets(line, sizeof(line), fp)) {
				const char *delim = " ";
				
				line[strlen(line)-1] = '\0';
				ALOGI("line =%s",line);

				if (!(ipAddr = strtok_r(line, delim, &save_ptr))) {
					ALOGI("Error parsing hwAddr");
				}
				
				if (!(hwType = strtok_r(NULL, delim, &save_ptr))) {
					ALOGI("Error parsing hwType");
				}
				if (!(Flag = strtok_r(NULL, delim, &save_ptr))) {
					ALOGI("Error parsing hwType");
				}				
				if (!(hwAddr = strtok_r(NULL, delim, &save_ptr))) {
					ALOGI("Error parsing hwAddr");
				}
				if (!(Mash = strtok_r(NULL, delim, &save_ptr))) {
					ALOGI("Error parsing Mash");
				}
				if (!(Device = strtok_r(NULL, delim, &save_ptr))) {
					ALOGI("Error parsing Device");
				}				
				ALOGI("Device =%s",Device);
				if(memcmp("p2p-p2p",Device,strlen("p2p"))==0 && memcmp("0x2",Flag,3)==0)
				{
				    //memcpy(IpAddr,ipAddr,strlen(ipAddr));
                                    strcpy(IpAddr, ipAddr);
				    ALOGI("####IpAddr ==%s",IpAddr);
				    iRet =1;
				    break;						
				}
			}
			fclose(fp);
			return iRet;
			
		}
	return iRet;
}

int startWFDSink(const char *devaddr, bool isgo) {
         int32_t iCount =0;
         AString connectToHost;
         int32_t connectToPort = -1;
         AString uri;
	 AString connectAddr;

         char ipAddr[PROPERTY_VALUE_MAX];
         const char *colonPos = strrchr(devaddr, ':');
         ALOGD("start---------WFD s optarg=%s",devaddr);
         if (colonPos == NULL) {
            connectAddr = devaddr;
            ALOGE("not found colon symbol, wrong devaddr");
            return -1;
         } else {
            connectAddr.setTo(devaddr, colonPos - devaddr);
            char *end;
            connectToPort = strtol(colonPos + 1, &end, 10);

            if (*end != '\0' || end == colonPos + 1
                 || connectToPort < 1 || connectToPort > 65535) {
                  fprintf(stderr, "Illegal port specified.\n");
                  return -1;
            }
            ALOGD("start---------WFD s connectAddr=%s",connectAddr.c_str());
         
            if (!isgo){
                connectToHost.setTo(devaddr+1, colonPos-devaddr-1);  //skip '\'
                ALOGD("Client Role: connect to Host %s:%d", connectToHost.c_str(),connectToPort);
            } else{
                for(iCount =0;iCount <TRY_ARP_COUNT;iCount++)
                {
                   if(Search_ipAddr(connectAddr.c_str(),ipAddr) >0)
                          break;
                   usleep(300*1000);

                }
                if(iCount >=TRY_ARP_COUNT)
                {
                    ALOGE("####iCount =%d > (TRY_ARP_COUNT=%d)",iCount,TRY_ARP_COUNT);
                    return -1;
                }
                                                
                connectToHost =(AString)ipAddr;
                ALOGD("arp found--------WFD s connectToHost =%s,connectToPort=%d",connectToHost.c_str(),connectToPort);
            }          
         }
    
    usleep(500*1000);   //waiting for WFD Source service running
    sp<ANetworkSession> session = new ANetworkSession;
    session->start();

    sp<ALooper> looper = new ALooper;
    looper->setName("wfd");

    sp<WifiDisplaySink> sink = new WifiDisplaySink(session);
    looper->registerHandler(sink);
    if (connectToPort >= 0) {
	ALOGD("start---------WFD s connectToPort=%d connectToHost.c_str() %s",connectToPort,connectToHost.c_str() );
        sink->start(connectToHost.c_str(), connectToPort);
    } else {
	ALOGD("start---------WFD s connectToPort=%d uri.c_str() %s",connectToPort,uri.c_str() );
        sink->start(uri.c_str());
    }

    looper->start(true /* runOnCallingThread */); 
    return 0;
}

static jint android_p2p_startWFDSink(JNIEnv *env, jobject *thiz, jstring peerinfo, jboolean go) {
   UNUSED(thiz);
   const char *peerinfoStr = env->GetStringUTFChars(peerinfo, NULL);
   if (peerinfoStr == NULL) {  // Out of memory
        return -1;
   }
   return startWFDSink(peerinfoStr, go);
}

static const JNINativeMethod gMethods[] = {
    { "native_startWFDSink", "(Ljava/lang/String;Z)I",
      (void *)android_p2p_startWFDSink },
};

int register_android_p2p_wfdsink(JNIEnv *env) {
    return AndroidRuntime::registerNativeMethods(env,
                "com/rockchip/wfd/WifiDisplayService", gMethods, NELEM(gMethods));
}

jint JNI_OnLoad(JavaVM* vm, void* /* reserved */)
{
    JNIEnv* env = NULL;
    jint result = -1;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        ALOGE("ERROR: GetEnv failed\n");
        goto bail;
    }
    assert(env != NULL);

    if (register_android_p2p_wfdsink(env) < 0) {
        ALOGE("ERROR: p2p wfdsink native registration failed");
        goto bail;
    }
    /* success -- return valid version number */
    result = JNI_VERSION_1_4;
bail:
    return result;
}

