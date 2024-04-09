#!/system/bin/sh

echo "powersave" > /sys/class/devfreq/fde60000.gpu/governor
echo "200000000" > /sys/class/devfreq/fde60000.gpu/min_freq
echo "200000000" > /sys/class/devfreq/fde60000.gpu/max_freq
echo "schedutil" > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor
echo "408000" > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq
echo "1104000" > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
echo "dmc_ondemand" > /sys/class/devfreq/dmc/governor
echo "324000000" > /sys/class/devfreq/dmc/min_freq
echo "780000000" > /sys/class/devfreq/dmc/max_freq

echo "255" > /sys/class/leds/battery_full/brightness
echo "255" > /sys/class/leds/battery_charging/brightness
echo "255" > /sys/class/leds/low_power/brightness

sh -c '( sleep 5s; echo "0" > /sys/class/leds/battery_full/brightness; echo "0" > /sys/class/leds/battery_charging/brightness; echo "0" > /sys/class/leds/low_power/brightness ) & echo "Done"' &
