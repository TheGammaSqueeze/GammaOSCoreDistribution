#!/system/bin/sh

# Need to switch to new boot image for RGB30 v2
isrgb30=$(cat /proc/device-tree/model)
if [[ "$isrgb30" == *"Powkiddy RGB30"* ]]; then
    if [ ! -f "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor" ]; then
        mkdir /data/tmpsetup
        unzip /system/etc/rgb30_v2_boot.zip -d /data/tmpsetup/
        dd if=/data/tmpsetup/boot.img of=/dev/block/by-name/boot bs=512
        rm /data/tmpsetup/boot.img
        reboot
    fi
fi

if [ ! -d /data/setupcompleted ] && [ -z $(getprop persist.sys.device_provisioned) ]; then
    wm density 137
    settings put system screen_off_timeout 1800000
    setenforce 0
    setprop service.bootanim.exit 0
    setprop service.bootanim.progress 0
    start bootanim

    setprop ctl.stop "tee-supplicant"
    [ ! -f /cache/swap ] && dd if=/dev/zero of=/cache/swap bs=1M count=300 && mkswap /cache/swap
    swapon -p -5 /cache/swap
    swapon /cache/swap
    settings put system screen_brightness 255

    # Retrieve the value of the Android property
    prop_value=$(getprop vendor.hwc.device.display-0)

    # Check if the property value contains "HDMI", case insensitive
    if echo "$prop_value" | grep -iq "HDMI"; then
        # If HDMI is found, execute the commands in the background
        start hdmiaudioworkaround
        sleep 1
        input keyevent 26 && sleep 5 && input keyevent 26
    fi

    settings put global development_settings_enabled 1
    settings put global stay_on_while_plugged_in 0
    settings put global mobile_data_always_on 0

    mkdir /data/tmpsetup

    pm install /system/etc/magisk.apk
    #pm install /system/etc/PlainLauncher.apk
    pm install /system/etc/projectivylauncher_4.36.apk
    pm install /system/etc/RetroArch_aarch64.apk

    appops set --uid org.plain.launcher MANAGE_EXTERNAL_STORAGE allow

    tar -xvf /system/etc/roms.tar.gz -C /

    tar -xvf /system/etc/retroarch64sdcard.tar.gz -C /
    launcheruser=$(stat -c "%U" /data/data/com.retroarch.aarch64)
    chown -R $launcheruser:media_rw /sdcard/RetroArch

    tar -xvf /system/etc/retroarch64sdcard2.tar.gz -C /
    launcheruser=$(stat -c "%U" /data/data/com.retroarch.aarch64)
    chown -R $launcheruser:ext_data_rw /sdcard/Android/data/com.retroarch.aarch64

    isarc=$(cat /proc/device-tree/model)
    if [[ "$isarc" == *"Anbernic RG403H"* ]]; then
        tar -xvf /system/etc/retroarch64sdcard1-arc.tar.gz -C /
        launcheruser=$(stat -c "%U" /data/data/com.retroarch.aarch64)
        chown -R $launcheruser:media_rw /sdcard/RetroArch
    fi

    pm grant com.retroarch.aarch64 android.permission.WRITE_EXTERNAL_STORAGE
    pm grant com.retroarch.aarch64 android.permission.READ_EXTERNAL_STORAGE

    launcheruser=$(stat -c "%U" /data/data/com.retroarch.aarch64)
    launchergroup=$(stat -c "%G" /data/data/com.retroarch.aarch64)
    tar -xvf /system/etc/retroarch64.tar.gz -C /
    chown -R $launcheruser:$launchergroup /data/data/com.retroarch.aarch64
    rm -rf /data/tmpsetup/*

    setprop service.bootanim.exit 1
    setprop service.bootanim.progress 1

    mkdir /data/setupcompleted
    sleep 60
    settings put system screen_off_timeout 120000
    setprop ctl.stop "tee-supplicant"
else
    setenforce 0
    setprop ctl.stop "tee-supplicant"

    [ ! -f /cache/swap ] && dd if=/dev/zero of=/cache/swap bs=1M count=300 && mkswap /cache/swap
    swapon -p -5 /cache/swap
    swapon /cache/swap

    # Retrieve the value of the Android property
    sleep 10
    prop_value=$(getprop vendor.hwc.device.display-0)

    # Check if the property value contains "HDMI", case insensitive
    if echo "$prop_value" | grep -iq "HDMI"; then
        # If HDMI is found, execute the commands in the background
        start hdmiaudioworkaround
        sleep 1
        input keyevent 26 && sleep 5 && input keyevent 26
    fi

fi
