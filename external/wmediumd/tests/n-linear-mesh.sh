#!/bin/bash
# run multiple mesh nodes in a linear topology
# each node is in the same coverage area, so
# total throughput is divided by n.

num_nodes=${1:-4}
daemon=${2:-iw}

session=wmediumd
subnet=10.10.10
macfmt='02:00:00:00:%02x:00'

. func

if [[ $UID -ne 0 ]]; then
	echo "Sorry, run me as root."
	exit 1
fi

modprobe -r mac80211_hwsim
modprobe mac80211_hwsim radios=$num_nodes

for i in `seq 0 $((num_nodes-1))`; do
	addrs[$i]=`printf $macfmt $i`
done

echo "ifaces: { count = $num_nodes; ids = [" > linear.cfg
for addr in "${addrs[@]}"; do
	echo -n '"'$addr'"' >> linear.cfg
	if [[ $addr != ${addrs[$((num_nodes-1))]} ]]; then
		echo ", " >> linear.cfg
	fi
done
echo "]; }" >> linear.cfg

tmux new -s $session -d

rm /tmp/netns.pid.* 2>/dev/null
i=0
for addr in ${addrs[@]}; do
	phy=`addr2phy $addr`
	dev=`ls /sys/class/ieee80211/$phy/device/net`
	phys[$i]=$phy
	devs[$i]=$dev

	ip=${subnet}.$((10 + i))

	# put this phy in own netns and tmux window, and start a mesh node
	win=$session:$((i+1)).0
	tmux new-window -t $session -n $ip

	# start netns
	pidfile=/tmp/netns.pid.$i
	tmux send-keys -t $win 'lxc-unshare -s NETWORK /bin/bash' C-m
	tmux send-keys -t $win 'echo $$ > '$pidfile C-m

	# wait for netns to exist
	while [[ ! -e $pidfile ]]; do
		echo "Waiting for netns $i -- $pidfile"
		sleep 0.5
	done

	tmux send-keys -t $session:0.0 'iw phy '$phy' set netns `cat '$pidfile'`' C-m

	# wait for phy to exist in netns
	while [[ -e /sys/class/ieee80211/$phy ]]; do
		echo "Waiting for $phy to move to netns..."
		sleep 0.5
	done

	# start mesh node
	tmux send-keys -t $win '. func' C-m
	tmux send-keys -t $win 'meshup-'$daemon ' ' $dev' linear 2412 '$ip C-m

	i=$((i+1))
done
winct=$i

# wait a few beacon periods for everyone to discover each other
sleep 3

# force a linear topology
for i in `seq 0 $((${#addrs[@]} - 1))`; do
	win=$session:$((i+1)).0
	addr=${addrs[$i]}
	dev=${devs[$i]}

	for j in `seq 0 $((${#addrs[@]} - 1))`; do
		oaddr=${addrs[$j]}
		if [[ $j -lt $((i-1)) || $j -gt $((i+1)) ]]; then
			tmux send-keys -t $win 'iw dev '$dev' station set '$oaddr' plink_action block' C-m
		fi
	done
done

# start wmediumd
win=$session:$((winct+1)).0
winct=$((winct+1))
tmux new-window -t $session -n wmediumd
tmux send-keys -t $win '../wmediumd/wmediumd -c linear.cfg' C-m

tmux attach
