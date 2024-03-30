/****************************************************************************
 ****************************************************************************
 ***
 ***   This header was automatically generated from a Linux kernel header
 ***   of the same name, to make information necessary for userspace to
 ***   call into the kernel available to libc.  It contains only constants,
 ***   structures, and macros generated from the original header, and thus,
 ***   contains no copyrightable information.
 ***
 ***   To edit the content of this header, modify the corresponding
 ***   source file (e.g. under external/kernel-headers/original/) then
 ***   run bionic/libc/kernel/tools/update_all.py
 ***
 ***   Any manual change here will be lost the next time this script will
 ***   be run. You've been warned!
 ***
 ****************************************************************************
 ****************************************************************************/
#ifndef _UAPI_COUNTER_H_
#define _UAPI_COUNTER_H_
#include <linux/ioctl.h>
#include <linux/types.h>
enum counter_component_type {
  COUNTER_COMPONENT_NONE,
  COUNTER_COMPONENT_SIGNAL,
  COUNTER_COMPONENT_COUNT,
  COUNTER_COMPONENT_FUNCTION,
  COUNTER_COMPONENT_SYNAPSE_ACTION,
  COUNTER_COMPONENT_EXTENSION,
};
enum counter_scope {
  COUNTER_SCOPE_DEVICE,
  COUNTER_SCOPE_SIGNAL,
  COUNTER_SCOPE_COUNT,
};
struct counter_component {
  __u8 type;
  __u8 scope;
  __u8 parent;
  __u8 id;
};
enum counter_event_type {
  COUNTER_EVENT_OVERFLOW,
  COUNTER_EVENT_UNDERFLOW,
  COUNTER_EVENT_OVERFLOW_UNDERFLOW,
  COUNTER_EVENT_THRESHOLD,
  COUNTER_EVENT_INDEX,
};
struct counter_watch {
  struct counter_component component;
  __u8 event;
  __u8 channel;
};
#define COUNTER_ADD_WATCH_IOCTL _IOW(0x3E, 0x00, struct counter_watch)
#define COUNTER_ENABLE_EVENTS_IOCTL _IO(0x3E, 0x01)
#define COUNTER_DISABLE_EVENTS_IOCTL _IO(0x3E, 0x02)
struct counter_event {
  __aligned_u64 timestamp;
  __aligned_u64 value;
  struct counter_watch watch;
  __u8 status;
};
enum counter_count_direction {
  COUNTER_COUNT_DIRECTION_FORWARD,
  COUNTER_COUNT_DIRECTION_BACKWARD,
};
enum counter_count_mode {
  COUNTER_COUNT_MODE_NORMAL,
  COUNTER_COUNT_MODE_RANGE_LIMIT,
  COUNTER_COUNT_MODE_NON_RECYCLE,
  COUNTER_COUNT_MODE_MODULO_N,
};
enum counter_function {
  COUNTER_FUNCTION_INCREASE,
  COUNTER_FUNCTION_DECREASE,
  COUNTER_FUNCTION_PULSE_DIRECTION,
  COUNTER_FUNCTION_QUADRATURE_X1_A,
  COUNTER_FUNCTION_QUADRATURE_X1_B,
  COUNTER_FUNCTION_QUADRATURE_X2_A,
  COUNTER_FUNCTION_QUADRATURE_X2_B,
  COUNTER_FUNCTION_QUADRATURE_X4,
};
enum counter_signal_level {
  COUNTER_SIGNAL_LEVEL_LOW,
  COUNTER_SIGNAL_LEVEL_HIGH,
};
enum counter_synapse_action {
  COUNTER_SYNAPSE_ACTION_NONE,
  COUNTER_SYNAPSE_ACTION_RISING_EDGE,
  COUNTER_SYNAPSE_ACTION_FALLING_EDGE,
  COUNTER_SYNAPSE_ACTION_BOTH_EDGES,
};
#endif
