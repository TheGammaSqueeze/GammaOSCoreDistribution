#!/system/bin/sh

value=1; printf "%b" "$(printf '\\x%02x\\x%02x\\x%02x\\x%02x' $((value & 0xFF)) $((value >> 8 & 0xFF)) $((value >> 16 & 0xFF)) $((value >> 24 & 0xFF)))" > /data/rgp2xbox/FAN_CONTROL
value=1; printf "%b" "$(printf '\\x%02x\\x%02x\\x%02x\\x%02x' $((value & 0xFF)) $((value >> 8 & 0xFF)) $((value >> 16 & 0xFF)) $((value >> 24 & 0xFF)))" > /data/rgp2xbox/FAN_CONTROL_ISUPDATED