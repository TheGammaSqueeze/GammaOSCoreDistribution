//! Collection of Profile UUIDs and helpers to use them.

use std::collections::{HashMap, HashSet};

use bt_topshim::btif::Uuid128Bit;

// List of profile uuids
pub const A2DP_SINK: &str = "0000110B-0000-1000-8000-00805F9B34FB";
pub const A2DP_SOURCE: &str = "0000110A-0000-1000-8000-00805F9B34FB";
pub const ADV_AUDIO_DIST: &str = "0000110D-0000-1000-8000-00805F9B34FB";
pub const HSP: &str = "00001108-0000-1000-8000-00805F9B34FB";
pub const HSP_AG: &str = "00001112-0000-1000-8000-00805F9B34FB";
pub const HFP: &str = "0000111E-0000-1000-8000-00805F9B34FB";
pub const HFP_AG: &str = "0000111F-0000-1000-8000-00805F9B34FB";
pub const AVRCP_CONTROLLER: &str = "0000110E-0000-1000-8000-00805F9B34FB";
pub const AVRCP_TARGET: &str = "0000110C-0000-1000-8000-00805F9B34FB";
pub const OBEX_OBJECT_PUSH: &str = "00001105-0000-1000-8000-00805f9b34fb";
pub const HID: &str = "00001124-0000-1000-8000-00805f9b34fb";
pub const HOGP: &str = "00001812-0000-1000-8000-00805f9b34fb";
pub const PANU: &str = "00001115-0000-1000-8000-00805F9B34FB";
pub const NAP: &str = "00001116-0000-1000-8000-00805F9B34FB";
pub const BNEP: &str = "0000000f-0000-1000-8000-00805F9B34FB";
pub const PBAP_PCE: &str = "0000112e-0000-1000-8000-00805F9B34FB";
pub const PBAP_PSE: &str = "0000112f-0000-1000-8000-00805F9B34FB";
pub const MAP: &str = "00001134-0000-1000-8000-00805F9B34FB";
pub const MNS: &str = "00001133-0000-1000-8000-00805F9B34FB";
pub const MAS: &str = "00001132-0000-1000-8000-00805F9B34FB";
pub const SAP: &str = "0000112D-0000-1000-8000-00805F9B34FB";
pub const HEARING_AID: &str = "0000FDF0-0000-1000-8000-00805f9b34fb";
pub const LE_AUDIO: &str = "EEEEEEEE-EEEE-EEEE-EEEE-EEEEEEEEEEEE";
pub const DIP: &str = "00001200-0000-1000-8000-00805F9B34FB";
pub const VOLUME_CONTROL: &str = "00001844-0000-1000-8000-00805F9B34FB";
pub const GENERIC_MEDIA_CONTROL: &str = "00001849-0000-1000-8000-00805F9B34FB";
pub const MEDIA_CONTROL: &str = "00001848-0000-1000-8000-00805F9B34FB";
pub const COORDINATED_SET: &str = "00001846-0000-1000-8000-00805F9B34FB";
pub const BASE_UUID: &str = "00000000-0000-1000-8000-00805F9B34FB";

/// List of profiles that with known uuids.
#[derive(Clone, Debug, Hash, PartialEq, PartialOrd, Eq, FromPrimitive, ToPrimitive, Copy)]
#[repr(u32)]
pub enum Profile {
    A2dpSink,
    A2dpSource,
    AdvAudioDist,
    Hsp,
    HspAg,
    Hfp,
    HfpAg,
    AvrcpController,
    AvrcpTarget,
    ObexObjectPush,
    Hid,
    Hogp,
    Panu,
    Nap,
    Bnep,
    PbapPce,
    PbapPse,
    Map,
    Mns,
    Mas,
    Sap,
    HearingAid,
    LeAudio,
    Dip,
    VolumeControl,
    GenericMediaControl,
    MediaControl,
    CoordinatedSet,
}

pub struct UuidHelper {
    /// A list of enabled profiles on the system. These may be modified by policy.
    pub enabled_profiles: HashSet<Profile>,

    /// Map a UUID to a known profile
    pub profiles: HashMap<Uuid128Bit, Profile>,
}

impl UuidHelper {
    pub fn new() -> Self {
        let enabled_profiles: HashSet<Profile> = [
            Profile::A2dpSink,
            Profile::A2dpSource,
            Profile::Hsp,
            Profile::Hfp,
            Profile::Hid,
            Profile::Hogp,
            Profile::Panu,
            Profile::PbapPce,
            Profile::Map,
            Profile::HearingAid,
            Profile::VolumeControl,
            Profile::CoordinatedSet,
        ]
        .iter()
        .cloned()
        .collect();

        let profiles: HashMap<Uuid128Bit, Profile> = [
            (UuidHelper::from_string(A2DP_SINK).unwrap(), Profile::A2dpSink),
            (UuidHelper::from_string(A2DP_SOURCE).unwrap(), Profile::A2dpSource),
            (UuidHelper::from_string(ADV_AUDIO_DIST).unwrap(), Profile::AdvAudioDist),
            (UuidHelper::from_string(HSP).unwrap(), Profile::Hsp),
            (UuidHelper::from_string(HSP_AG).unwrap(), Profile::HspAg),
            (UuidHelper::from_string(HFP).unwrap(), Profile::Hfp),
            (UuidHelper::from_string(HFP_AG).unwrap(), Profile::HfpAg),
            (UuidHelper::from_string(AVRCP_CONTROLLER).unwrap(), Profile::AvrcpController),
            (UuidHelper::from_string(AVRCP_TARGET).unwrap(), Profile::AvrcpTarget),
            (UuidHelper::from_string(OBEX_OBJECT_PUSH).unwrap(), Profile::ObexObjectPush),
            (UuidHelper::from_string(HID).unwrap(), Profile::Hid),
            (UuidHelper::from_string(HOGP).unwrap(), Profile::Hogp),
            (UuidHelper::from_string(PANU).unwrap(), Profile::Panu),
            (UuidHelper::from_string(NAP).unwrap(), Profile::Nap),
            (UuidHelper::from_string(BNEP).unwrap(), Profile::Bnep),
            (UuidHelper::from_string(PBAP_PCE).unwrap(), Profile::PbapPce),
            (UuidHelper::from_string(PBAP_PSE).unwrap(), Profile::PbapPse),
            (UuidHelper::from_string(MAP).unwrap(), Profile::Map),
            (UuidHelper::from_string(MNS).unwrap(), Profile::Mns),
            (UuidHelper::from_string(MAS).unwrap(), Profile::Mas),
            (UuidHelper::from_string(SAP).unwrap(), Profile::Sap),
            (UuidHelper::from_string(HEARING_AID).unwrap(), Profile::HearingAid),
            (UuidHelper::from_string(LE_AUDIO).unwrap(), Profile::LeAudio),
            (UuidHelper::from_string(DIP).unwrap(), Profile::Dip),
            (UuidHelper::from_string(VOLUME_CONTROL).unwrap(), Profile::VolumeControl),
            (UuidHelper::from_string(GENERIC_MEDIA_CONTROL).unwrap(), Profile::GenericMediaControl),
            (UuidHelper::from_string(MEDIA_CONTROL).unwrap(), Profile::MediaControl),
            (UuidHelper::from_string(COORDINATED_SET).unwrap(), Profile::CoordinatedSet),
        ]
        .iter()
        .cloned()
        .collect();

        UuidHelper { enabled_profiles, profiles }
    }

    /// Checks whether a UUID corresponds to a currently enabled profile.
    pub fn is_profile_enabled(&self, profile: &Profile) -> bool {
        self.enabled_profiles.contains(profile)
    }

    /// Converts a UUID to a known profile enum.
    pub fn is_known_profile(&self, uuid: &Uuid128Bit) -> Option<&Profile> {
        self.profiles.get(uuid)
    }

    pub fn get_enabled_profiles(&self) -> HashSet<Profile> {
        self.enabled_profiles.clone()
    }

    /// Converts a UUID byte array into a formatted string.
    pub fn to_string(uuid: &Uuid128Bit) -> String {
        return String::from(format!("{:02x}{:02x}{:02x}{:02x}-{:02x}{:02x}-{:02x}{:02x}-{:02x}{:02x}-{:02X}{:02X}{:02X}{:02X}{:02X}{:02X}",
            uuid[0], uuid[1], uuid[2], uuid[3],
            uuid[4], uuid[5],
            uuid[6], uuid[7],
            uuid[8], uuid[9],
            uuid[10], uuid[11], uuid[12], uuid[13], uuid[14], uuid[15]));
    }

    /// Converts a well-formatted UUID string to a UUID byte array.
    /// The UUID string should be in the format:
    /// 12345678-1234-1234-1234-1234567890
    pub fn from_string<S: Into<String>>(raw: S) -> Option<Uuid128Bit> {
        let raw: String = raw.into();

        // Make sure input is valid length and formatting
        let s = raw.split('-').collect::<Vec<&str>>();
        if s.len() != 5 || raw.len() != 36 {
            return None;
        }

        let mut uuid: Uuid128Bit = [0; 16];
        let mut idx = 0;
        for section in s.iter() {
            for i in (0..section.len()).step_by(2) {
                uuid[idx] = match u8::from_str_radix(&section[i..i + 2], 16) {
                    Ok(res) => res,
                    Err(_) => {
                        return None;
                    }
                };
                idx = idx + 1;
            }
        }

        Some(uuid)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_uuidhelper() {
        let uuidhelper = UuidHelper::new();
        for (uuid, _) in uuidhelper.profiles.iter() {
            let converted = UuidHelper::from_string(UuidHelper::to_string(&uuid));
            assert_eq!(converted.is_some(), true);
            converted.and_then::<Uuid128Bit, _>(|uu: Uuid128Bit| {
                assert_eq!(&uu, uuid);
                None
            });
        }
    }
}
