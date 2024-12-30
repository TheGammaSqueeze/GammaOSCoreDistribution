#!/system/bin/sh

echo "ondemand" > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor
echo "408000" > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq
echo "1800000" > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
