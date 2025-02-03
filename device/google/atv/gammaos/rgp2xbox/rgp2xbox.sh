#!/system/bin/sh

if [ -x /vendor/bin/rgp2xbox ]; then
    CMD=/vendor/bin/rgp2xbox
else
    CMD=/system/bin/rgp2xbox
fi

ionice -c 2 -n 0 nice -n -20 "$CMD"
