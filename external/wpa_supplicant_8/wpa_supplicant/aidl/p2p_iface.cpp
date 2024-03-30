/*
 * WPA Supplicant - P2P Iface Aidl interface
 * Copyright (c) 2021, Google Inc. All rights reserved.
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#include "aidl_manager.h"
#include "aidl_return_util.h"
#include "iface_config_utils.h"
#include "misc_utils.h"
#include "p2p_iface.h"
#include "sta_network.h"

extern "C"
{
#include "ap.h"
#include "wps_supplicant.h"
#include "wifi_display.h"
#include "utils/eloop.h"
#include "wpa_supplicant_i.h"
#include "driver_i.h"
}

#define P2P_MAX_JOIN_SCAN_ATTEMPTS 3
// Wait time before triggering the single channel scan to discover Auto GO.
// Use a shorter wait time when the given frequency is GO operating frequency.
// The idea is to quickly finish scans and return the status to application.
#define P2P_JOIN_SINGLE_CHANNEL_SCAN_INTERVAL_USECS 200000
// Wait time before triggering the multiple channel scan to discover Auto GO.
#define P2P_JOIN_MULTIPLE_CHANNEL_SCAN_INTERVAL_USECS 1000000

namespace {
const char kConfigMethodStrPbc[] = "pbc";
const char kConfigMethodStrDisplay[] = "display";
const char kConfigMethodStrKeypad[] = "keypad";
constexpr char kSetMiracastMode[] = "MIRACAST ";
constexpr uint8_t kWfdDeviceInfoSubelemId = 0;
constexpr uint8_t kWfdR2DeviceInfoSubelemId = 11;
constexpr char kWfdDeviceInfoSubelemLenHexStr[] = "0006";

std::function<void()> pending_join_scan_callback = NULL;
std::function<void()> pending_scan_res_join_callback = NULL;

using aidl::android::hardware::wifi::supplicant::ISupplicantP2pIface;
using aidl::android::hardware::wifi::supplicant::ISupplicantStaNetwork;
using aidl::android::hardware::wifi::supplicant::MiracastMode;
using aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask;

uint8_t convertAidlMiracastModeToInternal(
	MiracastMode mode)
{
	switch (mode) {
	case MiracastMode::DISABLED:
		return 0;
	case MiracastMode::SOURCE:
		return 1;
	case MiracastMode::SINK:
		return 2;
	};
	WPA_ASSERT(false);
}

/**
 * Check if the provided ssid is valid or not.
 *
 * Returns 1 if valid, 0 otherwise.
 */
int isSsidValid(const std::vector<uint8_t>& ssid)
{
	if (ssid.size() == 0 ||
		ssid.size() >
		static_cast<uint32_t>(ISupplicantStaNetwork::
					  SSID_MAX_LEN_IN_BYTES)) {
		return 0;
	}
	return 1;
}

/**
 * Check if the provided psk passhrase is valid or not.
 *
 * Returns 1 if valid, 0 otherwise.
 */
int isPskPassphraseValid(const std::string &psk)
{
	if (psk.size() <
		static_cast<uint32_t>(ISupplicantStaNetwork::
					  PSK_PASSPHRASE_MIN_LEN_IN_BYTES) ||
		psk.size() >
		static_cast<uint32_t>(ISupplicantStaNetwork::
					  PSK_PASSPHRASE_MAX_LEN_IN_BYTES)) {
		return 0;
	}
	if (has_ctrl_char((u8 *)psk.c_str(), psk.size())) {
		return 0;
	}
	return 1;
}

static int setBandScanFreqsList(
	struct wpa_supplicant *wpa_s,
	enum hostapd_hw_mode hw_mode,
	bool exclude_dfs,
	struct wpa_driver_scan_params *params)
{
	struct hostapd_hw_modes *mode;
	int count, i;

	mode = get_mode(wpa_s->hw.modes, wpa_s->hw.num_modes, hw_mode, 0);
	if (mode == NULL || !mode->num_channels) {
		wpa_printf(MSG_ERROR,
			"P2P: No channels supported in this hw_mode: %d", hw_mode);
		return -1;
	}

	/*
	 * Allocate memory for frequency array, allocate one extra
	 * slot for the zero-terminator.
	 */
	params->freqs = (int *) os_calloc(mode->num_channels + 1, sizeof(int));
	if (params->freqs == NULL) {
		return -ENOMEM;
	}
	for (count = 0, i = 0; i < mode->num_channels; i++) {
		if (mode->channels[i].flag & HOSTAPD_CHAN_DISABLED) {
			continue;
		}
		if (exclude_dfs && (mode->channels[i].flag & HOSTAPD_CHAN_RADAR)) {
			continue;
		}
		params->freqs[count++] = mode->channels[i].freq;
	}
	if (!count && params->freqs) {
		wpa_printf(MSG_ERROR,
			"P2P: All channels(exclude_dfs: %d) are disabled in this hw_mode: %d",
			exclude_dfs, hw_mode);
		os_free(params->freqs);
		return -1;
	}
	return 0;
}

static int setScanFreq(struct wpa_supplicant *wpa_s, struct wpa_driver_scan_params *params,
	int freq, int operating_freq)
{
	int frequency = operating_freq ? operating_freq : freq;
	if (disabled_freq(wpa_s, frequency)) {
		wpa_printf(MSG_ERROR,
				"P2P: freq %d is not supported for a client.", frequency);
		return -1;
	}
	/*
	 * Allocate memory for frequency array, with one extra
	 * slot for the zero-terminator.
	 */
	params->freqs = new int[2] {frequency, 0};
	return 0;
}

/**
 * setP2pCliOptimizedScanFreqsList - Fill the frequencies to scan in Scan
 * parameters.
 * @wpa_s: Pointer to wpa_supplicant data
 * @params: Pointer to Scan parameters.
 * @freq: Frequency/Band requested to scan by the application, possible values are,
 *		0 - All the frequencies - full scan
 *		2 - Frequencies in 2.4GHz
 *		5 - Frequencies in 5GHz
 *		- Valid frequency
 * @operating_freq: Frequency of BSS if found in scan cache
 * Returns: Pointer to the BSS entry or %NULL if not found
 */
static int setP2pCliOptimizedScanFreqsList(struct wpa_supplicant *wpa_s,
	struct wpa_driver_scan_params *params, int freq, int operating_freq)
{
	int ret;
	/* If BSS is found in scan cache, first scan its operating frequency */
	if (!wpa_s->p2p_join_scan_count && operating_freq) {
		ret = setScanFreq(wpa_s, params, freq, operating_freq);
		if (!ret) {
			return ret;
		}
	}

	/* Empty freq params means scan all the frequencies */
	if (freq == 0) {
		return 0;
	}
	else if (freq == 2 || freq == 5) {
		/* Scan the frequencies in the band */
		enum hostapd_hw_mode mode;
		int ret;
		if (wpa_s->hw.modes == NULL) {
			wpa_printf(MSG_DEBUG,
				   "P2P: Unknown what %dG channels the driver supports.", freq);
			return 0;
		}
		mode = freq == 5 ? HOSTAPD_MODE_IEEE80211A : HOSTAPD_MODE_IEEE80211G;
		if (wpa_s->p2p_join_scan_count < 2) {
			// scan all non DFS channels in the first two attempts
			ret = setBandScanFreqsList(wpa_s, mode, true, params);
			if (ret < 0 && (-ENOMEM != ret)) {
				// try to scan all channels before returning error
				ret = setBandScanFreqsList(wpa_s, mode, false, params);
			}
		} else {
			// scan all channels
			ret = setBandScanFreqsList(wpa_s, mode, false, params);
		}
		return ret;
	} else {
		/* Scan the frequency requested by the application */
		ret = setScanFreq(wpa_s, params, freq, 0);
		return ret;
	}
	return 0;
}

/**
 * getP2pJoinScanInterval - Get the delay in triggering the scan to discover
 * Auto GO.
 */
static int getP2pJoinScanIntervalUsecs(int freq)
{
	if (freq == 5 || freq == 2 || freq == 0) {
		return P2P_JOIN_MULTIPLE_CHANNEL_SCAN_INTERVAL_USECS;
	} else {
		return P2P_JOIN_SINGLE_CHANNEL_SCAN_INTERVAL_USECS;
	}
}

/*
 * isAnyEtherAddr - match any ether address
 *
 */
int isAnyEtherAddr(const u8 *a)
{
	// 02:00:00:00:00:00
	return (a[0] == 2) && !(a[1] | a[2] | a[3] | a[4] | a[5]);
}

/**
 * findBssBySsid - Fetch a BSS table entry based on SSID and optional BSSID.
 * @wpa_s: Pointer to wpa_supplicant data
 * @bssid: BSSID, 02:00:00:00:00:00 matches any bssid
 * @ssid: SSID
 * @ssid_len: Length of @ssid
 * Returns: Pointer to the BSS entry or %NULL if not found
 */
struct wpa_bss* findBssBySsid(
	struct wpa_supplicant *wpa_s, const u8 *bssid,
	const u8 *ssid, size_t ssid_len)
{
	struct wpa_bss *bss;
	dl_list_for_each(bss, &wpa_s->bss, struct wpa_bss, list) {
		if ((isAnyEtherAddr(bssid) ||
			os_memcmp(bss->bssid, bssid, ETH_ALEN) == 0) &&
			bss->ssid_len == ssid_len &&
			os_memcmp(bss->ssid, ssid, ssid_len) == 0)
			return bss;
	}
	return NULL;
}

/**
 * findBssBySsidFromAnyInterface - Fetch a BSS table entry based on SSID and optional BSSID
 * by iterating through all the interfaces.
 * @head: Head of Pointer to wpa_supplicant data
 * @bssid: BSSID, 02:00:00:00:00:00 matches any bssid
 * @ssid: SSID
 * @ssid_len: Length of @ssid
 * Returns: Pointer to the BSS entry or %NULL if not found
 */
struct wpa_bss* findBssBySsidFromAnyInterface(
	struct wpa_supplicant *head, const u8 *bssid,
	const u8 *ssid, size_t ssid_len)
{
	struct wpa_supplicant *wpa_s;
	struct wpa_bss *bss = NULL;
	for (wpa_s = head; wpa_s; wpa_s = wpa_s->next) {
		bss = findBssBySsid(wpa_s, bssid, ssid, ssid_len);
		if (bss != NULL) {
			return bss;
		}
	}
	return bss;
}

struct wpa_ssid* addGroupClientNetwork(
	struct wpa_supplicant* wpa_s,
	uint8_t *group_owner_bssid,
	const std::vector<uint8_t>& ssid,
	const std::string& passphrase)
{
	struct wpa_ssid* wpa_network = wpa_config_add_network(wpa_s->conf);
	if (!wpa_network) {
		return NULL;
	}
	// set general network defaults
	wpa_config_set_network_defaults(wpa_network);

	// set P2p network defaults
	wpa_network->p2p_group = 1;
	wpa_network->mode = wpas_mode::WPAS_MODE_INFRA;

	wpa_network->auth_alg = WPA_AUTH_ALG_OPEN;
	wpa_network->key_mgmt = WPA_KEY_MGMT_PSK;
	wpa_network->proto = WPA_PROTO_RSN;
	wpa_network->pairwise_cipher = WPA_CIPHER_CCMP;
	wpa_network->group_cipher = WPA_CIPHER_CCMP;
	wpa_network->disabled = 2;

	// set necessary fields
	os_memcpy(wpa_network->bssid, group_owner_bssid, ETH_ALEN);
	wpa_network->bssid_set = 1;

	wpa_network->ssid = (uint8_t *)os_malloc(ssid.size());
	if (wpa_network->ssid == NULL) {
		wpa_config_remove_network(wpa_s->conf, wpa_network->id);
		return  NULL;
	}
	memcpy(wpa_network->ssid, ssid.data(), ssid.size());
	wpa_network->ssid_len = ssid.size();

	wpa_network->psk_set = 0;
	wpa_network->passphrase = dup_binstr(passphrase.c_str(), passphrase.length());
	if (wpa_network->passphrase == NULL) {
		wpa_config_remove_network(wpa_s->conf, wpa_network->id);
		return  NULL;
	}
	wpa_config_update_psk(wpa_network);

	return wpa_network;

}

void joinScanWrapper(void *eloop_ctx, void *timeout_ctx)
{
	struct wpa_supplicant *wpa_s = (struct wpa_supplicant *) eloop_ctx;

	if (pending_join_scan_callback != NULL) {
		pending_join_scan_callback();
	}
}

void scanResJoinWrapper(
	struct wpa_supplicant *wpa_s,
	struct wpa_scan_results *scan_res)
{
	if (wpa_s->p2p_scan_work) {
		struct wpa_radio_work *work = wpa_s->p2p_scan_work;
		wpa_s->p2p_scan_work = NULL;
		radio_work_done(work);
	}

	if (pending_scan_res_join_callback) {
		pending_scan_res_join_callback();
	}
}

int joinScanReq(
	struct wpa_supplicant* wpa_s,
	const std::vector<uint8_t>& ssid,
	int freq, int operating_freq)
{
	int ret;
	struct wpa_driver_scan_params params;
	struct wpabuf *ies;
	size_t ielen;
	unsigned int bands;

	if (wpa_s->global->p2p == NULL || wpa_s->global->p2p_disabled) {
		wpa_printf(MSG_ERROR,
			"P2P: P2P interface is gone, cancel join scan");
		return -ENXIO;
	}

	os_memset(&params, 0, sizeof(params));
	if (ssid.size() > 0) {
		params.ssids[0].ssid = ssid.data();
		params.ssids[0].ssid_len = ssid.size();
	} else {
		params.ssids[0].ssid = (u8 *) P2P_WILDCARD_SSID;
		params.ssids[0].ssid_len = P2P_WILDCARD_SSID_LEN;
	}
	wpa_printf(MSG_DEBUG, "Scan SSID %s for join with frequency %d"
		"BSS operating_freq from scan cache %d",
		wpa_ssid_txt(params.ssids[0].ssid, params.ssids[0].ssid_len), freq, operating_freq);

	/* Construct an optimized p2p scan channel list */
	ret = setP2pCliOptimizedScanFreqsList(wpa_s, &params, freq, operating_freq);
	if (ret < 0) {
		wpa_printf(MSG_ERROR,
				   "Failed to set frequency in p2p scan params, error = %d", ret);
		return -1;
	}

	ielen = p2p_scan_ie_buf_len(wpa_s->global->p2p);
	ies = wpabuf_alloc(ielen);
	if (ies == NULL) {
		if (params.freqs) {
			os_free(params.freqs);
		}
		return -1;
	}

	bands = wpas_get_bands(wpa_s, params.freqs);
	p2p_scan_ie(wpa_s->global->p2p, ies, NULL, bands);

	params.p2p_probe = 1;
	params.extra_ies = (u8 *) wpabuf_head(ies);
	params.extra_ies_len = wpabuf_len(ies);
	if (wpa_s->clear_driver_scan_cache) {
		wpa_printf(MSG_DEBUG,
			"Request driver to clear scan cache due to local BSS flush");
		params.only_new_results = 1;
	}

	ret = wpa_drv_scan(wpa_s, &params);
	if (!ret) {
		os_get_reltime(&wpa_s->scan_trigger_time);
		if (wpa_s->scan_res_handler) {
			wpa_printf(MSG_DEBUG, "Replace current running scan result handler");
		}
		wpa_s->p2p_join_scan_count++;
		wpa_s->scan_res_handler = scanResJoinWrapper;
		wpa_s->own_scan_requested = 1;
		wpa_s->clear_driver_scan_cache = 0;
	}

	if (params.freqs) {
		os_free(params.freqs);
	}

	wpabuf_free(ies);

	return ret;
}

static bool is6GhzAllowed(struct wpa_supplicant *wpa_s) {
	if (!wpa_s->global->p2p) return false;
	return wpa_s->global->p2p->allow_6ghz;
}

int joinGroup(
	struct wpa_supplicant* wpa_s,
	uint8_t *group_owner_bssid,
	const std::vector<uint8_t>& ssid,
	const std::string& passphrase)
{
	int ret = 0;
	int he = wpa_s->conf->p2p_go_he;
	int vht = wpa_s->conf->p2p_go_vht;
	int ht40 = wpa_s->conf->p2p_go_ht40 || vht;

	// Construct a network for adding group.
	// Group client follows the persistent attribute of Group Owner.
	// If joined group is persistent, it adds a persistent network on GroupStarted.
	struct wpa_ssid *wpa_network = addGroupClientNetwork(
		wpa_s, group_owner_bssid, ssid, passphrase);
	if (wpa_network == NULL) {
		wpa_printf(MSG_ERROR, "P2P: Cannot construct a network for group join.");
		return -1;
	}

	// this is temporary network only for establishing the connection.
	wpa_network->temporary = 1;

	if (wpas_p2p_group_add_persistent(
		wpa_s, wpa_network, 0, 0, 0, 0, ht40, vht,
		CHANWIDTH_USE_HT, he, 0, NULL, 0, 0, is6GhzAllowed(wpa_s))) {
		ret = -1;
	}

	// Always remove this temporary network at the end.
	wpa_config_remove_network(wpa_s->conf, wpa_network->id);
	return ret;
}

void notifyGroupJoinFailure(
	struct wpa_supplicant* wpa_s)
{
	u8 zero_addr[ETH_ALEN] = {0};
	std::vector<uint8_t> ssid = {'D', 'I', 'R', 'E','C', 'T', '-'};
	std::string passphrase = "";
	struct wpa_ssid *wpa_network = addGroupClientNetwork(
		wpa_s, zero_addr, ssid, passphrase);
	if (wpa_network) {
		wpa_network->temporary = 1;
		wpas_notify_p2p_group_formation_failure(wpa_s, "Failed to find the group.");
		wpas_notify_p2p_group_removed(
			wpa_s, wpa_network, "client");
		wpa_config_remove_network(
			wpa_s->conf, wpa_network->id);
	} else {
		wpa_printf(MSG_ERROR,
			"P2P: Cannot construct a network.");
	}
}

void scanResJoinIgnore(struct wpa_supplicant *wpa_s, struct wpa_scan_results *scan_res) {
	wpa_printf(MSG_DEBUG, "P2P: Ignore group join scan results.");

	if (wpa_s->p2p_scan_work) {
		struct wpa_radio_work *work = wpa_s->p2p_scan_work;
		wpa_s->p2p_scan_work = NULL;
		radio_work_done(work);
	}

}

static void updateP2pVendorElem(struct wpa_supplicant* wpa_s, enum wpa_vendor_elem_frame frameType,
	const std::vector<uint8_t>& vendorElemBytes) {

	wpa_printf(MSG_INFO, "Set vendor elements to frames %d", frameType);
	struct wpa_supplicant* vendor_elem_wpa_s = wpas_vendor_elem(wpa_s, frameType);
	if (vendor_elem_wpa_s->vendor_elem[frameType]) {
		wpabuf_free(vendor_elem_wpa_s->vendor_elem[frameType]);
		vendor_elem_wpa_s->vendor_elem[frameType] = NULL;
	}
	if (vendorElemBytes.size() > 0) {
		vendor_elem_wpa_s->vendor_elem[frameType] =
			wpabuf_alloc_copy(vendorElemBytes.data(), vendorElemBytes.size());
	}
	wpas_vendor_elem_update(vendor_elem_wpa_s);
}

uint32_t convertWpaP2pFrameTypeToHalP2pFrameTypeBit(int frameType) {
	switch (frameType) {
	case VENDOR_ELEM_PROBE_REQ_P2P:
		return static_cast<uint32_t>(P2pFrameTypeMask::P2P_FRAME_PROBE_REQ_P2P);
	case VENDOR_ELEM_PROBE_RESP_P2P:
		return static_cast<uint32_t>(P2pFrameTypeMask::P2P_FRAME_PROBE_RESP_P2P);
	case VENDOR_ELEM_PROBE_RESP_P2P_GO:
		return static_cast<uint32_t>(P2pFrameTypeMask::P2P_FRAME_PROBE_RESP_P2P_GO);
	case VENDOR_ELEM_BEACON_P2P_GO:
		return static_cast<uint32_t>(P2pFrameTypeMask::P2P_FRAME_BEACON_P2P_GO);
	case VENDOR_ELEM_P2P_PD_REQ:
		return static_cast<uint32_t>(P2pFrameTypeMask::P2P_FRAME_P2P_PD_REQ);
	case VENDOR_ELEM_P2P_PD_RESP:
		return static_cast<uint32_t>(P2pFrameTypeMask::P2P_FRAME_P2P_PD_RESP);
	case VENDOR_ELEM_P2P_GO_NEG_REQ:
		return static_cast<uint32_t>(P2pFrameTypeMask::P2P_FRAME_P2P_GO_NEG_REQ);
	case VENDOR_ELEM_P2P_GO_NEG_RESP:
		return static_cast<uint32_t>(P2pFrameTypeMask::P2P_FRAME_P2P_GO_NEG_RESP);
	case VENDOR_ELEM_P2P_GO_NEG_CONF:
		return static_cast<uint32_t>(P2pFrameTypeMask::P2P_FRAME_P2P_GO_NEG_CONF);
	case VENDOR_ELEM_P2P_INV_REQ:
		return static_cast<uint32_t>(P2pFrameTypeMask::P2P_FRAME_P2P_INV_REQ);
	case VENDOR_ELEM_P2P_INV_RESP:
		return static_cast<uint32_t>(P2pFrameTypeMask::P2P_FRAME_P2P_INV_RESP);
	case VENDOR_ELEM_P2P_ASSOC_REQ:
		return static_cast<uint32_t>(P2pFrameTypeMask::P2P_FRAME_P2P_ASSOC_REQ);
	case VENDOR_ELEM_P2P_ASSOC_RESP:
		return static_cast<uint32_t>(P2pFrameTypeMask::P2P_FRAME_P2P_ASSOC_RESP);
	}
	return 0;
}
}  // namespace

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
using aidl_return_util::validateAndCall;
using misc_utils::createStatus;
using misc_utils::createStatusWithMsg;

P2pIface::P2pIface(struct wpa_global* wpa_global, const char ifname[])
	: wpa_global_(wpa_global), ifname_(ifname), is_valid_(true)
{}

void P2pIface::invalidate() { is_valid_ = false; }
bool P2pIface::isValid()
{
	return (is_valid_ && (retrieveIfacePtr() != nullptr));
}

::ndk::ScopedAStatus P2pIface::getName(
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::getNameInternal, _aidl_return);
}

::ndk::ScopedAStatus P2pIface::getType(
	IfaceType* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::getTypeInternal, _aidl_return);
}

::ndk::ScopedAStatus P2pIface::addNetwork(
	std::shared_ptr<ISupplicantP2pNetwork>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::addNetworkInternal, _aidl_return);
}

::ndk::ScopedAStatus P2pIface::removeNetwork(
	int32_t in_id)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::removeNetworkInternal, in_id);
}

::ndk::ScopedAStatus P2pIface::getNetwork(
	int32_t in_id, std::shared_ptr<ISupplicantP2pNetwork>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::getNetworkInternal, _aidl_return, in_id);
}

::ndk::ScopedAStatus P2pIface::listNetworks(
	std::vector<int32_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::listNetworksInternal, _aidl_return);
}

::ndk::ScopedAStatus P2pIface::registerCallback(
	const std::shared_ptr<ISupplicantP2pIfaceCallback>& in_callback)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::registerCallbackInternal, in_callback);
}

::ndk::ScopedAStatus P2pIface::getDeviceAddress(
	std::vector<uint8_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::getDeviceAddressInternal, _aidl_return);
}

::ndk::ScopedAStatus P2pIface::setSsidPostfix(
	const std::vector<uint8_t>& in_postfix)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::setSsidPostfixInternal, in_postfix);
}

::ndk::ScopedAStatus P2pIface::setGroupIdle(
	const std::string& in_groupIfName, int32_t in_timeoutInSec)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::setGroupIdleInternal, in_groupIfName,
		in_timeoutInSec);
}

::ndk::ScopedAStatus P2pIface::setPowerSave(
	const std::string& in_groupIfName, bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::setPowerSaveInternal, in_groupIfName, in_enable);
}

::ndk::ScopedAStatus P2pIface::find(
	int32_t in_timeoutInSec)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::findInternal, in_timeoutInSec);
}

::ndk::ScopedAStatus P2pIface::stopFind()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::stopFindInternal);
}

::ndk::ScopedAStatus P2pIface::flush()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::flushInternal);
}

::ndk::ScopedAStatus P2pIface::connect(
	const std::vector<uint8_t>& in_peerAddress,
	WpsProvisionMethod in_provisionMethod,
	const std::string& in_preSelectedPin, bool in_joinExistingGroup,
	bool in_persistent, int32_t in_goIntent, std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::connectInternal, _aidl_return, in_peerAddress,
		in_provisionMethod, in_preSelectedPin, in_joinExistingGroup,
		in_persistent, in_goIntent);
}

::ndk::ScopedAStatus P2pIface::cancelConnect()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::cancelConnectInternal);
}

::ndk::ScopedAStatus P2pIface::provisionDiscovery(
	const std::vector<uint8_t>& in_peerAddress,
	WpsProvisionMethod in_provisionMethod)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::provisionDiscoveryInternal, in_peerAddress,
		in_provisionMethod);
}

ndk::ScopedAStatus P2pIface::addGroup(
	bool in_persistent, int32_t in_persistentNetworkId)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::addGroupInternal, in_persistent,
		in_persistentNetworkId);
}

::ndk::ScopedAStatus P2pIface::addGroupWithConfig(
	const std::vector<uint8_t>& in_ssid,
	const std::string& in_pskPassphrase, bool in_persistent,
	int32_t in_freq, const std::vector<uint8_t>& in_peerAddress,
	bool in_joinExistingGroup)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::addGroupWithConfigInternal, in_ssid,
		in_pskPassphrase, in_persistent, in_freq,
		in_peerAddress, in_joinExistingGroup);
}

::ndk::ScopedAStatus P2pIface::removeGroup(
	const std::string& in_groupIfName)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::removeGroupInternal, in_groupIfName);
}

::ndk::ScopedAStatus P2pIface::reject(
	const std::vector<uint8_t>& in_peerAddress)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::rejectInternal, in_peerAddress);
}

::ndk::ScopedAStatus P2pIface::invite(
	const std::string& in_groupIfName,
	const std::vector<uint8_t>& in_goDeviceAddress,
	const std::vector<uint8_t>& in_peerAddress)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::inviteInternal, in_groupIfName,
		in_goDeviceAddress, in_peerAddress);
}

::ndk::ScopedAStatus P2pIface::reinvoke(
	int32_t in_persistentNetworkId,
	const std::vector<uint8_t>& in_peerAddress)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::reinvokeInternal, in_persistentNetworkId,
		in_peerAddress);
}

::ndk::ScopedAStatus P2pIface::configureExtListen(
	int32_t in_periodInMillis, int32_t in_intervalInMillis)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::configureExtListenInternal, in_periodInMillis,
		in_intervalInMillis);
}

::ndk::ScopedAStatus P2pIface::setListenChannel(
	int32_t in_channel, int32_t in_operatingClass)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::setListenChannelInternal, in_channel,
		in_operatingClass);
}

::ndk::ScopedAStatus P2pIface::setDisallowedFrequencies(
	const std::vector<FreqRange>& in_ranges)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::setDisallowedFrequenciesInternal, in_ranges);
}

::ndk::ScopedAStatus P2pIface::getSsid(
	const std::vector<uint8_t>& in_peerAddress,
	std::vector<uint8_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::getSsidInternal, _aidl_return, in_peerAddress);
}

::ndk::ScopedAStatus P2pIface::getGroupCapability(
	const std::vector<uint8_t>& in_peerAddress,
	P2pGroupCapabilityMask* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::getGroupCapabilityInternal, _aidl_return, in_peerAddress);
}

::ndk::ScopedAStatus P2pIface::addBonjourService(
	const std::vector<uint8_t>& in_query,
	const std::vector<uint8_t>& in_response)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::addBonjourServiceInternal, in_query, in_response);
}

::ndk::ScopedAStatus P2pIface::removeBonjourService(
	const std::vector<uint8_t>& in_query)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::removeBonjourServiceInternal, in_query);
}

::ndk::ScopedAStatus P2pIface::addUpnpService(
	int32_t in_version, const std::string& in_serviceName)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::addUpnpServiceInternal, in_version, in_serviceName);
}

::ndk::ScopedAStatus P2pIface::removeUpnpService(
	int32_t in_version, const std::string& in_serviceName)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::removeUpnpServiceInternal, in_version,
		in_serviceName);
}

::ndk::ScopedAStatus P2pIface::flushServices()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::flushServicesInternal);
}

::ndk::ScopedAStatus P2pIface::requestServiceDiscovery(
	const std::vector<uint8_t>& in_peerAddress,
	const std::vector<uint8_t>& in_query,
	int64_t* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::requestServiceDiscoveryInternal, _aidl_return,
		in_peerAddress, in_query);
}

::ndk::ScopedAStatus P2pIface::cancelServiceDiscovery(
	int64_t in_identifier)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::cancelServiceDiscoveryInternal, in_identifier);
}

::ndk::ScopedAStatus P2pIface::setMiracastMode(
	MiracastMode in_mode)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::setMiracastModeInternal, in_mode);
}

::ndk::ScopedAStatus P2pIface::startWpsPbc(
	const std::string& in_groupIfName,
	const std::vector<uint8_t>& in_bssid)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::startWpsPbcInternal, in_groupIfName, in_bssid);
}

::ndk::ScopedAStatus P2pIface::startWpsPinKeypad(
	const std::string& in_groupIfName,
	const std::string& in_pin)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::startWpsPinKeypadInternal, in_groupIfName, in_pin);
}

::ndk::ScopedAStatus P2pIface::startWpsPinDisplay(
	const std::string& in_groupIfName,
	const std::vector<uint8_t>& in_bssid,
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::startWpsPinDisplayInternal, _aidl_return,
		in_groupIfName, in_bssid);
}

::ndk::ScopedAStatus P2pIface::cancelWps(
	const std::string& in_groupIfName)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::cancelWpsInternal, in_groupIfName);
}

::ndk::ScopedAStatus P2pIface::setWpsDeviceName(
	const std::string& in_name)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::setWpsDeviceNameInternal, in_name);
}

::ndk::ScopedAStatus P2pIface::setWpsDeviceType(
	const std::vector<uint8_t>& in_type)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::setWpsDeviceTypeInternal, in_type);
}

::ndk::ScopedAStatus P2pIface::setWpsManufacturer(
	const std::string& in_manufacturer)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::setWpsManufacturerInternal, in_manufacturer);
}

::ndk::ScopedAStatus P2pIface::setWpsModelName(
	const std::string& in_modelName)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::setWpsModelNameInternal, in_modelName);
}

::ndk::ScopedAStatus P2pIface::setWpsModelNumber(
	const std::string& in_modelNumber)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::setWpsModelNumberInternal, in_modelNumber);
}

::ndk::ScopedAStatus P2pIface::setWpsSerialNumber(
	const std::string& in_serialNumber)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::setWpsSerialNumberInternal, in_serialNumber);
}

::ndk::ScopedAStatus P2pIface::setWpsConfigMethods(
	WpsConfigMethods in_configMethods)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::setWpsConfigMethodsInternal, in_configMethods);
}

::ndk::ScopedAStatus P2pIface::enableWfd(
	bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::enableWfdInternal, in_enable);
}

::ndk::ScopedAStatus P2pIface::setWfdDeviceInfo(
	const std::vector<uint8_t>& in_info)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::setWfdDeviceInfoInternal, in_info);
}

::ndk::ScopedAStatus P2pIface::createNfcHandoverRequestMessage(
	std::vector<uint8_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::createNfcHandoverRequestMessageInternal, _aidl_return);
}

::ndk::ScopedAStatus P2pIface::createNfcHandoverSelectMessage(
	std::vector<uint8_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::createNfcHandoverSelectMessageInternal, _aidl_return);
}

::ndk::ScopedAStatus P2pIface::reportNfcHandoverResponse(
	const std::vector<uint8_t>& in_request)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::reportNfcHandoverResponseInternal, in_request);
}

::ndk::ScopedAStatus P2pIface::reportNfcHandoverInitiation(
	const std::vector<uint8_t>& in_select)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::reportNfcHandoverInitiationInternal, in_select);
}

::ndk::ScopedAStatus P2pIface::saveConfig()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::saveConfigInternal);
}

::ndk::ScopedAStatus P2pIface::setMacRandomization(
	bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::setMacRandomizationInternal, in_enable);
}

::ndk::ScopedAStatus P2pIface::setEdmg(
	bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&P2pIface::setEdmgInternal, in_enable);
}

::ndk::ScopedAStatus P2pIface::getEdmg(
	bool* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&P2pIface::getEdmgInternal, _aidl_return);
}

::ndk::ScopedAStatus P2pIface::setWfdR2DeviceInfo(
	const std::vector<uint8_t>& in_info)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::setWfdR2DeviceInfoInternal, in_info);
}

::ndk::ScopedAStatus P2pIface::removeClient(
        const std::vector<uint8_t>& peer_address, bool isLegacyClient)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::removeClientInternal, peer_address, isLegacyClient);
}

::ndk::ScopedAStatus P2pIface::findOnSocialChannels(
	int32_t in_timeoutInSec)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::findOnSocialChannelsInternal, in_timeoutInSec);
}

::ndk::ScopedAStatus P2pIface::findOnSpecificFrequency(
	int32_t in_freq,
	int32_t in_timeoutInSec)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::findOnSpecificFrequencyInternal,
		in_freq, in_timeoutInSec);
}

::ndk::ScopedAStatus P2pIface::setVendorElements(
	P2pFrameTypeMask in_frameTypeMask,
	const std::vector<uint8_t>& in_vendorElemBytes)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&P2pIface::setVendorElementsInternal, in_frameTypeMask, in_vendorElemBytes);
}

std::pair<std::string, ndk::ScopedAStatus> P2pIface::getNameInternal()
{
	return {ifname_, ndk::ScopedAStatus::ok()};
}

std::pair<IfaceType, ndk::ScopedAStatus> P2pIface::getTypeInternal()
{
	return {IfaceType::P2P, ndk::ScopedAStatus::ok()};
}

std::pair<std::shared_ptr<ISupplicantP2pNetwork>, ndk::ScopedAStatus>
P2pIface::addNetworkInternal()
{
	std::shared_ptr<ISupplicantP2pNetwork> network;
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	struct wpa_ssid* ssid = wpa_supplicant_add_network(wpa_s);
	if (!ssid) {
		return {network, createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	AidlManager* aidl_manager = AidlManager::getInstance();
	if (!aidl_manager ||
		aidl_manager->getP2pNetworkAidlObjectByIfnameAndNetworkId(
		wpa_s->ifname, ssid->id, &network)) {
		return {network, createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {network, ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus P2pIface::removeNetworkInternal(int32_t id)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	int result = wpa_supplicant_remove_network(wpa_s, id);
	if (result == -1) {
		return createStatus(SupplicantStatusCode::FAILURE_NETWORK_UNKNOWN);
	} else if (result != 0) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

std::pair<std::shared_ptr<ISupplicantP2pNetwork>, ndk::ScopedAStatus>
P2pIface::getNetworkInternal(int32_t id)
{
	std::shared_ptr<ISupplicantP2pNetwork> network;
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	struct wpa_ssid* ssid = wpa_config_get_network(wpa_s->conf, id);
	if (!ssid) {
		return {network, createStatus(SupplicantStatusCode::FAILURE_NETWORK_UNKNOWN)};
	}
	AidlManager* aidl_manager = AidlManager::getInstance();
	if (!aidl_manager ||
		aidl_manager->getP2pNetworkAidlObjectByIfnameAndNetworkId(
		wpa_s->ifname, ssid->id, &network)) {
		return {network, createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {network, ndk::ScopedAStatus::ok()};
}

std::pair<std::vector<int32_t>, ndk::ScopedAStatus>
P2pIface::listNetworksInternal()
{
	std::vector<int32_t> network_ids;
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	for (struct wpa_ssid* wpa_ssid = wpa_s->conf->ssid; wpa_ssid;
		 wpa_ssid = wpa_ssid->next) {
		network_ids.emplace_back(wpa_ssid->id);
	}
	return {std::move(network_ids), ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus P2pIface::registerCallbackInternal(
	const std::shared_ptr<ISupplicantP2pIfaceCallback>& callback)
{
	AidlManager* aidl_manager = AidlManager::getInstance();
	if (!aidl_manager ||
		aidl_manager->addP2pIfaceCallbackAidlObject(ifname_, callback)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

std::pair<std::vector<uint8_t>, ndk::ScopedAStatus>
P2pIface::getDeviceAddressInternal()
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	std::vector<uint8_t> addr(
		wpa_s->global->p2p_dev_addr,
		wpa_s->global->p2p_dev_addr + ETH_ALEN);
	return {addr, ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus P2pIface::setSsidPostfixInternal(
	const std::vector<uint8_t>& postfix)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (p2p_set_ssid_postfix(
		wpa_s->global->p2p, postfix.data(), postfix.size())) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::setGroupIdleInternal(
	const std::string& group_ifname, uint32_t timeout_in_sec)
{
	struct wpa_supplicant* wpa_group_s =
		retrieveGroupIfacePtr(group_ifname);
	if (!wpa_group_s) {
		return createStatus(SupplicantStatusCode::FAILURE_IFACE_UNKNOWN);
	}
	wpa_group_s->conf->p2p_group_idle = timeout_in_sec;
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::setPowerSaveInternal(
	const std::string& group_ifname, bool enable)
{
	struct wpa_supplicant* wpa_group_s =
		retrieveGroupIfacePtr(group_ifname);
	if (!wpa_group_s) {
		return createStatus(SupplicantStatusCode::FAILURE_IFACE_UNKNOWN);
	}
	if (wpa_drv_set_p2p_powersave(wpa_group_s, enable, -1, -1)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::findInternal(uint32_t timeout_in_sec)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (wpa_s->wpa_state == WPA_INTERFACE_DISABLED) {
		return createStatus(SupplicantStatusCode::FAILURE_IFACE_DISABLED);
	}
	uint32_t search_delay = wpas_p2p_search_delay(wpa_s);
	if (wpas_p2p_find(
		wpa_s, timeout_in_sec, P2P_FIND_START_WITH_FULL, 0, nullptr,
		nullptr, search_delay, 0, nullptr, 0, is6GhzAllowed(wpa_s))) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::stopFindInternal()
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (wpa_s->wpa_state == WPA_INTERFACE_DISABLED) {
		return createStatus(SupplicantStatusCode::FAILURE_IFACE_DISABLED);
	}
	if (wpa_s->scan_res_handler == scanResJoinWrapper) {
		wpa_printf(MSG_DEBUG, "P2P: Stop pending group scan for stopping find).");
		pending_scan_res_join_callback = NULL;
		wpa_s->scan_res_handler = scanResJoinIgnore;
	}
	wpas_p2p_stop_find(wpa_s);
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::flushInternal()
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	os_memset(wpa_s->p2p_auth_invite, 0, ETH_ALEN);
	wpa_s->force_long_sd = 0;
	wpas_p2p_stop_find(wpa_s);
	wpa_s->parent->p2ps_method_config_any = 0;
	wpa_bss_flush(wpa_s);
	if (wpa_s->global->p2p)
		p2p_flush(wpa_s->global->p2p);
	return ndk::ScopedAStatus::ok();
}

// This method only implements support for subset (needed by Android framework)
// of parameters that can be specified for connect.
std::pair<std::string, ndk::ScopedAStatus> P2pIface::connectInternal(
	const std::vector<uint8_t>& peer_address,
	WpsProvisionMethod provision_method,
	const std::string& pre_selected_pin, bool join_existing_group,
	bool persistent, uint32_t go_intent)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (go_intent > 15) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID)};
	}
	if (peer_address.size() != ETH_ALEN) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID)};
	}
	int go_intent_signed = join_existing_group ? -1 : go_intent;
	p2p_wps_method wps_method = {};
	switch (provision_method) {
	case WpsProvisionMethod::PBC:
		wps_method = WPS_PBC;
		break;
	case WpsProvisionMethod::DISPLAY:
		wps_method = WPS_PIN_DISPLAY;
		break;
	case WpsProvisionMethod::KEYPAD:
		wps_method = WPS_PIN_KEYPAD;
		break;
	}
	int he = wpa_s->conf->p2p_go_he;
	int vht = wpa_s->conf->p2p_go_vht;
	int ht40 = wpa_s->conf->p2p_go_ht40 || vht;
	const char* pin =
		pre_selected_pin.length() > 0 ? pre_selected_pin.data() : nullptr;
	bool auto_join = !join_existing_group;
	int new_pin = wpas_p2p_connect(
		wpa_s, peer_address.data(), pin, wps_method, persistent, auto_join,
		join_existing_group, false, go_intent_signed, 0, 0, -1, false, ht40,
		vht, CHANWIDTH_USE_HT, he, 0, nullptr, 0, is6GhzAllowed(wpa_s));
	if (new_pin < 0) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	std::string pin_ret;
	if (provision_method == WpsProvisionMethod::DISPLAY &&
		pre_selected_pin.empty()) {
		pin_ret = misc_utils::convertWpsPinToString(new_pin);
	}
	return {pin_ret, ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus P2pIface::cancelConnectInternal()
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (wpa_s->scan_res_handler == scanResJoinWrapper) {
		wpa_printf(MSG_DEBUG, "P2P: Stop pending group scan for canceling connect");
		pending_scan_res_join_callback = NULL;
		wpa_s->scan_res_handler = scanResJoinIgnore;
	}
	if (wpas_p2p_cancel(wpa_s)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::provisionDiscoveryInternal(
	const std::vector<uint8_t>& peer_address,
	WpsProvisionMethod provision_method)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	p2ps_provision* prov_param;
	const char* config_method_str = nullptr;
	if (peer_address.size() != ETH_ALEN) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	switch (provision_method) {
	case WpsProvisionMethod::PBC:
		config_method_str = kConfigMethodStrPbc;
		break;
	case WpsProvisionMethod::DISPLAY:
		config_method_str = kConfigMethodStrDisplay;
		break;
	case WpsProvisionMethod::KEYPAD:
		config_method_str = kConfigMethodStrKeypad;
		break;
	}
	if (wpas_p2p_prov_disc(
		wpa_s, peer_address.data(), config_method_str,
		WPAS_P2P_PD_FOR_GO_NEG, nullptr)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::removeGroupInternal(const std::string& group_ifname)
{
	struct wpa_supplicant* wpa_group_s =
		retrieveGroupIfacePtr(group_ifname);
	if (!wpa_group_s) {
		return createStatus(SupplicantStatusCode::FAILURE_IFACE_UNKNOWN);
	}
	wpa_group_s->global->p2p_go_found_external_scan = 0;
	if (wpas_p2p_group_remove(wpa_group_s, group_ifname.c_str())) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::rejectInternal(
	const std::vector<uint8_t>& peer_address)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (wpa_s->global->p2p_disabled || wpa_s->global->p2p == NULL) {
		return createStatus(SupplicantStatusCode::FAILURE_IFACE_DISABLED);
	}
	if (peer_address.size() != ETH_ALEN) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	if (wpas_p2p_reject(wpa_s, peer_address.data())) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::inviteInternal(
	const std::string& group_ifname,
	const std::vector<uint8_t>& go_device_address,
	const std::vector<uint8_t>& peer_address)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (go_device_address.size() != ETH_ALEN || peer_address.size() != ETH_ALEN) {
		return {createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	if (wpas_p2p_invite_group(
		wpa_s, group_ifname.c_str(), peer_address.data(),
		go_device_address.data(), is6GhzAllowed(wpa_s))) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::reinvokeInternal(
	int32_t persistent_network_id,
	const std::vector<uint8_t>& peer_address)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	int he = wpa_s->conf->p2p_go_he;
	int vht = wpa_s->conf->p2p_go_vht;
	int ht40 = wpa_s->conf->p2p_go_ht40 || vht;
	struct wpa_ssid* ssid =
		wpa_config_get_network(wpa_s->conf, persistent_network_id);
	if (ssid == NULL || ssid->disabled != 2) {
		return createStatus(SupplicantStatusCode::FAILURE_NETWORK_UNKNOWN);
	}
	if (peer_address.size() != ETH_ALEN) {
		return {createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	if (wpas_p2p_invite(
		wpa_s, peer_address.data(), ssid, NULL, 0, 0, ht40, vht,
		CHANWIDTH_USE_HT, 0, he, 0, is6GhzAllowed(wpa_s))) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::configureExtListenInternal(
	uint32_t period_in_millis, uint32_t interval_in_millis)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (wpas_p2p_ext_listen(wpa_s, period_in_millis, interval_in_millis)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::setListenChannelInternal(
	uint32_t channel, uint32_t operating_class)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (p2p_set_listen_channel(
		wpa_s->global->p2p, operating_class, channel, 1)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::setDisallowedFrequenciesInternal(
	const std::vector<FreqRange>& ranges)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	using DestT = struct wpa_freq_range_list::wpa_freq_range;
	DestT* freq_ranges = nullptr;
	// Empty ranges is used to enable all frequencies.
	if (ranges.size() != 0) {
		freq_ranges = static_cast<DestT*>(
			os_malloc(sizeof(DestT) * ranges.size()));
		if (!freq_ranges) {
			return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
		}
		uint32_t i = 0;
		for (const auto& range : ranges) {
			freq_ranges[i].min = range.min;
			freq_ranges[i].max = range.max;
			i++;
		}
	}

	os_free(wpa_s->global->p2p_disallow_freq.range);
	wpa_s->global->p2p_disallow_freq.range = freq_ranges;
	wpa_s->global->p2p_disallow_freq.num = ranges.size();
	wpas_p2p_update_channel_list(wpa_s, WPAS_P2P_CHANNEL_UPDATE_DISALLOW);
	return ndk::ScopedAStatus::ok();
}

std::pair<std::vector<uint8_t>, ndk::ScopedAStatus> P2pIface::getSsidInternal(
	const std::vector<uint8_t>& peer_address)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (peer_address.size() != ETH_ALEN) {
		return {std::vector<uint8_t>(),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	const struct p2p_peer_info* info =
		p2p_get_peer_info(wpa_s->global->p2p, peer_address.data(), 0);
	if (!info) {
		return {std::vector<uint8_t>(),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	const struct p2p_device* dev =
		reinterpret_cast<const struct p2p_device*>(
		(reinterpret_cast<const uint8_t*>(info)) -
		offsetof(struct p2p_device, info));
	std::vector<uint8_t> ssid;
	if (dev && dev->oper_ssid_len) {
		ssid.assign(
			dev->oper_ssid, dev->oper_ssid + dev->oper_ssid_len);
	}
	return {ssid, ndk::ScopedAStatus::ok()};
}

std::pair<P2pGroupCapabilityMask, ndk::ScopedAStatus> P2pIface::getGroupCapabilityInternal(
	const std::vector<uint8_t>& peer_address)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (peer_address.size() != ETH_ALEN) {
		return {static_cast<P2pGroupCapabilityMask>(0),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	const struct p2p_peer_info* info =
		p2p_get_peer_info(wpa_s->global->p2p, peer_address.data(), 0);
	if (!info) {
		return {static_cast<P2pGroupCapabilityMask>(0),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {static_cast<P2pGroupCapabilityMask>(info->group_capab),
		ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus P2pIface::addBonjourServiceInternal(
	const std::vector<uint8_t>& query, const std::vector<uint8_t>& response)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	auto query_buf = misc_utils::convertVectorToWpaBuf(query);
	auto response_buf = misc_utils::convertVectorToWpaBuf(response);
	if (!query_buf || !response_buf) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	if (wpas_p2p_service_add_bonjour(
		wpa_s, query_buf.get(), response_buf.get())) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	// If successful, the wpabuf is referenced internally and hence should
	// not be freed.
	query_buf.release();
	response_buf.release();
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::removeBonjourServiceInternal(
	const std::vector<uint8_t>& query)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	auto query_buf = misc_utils::convertVectorToWpaBuf(query);
	if (!query_buf) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	if (wpas_p2p_service_del_bonjour(wpa_s, query_buf.get())) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::addUpnpServiceInternal(
	uint32_t version, const std::string& service_name)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (wpas_p2p_service_add_upnp(wpa_s, version, service_name.c_str())) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::removeUpnpServiceInternal(
	uint32_t version, const std::string& service_name)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (wpas_p2p_service_del_upnp(wpa_s, version, service_name.c_str())) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::flushServicesInternal()
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	wpas_p2p_service_flush(wpa_s);
	return ndk::ScopedAStatus::ok();
}

std::pair<uint64_t, ndk::ScopedAStatus> P2pIface::requestServiceDiscoveryInternal(
	const std::vector<uint8_t>& peer_address,
	const std::vector<uint8_t>& query)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	auto query_buf = misc_utils::convertVectorToWpaBuf(query);
	if (!query_buf) {
		return {0, createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	if (peer_address.size() != ETH_ALEN) {
		return {0, createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	const uint8_t* dst_addr = is_zero_ether_addr(peer_address.data())
					  ? nullptr
					  : peer_address.data();
	uint64_t identifier =
		wpas_p2p_sd_request(wpa_s, dst_addr, query_buf.get());
	if (identifier == 0) {
		return {0, createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {identifier, ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus P2pIface::cancelServiceDiscoveryInternal(uint64_t identifier)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (wpas_p2p_sd_cancel_request(wpa_s, identifier)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::setMiracastModeInternal(
	MiracastMode mode)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	uint8_t mode_internal = convertAidlMiracastModeToInternal(mode);
	const std::string cmd_str =
		kSetMiracastMode + std::to_string(mode_internal);
	std::vector<char> cmd(
		cmd_str.c_str(), cmd_str.c_str() + cmd_str.size() + 1);
	char driver_cmd_reply_buf[4096] = {};
	if (wpa_drv_driver_cmd(
		wpa_s, cmd.data(), driver_cmd_reply_buf,
		sizeof(driver_cmd_reply_buf))) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::startWpsPbcInternal(
	const std::string& group_ifname, const std::vector<uint8_t>& bssid)
{
	struct wpa_supplicant* wpa_group_s =
		retrieveGroupIfacePtr(group_ifname);
	if (!wpa_group_s) {
		return createStatus(SupplicantStatusCode::FAILURE_IFACE_UNKNOWN);
	}
	if (bssid.size() != ETH_ALEN) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	const uint8_t* bssid_addr =
		is_zero_ether_addr(bssid.data()) ? nullptr : bssid.data();
#ifdef CONFIG_AP
	if (wpa_group_s->ap_iface) {
		if (wpa_supplicant_ap_wps_pbc(wpa_group_s, bssid_addr, NULL)) {
			return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
		}
		return ndk::ScopedAStatus::ok();
	}
#endif /* CONFIG_AP */
	if (wpas_wps_start_pbc(wpa_group_s, bssid_addr, 0, 0)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::startWpsPinKeypadInternal(
	const std::string& group_ifname, const std::string& pin)
{
	struct wpa_supplicant* wpa_group_s =
		retrieveGroupIfacePtr(group_ifname);
	if (!wpa_group_s) {
		return createStatus(SupplicantStatusCode::FAILURE_IFACE_UNKNOWN);
	}
#ifdef CONFIG_AP
	if (wpa_group_s->ap_iface) {
		if (wpa_supplicant_ap_wps_pin(
			wpa_group_s, nullptr, pin.c_str(), nullptr, 0, 0) < 0) {
			return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
		}
		return ndk::ScopedAStatus::ok();
	}
#endif /* CONFIG_AP */
	if (wpas_wps_start_pin(
		wpa_group_s, nullptr, pin.c_str(), 0, DEV_PW_DEFAULT)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

std::pair<std::string, ndk::ScopedAStatus> P2pIface::startWpsPinDisplayInternal(
	const std::string& group_ifname, const std::vector<uint8_t>& bssid)
{
	struct wpa_supplicant* wpa_group_s =
		retrieveGroupIfacePtr(group_ifname);
	if (!wpa_group_s) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_IFACE_UNKNOWN)};
	}
	if (bssid.size() != ETH_ALEN) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	const uint8_t* bssid_addr =
		is_zero_ether_addr(bssid.data()) ? nullptr : bssid.data();
	int pin = wpas_wps_start_pin(
		wpa_group_s, bssid_addr, nullptr, 0, DEV_PW_DEFAULT);
	if (pin < 0) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {misc_utils::convertWpsPinToString(pin), ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus P2pIface::cancelWpsInternal(const std::string& group_ifname)
{
	struct wpa_supplicant* wpa_group_s =
		retrieveGroupIfacePtr(group_ifname);
	if (!wpa_group_s) {
		return createStatus(SupplicantStatusCode::FAILURE_IFACE_UNKNOWN);
	}
	if (wpas_wps_cancel(wpa_group_s)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::setWpsDeviceNameInternal(const std::string& name)
{
	return iface_config_utils::setWpsDeviceName(retrieveIfacePtr(), name);
}

ndk::ScopedAStatus P2pIface::setWpsDeviceTypeInternal(
	const std::vector<uint8_t>& type)
{
	std::array<uint8_t, 8> type_arr;
	if (type.size() != 8) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	std::copy_n(type.begin(), 8, type_arr.begin());
	return iface_config_utils::setWpsDeviceType(retrieveIfacePtr(), type_arr);
}

ndk::ScopedAStatus P2pIface::setWpsManufacturerInternal(
	const std::string& manufacturer)
{
	return iface_config_utils::setWpsManufacturer(
		retrieveIfacePtr(), manufacturer);
}

ndk::ScopedAStatus P2pIface::setWpsModelNameInternal(
	const std::string& model_name)
{
	return iface_config_utils::setWpsModelName(
		retrieveIfacePtr(), model_name);
}

ndk::ScopedAStatus P2pIface::setWpsModelNumberInternal(
	const std::string& model_number)
{
	return iface_config_utils::setWpsModelNumber(
		retrieveIfacePtr(), model_number);
}

ndk::ScopedAStatus P2pIface::setWpsSerialNumberInternal(
	const std::string& serial_number)
{
	return iface_config_utils::setWpsSerialNumber(
		retrieveIfacePtr(), serial_number);
}

ndk::ScopedAStatus P2pIface::setWpsConfigMethodsInternal(WpsConfigMethods config_methods)
{

	return iface_config_utils::setWpsConfigMethods(
		retrieveIfacePtr(), static_cast<uint16_t>(config_methods));
}

ndk::ScopedAStatus P2pIface::enableWfdInternal(bool enable)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	wifi_display_enable(wpa_s->global, enable);
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::setWfdDeviceInfoInternal(
	const std::vector<uint8_t>& info)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	std::vector<char> wfd_device_info_hex(info.size() * 2 + 1);
	wpa_snprintf_hex(
		wfd_device_info_hex.data(), wfd_device_info_hex.size(), info.data(),
		info.size());
	// |wifi_display_subelem_set| expects the first 2 bytes
	// to hold the lenght of the subelement. In this case it's
	// fixed to 6, so prepend that.
	std::string wfd_device_info_set_cmd_str =
		std::to_string(kWfdDeviceInfoSubelemId) + " " +
		kWfdDeviceInfoSubelemLenHexStr + wfd_device_info_hex.data();
	std::vector<char> wfd_device_info_set_cmd(
		wfd_device_info_set_cmd_str.c_str(),
		wfd_device_info_set_cmd_str.c_str() +
		wfd_device_info_set_cmd_str.size() + 1);
	if (wifi_display_subelem_set(
		wpa_s->global, wfd_device_info_set_cmd.data())) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

std::pair<std::vector<uint8_t>, ndk::ScopedAStatus>
P2pIface::createNfcHandoverRequestMessageInternal()
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	auto buf = misc_utils::createWpaBufUniquePtr(
		wpas_p2p_nfc_handover_req(wpa_s, 1));
	if (!buf) {
		return {std::vector<uint8_t>(),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {misc_utils::convertWpaBufToVector(buf.get()),
		ndk::ScopedAStatus::ok()};
}

std::pair<std::vector<uint8_t>, ndk::ScopedAStatus>
P2pIface::createNfcHandoverSelectMessageInternal()
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	auto buf = misc_utils::createWpaBufUniquePtr(
		wpas_p2p_nfc_handover_sel(wpa_s, 1, 0));
	if (!buf) {
		return {std::vector<uint8_t>(),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {misc_utils::convertWpaBufToVector(buf.get()),
		ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus P2pIface::reportNfcHandoverResponseInternal(
	const std::vector<uint8_t>& request)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	auto req = misc_utils::convertVectorToWpaBuf(request);
	auto sel = misc_utils::convertVectorToWpaBuf(std::vector<uint8_t>{0});
	if (!req || !sel) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}

	if (wpas_p2p_nfc_report_handover(wpa_s, 0, req.get(), sel.get(), 0)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::reportNfcHandoverInitiationInternal(
	const std::vector<uint8_t>& select)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	auto req = misc_utils::convertVectorToWpaBuf(std::vector<uint8_t>{0});
	auto sel = misc_utils::convertVectorToWpaBuf(select);
	if (!req || !sel) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}

	if (wpas_p2p_nfc_report_handover(wpa_s, 1, req.get(), sel.get(), 0)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::saveConfigInternal()
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (!wpa_s->conf->update_config) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	if (wpa_config_write(wpa_s->confname, wpa_s->conf)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::addGroupInternal(
	bool persistent, int32_t persistent_network_id)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	int he = wpa_s->conf->p2p_go_he;
	int vht = wpa_s->conf->p2p_go_vht;
	int ht40 = wpa_s->conf->p2p_go_ht40 || vht;
	struct wpa_ssid* ssid =
		wpa_config_get_network(wpa_s->conf, persistent_network_id);
	if (ssid == NULL) {
		if (wpas_p2p_group_add(
			wpa_s, persistent, 0, 0, ht40, vht,
			CHANWIDTH_USE_HT, he, 0, is6GhzAllowed(wpa_s))) {
			return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
		} else {
			return ndk::ScopedAStatus::ok();
		}
	} else if (ssid->disabled == 2) {
		if (wpas_p2p_group_add_persistent(
			wpa_s, ssid, 0, 0, 0, 0, ht40, vht,
			CHANWIDTH_USE_HT, he, 0, NULL, 0, 0, is6GhzAllowed(wpa_s))) {
			return createStatus(SupplicantStatusCode::FAILURE_NETWORK_UNKNOWN);
		} else {
			return ndk::ScopedAStatus::ok();
		}
	}
	return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
}

ndk::ScopedAStatus P2pIface::addGroupWithConfigInternal(
	const std::vector<uint8_t>& ssid, const std::string& passphrase,
	bool persistent, uint32_t freq, const std::vector<uint8_t>& peer_address,
	bool joinExistingGroup)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	int he = wpa_s->conf->p2p_go_he;
	int vht = wpa_s->conf->p2p_go_vht;
	int ht40 = wpa_s->conf->p2p_go_ht40 || vht;

	if (wpa_s->global->p2p == NULL || wpa_s->global->p2p_disabled) {
		return createStatus(SupplicantStatusCode::FAILURE_IFACE_DISABLED);
	}

	if (!isSsidValid(ssid)) {
		return createStatusWithMsg(SupplicantStatusCode::FAILURE_ARGS_INVALID,
			"SSID is invalid.");
	}

	if (!isPskPassphraseValid(passphrase)) {
		return createStatusWithMsg(SupplicantStatusCode::FAILURE_ARGS_INVALID,
			"Passphrase is invalid.");
	}

	if (!joinExistingGroup) {
		struct p2p_data *p2p = wpa_s->global->p2p;
		os_memcpy(p2p->ssid, ssid.data(), ssid.size());
		p2p->ssid_len = ssid.size();
		p2p->ssid_set = 1;

		os_memset(p2p->passphrase, 0, sizeof(p2p->passphrase));
		os_memcpy(p2p->passphrase, passphrase.c_str(), passphrase.length());
		p2p->passphrase_set = 1;

		if (wpas_p2p_group_add(
			wpa_s, persistent, freq, 0, ht40, vht,
			CHANWIDTH_USE_HT, he, 0, is6GhzAllowed(wpa_s))) {
			return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
		}
		return ndk::ScopedAStatus::ok();
	}

	// The rest is for group join.
	wpa_printf(MSG_DEBUG, "P2P: Stop any on-going P2P FIND before group join.");
	wpas_p2p_stop_find(wpa_s);

	if (peer_address.size() != ETH_ALEN) {
		return createStatusWithMsg(SupplicantStatusCode::FAILURE_ARGS_INVALID,
			"Peer address is invalid.");
	}

	if (pending_scan_res_join_callback != NULL) {
		wpa_printf(MSG_WARNING, "P2P: Renew scan result callback with new request.");
	}

	pending_join_scan_callback =
		[wpa_s, ssid, peer_address, freq]() {
		if (wpa_s->global->p2p == NULL || wpa_s->global->p2p_disabled) {
			return;
		}
		int operating_freq = 0;
		struct wpa_bss *bss = findBssBySsidFromAnyInterface(
			wpa_s->global->ifaces, peer_address.data(), ssid.data(), ssid.size());
		if (bss != NULL) {
			wpa_printf(MSG_DEBUG, "P2P: Found Group owner " MACSTR "in scan cache",
				MAC2STR(bss->bssid));
			operating_freq = bss->freq;
		}

		int ret = joinScanReq(wpa_s, ssid, freq, operating_freq);
		// for BUSY case, the scan might be occupied by WiFi.
		// Do not give up immediately, but try again later.
		if (-EBUSY == ret) {
			// re-schedule this join scan
			eloop_cancel_timeout(joinScanWrapper, wpa_s, NULL);
			eloop_register_timeout(0, P2P_JOIN_SINGLE_CHANNEL_SCAN_INTERVAL_USECS,
					joinScanWrapper, wpa_s, NULL);
		} else if (0 != ret) {
			notifyGroupJoinFailure(wpa_s);
			pending_scan_res_join_callback = NULL;
		}
	};

	pending_scan_res_join_callback = [wpa_s, ssid, passphrase, peer_address, freq, this]() {
		if (wpa_s->global->p2p == NULL || wpa_s->global->p2p_disabled) {
			return;
		}

		wpa_printf(MSG_DEBUG, "P2P: Scan results received for join (reinvoke).");

		struct wpa_bss *bss = findBssBySsid(
			wpa_s, peer_address.data(), ssid.data(), ssid.size());
		if (bss) {
			wpa_s->global->p2p_go_found_external_scan = 1;
			if (0 != joinGroup(wpa_s, bss->bssid, ssid, passphrase)) {
				wpa_printf(MSG_ERROR, "P2P: Failed to join a group.");
				wpa_s->global->p2p_go_found_external_scan = 0;
			}
			// no need to notify group join failure here,
			// it will be handled by wpas_p2p_group_add_persistent
			// called in joinGroup.
			pending_scan_res_join_callback = NULL;
			return;
		}
		wpa_printf(MSG_DEBUG, "P2P: Join scan count %d.", wpa_s->p2p_join_scan_count);
		eloop_cancel_timeout(joinScanWrapper, wpa_s, NULL);
		if (wpa_s->p2p_join_scan_count < P2P_MAX_JOIN_SCAN_ATTEMPTS) {
			wpa_printf(MSG_DEBUG, "P2P: Try join again later.");
			eloop_register_timeout(0, getP2pJoinScanIntervalUsecs(freq),
				joinScanWrapper, wpa_s, this);
			return;
		}

		wpa_printf(MSG_ERROR, "P2P: Failed to find the group with "
			"network name %s - stop join attempt",
			wpa_ssid_txt(ssid.data(), ssid.size()));
		notifyGroupJoinFailure(wpa_s);
		pending_scan_res_join_callback = NULL;
	};

	wpa_s->p2p_join_scan_count = 0;
	pending_join_scan_callback();
	if (pending_scan_res_join_callback == NULL) {
		return createStatusWithMsg(SupplicantStatusCode::FAILURE_UNKNOWN,
			"Failed to start scan.");
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::setMacRandomizationInternal(bool enable)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	bool currentEnabledState = !!wpa_s->conf->p2p_device_random_mac_addr;
	u8 *addr = NULL;

	// The same state, no change is needed.
	if (currentEnabledState == enable) {
		wpa_printf(MSG_DEBUG, "The random MAC is %s already.",
			(enable) ? "enabled" : "disabled");
		return ndk::ScopedAStatus::ok();
	}

	if (enable) {
		wpa_s->conf->p2p_device_random_mac_addr = 1;
		wpa_s->conf->p2p_interface_random_mac_addr = 1;

		// restore config if it failed to set up MAC address.
		if (wpas_p2p_mac_setup(wpa_s) < 0) {
			wpa_s->conf->p2p_device_random_mac_addr = 0;
			wpa_s->conf->p2p_interface_random_mac_addr = 0;
			return createStatusWithMsg(SupplicantStatusCode::FAILURE_UNKNOWN,
				"Failed to set up MAC address.");
		}
	} else {
		// disable random MAC will use original MAC address
		// regardless of any saved persistent groups.
		if (wpa_drv_set_mac_addr(wpa_s, NULL) < 0) {
			wpa_printf(MSG_ERROR, "Failed to restore MAC address");
			return createStatusWithMsg(SupplicantStatusCode::FAILURE_UNKNOWN,
				"Failed to restore MAC address.");
		}

		if (wpa_supplicant_update_mac_addr(wpa_s) < 0) {
			wpa_printf(MSG_INFO, "Could not update MAC address information");
			return createStatusWithMsg(SupplicantStatusCode::FAILURE_UNKNOWN,
				"Failed to update MAC address.");
		}
		wpa_s->conf->p2p_device_random_mac_addr = 0;
		wpa_s->conf->p2p_interface_random_mac_addr = 0;
	}

	// update internal data to send out correct device address in action frame.
	os_memcpy(wpa_s->global->p2p_dev_addr, wpa_s->own_addr, ETH_ALEN);
	os_memcpy(wpa_s->global->p2p->cfg->dev_addr, wpa_s->global->p2p_dev_addr, ETH_ALEN);

	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::setEdmgInternal(bool enable)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	wpa_printf(MSG_DEBUG, "set p2p_go_edmg to %d", enable);
	wpa_s->conf->p2p_go_edmg = enable ? 1 : 0;
	wpa_s->p2p_go_edmg = enable ? 1 : 0;
	return ndk::ScopedAStatus::ok();
}

std::pair<bool, ndk::ScopedAStatus> P2pIface::getEdmgInternal()
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	return {(wpa_s->p2p_go_edmg == 1), ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus P2pIface::setWfdR2DeviceInfoInternal(
	const std::vector<uint8_t>& info)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	uint32_t wfd_r2_device_info_hex_len = info.size() * 2 + 1;
	std::vector<char> wfd_r2_device_info_hex(wfd_r2_device_info_hex_len);
	wpa_snprintf_hex(
		wfd_r2_device_info_hex.data(), wfd_r2_device_info_hex.size(),
		info.data(),info.size());
	std::string wfd_r2_device_info_set_cmd_str =
		 std::to_string(kWfdR2DeviceInfoSubelemId) + " " +
		 wfd_r2_device_info_hex.data();
	std::vector<char> wfd_r2_device_info_set_cmd(
		 wfd_r2_device_info_set_cmd_str.c_str(),
		 wfd_r2_device_info_set_cmd_str.c_str() +
		 wfd_r2_device_info_set_cmd_str.size() + 1);
	if (wifi_display_subelem_set(
		wpa_s->global, wfd_r2_device_info_set_cmd.data())) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::removeClientInternal(
    const std::vector<uint8_t>& peer_address, bool isLegacyClient)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (peer_address.size() != ETH_ALEN) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	wpas_p2p_remove_client(wpa_s, peer_address.data(), isLegacyClient? 1 : 0);
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::findOnSocialChannelsInternal(uint32_t timeout_in_sec)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (wpa_s->wpa_state == WPA_INTERFACE_DISABLED) {
		return createStatus(SupplicantStatusCode::FAILURE_IFACE_DISABLED);
	}
	uint32_t search_delay = wpas_p2p_search_delay(wpa_s);
	if (wpas_p2p_find(
		wpa_s, timeout_in_sec, P2P_FIND_ONLY_SOCIAL, 0, nullptr,
		nullptr, search_delay, 0, nullptr, 0, is6GhzAllowed(wpa_s))) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::findOnSpecificFrequencyInternal(
	uint32_t freq, uint32_t timeout_in_sec)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	if (wpa_s->wpa_state == WPA_INTERFACE_DISABLED) {
		return createStatus(SupplicantStatusCode::FAILURE_IFACE_DISABLED);
	}
	uint32_t search_delay = wpas_p2p_search_delay(wpa_s);
	if (wpas_p2p_find(
		wpa_s, timeout_in_sec, P2P_FIND_START_WITH_FULL, 0, nullptr,
		nullptr, search_delay, 0, nullptr, freq, is6GhzAllowed(wpa_s))) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus P2pIface::setVendorElementsInternal(
	P2pFrameTypeMask frameTypeMask,
	const std::vector<uint8_t>& vendorElemBytes)
{
	struct wpa_supplicant* wpa_s = retrieveIfacePtr();
	for (int i = 0; i < NUM_VENDOR_ELEM_FRAMES; i++) {
		uint32_t bit = convertWpaP2pFrameTypeToHalP2pFrameTypeBit(i);
		if (0 == bit) continue;

		if (static_cast<uint32_t>(frameTypeMask) & bit) {
			updateP2pVendorElem(wpa_s, (enum wpa_vendor_elem_frame) i, vendorElemBytes);
		}
	}
	return ndk::ScopedAStatus::ok();
}

/**
 * Retrieve the underlying |wpa_supplicant| struct
 * pointer for this iface.
 * If the underlying iface is removed, then all RPC method calls on this object
 * will return failure.
 */
wpa_supplicant* P2pIface::retrieveIfacePtr()
{
	return wpa_supplicant_get_iface(wpa_global_, ifname_.c_str());
}

/**
 * Retrieve the underlying |wpa_supplicant| struct
 * pointer for this group iface.
 */
wpa_supplicant* P2pIface::retrieveGroupIfacePtr(const std::string& group_ifname)
{
	return wpa_supplicant_get_iface(wpa_global_, group_ifname.c_str());
}

}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
