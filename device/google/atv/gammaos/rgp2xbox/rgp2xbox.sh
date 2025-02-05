#!/system/bin/sh

if [ -x /vendor/bin/rgp2xbox ]; then
    # Create the destination directory if it doesn't exist
    mkdir -p /data/rgp2xbox_bin

    # Copy the file to /data/rgp2xbox preserving permissions
    cp -p /vendor/bin/rgp2xbox /data/rgp2xbox_bin/rgp2xbox

    CMD="/data/rgp2xbox_bin/rgp2xbox"
else
    CMD="/system/bin/rgp2xbox"
fi

ionice -c 2 -n 0 nice -n -20 "$CMD"
