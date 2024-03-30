/*
 * Copyright 2012 Daniel Drown
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * clatd.c - tun interface setup and main event loop
 */
#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#include <linux/filter.h>
#include <linux/if.h>
#include <linux/if_ether.h>
#include <linux/if_packet.h>
#include <linux/if_tun.h>
#include <net/if.h>
#include <sys/capability.h>
#include <sys/uio.h>

#include "clatd.h"
#include "config.h"
#include "dump.h"
#include "getaddr.h"
#include "logging.h"
#include "translate.h"

struct clat_config Global_Clatd_Config;

/* 40 bytes IPv6 header - 20 bytes IPv4 header + 8 bytes fragment header */
#define MTU_DELTA 28

volatile sig_atomic_t running = 1;

int ipv6_address_changed(const char *interface) {
  union anyip *interface_ip;

  interface_ip = getinterface_ip(interface, AF_INET6);
  if (!interface_ip) {
    logmsg(ANDROID_LOG_ERROR, "Unable to find an IPv6 address on interface %s", interface);
    return 1;
  }

  if (!ipv6_prefix_equal(&interface_ip->ip6, &Global_Clatd_Config.ipv6_local_subnet)) {
    char oldstr[INET6_ADDRSTRLEN];
    char newstr[INET6_ADDRSTRLEN];
    inet_ntop(AF_INET6, &Global_Clatd_Config.ipv6_local_subnet, oldstr, sizeof(oldstr));
    inet_ntop(AF_INET6, &interface_ip->ip6, newstr, sizeof(newstr));
    logmsg(ANDROID_LOG_INFO, "IPv6 prefix on %s changed: %s -> %s", interface, oldstr, newstr);
    free(interface_ip);
    return 1;
  } else {
    free(interface_ip);
    return 0;
  }
}

/* function: read_packet
 * reads a packet from the tunnel fd and translates it
 *   read_fd  - file descriptor to read original packet from
 *   write_fd - file descriptor to write translated packet to
 *   to_ipv6  - whether the packet is to be translated to ipv6 or ipv4
 */
void read_packet(int read_fd, int write_fd, int to_ipv6) {
  uint8_t buf[PACKETLEN];
  ssize_t readlen = read(read_fd, buf, PACKETLEN);

  if (readlen < 0) {
    if (errno != EAGAIN) {
      logmsg(ANDROID_LOG_WARN, "read_packet/read error: %s", strerror(errno));
    }
    return;
  } else if (readlen == 0) {
    logmsg(ANDROID_LOG_WARN, "read_packet/tun interface removed");
    running = 0;
    return;
  }

  if (!to_ipv6) {
    translate_packet(write_fd, 0 /* to_ipv6 */, buf, readlen);
    return;
  }

  struct tun_pi *tun_header = (struct tun_pi *)buf;
  if (readlen < (ssize_t)sizeof(*tun_header)) {
    logmsg(ANDROID_LOG_WARN, "read_packet/short read: got %ld bytes", readlen);
    return;
  }

  uint16_t proto = ntohs(tun_header->proto);
  if (proto != ETH_P_IP) {
    logmsg(ANDROID_LOG_WARN, "%s: unknown packet type = 0x%x", __func__, proto);
    return;
  }

  if (tun_header->flags != 0) {
    logmsg(ANDROID_LOG_WARN, "%s: unexpected flags = %d", __func__, tun_header->flags);
  }

  uint8_t *packet = (uint8_t *)(tun_header + 1);
  readlen -= sizeof(*tun_header);
  translate_packet(write_fd, 1 /* to_ipv6 */, packet, readlen);
}

/* function: event_loop
 * reads packets from the tun network interface and passes them down the stack
 *   tunnel - tun device data
 */
void event_loop(struct tun_data *tunnel) {
  time_t last_interface_poll;
  struct pollfd wait_fd[] = {
    { tunnel->read_fd6, POLLIN, 0 },
    { tunnel->fd4, POLLIN, 0 },
  };

  // start the poll timer
  last_interface_poll = time(NULL);

  while (running) {
    if (poll(wait_fd, ARRAY_SIZE(wait_fd), NO_TRAFFIC_INTERFACE_POLL_FREQUENCY * 1000) == -1) {
      if (errno != EINTR) {
        logmsg(ANDROID_LOG_WARN, "event_loop/poll returned an error: %s", strerror(errno));
      }
    } else {
      // Call read_packet if the socket has data to be read, but also if an
      // error is waiting. If we don't call read() after getting POLLERR, a
      // subsequent poll() will return immediately with POLLERR again,
      // causing this code to spin in a loop. Calling read() will clear the
      // socket error flag instead.
      if (wait_fd[0].revents) read_packet(tunnel->read_fd6, tunnel->fd4, 0 /* to_ipv6 */);
      if (wait_fd[1].revents) read_packet(tunnel->fd4, tunnel->write_fd6, 1 /* to_ipv6 */);
    }

    time_t now = time(NULL);
    if (now >= (last_interface_poll + INTERFACE_POLL_FREQUENCY)) {
      last_interface_poll = now;
      if (ipv6_address_changed(Global_Clatd_Config.native_ipv6_interface)) {
        break;
      }
    }
  }
}
