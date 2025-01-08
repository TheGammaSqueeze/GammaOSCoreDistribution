#!/system/bin/sh

echo "Starting configuration of the GammaOS system..."
sleep 1

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
	settings put --lineage system berry_black_theme 1
	settings put secure immersive_mode_confirmations confirmed
	settings put secure ui_night_mode 2
	settings put global window_animation_scale 0
	settings put global transition_animation_scale 0
	settings put global animator_duration_scale 0.5
	settings put system sound_effects_enabled 0
	setprop persist.sys.enable_mem_clear 1
	setprop persist.sys.disable_32bit_mode 1
	setprop persist.sys.disable_webview 0
	setprop sys.gamma_tweak_update 1
	cmd bluetooth_manager disable

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
        echo "Boot image updated. Continue setup..."
		sleep 5
    fi
fi

setprop ctl.stop "tee-supplicant"

echo "Set HDMI defaults"
setprop persist.vendor.resolution.HDMI-A-0 1920x1200@60
setprop persist.vendor.framebuffer.hdmi 1280x720

echo "Maximizing screen brightness."
settings put system screen_brightness 255

echo "Enabling developer settings and configuring system behaviors."
settings put global development_settings_enabled 1
settings put global stay_on_while_plugged_in 0
settings put global mobile_data_always_on 0
settings put global private_dns_mode "hostname"
settinns put global private_dns_specifier "dns.adguard-dns.com"

echo "Installing applications."
mkdir -p /data/tmpsetup

#echo "Installing Projectivy Launcher."
#pm install /system/etc/projectivylauncher_4.36.apk
#launcheruser=$(stat -c "%U" /data/data/com.spocky.projengmenu)
#launchergroup=$(stat -c "%G" /data/data/com.spocky.projengmenu)
#tar -xvf /system/etc/com.spocky.projengmenu.data.tar.gz -C /
#chown -R $launcheruser:$launchergroup /data/data/com.spocky.projengmenu

#echo "Installing PlainLauncher."
#pm install /system/etc/PlainLauncher.apk
#launcheruser=$(stat -c "%U" /data/data/org.plain.launcher)
#launchergroup=$(stat -c "%G" /data/data/org.plain.launcher)
#tar -xvf /system/etc/plainlauncher.tar.gz -C /
#chown -R $launcheruser:$launchergroup /data/data/org.plain.launcher
#chown -R $launcheruser:ext_data_rw /sdcard/Android/data/org.plain.launcher

echo "Installing drastic DS emulator."
pm install /system/etc/drastic_r2.6.0.4a.apk
launcheruser=$( stat -c "%U" /data/data/com.dsemu.drastic)
launchergroup=$( stat -c "%G" /data/data/com.dsemu.drastic)
tar -xvf /system/etc/drastic.tar.gz -C /
chown -R $launcheruser:$launchergroup /data/data/com.dsemu.drastic

echo "Installing M64Plus FZ N64 Emulator."
pm install /system/etc/mupen64plusae_3.0.335.apk
launcheruser=$( stat -c "%U" /data/data/org.mupen64plusae.v3.fzurita)
launchergroup=$( stat -c "%G" /data/data/org.mupen64plusae.v3.fzurita)
tar -xvf /system/etc/mupen64plusae.tar.gz -C /
chown -R $launcheruser:$launchergroup /data/data/org.mupen64plusae.v3.fzurita
pm grant org.mupen64plusae.v3.fzurita android.permission.POST_NOTIFICATIONS

echo "Installing PPSSPP PSP emulator."
pm install /system/etc/ppsspp_1.18.1.apk
launcheruser=$( stat -c "%U" /data/data/org.ppsspp.ppsspp)
launchergroup=$( stat -c "%G" /data/data/org.ppsspp.ppsspp)
tar -xvf /system/etc/ppsspp.tar.gz -C /
chown -R $launcheruser:$launchergroup /data/data/org.ppsspp.ppsspp
rm -rf /sdcard/Android/data/org.ppsspp.ppsspp
appops set --uid org.ppsspp.ppsspp MANAGE_EXTERNAL_STORAGE allow
pm grant org.ppsspp.ppsspp android.permission.WRITE_EXTERNAL_STORAGE
pm grant org.ppsspp.ppsspp android.permission.READ_EXTERNAL_STORAGE

echo "Installing Daijisho."
mkdir -p /sdcard/daijisho
tar -xvf /system/etc/daijisho_408.tar.gz -C /sdcard/daijisho/
cd /sdcard/daijisho/daijisho_408

session_id=$(pm install-create -r | cut -d '[' -f2 | cut -d ']' -f1)
for apk in *.apk; do
    pm install-write $session_id $(basename $apk) $apk
done
pm install-commit $session_id

cd /
rm -rf /sdcard/daijisho
launcheruser=$( stat -c "%U" /data/data/com.magneticchen.daijishou)
launchergroup=$( stat -c "%G" /data/data/com.magneticchen.daijishou)
tar -xvf /system/etc/daijisho.tar.gz -C /
chown -R $launcheruser:$launchergroup /data/data/com.magneticchen.daijishou

echo "Installing Aurora Store."
pm install /system/etc/AuroraStore_4.6.2.apk

echo "Installing MiXplorer."
pm install /system/etc/MiXplorer_v6.64.3-API29_B23090720.apk

echo "Installing RetroArch."
pm install /system/etc/RetroArch_aarch64.apk

echo "Installing GammaOS Splash app."
pm install /system/etc/gammaos-displayloading.apk
appops set com.gammaos.displayloading SYSTEM_ALERT_WINDOW allow
cmd deviceidle whitelist +com.gammaos.displayloading
pm install system/etc/Toast.apk
pm grant bellavita.toast android.permission.POST_NOTIFICATIONS

echo "Granting permissions to applications."
appops set --uid org.plain.launcher MANAGE_EXTERNAL_STORAGE allow
pm grant com.spocky.projengmenu android.permission.READ_TV_LISTINGS
cmd notification allow_listener com.spocky.projengmenu/.services.notification.NotificationListener
cmd package set-home-activity com.magneticchen.daijishou/.app.HomeActivity
pm set-home-activity com.magneticchen.daijishou/.app.HomeActivity -user --user 0

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
    chown -R $launcheruser:ext_data_rw /sdcard/Android/data/com.retroarch.aarch64
fi

echo "Granting read/write permissions to RetroArch."
pm grant com.retroarch.aarch64 android.permission.WRITE_EXTERNAL_STORAGE
pm grant com.retroarch.aarch64 android.permission.READ_EXTERNAL_STORAGE

echo "Cleaning up and finalizing setup."
tar -xvf /system/etc/retroarch64.tar.gz -C /
chown -R $launcheruser:$launchergroup /data/data/com.retroarch.aarch64
rm -rf /data/tmpsetup/*

# Check if the device is Powkiddy RGB20 Pro, enable system sounds, avoids interference when no audio is being played
isrgb20pro=$(cat /proc/device-tree/model)
if [[ "$isrgb20pro" == *"Powkiddy RGB20 Pro aka wonderfully wacky unit"* ]]; then
    settings put system sound_effects_enabled 1
    rm /sdcard/RetroArch/config/global.slangp
fi

mkdir -p /data/setupcompleted
sleep 4 && settings put system screen_off_timeout 240000 &

rm /sdcard/RetroArch/config/global.slangp

echo "All settings have been applied successfully."
