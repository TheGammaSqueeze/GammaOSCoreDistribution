# This script runs 256 MB file benchmark, both on host and on authfs.
# Usage: after connecting the device with adb, run:
# $ packages/modules/Virtualization/tests/benchmark/benchmark_example.sh <target> (e.g. aosp_oriole_pkvm-userdebug)

set -e

# Prerequisite: we need root to flush disk cache.
adb root

# 1. Build needed artifacts, and install it to device
source build/make/rbesetup.sh
lunch $1
m fs_benchmark MicrodroidFilesystemBenchmarkApp fsverity fsverity_metadata_generator
adb push $OUT/system/bin/fs_benchmark /data/local/tmp
adb install $OUT/system/app/MicrodroidFilesystemBenchmarkApp/MicrodroidFilesystemBenchmarkApp.apk

# 2. Generate testcases
# /data/local/tmp/testcase: 256 MB, signed by fsverity.
# /data/local/tmp/testcase2: empty file, used for authfs write test.
adb shell 'rm -rf /data/local/tmp/virt /data/local/tmp/testcase*'
adb shell 'mkdir -p /data/local/tmp/virt'
dd if=/dev/zero of=/tmp/testcase bs=1048576 count=256
fsverity_metadata_generator --fsverity-path $(which fsverity) --signature none --hash-alg sha256 --out /tmp/testcase.fsv_meta /tmp/testcase
adb shell 'dd if=/dev/zero of=/data/local/tmp/testcase bs=1048576 count=256'
adb push /tmp/testcase.fsv_meta /data/local/tmp

# 3. Run fd_server from host
adb shell 'exec 3</data/local/tmp/testcase 4</data/local/tmp/testcase.fsv_meta 6</data/local/tmp/testcase 7<>/data/local/tmp/testcase2 /apex/com.android.virt/bin/fd_server --ro-fds 3:4 --ro-fds 6 --rw-fds 7' &

# 4. Run VM and get the CID
result=$(adb shell "/apex/com.android.virt/bin/vm run-app --debug full --daemonize --log /data/local/tmp/virt/log.txt $(adb shell pm path com.android.microdroid.benchmark | cut -d':' -f2) /data/local/tmp/virt/MicrodroidFilesystemBenchmarkApp.apk.idsig /data/local/tmp/virt/instance.img assets/vm_config.json")
cid=$(echo $result | grep -P "with CID \d+" --only-matching --color=none | cut -d' ' -f3)
echo "CID IS $cid"

# 5. Run host tests
echo "Running host read/write test..."
adb shell 'dd if=/dev/zero of=/data/local/tmp/testcase_host bs=1048576 count=256'
adb shell '/data/local/tmp/fs_benchmark /data/local/tmp/testcase_host 268435456 both 5'

# 6. Connect to the VM
# We are cheating here. The VM is expected to finish booting, while the host tests are running.
adb forward tcp:8000 vsock:$cid:5555
adb connect localhost:8000
adb -s localhost:8000 root
sleep 10

# 7. Install artifacts and run authfs
adb -s localhost:8000 push $OUT/system/bin/fs_benchmark /data/local/tmp
adb -s localhost:8000 shell "mkdir -p /data/local/tmp/authfs"
adb -s localhost:8000 shell "/system/bin/authfs /data/local/tmp/authfs --cid 2 --remote-ro-file 3:sha256-$(fsverity digest /tmp/testcase --hash-alg sha256 --compact) --remote-ro-file-unverified 6 --remote-new-rw-file 7" &

# 8. Run guest tests
echo "Running guest block device read test..."
adb -s localhost:8000 shell "/data/local/tmp/fs_benchmark /dev/block/vda $(adb -s localhost:8000 shell blockdev --getsize64 /dev/block/vda) read 5"
echo "Running guest authfs read test..."
adb -s localhost:8000 shell "/data/local/tmp/fs_benchmark /data/local/tmp/authfs/3 268435456 read 5"
echo "Running guest authfs unverified read test..."
adb -s localhost:8000 shell "/data/local/tmp/fs_benchmark /data/local/tmp/authfs/6 268435456 read 5"
echo "Running guest authfs write test..."
adb -s localhost:8000 shell "/data/local/tmp/fs_benchmark /data/local/tmp/authfs/7 268435456 write 5"
