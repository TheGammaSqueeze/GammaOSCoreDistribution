#!/bin/env bash

# Run Rootcanal and forward port
if [ "$1" == "--rootcanal" ]
then
    adb root
    adb shell ./vendor/bin/hw/android.hardware.bluetooth@1.1-service.sim &
    adb forward tcp:6211 tcp:6211
fi

# Forward Pandora server port
adb forward tcp:8999 tcp:8999
