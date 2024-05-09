#!/system/bin/sh

echo "Starting configuration of the GammaOS system..."
sleep 1

# Check if the device is Powkiddy RGB30v2 and switch to new boot image for RGB30 v2
isrgb30=$(cat /proc/device-tree/model)
if [[ "$isrgb30" == *"Powkiddy RGB30"* ]]; then
    if [ ! -f "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor" ]; then
    	echo "Device is Powkiddy RGB30v2. Preparing to update boot image."
        echo "Scaling governor not found. Setting up for new boot image."
        mkdir -p /data/tmpsetup
        unzip /system/etc/rgb30_v2_boot.zip -d /data/tmpsetup/
        dd if=/data/tmpsetup/boot.img of=/dev/block/by-name/boot bs=512
        rm -f /data/tmpsetup/boot.img
        echo "Boot image updated. Rebooting device."
		sleep 5
        reboot
    fi
fi

setprop ctl.stop "tee-supplicant"

echo "Set HDMI defaults"
setprop persist.vendor.resolution.HDMI-A-0 1920x1200@60
setprop persist.vendor.framebuffer.hdmi 1280x720

echo "Setting up swap space."
[ ! -f /cache/swap ] && dd if=/dev/zero of=/cache/swap bs=1M count=300 && mkswap /cache/swap
swapon /cache/swap

echo "Maximizing screen brightness."
settings put system screen_brightness 255

echo "Enabling developer settings and configuring system behaviors."
settings put global development_settings_enabled 1
settings put global stay_on_while_plugged_in 0
settings put global mobile_data_always_on 0

echo "Installing applications."
mkdir -p /data/tmpsetup

echo "Installing Projectivy Launcher."
pm install /system/etc/projectivylauncher_4.36.apk
launcheruser=$(stat -c "%U" /data/data/com.spocky.projengmenu)
launchergroup=$(stat -c "%G" /data/data/com.spocky.projengmenu)
tar -xvf /system/etc/com.spocky.projengmenu.data.tar.gz -C /
chown -R $launcheruser:$launchergroup /data/data/com.spocky.projengmenu

echo "Installing RetroArch application."
pm install /system/etc/RetroArch_aarch64.apk

echo "Granting permissions to applications."
appops set --uid org.plain.launcher MANAGE_EXTERNAL_STORAGE allow
pm grant com.spocky.projengmenu android.permission.READ_TV_LISTINGS
cmd notification allow_listener com.spocky.projengmenu/.services.notification.NotificationListener

echo "Extracting and setting up ROMs."
tar -xvf /system/etc/roms.tar.gz -C /

echo "Extracting and setting up RetroArch cores and configuration."
sleep 2
tar -xvf /system/etc/retroarch64sdcard.tar.gz -C /

launcheruser=$(stat -c "%U" /data/data/com.retroarch.aarch64)
launchergroup=$(stat -c "%G" /data/data/com.retroarch.aarch64)
chown -R $launcheruser:media_rw /sdcard/RetroArch

tar -xvf /system/etc/retroarch64sdcard2.tar.gz -C /
chown -R $launcheruser:ext_data_rw /sdcard/Android/data/com.retroarch.aarch64

# Additional setup for Anbernic RG403H device
isarc=$(cat /proc/device-tree/model)
if [[ "$isarc" == *"Anbernic RG403H"* ]]; then
    echo "Setting up for Anbernic RG ARC."
    tar -xvf /system/etc/retroarch64sdcard1-arc.tar.gz -C /
    chown -R $launcheruser:media_rw /sdcard/RetroArch
fi

echo "Granting read/write permissions to RetroArch."
pm grant com.retroarch.aarch64 android.permission.WRITE_EXTERNAL_STORAGE
pm grant com.retroarch.aarch64 android.permission.READ_EXTERNAL_STORAGE

echo "Cleaning up and finalizing setup."
tar -xvf /system/etc/retroarch64.tar.gz -C /
chown -R $launcheruser:$launchergroup /data/data/com.retroarch.aarch64
rm -rf /data/tmpsetup/*

mkdir -p /data/setupcompleted
sleep 4 && settings put system screen_off_timeout 240000 &

echo "All settings have been applied successfully."
