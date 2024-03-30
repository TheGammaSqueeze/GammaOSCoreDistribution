/*
 * WPA Supplicant - Sta Iface Aidl interface
 * Copyright (c) 2021, Google Inc. All rights reserved.
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#include "aidl_manager.h"
#include "aidl_return_util.h"
#include "iface_config_utils.h"
#include "misc_utils.h"
#include "sta_iface.h"

extern "C"
{
#include "utils/eloop.h"
#include "gas_query.h"
#include "interworking.h"
#include "hs20_supplicant.h"
#include "wps_supplicant.h"
#include "dpp.h"
#include "dpp_supplicant.h"
#include "rsn_supp/wpa.h"
#include "rsn_supp/pmksa_cache.h"
}

namespace {
using aidl::android::hardware::wifi::supplicant::AidlManager;
using aidl::android::hardware::wifi::supplicant::BtCoexistenceMode;
using aidl::android::hardware::wifi::supplicant::ConnectionCapabilities;
using aidl::android::hardware::wifi::supplicant::DppCurve;
using aidl::android::hardware::wifi::supplicant::DppResponderBootstrapInfo;
using aidl::android::hardware::wifi::supplicant::ISupplicant;
using aidl::android::hardware::wifi::supplicant::ISupplicantStaIface;
using aidl::android::hardware::wifi::supplicant::ISupplicantStaNetwork;
using aidl::android::hardware::wifi::supplicant::KeyMgmtMask;
using aidl::android::hardware::wifi::supplicant::LegacyMode;
using aidl::android::hardware::wifi::supplicant::RxFilterType;
using aidl::android::hardware::wifi::supplicant::SupplicantStatusCode;
using aidl::android::hardware::wifi::supplicant::WifiTechnology;
using aidl::android::hardware::wifi::supplicant::misc_utils::createStatus;

// TODO (b/204810426): Import from wifi vendor AIDL interface when it exists
enum WifiChannelWidthInMhz {
  WIDTH_20	= 0,
  WIDTH_40	= 1,
  WIDTH_80	= 2,
  WIDTH_160   = 3,
  WIDTH_80P80 = 4,
  WIDTH_5	 = 5,
  WIDTH_10	= 6,
  WIDTH_INVALID = -1
};

constexpr uint32_t kMaxAnqpElems = 100;
constexpr char kGetMacAddress[] = "MACADDR";
constexpr char kStartRxFilter[] = "RXFILTER-START";
constexpr char kStopRxFilter[] = "RXFILTER-STOP";
constexpr char kAddRxFilter[] = "RXFILTER-ADD";
constexpr char kRemoveRxFilter[] = "RXFILTER-REMOVE";
constexpr char kSetBtCoexistenceMode[] = "BTCOEXMODE";
constexpr char kSetBtCoexistenceScanStart[] = "BTCOEXSCAN-START";
constexpr char kSetBtCoexistenceScanStop[] = "BTCOEXSCAN-STOP";
constexpr char kSetSupendModeEnabled[] = "SETSUSPENDMODE 1";
constexpr char kSetSupendModeDisabled[] = "SETSUSPENDMODE 0";
constexpr char kSetCountryCode[] = "COUNTRY";
constexpr uint32_t kExtRadioWorkDefaultTimeoutInSec =
	static_cast<uint32_t>(ISupplicant::EXT_RADIO_WORK_TIMEOUT_IN_SECS);
constexpr char kExtRadioWorkNamePrefix[] = "ext:";

uint8_t convertAidlRxFilterTypeToInternal(
	RxFilterType type)
{
	switch (type) {
	case RxFilterType::V4_MULTICAST:
		return 2;
	case RxFilterType::V6_MULTICAST:
		return 3;
	};
	WPA_ASSERT(false);
}

uint8_t convertAidlBtCoexModeToInternal(
	BtCoexistenceMode mode)
{
	switch (mode) {
	case BtCoexistenceMode::ENABLED:
		return 0;
	case BtCoexistenceMode::DISABLED:
		return 1;
	case BtCoexistenceMode::SENSE:
		return 2;
	};
	WPA_ASSERT(false);
}

ndk::ScopedAStatus doZeroArgDriverCommand(
	struct wpa_supplicant *wpa_s, const char *cmd)
{
	std::vector<char> cmd_vec(cmd, cmd + strlen(cmd) + 1);
	char driver_cmd_reply_buf[4096] = {};
	if (wpa_drv_driver_cmd(
		wpa_s, cmd_vec.data(), driver_cmd_reply_buf,
		sizeof(driver_cmd_reply_buf))) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus doOneArgDriverCommand(
	struct wpa_supplicant *wpa_s, const char *cmd, uint8_t arg)
{
	std::string cmd_str = std::string(cmd) + " " + std::to_string(arg);
	return doZeroArgDriverCommand(wpa_s, cmd_str.c_str());
}

ndk::ScopedAStatus doOneArgDriverCommand(
	struct wpa_supplicant *wpa_s, const char *cmd, const std::string &arg)
{
	std::string cmd_str = std::string(cmd) + " " + arg;
	return doZeroArgDriverCommand(wpa_s, cmd_str.c_str());
}

void endExtRadioWork(struct wpa_radio_work *work)
{
	auto *ework = static_cast<struct wpa_external_work *>(work->ctx);
	work->wpa_s->ext_work_in_progress = 0;
	radio_work_done(work);
	os_free(ework);
}

void extRadioWorkTimeoutCb(void *eloop_ctx, void *timeout_ctx)
{
	auto *work = static_cast<struct wpa_radio_work *>(eloop_ctx);
	auto *ework = static_cast<struct wpa_external_work *>(work->ctx);
	wpa_dbg(
		work->wpa_s, MSG_DEBUG, "Timing out external radio work %u (%s)",
		ework->id, work->type);

	AidlManager *aidl_manager = AidlManager::getInstance();
	WPA_ASSERT(aidl_manager);
	aidl_manager->notifyExtRadioWorkTimeout(work->wpa_s, ework->id);

	endExtRadioWork(work);
}

void startExtRadioWork(struct wpa_radio_work *work)
{
	auto *ework = static_cast<struct wpa_external_work *>(work->ctx);
	work->wpa_s->ext_work_in_progress = 1;
	if (!ework->timeout) {
		ework->timeout = kExtRadioWorkDefaultTimeoutInSec;
	}
	eloop_register_timeout(
		ework->timeout, 0, extRadioWorkTimeoutCb, work, nullptr);
}

void extRadioWorkStartCb(struct wpa_radio_work *work, int deinit)
{
	// deinit==1 is invoked during interface removal. Since the AIDL
	// interface does not support interface addition/removal, we don't
	// need to handle this scenario.
	WPA_ASSERT(!deinit);

	auto *ework = static_cast<struct wpa_external_work *>(work->ctx);
	wpa_dbg(
		work->wpa_s, MSG_DEBUG, "Starting external radio work %u (%s)",
		ework->id, ework->type);

	AidlManager *aidl_manager = AidlManager::getInstance();
	WPA_ASSERT(aidl_manager);
	aidl_manager->notifyExtRadioWorkStart(work->wpa_s, ework->id);

	startExtRadioWork(work);
}

KeyMgmtMask convertWpaKeyMgmtCapabilitiesToAidl (
	struct wpa_supplicant *wpa_s, struct wpa_driver_capa *capa) {

	uint32_t mask = 0;
	/* Logic from ctrl_iface.c, NONE and IEEE8021X have no capability
	 * flags and always enabled.
	 */
	mask |=
		(static_cast<uint32_t>(KeyMgmtMask::NONE) |
		 static_cast<uint32_t>(KeyMgmtMask::IEEE8021X));

	if (capa->key_mgmt &
		(WPA_DRIVER_CAPA_KEY_MGMT_WPA | WPA_DRIVER_CAPA_KEY_MGMT_WPA2)) {
		mask |= static_cast<uint32_t>(KeyMgmtMask::WPA_EAP);
	}

	if (capa->key_mgmt & (WPA_DRIVER_CAPA_KEY_MGMT_WPA_PSK |
				 WPA_DRIVER_CAPA_KEY_MGMT_WPA2_PSK)) {
		mask |= static_cast<uint32_t>(KeyMgmtMask::WPA_PSK);
	}
#ifdef CONFIG_SUITEB192
	if (capa->key_mgmt & WPA_DRIVER_CAPA_KEY_MGMT_SUITE_B_192) {
		mask |= static_cast<uint32_t>(KeyMgmtMask::SUITE_B_192);
	}
#endif /* CONFIG_SUITEB192 */
#ifdef CONFIG_OWE
	if (capa->key_mgmt & WPA_DRIVER_CAPA_KEY_MGMT_OWE) {
		mask |= static_cast<uint32_t>(KeyMgmtMask::OWE);
	}
#endif /* CONFIG_OWE */
#ifdef CONFIG_SAE
	if (wpa_s->drv_flags & WPA_DRIVER_FLAGS_SAE) {
		mask |= static_cast<uint32_t>(KeyMgmtMask::SAE);
	}
#endif /* CONFIG_SAE */
#ifdef CONFIG_DPP
	if (capa->key_mgmt & WPA_DRIVER_CAPA_KEY_MGMT_DPP) {
		mask |= static_cast<uint32_t>(KeyMgmtMask::DPP);
	}
#endif
#ifdef CONFIG_WAPI_INTERFACE
	mask |= static_cast<uint32_t>(KeyMgmtMask::WAPI_PSK);
	mask |= static_cast<uint32_t>(KeyMgmtMask::WAPI_CERT);
#endif /* CONFIG_WAPI_INTERFACE */
#ifdef CONFIG_FILS
	if (capa->key_mgmt & WPA_DRIVER_CAPA_KEY_MGMT_FILS_SHA256) {
		mask |= static_cast<uint32_t>(KeyMgmtMask::FILS_SHA256);
	}
	if (capa->key_mgmt & WPA_DRIVER_CAPA_KEY_MGMT_FILS_SHA384) {
		mask |= static_cast<uint32_t>(KeyMgmtMask::FILS_SHA384);
	}
#endif /* CONFIG_FILS */
	return static_cast<KeyMgmtMask>(mask);
}

const std::string getDppListenChannel(struct wpa_supplicant *wpa_s, int32_t *listen_channel)
{
	struct hostapd_hw_modes *mode;
	int chan44 = 0, chan149 = 0;
	*listen_channel = 0;

	/* Check if device support 2.4GHz band*/
	mode = get_mode(wpa_s->hw.modes, wpa_s->hw.num_modes,
			HOSTAPD_MODE_IEEE80211G, 0);
	if (mode) {
		*listen_channel = 6;
		return "81/6";
	}
	/* Check if device support 5GHz band */
	mode = get_mode(wpa_s->hw.modes, wpa_s->hw.num_modes,
			HOSTAPD_MODE_IEEE80211A, 0);
	if (mode) {
		for (int i = 0; i < mode->num_channels; i++) {
			struct hostapd_channel_data *chan = &mode->channels[i];

			if (chan->flag & (HOSTAPD_CHAN_DISABLED |
					  HOSTAPD_CHAN_RADAR))
				continue;
			if (chan->freq == 5220)
				chan44 = 1;
			if (chan->freq == 5745)
				chan149 = 1;
		}
		if (chan149) {
			*listen_channel = 149;
			return "124/149";
		} else if (chan44) {
			*listen_channel = 44;
			return "115/44";
		}
	}

	return "";
}

const std::string convertCurveTypeToName(DppCurve curve)
{
	switch (curve) {
	case DppCurve::PRIME256V1:
		return "prime256v1";
	case DppCurve::SECP384R1:
		return "secp384r1";
	case DppCurve::SECP521R1:
		return "secp521r1";
	case DppCurve::BRAINPOOLP256R1:
		return "brainpoolP256r1";
	case DppCurve::BRAINPOOLP384R1:
		return "brainpoolP384r1";
	case DppCurve::BRAINPOOLP512R1:
		return "brainpoolP512r1";
	}
	WPA_ASSERT(false);
}

}  // namespace

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
using aidl_return_util::validateAndCall;
using misc_utils::createStatus;

StaIface::StaIface(struct wpa_global *wpa_global, const char ifname[])
	: wpa_global_(wpa_global), ifname_(ifname), is_valid_(true)
{}

void StaIface::invalidate() { is_valid_ = false; }
bool StaIface::isValid()
{
	return (is_valid_ && (retrieveIfacePtr() != nullptr));
}

::ndk::ScopedAStatus StaIface::getName(
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::getNameInternal, _aidl_return);
}

::ndk::ScopedAStatus StaIface::getType(
	IfaceType* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::getTypeInternal, _aidl_return);
}

::ndk::ScopedAStatus StaIface::addNetwork(
	std::shared_ptr<ISupplicantStaNetwork>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::addNetworkInternal, _aidl_return);
}

::ndk::ScopedAStatus StaIface::removeNetwork(
	int32_t in_id)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::removeNetworkInternal, in_id);
}

::ndk::ScopedAStatus StaIface::filsHlpFlushRequest()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::filsHlpFlushRequestInternal);
}

::ndk::ScopedAStatus StaIface::filsHlpAddRequest(
	const std::vector<uint8_t>& in_dst_mac,
	const std::vector<uint8_t>& in_pkt)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::filsHlpAddRequestInternal, in_dst_mac, in_pkt);
}

::ndk::ScopedAStatus StaIface::getNetwork(
	int32_t in_id, std::shared_ptr<ISupplicantStaNetwork>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::getNetworkInternal, _aidl_return, in_id);
}

::ndk::ScopedAStatus StaIface::listNetworks(
	std::vector<int32_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::listNetworksInternal, _aidl_return);
}

::ndk::ScopedAStatus StaIface::registerCallback(
	const std::shared_ptr<ISupplicantStaIfaceCallback>& in_callback)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::registerCallbackInternal, in_callback);
}

::ndk::ScopedAStatus StaIface::reassociate()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::reassociateInternal);
}

::ndk::ScopedAStatus StaIface::reconnect()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::reconnectInternal);
}

::ndk::ScopedAStatus StaIface::disconnect()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::disconnectInternal);
}

::ndk::ScopedAStatus StaIface::setPowerSave(
	bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::setPowerSaveInternal, in_enable);
}

::ndk::ScopedAStatus StaIface::initiateTdlsDiscover(
	const std::vector<uint8_t>& in_macAddress)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::initiateTdlsDiscoverInternal, in_macAddress);
}

::ndk::ScopedAStatus StaIface::initiateTdlsSetup(
	const std::vector<uint8_t>& in_macAddress)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::initiateTdlsSetupInternal, in_macAddress);
}

::ndk::ScopedAStatus StaIface::initiateTdlsTeardown(
	const std::vector<uint8_t>& in_macAddress)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::initiateTdlsTeardownInternal, in_macAddress);
}

::ndk::ScopedAStatus StaIface::initiateAnqpQuery(
	const std::vector<uint8_t>& in_macAddress,
	const std::vector<AnqpInfoId>& in_infoElements,
	const std::vector<Hs20AnqpSubtypes>& in_subTypes)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::initiateAnqpQueryInternal, in_macAddress,
		in_infoElements, in_subTypes);
}

::ndk::ScopedAStatus StaIface::initiateVenueUrlAnqpQuery(
	const std::vector<uint8_t>& in_macAddress)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::initiateVenueUrlAnqpQueryInternal, in_macAddress);
}

::ndk::ScopedAStatus StaIface::initiateHs20IconQuery(
	const std::vector<uint8_t>& in_macAddress,
	const std::string& in_fileName)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::initiateHs20IconQueryInternal, in_macAddress,
		in_fileName);
}

::ndk::ScopedAStatus StaIface::getMacAddress(
	std::vector<uint8_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::getMacAddressInternal, _aidl_return);
}

::ndk::ScopedAStatus StaIface::startRxFilter()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::startRxFilterInternal);
}

::ndk::ScopedAStatus StaIface::stopRxFilter()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::stopRxFilterInternal);
}

::ndk::ScopedAStatus StaIface::addRxFilter(
	RxFilterType in_type)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::addRxFilterInternal, in_type);
}

::ndk::ScopedAStatus StaIface::removeRxFilter(
	RxFilterType in_type)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::removeRxFilterInternal, in_type);
}

::ndk::ScopedAStatus StaIface::setBtCoexistenceMode(
	BtCoexistenceMode in_mode)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::setBtCoexistenceModeInternal, in_mode);
}

::ndk::ScopedAStatus StaIface::setBtCoexistenceScanModeEnabled(
	bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::setBtCoexistenceScanModeEnabledInternal,
		in_enable);
}

::ndk::ScopedAStatus StaIface::setSuspendModeEnabled(
	bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::setSuspendModeEnabledInternal, in_enable);
}

::ndk::ScopedAStatus StaIface::setCountryCode(
	const std::vector<uint8_t>& in_code)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::setCountryCodeInternal, in_code);
}

::ndk::ScopedAStatus StaIface::startWpsRegistrar(
	const std::vector<uint8_t>& in_bssid,
	const std::string& in_pin)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::startWpsRegistrarInternal, in_bssid, in_pin);
}

::ndk::ScopedAStatus StaIface::startWpsPbc(
	const std::vector<uint8_t>& in_bssid)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::startWpsPbcInternal, in_bssid);
}

::ndk::ScopedAStatus StaIface::startWpsPinKeypad(
	const std::string& in_pin)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::startWpsPinKeypadInternal, in_pin);
}

::ndk::ScopedAStatus StaIface::startWpsPinDisplay(
	const std::vector<uint8_t>& in_bssid,
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::startWpsPinDisplayInternal, _aidl_return, in_bssid);
}

::ndk::ScopedAStatus StaIface::cancelWps()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::cancelWpsInternal);
}

::ndk::ScopedAStatus StaIface::setWpsDeviceName(
	const std::string& in_name)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::setWpsDeviceNameInternal, in_name);
}

::ndk::ScopedAStatus StaIface::setWpsDeviceType(
	const std::vector<uint8_t>& in_type)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::setWpsDeviceTypeInternal, in_type);
}

::ndk::ScopedAStatus StaIface::setWpsManufacturer(
	const std::string& in_manufacturer)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::setWpsManufacturerInternal, in_manufacturer);
}

::ndk::ScopedAStatus StaIface::setWpsModelName(
	const std::string& in_modelName)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::setWpsModelNameInternal, in_modelName);
}

::ndk::ScopedAStatus StaIface::setWpsModelNumber(
	const std::string& in_modelNumber)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::setWpsModelNumberInternal, in_modelNumber);
}

::ndk::ScopedAStatus StaIface::setWpsSerialNumber(
	const std::string& in_serialNumber)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::setWpsSerialNumberInternal, in_serialNumber);
}

::ndk::ScopedAStatus StaIface::setWpsConfigMethods(
	WpsConfigMethods in_configMethods)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::setWpsConfigMethodsInternal, in_configMethods);
}

::ndk::ScopedAStatus StaIface::setExternalSim(
	bool in_useExternalSim)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::setExternalSimInternal, in_useExternalSim);
}

::ndk::ScopedAStatus StaIface::addExtRadioWork(
	const std::string& in_name, int32_t in_freqInMhz,
	int32_t in_timeoutInSec,
	int32_t* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::addExtRadioWorkInternal, _aidl_return, in_name, in_freqInMhz,
		in_timeoutInSec);
}

::ndk::ScopedAStatus StaIface::removeExtRadioWork(
	int32_t in_id)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::removeExtRadioWorkInternal, in_id);
}

::ndk::ScopedAStatus StaIface::enableAutoReconnect(
	bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::enableAutoReconnectInternal, in_enable);
}

::ndk::ScopedAStatus StaIface::getKeyMgmtCapabilities(
	KeyMgmtMask* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaIface::getKeyMgmtCapabilitiesInternal, _aidl_return);
}

::ndk::ScopedAStatus StaIface::addDppPeerUri(
	const std::string& in_uri, int32_t* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaIface::addDppPeerUriInternal, _aidl_return, in_uri);
}

::ndk::ScopedAStatus StaIface::removeDppUri(
	int32_t in_id)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaIface::removeDppUriInternal, in_id);
}

::ndk::ScopedAStatus StaIface::startDppConfiguratorInitiator(
	int32_t in_peerBootstrapId, int32_t in_ownBootstrapId,
	const std::string& in_ssid, const std::string& in_password,
	const std::string& in_psk, DppNetRole in_netRole,
	DppAkm in_securityAkm, const std::vector<uint8_t>& in_privEcKey,
	std::vector<uint8_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaIface::startDppConfiguratorInitiatorInternal, _aidl_return,
		in_peerBootstrapId,in_ownBootstrapId, in_ssid, in_password,
		in_psk, in_netRole, in_securityAkm, in_privEcKey);
}

::ndk::ScopedAStatus StaIface::startDppEnrolleeInitiator(
	int32_t in_peerBootstrapId, int32_t in_ownBootstrapId)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaIface::startDppEnrolleeInitiatorInternal, in_peerBootstrapId,
		in_ownBootstrapId);
}

::ndk::ScopedAStatus StaIface::stopDppInitiator()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaIface::stopDppInitiatorInternal);
}

::ndk::ScopedAStatus StaIface::getConnectionCapabilities(
	ConnectionCapabilities* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_UNKNOWN,
		&StaIface::getConnectionCapabilitiesInternal,
		_aidl_return);
}

::ndk::ScopedAStatus StaIface::generateDppBootstrapInfoForResponder(
	const std::vector<uint8_t>& in_macAddress, const std::string& in_deviceInfo,
	DppCurve in_curve, DppResponderBootstrapInfo* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::generateDppBootstrapInfoForResponderInternal, _aidl_return, 
		in_macAddress, in_deviceInfo, in_curve);
}

::ndk::ScopedAStatus StaIface::startDppEnrolleeResponder(
	int32_t in_listenChannel)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::startDppEnrolleeResponderInternal, in_listenChannel);
}

::ndk::ScopedAStatus StaIface::stopDppResponder(
	int32_t in_ownBootstrapId)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::stopDppResponderInternal, in_ownBootstrapId);
}

::ndk::ScopedAStatus StaIface::generateSelfDppConfiguration(
	const std::string& in_ssid, const std::vector<uint8_t>& in_privEcKey)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_IFACE_INVALID,
		&StaIface::generateSelfDppConfigurationInternal, in_ssid, in_privEcKey);
}

::ndk::ScopedAStatus StaIface::getWpaDriverCapabilities(
	WpaDriverCapabilitiesMask* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_UNKNOWN,
		&StaIface::getWpaDriverCapabilitiesInternal, _aidl_return);
}

::ndk::ScopedAStatus StaIface::setMboCellularDataStatus(
	bool in_available)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_UNKNOWN,
		&StaIface::setMboCellularDataStatusInternal, in_available);
}

::ndk::ScopedAStatus StaIface::setQosPolicyFeatureEnabled(
	bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_UNKNOWN,
		&StaIface::setQosPolicyFeatureEnabledInternal, in_enable);
}

::ndk::ScopedAStatus StaIface::sendQosPolicyResponse(
	int32_t in_qosPolicyRequestId, bool in_morePolicies,
	const std::vector<QosPolicyStatus>& in_qosPolicyStatusList)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_UNKNOWN,
		&StaIface::sendQosPolicyResponseInternal, in_qosPolicyRequestId,
		in_morePolicies, in_qosPolicyStatusList);
}

::ndk::ScopedAStatus StaIface::removeAllQosPolicies()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_UNKNOWN,
		&StaIface::removeAllQosPoliciesInternal);
}

::ndk::ScopedAStatus StaIface::getConnectionMloLinksInfo(MloLinksInfo* _aidl_return) {
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_UNKNOWN,
		&StaIface::getConnectionMloLinksInfoInternal, _aidl_return);
}

std::pair<std::string, ndk::ScopedAStatus> StaIface::getNameInternal()
{
	return {ifname_, ndk::ScopedAStatus::ok()};
}

std::pair<IfaceType, ndk::ScopedAStatus> StaIface::getTypeInternal()
{
	return {IfaceType::STA, ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus StaIface::filsHlpFlushRequestInternal()
{
#ifdef CONFIG_FILS
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();

	wpas_flush_fils_hlp_req(wpa_s);
	return ndk::ScopedAStatus::ok();
#else /* CONFIG_FILS */
	return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN, "");
#endif /* CONFIG_FILS */
}

ndk::ScopedAStatus StaIface::filsHlpAddRequestInternal(
	const std::vector<uint8_t> &dst_mac, const std::vector<uint8_t> &pkt)
{
#ifdef CONFIG_FILS
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	struct fils_hlp_req *req;

	if (!pkt.size())
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	if (dst_mac.size() != ETH_ALEN)
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);


	req = (struct fils_hlp_req *)os_zalloc(sizeof(*req));
	if (!req)
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);

	os_memcpy(req->dst, dst_mac.data(), ETH_ALEN);

	req->pkt = wpabuf_alloc_copy(pkt.data(), pkt.size());
	if (!req->pkt) {
		os_free(req);
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}

	dl_list_add_tail(&wpa_s->fils_hlp_req, &req->list);
	return ndk::ScopedAStatus::ok();
#else /* CONFIG_FILS */
	return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
#endif /* CONFIG_FILS */
}

std::pair<std::shared_ptr<ISupplicantStaNetwork>, ndk::ScopedAStatus>
StaIface::addNetworkInternal()
{
	std::shared_ptr<ISupplicantStaNetwork> network;
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	struct wpa_ssid *ssid = wpa_supplicant_add_network(wpa_s);
	if (!ssid) {
		return {network, createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager ||
		aidl_manager->getStaNetworkAidlObjectByIfnameAndNetworkId(
		wpa_s->ifname, ssid->id, &network)) {
		return {network, createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {network, ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus StaIface::removeNetworkInternal(int32_t id)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	int result = wpa_supplicant_remove_network(wpa_s, id);
	if (result == -1) {
		return createStatus(SupplicantStatusCode::FAILURE_NETWORK_UNKNOWN);
	}
	if (result != 0) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

std::pair<std::shared_ptr<ISupplicantStaNetwork>, ndk::ScopedAStatus>
StaIface::getNetworkInternal(int32_t id)
{
	std::shared_ptr<ISupplicantStaNetwork> network;
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	struct wpa_ssid *ssid = wpa_config_get_network(wpa_s->conf, id);
	if (!ssid) {
		return {network, createStatus(SupplicantStatusCode::FAILURE_NETWORK_UNKNOWN)};
	}
	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager ||
		aidl_manager->getStaNetworkAidlObjectByIfnameAndNetworkId(
		wpa_s->ifname, ssid->id, &network)) {
		return {network, createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {network, ndk::ScopedAStatus::ok()};
}

std::pair<std::vector<int32_t>, ndk::ScopedAStatus>
StaIface::listNetworksInternal()
{
	std::vector<int32_t> network_ids;
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	for (struct wpa_ssid *wpa_ssid = wpa_s->conf->ssid; wpa_ssid;
		 wpa_ssid = wpa_ssid->next) {
		network_ids.emplace_back(wpa_ssid->id);
	}
	return {std::move(network_ids), ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus StaIface::registerCallbackInternal(
	const std::shared_ptr<ISupplicantStaIfaceCallback> &callback)
{
	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager ||
		aidl_manager->addStaIfaceCallbackAidlObject(ifname_, callback)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaIface::reassociateInternal()
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (wpa_s->wpa_state == WPA_INTERFACE_DISABLED) {
		return createStatus(SupplicantStatusCode::FAILURE_IFACE_DISABLED);
	}
	wpas_request_connection(wpa_s);
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaIface::reconnectInternal()
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (wpa_s->wpa_state == WPA_INTERFACE_DISABLED) {
		return createStatus(SupplicantStatusCode::FAILURE_IFACE_DISABLED);
	}
	if (!wpa_s->disconnected) {
		return createStatus(SupplicantStatusCode::FAILURE_IFACE_NOT_DISCONNECTED);
	}
	wpas_request_connection(wpa_s);
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaIface::disconnectInternal()
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (wpa_s->wpa_state == WPA_INTERFACE_DISABLED) {
		return createStatus(SupplicantStatusCode::FAILURE_IFACE_DISABLED);
	}
	wpas_request_disconnection(wpa_s);
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaIface::setPowerSaveInternal(bool enable)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (wpa_s->wpa_state == WPA_INTERFACE_DISABLED) {
		return createStatus(SupplicantStatusCode::FAILURE_IFACE_DISABLED);
	}
	if (wpa_drv_set_p2p_powersave(wpa_s, enable, -1, -1)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaIface::initiateTdlsDiscoverInternal(
	const std::vector<uint8_t> &mac_address)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	int ret;
	if (mac_address.size() != ETH_ALEN) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	const u8 *peer = mac_address.data();
	if (wpa_tdls_is_external_setup(wpa_s->wpa)) {
		ret = wpa_tdls_send_discovery_request(wpa_s->wpa, peer);
	} else {
		ret = wpa_drv_tdls_oper(wpa_s, TDLS_DISCOVERY_REQ, peer);
	}
	if (ret) {
		wpa_printf(MSG_INFO, "StaIface: TDLS discover failed: %d", ret);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaIface::initiateTdlsSetupInternal(
	const std::vector<uint8_t> &mac_address)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	int ret;
	if (mac_address.size() != ETH_ALEN) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	const u8 *peer = mac_address.data();
	if (wpa_tdls_is_external_setup(wpa_s->wpa) &&
		!(wpa_s->conf->tdls_external_control)) {
		wpa_tdls_remove(wpa_s->wpa, peer);
		ret = wpa_tdls_start(wpa_s->wpa, peer);
	} else {
		ret = wpa_drv_tdls_oper(wpa_s, TDLS_SETUP, peer);
	}
	if (ret) {
		wpa_printf(MSG_INFO, "StaIface: TDLS setup failed: %d", ret);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaIface::initiateTdlsTeardownInternal(
	const std::vector<uint8_t> &mac_address)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	int ret;
	if (mac_address.size() != ETH_ALEN) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	const u8 *peer = mac_address.data();
	if (wpa_tdls_is_external_setup(wpa_s->wpa) &&
		!(wpa_s->conf->tdls_external_control)) {
		ret = wpa_tdls_teardown_link(
			wpa_s->wpa, peer, WLAN_REASON_TDLS_TEARDOWN_UNSPECIFIED);
	} else {
		ret = wpa_drv_tdls_oper(wpa_s, TDLS_TEARDOWN, peer);
	}
	if (ret) {
		wpa_printf(MSG_INFO, "StaIface: TDLS teardown failed: %d", ret);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaIface::initiateAnqpQueryInternal(
	const std::vector<uint8_t> &mac_address,
	const std::vector<AnqpInfoId> &info_elements,
	const std::vector<Hs20AnqpSubtypes> &sub_types)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (info_elements.size() > kMaxAnqpElems) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}
	uint16_t info_elems_buf[kMaxAnqpElems];
	uint32_t num_info_elems = 0;
	for (const auto &info_element : info_elements) {
		info_elems_buf[num_info_elems++] =
			static_cast<std::underlying_type<
			AnqpInfoId>::type>(info_element);
	}
	uint32_t sub_types_bitmask = 0;
	for (const auto &type : sub_types) {
		sub_types_bitmask |= BIT(
			static_cast<std::underlying_type<
			Hs20AnqpSubtypes>::type>(type));
	}
	if (mac_address.size() != ETH_ALEN) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}

	if (anqp_send_req(
		wpa_s, mac_address.data(), 0, info_elems_buf, num_info_elems,
		sub_types_bitmask, 0)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaIface::initiateVenueUrlAnqpQueryInternal(
	const std::vector<uint8_t> &mac_address)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	uint16_t info_elems_buf[1] = {ANQP_VENUE_URL};
	if (mac_address.size() != ETH_ALEN) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}

	if (anqp_send_req(
		wpa_s, mac_address.data(), 0, info_elems_buf, 1, 0, 0)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaIface::initiateHs20IconQueryInternal(
	const std::vector<uint8_t> &mac_address, const std::string &file_name)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (mac_address.size() != ETH_ALEN) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	wpa_s->fetch_osu_icon_in_progress = 0;
	if (hs20_anqp_send_req(
		wpa_s, mac_address.data(), BIT(HS20_STYPE_ICON_REQUEST),
		reinterpret_cast<const uint8_t *>(file_name.c_str()),
		file_name.size(), true)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

std::pair<std::vector<uint8_t>, ndk::ScopedAStatus>
StaIface::getMacAddressInternal()
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	std::vector<char> cmd(
		kGetMacAddress, kGetMacAddress + sizeof(kGetMacAddress));
	char driver_cmd_reply_buf[4096] = {};
	int ret = wpa_drv_driver_cmd(
		wpa_s, cmd.data(), driver_cmd_reply_buf,
		sizeof(driver_cmd_reply_buf));
	// Reply is of the format: "Macaddr = XX:XX:XX:XX:XX:XX"
	std::string reply_str = driver_cmd_reply_buf;
	if (ret < 0 || reply_str.empty() ||
		reply_str.find("=") == std::string::npos) {
		return {std::vector<uint8_t>(),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	// Remove all whitespace first and then split using the delimiter "=".
	reply_str.erase(
		remove_if(reply_str.begin(), reply_str.end(), isspace),
		reply_str.end());
	std::string mac_addr_str =
		reply_str.substr(reply_str.find("=") + 1, reply_str.size());
	std::vector<uint8_t> mac_addr(6);
	if (hwaddr_aton(mac_addr_str.c_str(), mac_addr.data())) {
		return {std::vector<uint8_t>(),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {mac_addr, ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus StaIface::startRxFilterInternal()
{
	return doZeroArgDriverCommand(retrieveIfacePtr(), kStartRxFilter);
}

ndk::ScopedAStatus StaIface::stopRxFilterInternal()
{
	return doZeroArgDriverCommand(retrieveIfacePtr(), kStopRxFilter);
}

ndk::ScopedAStatus StaIface::addRxFilterInternal(
	RxFilterType type)
{
	return doOneArgDriverCommand(
		retrieveIfacePtr(), kAddRxFilter,
		convertAidlRxFilterTypeToInternal(type));
}

ndk::ScopedAStatus StaIface::removeRxFilterInternal(
	RxFilterType type)
{
	return doOneArgDriverCommand(
		retrieveIfacePtr(), kRemoveRxFilter,
		convertAidlRxFilterTypeToInternal(type));
}

ndk::ScopedAStatus StaIface::setBtCoexistenceModeInternal(
	BtCoexistenceMode mode)
{
	return doOneArgDriverCommand(
		retrieveIfacePtr(), kSetBtCoexistenceMode,
		convertAidlBtCoexModeToInternal(mode));
}

ndk::ScopedAStatus StaIface::setBtCoexistenceScanModeEnabledInternal(bool enable)
{
	const char *cmd;
	if (enable) {
		cmd = kSetBtCoexistenceScanStart;
	} else {
		cmd = kSetBtCoexistenceScanStop;
	}
	return doZeroArgDriverCommand(retrieveIfacePtr(), cmd);
}

ndk::ScopedAStatus StaIface::setSuspendModeEnabledInternal(bool enable)
{
	const char *cmd;
	if (enable) {
		cmd = kSetSupendModeEnabled;
	} else {
		cmd = kSetSupendModeDisabled;
	}
	return doZeroArgDriverCommand(retrieveIfacePtr(), cmd);
}

ndk::ScopedAStatus StaIface::setCountryCodeInternal(
	const std::vector<uint8_t> &code)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	//2-Character alphanumeric country code
	if (code.size() != 2) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	ndk::ScopedAStatus status = doOneArgDriverCommand(
		wpa_s, kSetCountryCode,
		std::string(std::begin(code), std::end(code)));
	if (!status.isOk()) {
		return status;
	}
	struct p2p_data *p2p = wpa_s->global->p2p;
	if (p2p) {
		char country[3];
		country[0] = code[0];
		country[1] = code[1];
		country[2] = 0x04;
		p2p_set_country(p2p, country);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaIface::startWpsRegistrarInternal(
	const std::vector<uint8_t> &bssid, const std::string &pin)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (bssid.size() != ETH_ALEN) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	if (wpas_wps_start_reg(wpa_s, bssid.data(), pin.c_str(), nullptr)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaIface::startWpsPbcInternal(
	const std::vector<uint8_t> &bssid)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (bssid.size() != ETH_ALEN) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	const uint8_t *bssid_addr =
		is_zero_ether_addr(bssid.data()) ? nullptr : bssid.data();
	if (wpas_wps_start_pbc(wpa_s, bssid_addr, 0, 0)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaIface::startWpsPinKeypadInternal(const std::string &pin)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (wpas_wps_start_pin(
		wpa_s, nullptr, pin.c_str(), 0, DEV_PW_DEFAULT)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

std::pair<std::string, ndk::ScopedAStatus> StaIface::startWpsPinDisplayInternal(
	const std::vector<uint8_t> &bssid)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (bssid.size() != ETH_ALEN) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	const uint8_t *bssid_addr =
		is_zero_ether_addr(bssid.data()) ? nullptr : bssid.data();
	int pin =
		wpas_wps_start_pin(wpa_s, bssid_addr, nullptr, 0, DEV_PW_DEFAULT);
	if (pin < 0) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {misc_utils::convertWpsPinToString(pin),
		ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus StaIface::cancelWpsInternal()
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (wpas_wps_cancel(wpa_s)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaIface::setWpsDeviceNameInternal(const std::string &name)
{
	return iface_config_utils::setWpsDeviceName(retrieveIfacePtr(), name);
}

ndk::ScopedAStatus StaIface::setWpsDeviceTypeInternal(
	const std::vector<uint8_t> &type)
{
	std::array<uint8_t, 8> type_arr;
	std::copy_n(type.begin(), 8, type_arr.begin());
	return iface_config_utils::setWpsDeviceType(retrieveIfacePtr(), type_arr);
}

ndk::ScopedAStatus StaIface::setWpsManufacturerInternal(
	const std::string &manufacturer)
{
	return iface_config_utils::setWpsManufacturer(
		retrieveIfacePtr(), manufacturer);
}

ndk::ScopedAStatus StaIface::setWpsModelNameInternal(
	const std::string &model_name)
{
	return iface_config_utils::setWpsModelName(
		retrieveIfacePtr(), model_name);
}

ndk::ScopedAStatus StaIface::setWpsModelNumberInternal(
	const std::string &model_number)
{
	return iface_config_utils::setWpsModelNumber(
		retrieveIfacePtr(), model_number);
}

ndk::ScopedAStatus StaIface::setWpsSerialNumberInternal(
	const std::string &serial_number)
{
	return iface_config_utils::setWpsSerialNumber(
		retrieveIfacePtr(), serial_number);
}

ndk::ScopedAStatus StaIface::setWpsConfigMethodsInternal(WpsConfigMethods config_methods)
{
	return iface_config_utils::setWpsConfigMethods(
		retrieveIfacePtr(), static_cast<uint16_t>(config_methods));
}

ndk::ScopedAStatus StaIface::setExternalSimInternal(bool useExternalSim)
{
	return iface_config_utils::setExternalSim(
		retrieveIfacePtr(), useExternalSim);
}

std::pair<uint32_t, ndk::ScopedAStatus> StaIface::addExtRadioWorkInternal(
	const std::string &name, uint32_t freq_in_mhz, uint32_t timeout_in_sec)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	auto *ework = static_cast<struct wpa_external_work *>(
		os_zalloc(sizeof(struct wpa_external_work)));
	if (!ework) {
		return {UINT32_MAX, createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}

	std::string radio_work_name = kExtRadioWorkNamePrefix + name;
	os_strlcpy(ework->type, radio_work_name.c_str(), sizeof(ework->type));
	ework->timeout = timeout_in_sec;
	wpa_s->ext_work_id++;
	if (wpa_s->ext_work_id == 0) {
		wpa_s->ext_work_id++;
	}
	ework->id = wpa_s->ext_work_id;

	if (radio_add_work(
		wpa_s, freq_in_mhz, ework->type, 0, extRadioWorkStartCb,
		ework)) {
		os_free(ework);
		return {UINT32_MAX, createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {ework->id, ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus StaIface::removeExtRadioWorkInternal(uint32_t id)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	struct wpa_radio_work *work;
	dl_list_for_each(work, &wpa_s->radio->work, struct wpa_radio_work, list)
	{
		if (os_strncmp(
			work->type, kExtRadioWorkNamePrefix,
			sizeof(kExtRadioWorkNamePrefix)) != 0)
			continue;

		auto *ework =
			static_cast<struct wpa_external_work *>(work->ctx);
		if (ework->id != id)
			continue;

		wpa_dbg(
			wpa_s, MSG_DEBUG, "Completed external radio work %u (%s)",
			ework->id, ework->type);
		eloop_cancel_timeout(extRadioWorkTimeoutCb, work, NULL);
		endExtRadioWork(work);

		return ndk::ScopedAStatus::ok();
	}
	return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
}

ndk::ScopedAStatus StaIface::enableAutoReconnectInternal(bool enable)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	wpa_s->auto_reconnect_disabled = enable ? 0 : 1;
	return ndk::ScopedAStatus::ok();
}

std::pair<uint32_t, ndk::ScopedAStatus>
StaIface::addDppPeerUriInternal(const std::string& uri)
{
#ifdef CONFIG_DPP
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	int32_t id;

	id = wpas_dpp_qr_code(wpa_s, uri.c_str());

	if (id > 0) {
		return {id, ndk::ScopedAStatus::ok()};
	}
#endif
	return {-1, createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
}

ndk::ScopedAStatus StaIface::removeDppUriInternal(uint32_t bootstrap_id)
{
#ifdef CONFIG_DPP
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	std::string bootstrap_id_str;

	if (bootstrap_id == 0) {
		bootstrap_id_str = "*";
	}
	else {
		bootstrap_id_str = std::to_string(bootstrap_id);
	}

	if (dpp_bootstrap_remove(wpa_s->dpp, bootstrap_id_str.c_str()) >= 0) {
		return ndk::ScopedAStatus::ok();
	}
#endif
	return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
}

std::pair<std::vector<uint8_t>, ndk::ScopedAStatus>
StaIface::startDppConfiguratorInitiatorInternal(
		uint32_t peer_bootstrap_id, uint32_t own_bootstrap_id,
		const std::string& ssid, const std::string& password,
		const std::string& psk, DppNetRole net_role, DppAkm security_akm,
		const std::vector<uint8_t> &privEcKey)
{
#ifdef CONFIG_DPP
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	std::string cmd = "";
	std::string cmd2 = "";
	int32_t id;
	char key[1024];

	if (net_role != DppNetRole::AP &&
			net_role != DppNetRole::STA) {
		wpa_printf(MSG_ERROR,
			   "DPP: Error: Invalid network role specified: %d", net_role);
		return {std::vector<uint8_t>(),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}

	cmd += " peer=" + std::to_string(peer_bootstrap_id);
	cmd += (own_bootstrap_id > 0) ?
			" own=" + std::to_string(own_bootstrap_id) : "";

	/* Check for supported AKMs */
	if (security_akm != DppAkm::PSK && security_akm != DppAkm::SAE &&
			security_akm != DppAkm::PSK_SAE && security_akm != DppAkm::DPP) {
		wpa_printf(MSG_ERROR, "DPP: Error: invalid AKM specified: %d",
				security_akm);
		return {std::vector<uint8_t>(),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}

	/* SAE AKM requires SSID and password to be initialized */
	if ((security_akm == DppAkm::SAE ||
			security_akm == DppAkm::PSK_SAE) &&
			(ssid.empty() || password.empty())) {
		wpa_printf(MSG_ERROR, "DPP: Error: Password or SSID not specified");
		return {std::vector<uint8_t>(),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	} else if (security_akm == DppAkm::PSK ||
			security_akm == DppAkm::PSK_SAE) {
		/* PSK AKM requires SSID and password/psk to be initialized */
		if (ssid.empty()) {
			wpa_printf(MSG_ERROR, "DPP: Error: SSID not specified");
			return {std::vector<uint8_t>(),
				createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
		}
		if (password.empty() && psk.empty()) {
			wpa_printf(MSG_ERROR, "DPP: Error: Password or PSK not specified");
			return {std::vector<uint8_t>(),
				createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
		}
	}

	cmd += " role=configurator";
	cmd += (ssid.empty()) ? "" : " ssid=" + ssid;

	if (!psk.empty()) {
		cmd += " psk=" + psk;
	} else {
		cmd += (password.empty()) ? "" : " pass=" + password;
	}

	std::string role = "";
	if (net_role == DppNetRole::AP) {
		role = "ap-";
	}
	else {
		role = "sta-";
	}

	switch (security_akm) {
	case DppAkm::PSK:
		role += "psk";
		break;

	case DppAkm::SAE:
		role += "sae";
		break;

	case DppAkm::PSK_SAE:
		role += "psk-sae";
		break;

	case DppAkm::DPP:
		role += "dpp";
		break;

	default:
		wpa_printf(MSG_ERROR,
			   "DPP: Invalid or unsupported security AKM specified: %d", security_akm);
		return {std::vector<uint8_t>(),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}

	cmd += " conf=";
	cmd += role;

	if (net_role == DppNetRole::STA) {
		/* DPP R2 connection status request */
		cmd += " conn_status=1";
	}

	if (security_akm == DppAkm::DPP) {
		if (!privEcKey.empty()) {
			cmd2 += " key=" + std::string(privEcKey.begin(), privEcKey.end());
		}
		id = dpp_configurator_add(wpa_s->dpp, cmd2.c_str());
		if (id < 0 || (privEcKey.empty() &&
			       (dpp_configurator_get_key_id(wpa_s->dpp, id, key, sizeof(key)) < 0)))
		{
			wpa_printf(MSG_ERROR, "DPP configurator add failed. "
			           "Input key might be incorrect");
			return {std::vector<uint8_t>(),
				createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
		}

		cmd += " configurator=" + std::to_string(id);
	}

	wpa_printf(MSG_DEBUG,
		   "DPP initiator command: %s", cmd.c_str());

	if (wpas_dpp_auth_init(wpa_s, cmd.c_str()) == 0) {
		// Return key if input privEcKey was null/empty.
		if (security_akm == DppAkm::DPP && privEcKey.empty()) {
			std::string k(key);
			std::vector<uint8_t> vKey(k.begin(), k.end());
			return {vKey, ndk::ScopedAStatus::ok()};
		}
		return {std::vector<uint8_t>(), ndk::ScopedAStatus::ok()};
	}
#endif
	return {std::vector<uint8_t>(), createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
}

ndk::ScopedAStatus StaIface::startDppEnrolleeInitiatorInternal(
	uint32_t peer_bootstrap_id, uint32_t own_bootstrap_id) {
#ifdef CONFIG_DPP
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	std::string cmd = "";

	/* Report received configuration to AIDL and create an internal profile */
	wpa_s->conf->dpp_config_processing = 1;

	cmd += " peer=" + std::to_string(peer_bootstrap_id);
	cmd += (own_bootstrap_id > 0) ?
			" own=" + std::to_string(own_bootstrap_id) : "";

	cmd += " role=enrollee";

	wpa_printf(MSG_DEBUG,
		   "DPP initiator command: %s", cmd.c_str());

	if (wpas_dpp_auth_init(wpa_s, cmd.c_str()) == 0) {
		return ndk::ScopedAStatus::ok();
	}
#endif
	return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
}
ndk::ScopedAStatus StaIface::stopDppInitiatorInternal()
{
#ifdef CONFIG_DPP
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();

	wpas_dpp_stop(wpa_s);
	return ndk::ScopedAStatus::ok();
#else
	return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
#endif
}

std::pair<DppResponderBootstrapInfo, ndk::ScopedAStatus>
StaIface::generateDppBootstrapInfoForResponderInternal(
	const std::vector<uint8_t> &mac_address, 
	const std::string& device_info, DppCurve curve)
{
#ifdef CONFIG_DPP
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	std::string cmd = "type=qrcode";
	int32_t id;
	int32_t listen_channel = 0;
	DppResponderBootstrapInfo bootstrap_info;
	const char *uri;
	std::string listen_channel_str;
	std::string mac_addr_str;
	char buf[3] = {0};

	cmd += (device_info.empty()) ? "" : " info=" + device_info;

	listen_channel_str = getDppListenChannel(wpa_s, &listen_channel);
	if (listen_channel == 0) {
		wpa_printf(MSG_ERROR, "StaIface: Failed to derive DPP listen channel");
		return {bootstrap_info, createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	cmd += " chan=" + listen_channel_str;

	if (mac_address.size() != ETH_ALEN) {
		return {bootstrap_info, createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	cmd += " mac=";
	for (int i = 0;i < 6;i++) {
		snprintf(buf, sizeof(buf), "%02x", mac_address[i]);
		mac_addr_str.append(buf);
	}
	cmd += mac_addr_str;

	cmd += " curve=" + convertCurveTypeToName(curve);

	id = dpp_bootstrap_gen(wpa_s->dpp, cmd.c_str());
	wpa_printf(MSG_DEBUG,
		   "DPP generate bootstrap QR code command: %s id: %d", cmd.c_str(), id);
	if (id > 0) {
		uri = dpp_bootstrap_get_uri(wpa_s->dpp, id);
		if (uri) {
			wpa_printf(MSG_DEBUG, "DPP Bootstrap info: id: %d "
				   "listen_channel: %d uri: %s", id, listen_channel, uri);
			bootstrap_info.bootstrapId = id;
			bootstrap_info.listenChannel = listen_channel;
			bootstrap_info.uri = uri;
			return {bootstrap_info, ndk::ScopedAStatus::ok()};
		}
	}
	return {bootstrap_info, createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
#else
	return {bootstrap_info, createStatus(SupplicantStatusCode::FAILURE_UNSUPPORTED)};
#endif
}

ndk::ScopedAStatus StaIface::startDppEnrolleeResponderInternal(uint32_t listen_channel)
{
#ifdef CONFIG_DPP
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	std::string cmd = "";
	uint32_t freq = (listen_channel <= 14 ? 2407 : 5000) + listen_channel * 5;

	/* Report received configuration to AIDL and create an internal profile */
	wpa_s->conf->dpp_config_processing = 1;

	cmd += std::to_string(freq);
	cmd += " role=enrollee netrole=sta";

	wpa_printf(MSG_DEBUG,
		   "DPP Enrollee Responder command: %s", cmd.c_str());

	if (wpas_dpp_listen(wpa_s, cmd.c_str()) == 0) {
		return ndk::ScopedAStatus::ok();
	}
	return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
#else
	return createStatus(SupplicantStatusCode::FAILURE_UNSUPPORTED);
#endif
}

ndk::ScopedAStatus StaIface::stopDppResponderInternal(uint32_t own_bootstrap_id)
{
#ifdef CONFIG_DPP
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	std::string bootstrap_id_str;

	if (own_bootstrap_id == 0) {
		bootstrap_id_str = "*";
	}
	else {
		bootstrap_id_str = std::to_string(own_bootstrap_id);
	}

	wpa_printf(MSG_DEBUG, "DPP Stop DPP Responder id: %d ", own_bootstrap_id);
	wpas_dpp_stop(wpa_s);
	wpas_dpp_listen_stop(wpa_s);

	if (dpp_bootstrap_remove(wpa_s->dpp, bootstrap_id_str.c_str()) < 0) {
		wpa_printf(MSG_ERROR, "StaIface: dpp_bootstrap_remove failed");
	}

	return ndk::ScopedAStatus::ok();
#else
	return createStatus(SupplicantStatusCode::FAILURE_UNSUPPORTED);
#endif
}

ndk::ScopedAStatus StaIface::generateSelfDppConfigurationInternal(const std::string& ssid,
		const std::vector<uint8_t> &privEcKey)
{
#ifdef CONFIG_DPP
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	std::string cmd = "";
	char *ssid_hex_str;
	int len;
	int32_t id;

	if (ssid.empty() || privEcKey.empty()) {
		wpa_printf(MSG_ERROR, "DPP generate self configuration failed. ssid/key empty");
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}

	cmd += " key=" + std::string(privEcKey.begin(), privEcKey.end());

	id = dpp_configurator_add(wpa_s->dpp, cmd.c_str());
	if (id < 0) {
		wpa_printf(MSG_ERROR, "DPP configurator add failed. Input key might be incorrect");
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}

	cmd = " conf=sta-dpp";
	cmd += " configurator=" + std::to_string(id);

	ssid_hex_str = (char *) os_zalloc(ssid.size() * 2 + 1);
	if (!ssid_hex_str) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}

	wpa_snprintf_hex(ssid_hex_str, ssid.size() * 2 + 1, (u8*)ssid.data(), ssid.size());
	cmd += " ssid=" + std::string(ssid_hex_str);

	/* Report received configuration to AIDL and create an internal profile */
	wpa_s->conf->dpp_config_processing = 1;

	if (wpas_dpp_configurator_sign(wpa_s, cmd.c_str()) == 0) {
		os_free(ssid_hex_str);
		return ndk::ScopedAStatus::ok();
	}

	os_free(ssid_hex_str);
	return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
#else
	return createStatus(SupplicantStatusCode::FAILURE_UNSUPPORTED);
#endif
}

std::pair<ConnectionCapabilities, ndk::ScopedAStatus>
StaIface::getConnectionCapabilitiesInternal()
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	ConnectionCapabilities capa;

	if (wpa_s->connection_set) {
		capa.legacyMode = LegacyMode::UNKNOWN;
		if (wpa_s->connection_he) {
			capa.technology = WifiTechnology::HE;
		} else if (wpa_s->connection_vht) {
			capa.technology = WifiTechnology::VHT;
		} else if (wpa_s->connection_ht) {
			capa.technology = WifiTechnology::HT;
		} else {
			capa.technology = WifiTechnology::LEGACY;
			if (wpas_freq_to_band(wpa_s->assoc_freq) == BAND_2_4_GHZ) {
				capa.legacyMode = (wpa_s->connection_11b_only) ? LegacyMode::B_MODE
						: LegacyMode::G_MODE; 
			} else {
				capa.legacyMode = LegacyMode::A_MODE;
			}
		}
		switch (wpa_s->connection_channel_bandwidth) {
		case CHAN_WIDTH_20:
			capa.channelBandwidth = WifiChannelWidthInMhz::WIDTH_20;
			break;
		case CHAN_WIDTH_40:
			capa.channelBandwidth = WifiChannelWidthInMhz::WIDTH_40;
			break;
		case CHAN_WIDTH_80:
			capa.channelBandwidth = WifiChannelWidthInMhz::WIDTH_80;
			break;
		case CHAN_WIDTH_160:
			capa.channelBandwidth = WifiChannelWidthInMhz::WIDTH_160;
			break;
		case CHAN_WIDTH_80P80:
			capa.channelBandwidth = WifiChannelWidthInMhz::WIDTH_80P80;
			break;
		default:
			capa.channelBandwidth = WifiChannelWidthInMhz::WIDTH_20;
			break;
		}
		capa.maxNumberRxSpatialStreams = wpa_s->connection_max_nss_rx;
		capa.maxNumberTxSpatialStreams = wpa_s->connection_max_nss_tx;
	} else {
		capa.technology = WifiTechnology::UNKNOWN;
		capa.channelBandwidth = WifiChannelWidthInMhz::WIDTH_20;
		capa.maxNumberTxSpatialStreams = 1;
		capa.maxNumberRxSpatialStreams = 1;
		capa.legacyMode = LegacyMode::UNKNOWN;
	}
	return {capa, ndk::ScopedAStatus::ok()};
}

std::pair<WpaDriverCapabilitiesMask, ndk::ScopedAStatus>
StaIface::getWpaDriverCapabilitiesInternal()
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	uint32_t mask = 0;

#ifdef CONFIG_MBO
	/* MBO has no capability flags. It's mainly legacy 802.11v BSS
	 * transition + Cellular steering. 11v is a default feature in
	 * supplicant. And cellular steering is handled in framework.
	 */
	mask |= static_cast<uint32_t>(WpaDriverCapabilitiesMask::MBO);
	if (wpa_s->enable_oce & OCE_STA) {
		mask |= static_cast<uint32_t>(WpaDriverCapabilitiesMask::OCE);
	}
#endif
#ifdef CONFIG_SAE_PK
	mask |= static_cast<uint32_t>(WpaDriverCapabilitiesMask::SAE_PK);
#endif
	mask |= static_cast<uint32_t>(WpaDriverCapabilitiesMask::WFD_R2);

	mask |= static_cast<uint32_t>(WpaDriverCapabilitiesMask::TRUST_ON_FIRST_USE);

	wpa_printf(MSG_DEBUG, "Driver capability mask: 0x%x", mask);

	return {static_cast<WpaDriverCapabilitiesMask>(mask),
		ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus StaIface::setMboCellularDataStatusInternal(bool available)
{
#ifdef CONFIG_MBO
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	enum mbo_cellular_capa mbo_cell_capa;

	if (available) {
		mbo_cell_capa = MBO_CELL_CAPA_AVAILABLE;
	} else {
		mbo_cell_capa = MBO_CELL_CAPA_NOT_AVAILABLE;
	}

#ifdef ENABLE_PRIV_CMD_UPDATE_MBO_CELL_STATUS
	char mbo_cmd[32];
	char buf[32];

	os_snprintf(mbo_cmd, sizeof(mbo_cmd), "%s %d", "MBO CELL_DATA_CAP", mbo_cell_capa);
	if (wpa_drv_driver_cmd(wpa_s, mbo_cmd, buf, sizeof(buf)) < 0) {
		wpa_printf(MSG_ERROR, "MBO CELL_DATA_CAP cmd failed CAP:%d", mbo_cell_capa);
	}
#else
	wpas_mbo_update_cell_capa(wpa_s, mbo_cell_capa);
#endif

	return ndk::ScopedAStatus::ok();
#else
	return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
#endif
}

std::pair<KeyMgmtMask, ndk::ScopedAStatus>
StaIface::getKeyMgmtCapabilitiesInternal()
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	struct wpa_driver_capa capa;

	/* Get capabilities from driver and populate the key management mask */
	if (wpa_drv_get_capa(wpa_s, &capa) < 0) {
		return {static_cast<KeyMgmtMask>(0),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}

	return {convertWpaKeyMgmtCapabilitiesToAidl(wpa_s, &capa),
		ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus StaIface::setQosPolicyFeatureEnabledInternal(bool enable)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	wpa_s->enable_dscp_policy_capa = enable ? 1 : 0;
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaIface::sendQosPolicyResponseInternal(
	int32_t qos_policy_request_id, bool more_policies,
	const std::vector<QosPolicyStatus>& qos_policy_status_list)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	struct dscp_resp_data resp_data;
	int num_policies = qos_policy_status_list.size();

	memset(&resp_data, 0, sizeof(resp_data));

	resp_data.more = more_policies ? 1 : 0;
	resp_data.policy = (struct dscp_policy_status *) malloc(
		sizeof(struct dscp_policy_status) * num_policies);
	if (num_policies && !resp_data.policy){
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}

	resp_data.solicited = true;
	wpa_s->dscp_req_dialog_token = qos_policy_request_id;

	for (int i = 0; i < num_policies; i++) {
		resp_data.policy[i].id = qos_policy_status_list.at(i).policyId;
		resp_data.policy[i].status =
			static_cast<uint8_t>(qos_policy_status_list.at(i).status);
	}
	resp_data.num_policies = num_policies;

	if (wpas_send_dscp_response(wpa_s, &resp_data)) {
		free(resp_data.policy);
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}

	free(resp_data.policy);
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaIface::removeAllQosPoliciesInternal()
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	struct dscp_resp_data resp_data;

	memset(&resp_data, 0, sizeof(resp_data));
	resp_data.reset = true;
	resp_data.solicited = false;
	wpa_s->dscp_req_dialog_token = 0;

	if (wpas_send_dscp_response(wpa_s, &resp_data)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

std::pair<MloLinksInfo, ndk::ScopedAStatus> StaIface::getConnectionMloLinksInfoInternal()
{
	MloLinksInfo linksInfo;
	return {linksInfo, ndk::ScopedAStatus::ok()};
}

/**
 * Retrieve the underlying |wpa_supplicant| struct
 * pointer for this iface.
 * If the underlying iface is removed, then all RPC method calls on this object
 * will return failure.
 */
wpa_supplicant *StaIface::retrieveIfacePtr()
{
	return wpa_supplicant_get_iface(wpa_global_, ifname_.c_str());
}
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
