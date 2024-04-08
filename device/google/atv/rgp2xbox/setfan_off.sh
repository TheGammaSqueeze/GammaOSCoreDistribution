#!/system/bin/sh

echo 0 > /sys/devices/platform/singleadc-joypad/fan_power
echo 0 > /sys/devices/platform/pwm-fan/hwmon/hwmon0/pwm_en
echo 0   > /sys/devices/platform/pwm-fan/hwmon/hwmon0/pwm1