#!/system/bin/sh

if [ ! -d /data/setupcompleted ] && [ -z $(getprop persist.sys.device_provisioned) ]
then
	wm density 135
	am broadcast -a android.bluetooth.adapter.action.REQUEST_DISABLE
	am broadcast -a android.bluetooth.adapter.action.REQUEST_DISABLE
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

	#input keyevent 26
	#sleep 1
	#input keyevent 26
	#sleep 1

	settings put global development_settings_enabled 1
	settings put global stay_on_while_plugged_in 0
	settings put global mobile_data_always_on 0

	/system/bin/mkdir /data/tmpsetup

	/system/bin/pm install /system/etc/magisk.apk
        #/system/bin/pm install /system/etc/PlainLauncher.apk
        /system/bin/pm install /system/etc/projectivylauncher_4.36.apk
	/system/bin/pm install /system/etc/RetroArch_aarch64.apk

	appops set --uid org.plain.launcher MANAGE_EXTERNAL_STORAGE allow

	#/system/bin/tar -xvf /system/etc/launcherconfig.tar.gz -C /data/tmpsetup/
	#launcheruser=$( stat -c "%U" /data/data/com.magneticchen.daijishou)
	#launchergroup=$( stat -c "%G" /data/data/com.magneticchen.daijishou)
	#/system/bin/chown -R $launcheruser:$launchergroup /data/tmpsetup/data/data/com.magneticchen.daijishou
	#/system/bin/rm -rf /data/tmpsetup/data/data/com.magneticchen.daijishou/cache
	#/system/bin/rm -rf /data/tmpsetup/data/data/com.magneticchen.daijishou/code_cache
	#/system/bin/cp -pdrav /data/tmpsetup/data/data/com.magneticchen.daijishou /data/data/
	#/system/bin/rm -rf /data/tmpsetup/*
	/system/bin/tar -xvf /system/etc/roms.tar.gz -C /

	/system/bin/tar -xvf /system/etc/retroarch64sdcard.tar.gz -C /
	launcheruser=$( stat -c "%U" /data/data/com.retroarch.aarch64)
	/system/bin/chown -R $launcheruser:media_rw /sdcard/RetroArch

	/system/bin/tar -xvf /system/etc/retroarch64sdcard2.tar.gz -C /
	launcheruser=$( stat -c "%U" /data/data/com.retroarch.aarch64)
	/system/bin/chown -R $launcheruser:ext_data_rw /sdcard/Android/data/com.retroarch.aarch64

	isarc=$(cat /proc/device-tree/model)
	if [[ "$isarc" == *"Anbernic RG403H"* ]]; then
	/system/bin/tar -xvf /system/etc/retroarch64sdcard1-arc.tar.gz -C /
	launcheruser=$( stat -c "%U" /data/data/com.retroarch.aarch64)
	/system/bin/chown -R $launcheruser:media_rw /sdcard/RetroArch
	fi

	/system/bin/pm grant com.retroarch.aarch64 android.permission.WRITE_EXTERNAL_STORAGE
	/system/bin/pm grant com.retroarch.aarch64 android.permission.READ_EXTERNAL_STORAGE

	launcheruser=$( stat -c "%U" /data/data/com.retroarch.aarch64)
	launchergroup=$( stat -c "%G" /data/data/com.retroarch.aarch64)
        /system/bin/tar -xvf /system/etc/retroarch64.tar.gz -C /
	/system/bin/chown -R $launcheruser:$launchergroup /data/data/com.retroarch.aarch64
	/system/bin/rm -rf /data/tmpsetup/*

	setprop service.bootanim.exit 1
	setprop service.bootanim.progress 1

	#input keyevent 61
	#sleep 0.5
	#input keyevent 66
	#sleep 2

	mkdir /data/setupcompleted
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

	#input keyevent 26
	#sleep 1
	#input keyevent 26
fi
