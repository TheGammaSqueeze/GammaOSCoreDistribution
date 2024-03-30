//! reimport of generated packets (to go away once rust_genrule exists)

#![allow(clippy::all)]
#![allow(unused)]
#![allow(missing_docs)]

use std::convert::TryFrom;
use std::fmt;

pub mod test_packets {

    // Custom boolean type
    #[derive(Clone, Copy, Eq, PartialEq, Hash, Ord, PartialOrd, Debug)]
    pub struct Boolean {
        pub value: u8,
    }

    impl fmt::Display for Boolean {
        fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
            write!(f, "{:02x}", self.value)
        }
    }

    #[derive(Debug, Clone)]
    pub struct InvalidBooleanError;

    impl TryFrom<&[u8]> for Boolean {
        type Error = InvalidBooleanError;

        fn try_from(slice: &[u8]) -> std::result::Result<Self, Self::Error> {
            if slice.len() != 1 || slice[0] > 1 {
                Err(InvalidBooleanError)
            } else {
                Ok(Boolean { value: slice[0] })
            }
        }
    }

    impl From<Boolean> for [u8; 1] {
        fn from(b: Boolean) -> [u8; 1] {
            [b.value]
        }
    }

    include!(concat!(env!("OUT_DIR"), "/rust_test_packets.rs"));
}

#[cfg(test)]
pub mod test {
    use crate::test_packets::*;

    #[test]
    fn test_invalid_enum_field_value() {
        // 0x0 is not a recognized Enum value.
        let input = [0x0];
        let res = TestEnumPacket::parse(&input);
        assert!(res.is_err());
    }

    #[test]
    fn test_invalid_custom_field_value() {
        // 0x2 is not a recognized Boolean value.
        let input = [0x2];
        let res = TestCustomFieldPacket::parse(&input);
        assert!(res.is_err());
    }

    #[test]
    fn test_invalid_array_size() {
        // Size 4, have 2.
        let input = [0x4, 0x0, 0x0];
        let res = TestArraySizePacket::parse(&input);
        assert!(res.is_err());
    }

    #[test]
    fn test_invalid_array_count() {
        // Count 2, have 1.
        let input = [0x2, 0x0, 0x0];
        let res = TestArrayCountPacket::parse(&input);
        assert!(res.is_err());
    }

    #[test]
    fn test_invalid_payload_size() {
        // Size 2, have 1.
        let input = [0x2, 0x0];
        let res = TestPayloadSizePacket::parse(&input);
        assert!(res.is_err());
    }

    #[test]
    fn test_invalid_body_size() {
        // Size 2, have 1.
        // Body does not have a concrete representation,
        // the size and payload are both discarded.
        let input = [0x2, 0x0];
        let res = TestBodySizePacket::parse(&input);
        assert!(res.is_ok());
    }
}
