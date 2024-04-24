#!/system/bin/sh

echo "performance" > /sys/class/devfreq/fde60000.gpu/governor
echo "800000000" > /sys/class/devfreq/fde60000.gpu/min_freq
echo "800000000" > /sys/class/devfreq/fde60000.gpu/max_freq
echo "performance" > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor
echo "1992000" > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq
echo "1992000" > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
echo "performance" > /sys/class/devfreq/dmc/governor
echo "1056000000" > /sys/class/devfreq/dmc/min_freq
echo "1056000000" > /sys/class/devfreq/dmc/max_freq


su -lp 2000 -c "am start -a android.intent.action.MAIN -e toasttext 'Max Performance Mode Activated' -n bellavita.toast/.MainActivity"

#echo "0" > /sys/class/leds/battery_full/brightness
#echo "0" > /sys/class/leds/battery_charging/brightness
#echo "255" > /sys/class/leds/low_power/brightness

#sh -c '( sleep 5s; echo "0" > /sys/class/leds/battery_full/brightness; echo "0" > /sys/class/leds/battery_charging/brightness; echo "0" > /sys/class/leds/low_power/brightness ) & echo "Done"' &
