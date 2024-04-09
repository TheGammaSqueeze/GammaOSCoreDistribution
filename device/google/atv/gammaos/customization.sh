#!/system/bin/sh

if [ ! -d /data/setupcompleted ] && [ -z $(getprop persist.sys.device_provisioned) ]
then

	settings put system screen_off_timeout 1800000
	setenforce 0
	setprop service.bootanim.exit 0
	setprop service.bootanim.progress 0
	start bootanim

	settings put system screen_brightness 255

	#input keyevent 26
	#sleep 1
	#input keyevent 26
	#sleep 1

	settings put secure doze_pulse_on_pick_up 0
	settings put secure camera_double_tap_power_gesture_disabled 1
	settings put secure wake_gesture_enabled 0
	settings put --lineage global wake_when_plugged_or_unplugged 0
	settings put --lineage global trust_restrict_usb 0
	settings put --lineage secure advanced_reboot 1
	settings put --lineage secure trust_warning 0
	settings put --lineage secure trust_warnings 0
	settings put --lineage secure power_menu_actions "lockdown|power|restart|screenshot|bugreport|logout"
	settings put --lineage secure qs_show_auto_brightness 0
	settings put --lineage secure qs_show_brightness_slider 1
	settings put --lineage system app_switch_wake_screen 0
	settings put --lineage system assist_wake_screen 0
	settings put --lineage system trust_interface_hinted 1
	settings put --lineage system back_wake_screen 0
	settings put --lineage system camera_launch 0
	settings put --lineage system camera_sleep_on_release 0
	settings put --lineage system camera_wake_screen 0
	settings put --lineage system click_partial_screenshot 0
	settings put --lineage system double_tap_sleep_gesture 0
	settings put --lineage system home_wake_screen 1
	settings put --lineage system key_back_long_press_action 2
	settings put --lineage system lockscreen_rotation 1
	settings put --lineage system menu_wake_screen 0
	settings put --lineage system navigation_bar_menu_arrow_keys 0
	settings put --lineage system status_bar_am_pm 2
	settings put --lineage system status_bar_brightness_control 1
	settings put --lineage system status_bar_clock_auto_hide 0
	settings put --lineage system status_bar_show_battery_percent 2
	settings put secure immersive_mode_confirmations confirmed


	/system/bin/mkdir /data/tmpsetup

	/system/bin/pm install /system/etc/magisk.apk
	/system/bin/pm install /system/etc/399.apk
	/system/bin/pm install /system/etc/RetroArch_aarch64.apk

	/system/bin/tar -xvf /system/etc/launcherconfig.tar.gz -C /data/tmpsetup/
	launcheruser=$( stat -c "%U" /data/data/com.magneticchen.daijishou)
	launchergroup=$( stat -c "%G" /data/data/com.magneticchen.daijishou)
	/system/bin/chown -R $launcheruser:$launchergroup /data/tmpsetup/data/data/com.magneticchen.daijishou
	/system/bin/rm -rf /data/tmpsetup/data/data/com.magneticchen.daijishou/cache
	/system/bin/rm -rf /data/tmpsetup/data/data/com.magneticchen.daijishou/code_cache
	/system/bin/cp -pdrav /data/tmpsetup/data/data/com.magneticchen.daijishou /data/data/
	/system/bin/rm -rf /data/tmpsetup/*
	/system/bin/tar -xvf /system/etc/roms.tar.gz -C /

	/system/bin/tar -xvf /system/etc/retroarch64sdcard.tar.gz -C /
	launcheruser=$( stat -c "%U" /data/data/com.retroarch.aarch64)
	/system/bin/chown -R $launcheruser:media_rw /sdcard/RetroArch

	/system/bin/tar -xvf /system/etc/retroarch64sdcard2.tar.gz -C /
	launcheruser=$( stat -c "%U" /data/data/com.retroarch.aarch64)
	/system/bin/chown -R $launcheruser:ext_data_rw /sdcard/Android/data/com.retroarch.aarch64


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
else
	setenforce 0

	#input keyevent 26
	#sleep 1
	#input keyevent 26
fi
