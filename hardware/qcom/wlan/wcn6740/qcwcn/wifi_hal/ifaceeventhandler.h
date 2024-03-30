/* Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef __WIFI_HAL_IFACEEVENTHANDLER_COMMAND_H__
#define __WIFI_HAL_IFACEEVENTHANDLER_COMMAND_H__

#include "common.h"
#include "cpp_bindings.h"
#ifdef __GNUC__
#define PRINTF_FORMAT(a,b) __attribute__ ((format (printf, (a), (b))))
#define STRUCT_PACKED __attribute__ ((packed))
#else
#define PRINTF_FORMAT(a,b)
#define STRUCT_PACKED
#endif
#include "vendor_definitions.h"
#include "wifi_hal.h"

#ifdef __cplusplus
extern "C"
{
#endif /* __cplusplus */

typedef struct{
    int num_entries;
    radar_history_result *entries;
} RadarHistoryResultsParams;

class wifiEventHandler: public WifiCommand
{
private:
    int mRequestId;

protected:
    struct nlattr *tb[NL80211_ATTR_MAX + 1];
    u32 mSubcmd;

public:
    wifiEventHandler(wifi_handle handle, int id, u32 subcmd);
    virtual ~wifiEventHandler();
    virtual int get_request_id();
    virtual int handleEvent(WifiEvent &event);
};

class IfaceEventHandlerCommand: public wifiEventHandler
{
private:
    char *mEventData;
    u32 mDataLen;
    wifi_event_handler mHandler;

public:
    IfaceEventHandlerCommand(wifi_handle handle, int id, u32 subcmd);
    virtual ~IfaceEventHandlerCommand();

    virtual int handleEvent(WifiEvent &event);
    virtual void setCallbackHandler(wifi_event_handler nHandler);
    virtual int get_request_id();
};

class WifihalGeneric: public WifiVendorCommand
{
private:
    feature_set mSet;
    features_info mDriverFeatures;
    int mSetSizeMax;
    int *mSetSizePtr;
    feature_set *mConcurrencySet;
    int filterVersion;
    int filterLength;
    int firmware_bus_max_size;
    wifi_capa *mCapa;
    /* Packet Filter buffer and length */
    u8 *mfilter_packet_read_buffer;
    int mfilter_packet_length;
    u32 res_size;
    wifi_usable_channel *channel_buff;
    RadarHistoryResultsParams mRadarResultParams;
    virtual wifi_error wifiParseCapabilities(struct nlattr **tbVendor);
    virtual wifi_error wifiParseRadarHistory();

public:
    WifihalGeneric(wifi_handle handle, int id, u32 vendor_id, u32 subcmd);
    virtual ~WifihalGeneric();
    virtual wifi_error requestResponse();
    virtual int handleResponse(WifiEvent &reply);
    virtual int handle_response_usable_channels(struct nlattr *VendorData,
                                                u32 mDataLen);
    virtual void getResponseparams(feature_set *pset);
    virtual void getDriverFeatures(features_info *pfeatures);
    virtual void setMaxSetSize(int set_size_max);
    virtual void setSizePtr(int *set_size);
    virtual void setPacketBufferParams(u8 *host_packet_buffer, int packet_length);
    virtual void setConcurrencySet(feature_set set[]);
    virtual int getFilterVersion();
    virtual int getFilterLength();
    virtual int getBusSize();
    virtual wifi_error wifiGetCapabilities(wifi_interface_handle handle);
    virtual void set_channels_buff(wifi_usable_channel *channels);
    virtual u32 get_results_size(void);
    virtual wifi_error copyCachedRadarHistory(radar_history_result *resultBuf,
            int resultBufSize, int *numResults);
    virtual void freeCachedRadarHistory();
    virtual wifi_error getSarVersion(wifi_interface_handle handle);
};

/**
 * nla_for_each_nested from libnl is throwing implicit conversion from void*
 * error. Adding a local definition to avoid it.
 */
#define for_each_nested_attribute(pos, nla, rem) \
    for (pos = (struct nlattr *)nla_data(nla), rem = nla_len(nla); \
         nla_ok(pos, rem); \
         pos = nla_next(pos, &(rem)))

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif
