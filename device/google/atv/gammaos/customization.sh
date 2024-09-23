#!/system/bin/sh

if [ ! -d /data/setupcompleted ] && [ -z $(getprop persist.sys.device_provisioned) ]; then
    wm density 137
    settings put system screen_off_timeout 1800000
    setenforce 0
    settings put secure navigation_mode 2

    echo "Installing Magisk."
    pm install /system/etc/magisk.apk
    am force-stop com.topjohnwu.magisk
    launcheruser=$(stat -c "%U" /data/data/com.topjohnwu.magisk)
    launchergroup=$(stat -c "%G" /data/data/com.topjohnwu.magisk)
    tar -xvf /system/etc/magisk.tar.gz -C /
    chown -R $launcheruser:$launchergroup /data/data/com.topjohnwu.magisk
    chown -R $launcheruser:$launchergroup /data_mirror/data_de/null/0/com.topjohnwu.magisk
    am force-stop com.topjohnwu.magisk

    setprop ctl.stop "tee-supplicant"

    # Retrieve the value of the Android property
    prop_value=$(getprop vendor.hwc.device.display-0)

    # Check if the property value contains "HDMI", case insensitive
    if echo "$prop_value" | grep -iq "HDMI"; then
        # If HDMI is found, execute the commands in the background
        start hdmiaudioworkaround
        sleep 1
        input keyevent 26 && sleep 5 && input keyevent 26
    fi

else
    setenforce 0
    setprop ctl.stop "tee-supplicant"

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

    svc usb setFunctions mtp


fi
