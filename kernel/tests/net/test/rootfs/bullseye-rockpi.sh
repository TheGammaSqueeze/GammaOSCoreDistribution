#!/bin/bash
#
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -e
set -u

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)

. $SCRIPT_DIR/bullseye-common.sh

sed -i "s,debian,rockpi," /etc/hosts
sed -i "s,debian,rockpi," /etc/hostname

# Build U-Boot FIT based on the Debian initrd
if [ -n "${embed_kernel_initrd_dtb}" ]; then
  mkimage -f auto -A arm64 -O linux -T kernel -C none -a 0x02080000 \
    -d /boot/vmlinuz-$(uname -r) -i /boot/initrd.img-$(uname -r) \
    -b /boot/dtb/rockchip/rk3399-rock-pi-4b.dtb /boot/boot.fit
fi

# Write U-Boot script to /boot
cat >/boot/boot.cmd <<"EOF"
setenv start_poe 'gpio set 150; gpio clear 146'
run start_poe
setenv bootcmd_dhcp '
mw.b ${scriptaddr} 0 0x8000
mmc dev 0 0
mmc read ${scriptaddr} 0x1fc0 0x40
env import -b ${scriptaddr} 0x8000
mw.b ${scriptaddr} 0 0x8000
if dhcp ${scriptaddr} manifest.txt; then
	setenv OldSha ${Sha}
	setenv Sha
	env import -t ${scriptaddr} 0x8000 ManifestVersion
	echo "Manifest version $ManifestVersion";
	if test "$ManifestVersion" = "1"; then
		run manifest1
	elif test "$ManifestVersion" = "2"; then
		run manifest2
	else
		run manifestX
	fi
fi'
setenv manifestX 'echo "***** ERROR: Unknown manifest version! *****";'
setenv manifest1 '
env import -t ${scriptaddr} 0x8000
if test "$Sha" != "$OldSha"; then
	setenv serverip ${TftpServer}
	setenv loadaddr 0x00200000
	mmc dev 0 0;
	setenv file $TplSplImg; offset=0x40; size=0x1f80; run tftpget1; setenv TplSplImg
	setenv file $UbootItb;  offset=0x4000; size=0x2000; run tftpget1; setenv UbootItb
	setenv file $TrustImg; offset=0x6000; size=0x2000; run tftpget1; setenv TrustImg
	setenv file $RootfsImg; offset=0x8000; size=0; run tftpget1; setenv RootfsImg
	setenv file $UbootEnv; offset=0x1fc0; size=0x40; run tftpget1; setenv UbootEnv
	mw.b ${scriptaddr} 0 0x8000
	env export -b ${scriptaddr} 0x8000
	mmc write ${scriptaddr} 0x1fc0 0x40
else
	echo "Already have ${Sha}. Booting..."
fi'
setenv manifest2 '
env import -t ${scriptaddr} 0x8000
if test "$DFUethaddr" = "$ethaddr" || test "$DFUethaddr" = ""; then
	if test "$Sha" != "$OldSha"; then
		setenv serverip ${TftpServer}
		setenv loadaddr 0x00200000
		mmc dev 0 0;
		setenv file $TplSplImg; offset=0x40; size=0x1f80; run tftpget1; setenv TplSplImg
		setenv file $UbootItb;  offset=0x4000; size=0x2000; run tftpget1; setenv UbootItb
		setenv file $TrustImg; offset=0x6000; size=0x2000; run tftpget1; setenv TrustImg
		setenv file $RootfsImg; offset=0x8000; size=0; run tftpget1; setenv RootfsImg
		setenv file $UbootEnv; offset=0x1fc0; size=0x40; run tftpget1; setenv UbootEnv
		mw.b ${scriptaddr} 0 0x8000
		env export -b ${scriptaddr} 0x8000
		mmc write ${scriptaddr} 0x1fc0 0x40
	else
		echo "Already have ${Sha}. Booting..."
	fi
else
	echo "Update ${Sha} is not for me. Booting..."
fi'
setenv tftpget1 '
if test "$file" != ""; then
	mw.b ${loadaddr} 0 0x400000
	tftp ${file}
	if test $? = 0; then
		setenv isGz 0 && setexpr isGz sub .*\\.gz\$ 1 ${file}
		if test $isGz = 1; then
			if test ${file} = ${UbootEnv}; then
				echo "** gzipped env unsupported **"
			else
				setexpr boffset ${offset} * 0x200
				gzwrite mmc 0 ${loadaddr} 0x${filesize} 100000 ${boffset} && echo Updated: ${file}
			fi
		elif test ${file} = ${UbootEnv}; then
			env import -b ${loadaddr} && echo Updated: ${file}
		else
			if test $size = 0; then
				setexpr x $filesize - 1
				setexpr x $x / 0x1000
				setexpr x $x + 1
				setexpr x $x * 0x1000
				setexpr x $x / 0x200
				size=0x${x}
			fi
			mmc write ${loadaddr} ${offset} ${size} && echo Updated: ${file}
		fi
	fi
	if test $? != 0; then
		echo ** UPDATE FAILED: ${file} **
	fi
fi'
if mmc dev 1 0; then; else
	run bootcmd_dhcp;
fi
if test -e mmc ${devnum}:${distro_bootpart} /boot/rootfs.gz; then
	setenv loadaddr 0x00200000
	mw.b ${loadaddr} 0 0x400000
	load mmc ${devnum}:${distro_bootpart} ${loadaddr} /boot/rootfs.gz
	gzwrite mmc ${devnum} ${loadaddr} 0x${filesize} 100000 0x1000000
fi
load mmc ${devnum}:${distro_bootpart} 0x06080000 /boot/boot.fit
setenv bootargs "8250.nr_uarts=4 earlycon=uart8250,mmio32,0xff1a0000 console=ttyS2,1500000n8 loglevel=7 sdhci.debug_quirks=0x20000000 root=LABEL=ROOT"
bootm 0x06080000
EOF
mkimage -C none -A arm -T script -d /boot/boot.cmd /boot/boot.scr

# Write control script for PoE hat
cat >/usr/local/bin/poe <<"EOF"
#!/bin/bash

if [ "$1" == "--start" ]; then
	echo 146 > /sys/class/gpio/export
	echo out > /sys/class/gpio/gpio146/direction
	echo 0 > /sys/class/gpio/gpio146/value
	echo 150 > /sys/class/gpio/export
	echo out > /sys/class/gpio/gpio150/direction
	echo 1 > /sys/class/gpio/gpio150/value
	exit 0
fi

if [ "$1" == "--stop" ]; then
	echo 0 > /sys/class/gpio/gpio146/value
	echo 146 > /sys/class/gpio/unexport
	echo 0 > /sys/class/gpio/gpio150/value
	echo 150 > /sys/class/gpio/unexport
	exit 0
fi

if [ ! -e /sys/class/gpio/gpio146/value ] || [ ! -e /sys/class/gpio/gpio150/value ]; then
	echo "error: PoE service not initialized"
	exit 1
fi

if [ "$1" == "0" ] || [ "$1" == "off" ] || [ "$1" == "OFF" ]; then
	echo 0 > /sys/class/gpio/gpio150/value
	exit 0
fi

if [ "$1" == "1" ] || [ "$1" == "on" ] || [ "$1" == "ON" ]; then
	echo 1 > /sys/class/gpio/gpio150/value
	exit 0
fi

echo "usage: poe <0|1>"
exit 1
EOF
chmod a+x /usr/local/bin/poe

# Write service to start PoE control script
cat >/etc/systemd/system/poe.service <<EOF
[Unit]
Description=PoE service
ConditionPathExists=/usr/local/bin/poe

[Service]
Type=oneshot
ExecStart=/usr/local/bin/poe --start
ExecStop=/usr/local/bin/poe --stop
RemainAfterExit=true
StandardOutput=journal

[Install]
WantedBy=multi-user.target
EOF

# Write control script for status LEDs
cat >/usr/local/bin/led <<"EOF"
#!/bin/bash

if [ "$1" == "--start" ]; then
	echo 125 > /sys/class/gpio/export
	echo out > /sys/class/gpio/gpio125/direction
	chmod 666 /sys/class/gpio/gpio125/value
	echo 0 > /sys/class/gpio/gpio125/value
	exit 0
fi

if [ "$1" == "--stop" ]; then
	echo 0 > /sys/class/gpio/gpio125/value
	echo 125 > /sys/class/gpio/unexport
	exit 0
fi

if [ ! -e /sys/class/gpio/gpio125/value ]; then
	echo "error: led service not initialized"
	exit 1
fi

if [ "$1" == "0" ] || [ "$1" == "off" ] || [ "$1" == "OFF" ]; then
	echo 0 > /sys/class/gpio/gpio125/value
	exit 0
fi

if [ "$1" == "1" ] || [ "$1" == "on" ] || [ "$1" == "ON" ]; then
	echo 1 > /sys/class/gpio/gpio125/value
	exit 0
fi

echo "usage: led <0|1>"
exit 1
EOF
chmod a+x /usr/local/bin/led

# Write service to start LED control script
cat >/etc/systemd/system/led.service <<EOF
[Unit]
Description=led service
ConditionPathExists=/usr/local/bin/led

[Service]
Type=oneshot
ExecStart=/usr/local/bin/led --start
ExecStop=/usr/local/bin/led --stop
RemainAfterExit=true
StandardOutput=journal

[Install]
WantedBy=multi-user.target
EOF

# Write control script for one-time SD-Card->eMMC duplication
cat >/usr/local/bin/sd-dupe <<"EOF"
#!/bin/bash
led 0

src_dev=mmcblk0
dest_dev=mmcblk1
part_num=p5

if [ -e /dev/mmcblk0p5 ] && [ -e /dev/mmcblk1p5 ]; then
	led 1

	sgdisk -Z -a1 /dev/${dest_dev}
	sgdisk -a1 -n:1:64:8127 -t:1:8301 -c:1:loader1 /dev/${dest_dev}
	sgdisk -a1 -n:2:8128:8191 -t:2:8301 -c:2:env /dev/${dest_dev}
	sgdisk -a1 -n:3:16384:24575 -t:3:8301 -c:3:loader2 /dev/${dest_dev}
	sgdisk -a1 -n:4:24576:32767 -t:4:8301 -c:4:trust /dev/${dest_dev}
	sgdisk -a1 -n:5:32768:- -A:5:set:2 -t:5:8305 -c:5:rootfs /dev/${dest_dev}

	src_block_count=$(tune2fs -l /dev/${src_dev}${part_num} | grep "Block count:" | sed 's/.*: *//')
	src_block_size=$(tune2fs -l /dev/${src_dev}${part_num} | grep "Block size:" | sed 's/.*: *//')
	src_fs_size=$(( src_block_count*src_block_size ))
	src_fs_size_m=$(( src_fs_size / 1024 / 1024 + 1 ))

	dd if=/dev/${src_dev}p1 of=/dev/${dest_dev}p1 conv=sync,noerror status=progress
	dd if=/dev/${src_dev}p2 of=/dev/${dest_dev}p2 conv=sync,noerror status=progress
	dd if=/dev/${src_dev}p3 of=/dev/${dest_dev}p3 conv=sync,noerror status=progress
	dd if=/dev/${src_dev}p4 of=/dev/${dest_dev}p4 conv=sync,noerror status=progress

	echo "Writing ${src_fs_size_m} MB: /dev/${src_dev} -> /dev/${dest_dev}..."
	dd if=/dev/${src_dev}${part_num} of=/dev/${dest_dev}${part_num} bs=1M conv=sync,noerror status=progress

	echo "Expanding /dev/${dest_dev}${part_num} filesystem..."
	e2fsck -fy /dev/${dest_dev}${part_num}
	resize2fs /dev/${dest_dev}${part_num}
	tune2fs -O has_journal /dev/${dest_dev}${part_num}
	e2fsck -fy /dev/${dest_dev}${part_num}
	sync /dev/${dest_dev}

	echo "Cleaning up..."
	mount /dev/${dest_dev}${part_num} /media
	chroot /media /usr/local/bin/install-cleanup

	if [ $? == 0 ]; then
		echo "Successfully copied Rock Pi image!"
		while true; do
			led 1; sleep 0.5
			led 0; sleep 0.5
		done
	else
		echo "Error while copying Rock Pi image"
		while true; do
			led 1; sleep 0.1
			led 0; sleep 0.1
		done
	fi
else
	echo "Expanding /dev/${dest_dev}${part_num} filesystem..."
	e2fsck -fy /dev/${dest_dev}${part_num}
	resize2fs /dev/${dest_dev}${part_num}
	tune2fs -O has_journal /dev/${dest_dev}${part_num}
	e2fsck -fy /dev/${dest_dev}${part_num}
	sync /dev/${dest_dev}

	echo "Cleaning up..."
	/usr/local/bin/install-cleanup
fi
EOF
chmod a+x /usr/local/bin/sd-dupe

# Write one-shot service for SDCard->eMMC duplication
cat >/etc/systemd/system/sd-dupe.service <<EOF
[Unit]
Description=Duplicate SD card rootfs to eMMC on Rock Pi
ConditionPathExists=/usr/local/bin/sd-dupe
After=led.service

[Service]
Type=simple
ExecStart=/usr/local/bin/sd-dupe
TimeoutSec=0
StandardOutput=tty

[Install]
WantedBy=multi-user.target
EOF

# Write cleanup script for eMMC (after duplication)
cat >/usr/local/bin/install-cleanup <<"EOF"
#!/bin/bash
MAC=$(ip link | grep eth0 -A1 | grep ether | sed 's/.*\(..:..:..:..:..:..\) .*/\1/' | tr -d :)
sed -i "s,rockpi,rockpi-${MAC}," /etc/hosts
sudo hostnamectl set-hostname "rockpi-${MAC}"

rm -f /etc/machine-id
rm -f /var/lib/dbus/machine-id
dbus-uuidgen --ensure
systemd-machine-id-setup

systemctl disable sd-dupe
rm -f /etc/systemd/system/sd-dupe.service
rm -f /usr/local/bin/sd-dupe
rm -f /usr/local/bin/install-cleanup
EOF
chmod a+x /usr/local/bin/install-cleanup

systemctl enable poe
systemctl enable led
systemctl enable sd-dupe

setup_dynamic_networking "en*" ""

update_apt_sources bullseye

setup_cuttlefish_user

setup_and_build_cuttlefish
setup_and_build_iptables

install_and_cleanup_cuttlefish
install_and_cleanup_iptables

create_systemd_getty_symlinks ttyS0 hvc1

apt-get purge -y vim-tiny
bullseye_cleanup
