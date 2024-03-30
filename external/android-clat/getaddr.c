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
 * getaddr.c - get a locally configured address
 */
#include "getaddr.h"

#include <errno.h>
#include <linux/if_addr.h>
#include <linux/rtnetlink.h>
#include <net/if.h>
#include <netinet/in.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <unistd.h>

#include "logging.h"

// Kernel suggests that keep the packet under 8KiB (NLMSG_GOODSIZE) in include/linux/netlink.h.
#define NLMSG_SIZE 8192

// shared state between getinterface_ip and parse_ifaddrmsg
// TODO: refactor the communication between getinterface_ip and parse_ifaddrmsg because there
// is no netlink callback anymore.
struct target {
  int family;
  unsigned int ifindex;
  union anyip ip;
  int foundip;
};

/* function: parse_ifaddrmsg
 * parse ifaddrmsg for getinterface_ip
 *   nh  - netlink message header
 *   targ_p - (struct target) info for which address we're looking for
 *            and the parsed result if any.
 */
static void parse_ifaddrmsg(struct nlmsghdr *nh, struct target *targ_p) {
  struct ifaddrmsg *ifa_p;
  struct rtattr *rta_p;
  int rta_len;

  ifa_p = (struct ifaddrmsg *)NLMSG_DATA(nh);
  rta_p = (struct rtattr *)IFA_RTA(ifa_p);

  if (ifa_p->ifa_index != targ_p->ifindex) return;

  if (ifa_p->ifa_scope != RT_SCOPE_UNIVERSE) return;

  rta_len = IFA_PAYLOAD(nh);
  for (; RTA_OK(rta_p, rta_len); rta_p = RTA_NEXT(rta_p, rta_len)) {
    switch (rta_p->rta_type) {
      case IFA_ADDRESS:
        if ((targ_p->family == AF_INET6) && !(ifa_p->ifa_flags & IFA_F_SECONDARY)) {
          memcpy(&targ_p->ip.ip6, RTA_DATA(rta_p), rta_p->rta_len - sizeof(struct rtattr));
          targ_p->foundip = 1;
          return;
        }
        break;
      case IFA_LOCAL:
        if (targ_p->family == AF_INET) {
          memcpy(&targ_p->ip.ip4, RTA_DATA(rta_p), rta_p->rta_len - sizeof(struct rtattr));
          targ_p->foundip = 1;
          return;
        }
        break;
    }
  }
}

void sendrecv_ifaddrmsg(struct target *targ_p) {
  int s = socket(PF_NETLINK, SOCK_DGRAM | SOCK_CLOEXEC, NETLINK_ROUTE);
  if (s < 0) {
    logmsg(ANDROID_LOG_ERROR, "open NETLINK_ROUTE socket failed %s", strerror(errno));
    return;
  }

  // Fill in netlink structures.
  struct {
    struct nlmsghdr n;
    struct ifaddrmsg r;
  } req = {
    // Netlink message header.
    .n.nlmsg_len   = NLMSG_LENGTH(sizeof(struct ifaddrmsg)),
    .n.nlmsg_flags = NLM_F_REQUEST | NLM_F_ROOT,
    .n.nlmsg_type  = RTM_GETADDR,

    // Interface address message header.
    .r.ifa_family = targ_p->family,
  };

  // Send interface address message.
  if ((send(s, &req, req.n.nlmsg_len, 0)) < 0) {
    logmsg(ANDROID_LOG_ERROR, "send netlink socket failed %s", strerror(errno));
    close(s);
    return;
  }

  // Read interface address message and parse the result if any.
  ssize_t bytes_read;
  char buf[NLMSG_SIZE];
  while ((bytes_read = recv(s, buf, sizeof(buf), 0)) > 0) {
    struct nlmsghdr *nh = (struct nlmsghdr *)buf;
    for (; NLMSG_OK(nh, bytes_read); nh = NLMSG_NEXT(nh, bytes_read)) {
      if (nh->nlmsg_type == NLMSG_DONE) {
        close(s);
        return;
      }
      if (nh->nlmsg_type == NLMSG_ERROR) {
        logmsg(ANDROID_LOG_ERROR, "netlink message error");
        close(s);
        return;
      }
      if (nh->nlmsg_type == RTM_NEWADDR) {
        // Walk through the all messages and update struct target variable as the deleted
        // callback behavior of getaddr_cb() which always returns NL_OK.
        // TODO: review if this can early return once address has been found.
        parse_ifaddrmsg(nh, targ_p);
      }
    }
  }
  close(s);
}

/* function: getinterface_ip
 * finds the first global non-privacy IP of the given family for the given interface, or returns
 * NULL.  caller frees pointer
 *   interface - interface to look for
 *   family    - family
 */
union anyip *getinterface_ip(const char *interface, int family) {
  union anyip *retval = NULL;
  struct target targ  = {
    .family  = family,
    .foundip = 0,
    .ifindex = if_nametoindex(interface),
  };

  if (targ.ifindex == 0) {
    return NULL;  // interface not found
  }

  // sends message and receives the response.
  sendrecv_ifaddrmsg(&targ);

  if (targ.foundip) {
    retval = malloc(sizeof(union anyip));
    if (!retval) {
      logmsg(ANDROID_LOG_FATAL, "getinterface_ip/out of memory");
      return NULL;
    }
    memcpy(retval, &targ.ip, sizeof(union anyip));
  }

  return retval;
}
