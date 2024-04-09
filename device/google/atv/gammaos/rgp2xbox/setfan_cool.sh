#!/system/bin/sh

echo 1 > /sys/devices/platform/singleadc-joypad/fan_power
echo 1 > /sys/devices/platform/pwm-fan/hwmon/hwmon0/pwm_en
echo 204 > /sys/devices/platform/pwm-fan/hwmon/hwmon0/pwm1