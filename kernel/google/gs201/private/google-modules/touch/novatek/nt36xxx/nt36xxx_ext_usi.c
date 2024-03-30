// SPDX-License-Identifier: GPL-2.0-only
/*
 * Copyright (C) 2010 - 2022 Novatek, Inc.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 */

#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/fs.h>
#include <linux/miscdevice.h>
#include <linux/uaccess.h>
#include <linux/hid.h>
#include <linux/hidraw.h>
#include <linux/types.h>
#include "nt36xxx.h"

#if NVT_TOUCH_EXT_USI

#define GID_NUM					12
#define CAP_NUM					12
#define FW_VER_NUM				2

/*
 * The following HID Report Descriptor is copied from
 * USIv2-HID-Report-Descriptor.h from universalstylus.org.
 */
#define PHYSICAL_WIDTH				23585
#define LOGICAL_WIDTH				3200
#define PHYSICAL_HEIGHT				14740
#define LOGICAL_HEIGHT				5120

#define MAX_SUPPORTED_STYLI			1

/* Change these to your preferred values. */
#define	HID_REPORTID_TABLET			8
#define	HID_REPORTID_ERROR			10
#define	HID_REPORTID_GETSET_COLOR8		11
#define	HID_REPORTID_GETSET_WIDTH		12
#define	HID_REPORTID_GETSET_STYLE		13
#define	HID_REPORTID_DIAGNOSE			14
#define	HID_REPORTID_GETSET_BUTTONS		15
#define	HID_REPORTID_GET_FIRMWARE		16
#define	HID_REPORTID_GET_PROTOCOL		17
#define	HID_REPORTID_GETSET_VENDOR		18
#define	HID_REPORTID_SET_TRANSDUCER		19
#define	HID_REPORTID_GETSET_COLOR24		20

/* Convenience defines */
#define	TABLET_TIP				(1 << 0)
#define	TABLET_BARREL				(1 << 1)
#define	TABLET_SECONDARYBARREL			(1 << 2)
#define	TABLET_INVERT				(1 << 3)
#define	TABLET_ERASER				(1 << 4)
#define	TABLET_INRANGE				(1 << 5)

#define LOW_BYTE(x)				((x) & 0xFF)
#define HIGH_BYTE(x)				(((x) >> 8) & 0xFF)

/* 7.4 HID Descriptor for a Data Report */
static uint8_t USI_report_descriptor_v2_0[] = {
    0x05, 0x0d,                    // USAGE_PAGE (Digitizers)
    0x09, 0x02,                    // USAGE (Pen)
    0xa1, 0x01,                    // COLLECTION (Application)
    0x09, 0x20,                    //   USAGE (Stylus)
    0xa1, 0x00,                    //   COLLECTION (Physical)
    0x85, HID_REPORTID_TABLET,     //     REPORT_ID (HID_REPORTID_TABLET)
    0x05, 0x01,                    //     USAGE_PAGE (Generic Desktop)
    0xa4,                          //     PUSH
    0x09, 0x30,                    //     USAGE (X)
    0x35, 0x00,                    //     PHYSICAL_MINIMUM (0)
    0x47, LOW_BYTE(PHYSICAL_WIDTH), HIGH_BYTE(PHYSICAL_WIDTH), 0x00, 0x00,  // PHYSICAL_MAXIMUM (PHYSICAL_WIDTH)
    0x15, 0x00,                    //     LOGICAL_MINIMUM (0)
    0x27, LOW_BYTE(LOGICAL_WIDTH), HIGH_BYTE(LOGICAL_WIDTH), 0x00, 0x00,    // LOGICAL_MAXIMUM (LOGICAL_WIDTH)
    0x55, 0x0d,                    //     UNIT_EXPONENT (-3)
    0x65, 0x11,                    //     UNIT (Centimeter,SILinear)
    0x75, 0x10,                    //     REPORT_SIZE (16)
    0x95, 0x01,                    //     REPORT_COUNT (1)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x09, 0x31,                    //     USAGE (Y)
    0x47, LOW_BYTE(PHYSICAL_HEIGHT), HIGH_BYTE(PHYSICAL_HEIGHT), 0x00, 0x00, // PHYSICAL_MAXIMUM (PHYSICAL_HEIGT)
    0x27, LOW_BYTE(LOGICAL_HEIGHT), HIGH_BYTE(LOGICAL_HEIGHT), 0x00, 0x00,   // LOGICAL_MAXIMUM (LOGICAL_HEIGT)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0xb4,                          //     POP
    0x05, 0x0d,                    //     USAGE_PAGE (Digitizers)
    0x09, 0x38,                    //     USAGE (Transducer Index)
    0x95, 0x01,                    //     REPORT_COUNT (1)
    0x75, 0x08,                    //     REPORT_SIZE (8)
    0x15, 0x00,                    //     LOGICAL_MINIMUM (0)
    0x25, MAX_SUPPORTED_STYLI,     //     LOGICAL_MAXIMUM (MAX_SUPPORTED_STYLI)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x09, 0x30,                    //     USAGE (Tip Pressure)
    0x75, 0x10,                    //     REPORT_SIZE (16)
    0x26, 0xff, 0x0f,              //     LOGICAL_MAXIMUM (4095)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x09, 0x31,                    //     USAGE (Barrel Pressure)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x09, 0x42,                    //     USAGE (Tip Switch)
    0x09, 0x44,                    //     USAGE (Barrel Switch)
    0x09, 0x5a,                    //     USAGE (Secondary Barrel Switch)
    0x09, 0x3c,                    //     USAGE (Invert)
    0x09, 0x45,                    //     USAGE (Eraser)
    0x09, 0x32,                    //     USAGE (In Range)
    0x75, 0x01,                    //     REPORT_SIZE (1)
    0x95, 0x06,                    //     REPORT_COUNT (6)
    0x25, 0x01,                    //     LOGICAL_MAXIMUM (1)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x95, 0x02,                    //     REPORT_COUNT (2)
    0x81, 0x03,                    //     INPUT (Cnst,Var,Abs)
    0x09, 0x3d,                    //     USAGE (X Tilt)
    0x55, 0x0e,                    //     UNIT_EXPONENT (-2)
    0x65, 0x14,                    //     UNIT (Eng Rot:Angular Pos)
    0x36, 0xd8, 0xdc,              //     PHYSICAL_MINIMUM (-9000)
    0x46, 0x28, 0x23,              //     PHYSICAL_MAXIMUM (9000)
    0x16, 0xd8, 0xdc,              //     LOGICAL_MINIMUM (-9000)
    0x26, 0x28, 0x23,              //     LOGICAL_MAXIMUM (9000)
    0x95, 0x01,                    //     REPORT_COUNT (1)
    0x75, 0x10,                    //     REPORT_SIZE (16)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x09, 0x3e,                    //     USAGE (Y Tilt)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x09, 0x41,                    //     USAGE (Twist)
    0x15, 0x00,                    //     LOGICAL_MINIMUM (0)
    0x27, 0xa0, 0x8c, 0x00, 0x00,  //     LOGICAL_MAXIMUM (36000)
    0x35, 0x00,                    //     PHYSICAL_MINIMUM (0)
    0x47, 0xa0, 0x8c, 0x00, 0x00,  //     PHYSICAL_MAXIMUM (36000)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x05, 0x20,                    //     USAGE_PAGE (Sensors)
    0x0a, 0x53, 0x04,              //     USAGE (Data Field: Acceleration Axis X)
    0x65, 0x00,                    //     UNIT (None)
    0x16, 0x01, 0xf8,              //     LOGICAL_MINIMUM (-2047)
    0x26, 0xff, 0x07,              //     LOGICAL_MAXIMUM (2047)
    0x75, 0x10,                    //     REPORT_SIZE (16)
    0x95, 0x01,                    //     REPORT_COUNT (1)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x0a, 0x54, 0x04,              //     USAGE (Data Field: Acceleration Axis Y)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x0a, 0x55, 0x04,              //     USAGE (Data Field: Acceleration Axis Z)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x0a, 0x57, 0x04,              //     USAGE (Data Field: Angular Velocity Axis X)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x0a, 0x58, 0x04,              //     USAGE (Data Field: Angular Velocity Axis Y)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x0a, 0x59, 0x04,              //     USAGE (Data Field: Angular Velocity Axis Z)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x0a, 0x72, 0x04,              //     USAGE (Data Field: Heading X Axis)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x0a, 0x73, 0x04,              //     USAGE (Data Field: Heading Y Axis)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x0a, 0x74, 0x04,              //     USAGE (Data Field: Heading Z Axis)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x05, 0x0d,                    //     USAGE_PAGE (Digitizers)
    0x09, 0x3b,                    //     USAGE (Battery Strength)
    0x15, 0x00,                    //     LOGICAL_MINIMUM (0)
    0x25, 0x64,                    //     LOGICAL_MAXIMUM (100)
    0x75, 0x08,                    //     REPORT_SIZE (8)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x09, 0x5b,                    //     USAGE (Transducer Serial Number)
    0x17, 0x00, 0x00, 0x00, 0x80,  //     LOGICAL_MINIMUM(-2,147,483,648)
    0x27, 0xFF, 0xFF, 0xFF, 0x7F,  //     LOGICAL_MAXIMUM(2,147,483,647)
    0x75, 0x40,                    //     REPORT_SIZE (64)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x09, 0x6E,                    //     USAGE(Transducer Serial Number Part 2[110])
    0x75, 0x20,                    //     REPORT_SIZE (32)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x05, 0x0d,                    //     USAGE_PAGE (Digitizers)
    0x09, 0x5c,                    //     USAGE (Preferred Color)
    0x15, 0x00,                    //     LOGICAL_MINIMUM (0)
    0x26, 0xff, 0x00,              //     LOGICAL_MAXIMUM (255)
    0x75, 0x08,                    //     REPORT_SIZE (8)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x09, 0x5c,                    //     USAGE (Preferred Color)
    0x27, 0xff, 0xff, 0xff, 0x00,  //     LOGICAL_MAXIMUM (0x00FFFFFF)
    0x75, 0x18,                    //     REPORT_SIZE (24)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x09, 0x6f,                    //     USAGE (No Preferred Color)
    0x25, 0x01,                    //     LOGICAL_MAXIMUM (1)
    0x75, 0x01,                    //     REPORT_SIZE (1)
    0x95, 0x01,                    //     REPORT_COUNT (1)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x95, 0x07,                    //     REPORT_COUNT (7)
    0x81, 0x03,                    //     INPUT (Cnst,Var,Abs)
    0x09, 0x5e,                    //     USAGE (Preferred Line Width)
    0x26, 0xff, 0x00,              //     LOGICAL_MAXIMUM (255)
    0x75, 0x08,                    //     REPORT_SIZE (8)
    0x95, 0x01,                    //     REPORT_COUNT (1)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x09, 0x70,                    //     USAGE (Preferred Line Style)
    0xa1, 0x02,                    //     COLLECTION (Logical)
    0x15, 0x01,                    //       LOGICAL_MINIMUM (1)
    0x25, 0x06,                    //       LOGICAL_MAXIMUM (6)
    0x09, 0x72,                    //       USAGE (Ink)
    0x09, 0x73,                    //       USAGE (Pencil)
    0x09, 0x74,                    //       USAGE (Highlighter)
    0x09, 0x75,                    //       USAGE (Chisel Marker)
    0x09, 0x76,                    //       USAGE (Brush)
    0x09, 0x77,                    //       USAGE (No Preferred Line Style)
    0x81, 0x20,                    //       INPUT (Data,Ary,Abs,NPrf)
    0xc0,                          //     END_COLLECTION
    0x06, 0x00, 0xff,              //     USAGE_PAGE (Vendor Defined Page 1)
    0x09, 0x01,                    //     USAGE (Vendor Usage 1)
    0x15, 0x00,                    //     LOGICAL_MINIMUM (0)
    0x27, 0xff, 0xff, 0x00, 0x00,  //     LOGICAL_MAXIMUM (65535)
    0x75, 0x10,                    //     REPORT_SIZE (16)
    0x95, 0x01,                    //     REPORT_COUNT (1)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0x05, 0x0d,                    //     USAGE_PAGE (Digitizers)
    0x55, 0x0c,                    //     UNIT_EXPONENT (-4)
    0x66, 0x01, 0x10,              //     UNIT (SI Lin:Time)
    0x47, 0xff, 0xff, 0x00, 0x00,  //     PHYSICAL_MAXIMUM (65535)
    0x27, 0xff, 0xff, 0x00, 0x00,  //     LOGICAL_MAXIMUM (65535)
    0x09, 0x56,                    //     USAGE (Scan Time)
    0x75, 0x10,                    //     REPORT_SIZE (16)
    0x81, 0x02,                    //     INPUT (Data,Var,Abs)
    0xc0,                          //   END_COLLECTION

// 7.5 HID Descriptor for Status Reports

// The following is the portion of the HID descriptor for the status report that A USI
// controller shall support for reporting status and error conditions.

    0x05, 0x0d,                    //   USAGE_PAGE (Digitizers)
    0x85, HID_REPORTID_ERROR,      //   REPORT_ID (HID_REPORTID_ERROR)
    0x09, 0x38,                    //   USAGE (Transducer Index)
    0x75, 0x08,                    //   REPORT_SIZE (8)
    0x95, 0x01,                    //   REPORT_COUNT (1)
    0x15, 0x00,                    //   LOGICAL_MINIMUM (0)
    0x25, MAX_SUPPORTED_STYLI,     //   LOGICAL_MAXIMUM (MAX_SUPPORTED_STYLI)
    0x81, 0x02,                    //   INPUT (Data,Var,Abs)
    0x15, 0x01,                    //   LOGICAL_MINIMUM (1)
    0x25, 0x04,                    //   LOGICAL_MAXIMUM (4)
    0x09, 0x81,                    //   USAGE (Digitizer Error)
    0xa1, 0x02,                    //   COLLECTION (Logical)
    0x09, 0x82,                    //     USAGE (Err Normal Status)
    0x09, 0x83,                    //     USAGE (Err Transducers Exceeded)
    0x09, 0x84,                    //     USAGE (Err Full Trans Features Unavail)
    0x09, 0x85,                    //     USAGE (Err Charge Low)
    0x81, 0x20,                    //     INPUT (Data,Ary,Abs,NPrf)
    0xc0,                          //   END_COLLECTION


// 7.6 HID Descriptor for Feature Reports

// Following is the portion of the HID descriptor for the Get/Set Feature Reports.

    // Feature Get/Set - 8-Bit Line Color
    0x85, HID_REPORTID_GETSET_COLOR8,    //   REPORT_ID (HID_REPORTID_GETSET_COLOR8)
    0x15, 0x00,                    //   LOGICAL_MINIMUM (0)
    0x25, MAX_SUPPORTED_STYLI,     //   LOGICAL_MAXIMUM (MAX_SUPPORTED_STYLI)
    0x75, 0x08,                    //   REPORT_SIZE (8)
    0x95, 0x01,                    //   REPORT_COUNT (1)
    0x09, 0x38,                    //   USAGE (Transducer Index)
    0xb1, 0x02,                    //   FEATURE (Data,Var,Abs)
    0x09, 0x5c,                    //   USAGE (Preferred Color)
    0x26, 0xff, 0x00,              //   LOGICAL_MAXIMUM (255)
    0xb1, 0x02,                    //   FEATURE (Data,Var,Abs)
    0x09, 0x5d,                    //   USAGE (Preferred Color is Locked)
    0x75, 0x01,                    //   REPORT_SIZE (1)
    0x95, 0x01,                    //   REPORT_COUNT (1)
    0x25, 0x01,                    //   LOGICAL_MAXIMUM (1)
    0xb1, 0x02,                    //   FEATURE (Data,Var,Abs)
    0x95, 0x07,                    //   REPORT_COUNT (7)
    0xb1, 0x03,                    //   FEATURE (Cnst,Var,Abs)

    // Feature Get/Set - 24-Bit Line Color
    0x85, HID_REPORTID_GETSET_COLOR24,// REPORT_ID (HID_REPORTID_GETSET_COLOR24)
    0x15, 0x00,                    //   LOGICAL_MINIMUM (0)
    0x25, MAX_SUPPORTED_STYLI,     //   LOGICAL_MAXIMUM (MAX_SUPPORTED_STYLI)
    0x75, 0x08,                    //   REPORT_SIZE (8)
    0x95, 0x01,                    //   REPORT_COUNT (1)
    0x09, 0x38,                    //   USAGE (Transducer Index)
    0xb1, 0x02,                    //   FEATURE (Data,Var,Abs)
    0x09, 0x5c,                    //   USAGE (Preferred Color)
    0x27, 0xff, 0xff, 0xff, 0x00,  //   LOGICAL_MAXIMUM (0xFFFFFF)
    0x75, 0x18,                    //   REPORT_SIZE (24)
    0xb1, 0x02,                    //   FEATURE (Data,Var,Abs)
    0x09, 0x6f,                    //   USAGE (No Preferred Color)
    0x75, 0x01,                    //   REPORT_SIZE (1)
    0x25, 0x01,                    //   LOGICAL_MAXIMUM (1)
    0xb1, 0x02,                    //   FEATURE (Data,Var,Abs)
    0x09, 0x5d,                    //   USAGE (Preferred Color is Locked)
    0xb1, 0x02,                    //   FEATURE (Data,Var,Abs)
    0x95, 0x06,                    //   REPORT_COUNT (6)
    0xb1, 0x03,                    //   FEATURE (Cnst,Var,Abs)

    // Feature Get/Set - Line Width
    0x85, HID_REPORTID_GETSET_WIDTH,    //   REPORT_ID (HID_REPORTID_GETSET_WIDTH)
    0x09, 0x38,                    //   USAGE (Transducer Index)
    0x15, 0x00,                    //   LOGICAL_MINIMUM (0)
    0x25, MAX_SUPPORTED_STYLI,     //   LOGICAL_MAXIMUM (MAX_SUPPORTED_STYLI)
    0x75, 0x08,                    //   REPORT_SIZE (8)
    0x95, 0x01,                    //   REPORT_COUNT (1)
    0xb1, 0x02,                    //   FEATURE (Data,Var,Abs)
    0x09, 0x5e,                    //   USAGE (Preferred Line Width)
    0x26, 0xff, 0x00,              //   LOGICAL_MAXIMUM (255)
    0xb1, 0x02,                    //   FEATURE (Data,Var,Abs)
    0x09, 0x5f,                    //   USAGE (Preferred Line Width is Locked)
    0x75, 0x01,                    //   REPORT_SIZE (1)
    0x25, 0x01,                    //   LOGICAL_MAXIMUM (1)
    0xb1, 0x02,                    //   FEATURE (Data,Var,Abs)
    0x75, 0x07,                    //   REPORT_SIZE (7)
    0xb1, 0x03,                    //   FEATURE (Cnst,Var,Abs)

    // Feature Get/Set - Line Style
    0x85, HID_REPORTID_GETSET_STYLE,    //   REPORT_ID (HID_REPORTID_GETSET_STYLE)
    0x75, 0x08,                    //   REPORT_SIZE (8)
    0x95, 0x01,                    //   REPORT_COUNT (1)
    0x15, 0x00,                    //   LOGICAL_MINIMUM (0)
    0x25, MAX_SUPPORTED_STYLI,     //   LOGICAL_MAXIMUM (MAX_SUPPORTED_STYLI)
    0x09, 0x38,                    //   USAGE (Transducer Index)
    0xb1, 0x22,                    //   FEATURE (Data,Var,Abs,NPrf)
    0x09, 0x70,                    //   USAGE (Preferred Line Style)
    0x15, 0x01,                    //   LOGICAL_MINIMUM (1)
    0x25, 0x06,                    //   LOGICAL_MAXIMUM (6)
    0xa1, 0x02,                    //   COLLECTION (Logical)
    0x09, 0x72,                    //     USAGE (Ink)
    0x09, 0x73,                    //     USAGE (Pencil)
    0x09, 0x74,                    //     USAGE (Highlighter)
    0x09, 0x75,                    //     USAGE (Chisel Marker)
    0x09, 0x76,                    //     USAGE (Brush)
    0x09, 0x77,                    //     USAGE (No Preferred Line Style)
    0xb1, 0x20,                    //     FEATURE (Data,Ary,Abs,NPrf)
    0xc0,                          //   END_COLLECTION
    0x09, 0x71,                    //   USAGE (Preferred Line Style is Locked)
    0x75, 0x01,                    //   REPORT_SIZE (1)
    0x15, 0x00,                    //   LOGICAL_MINIMUM (0)
    0x25, 0x01,                    //   LOGICAL_MAXIMUM (1)
    0xb1, 0x02,                    //   FEATURE (Data,Var,Abs)
    0x75, 0x07,                    //   REPORT_SIZE (7)
    0xb1, 0x03,                    //   FEATURE (Cnst,Var,Abs)

    // Feature Get/Set - Diagnostic
    0x85, HID_REPORTID_DIAGNOSE,        //   REPORT_ID (HID_REPORTID_DIAGNOSE)
    0x09, 0x80,                    //   USAGE (Digitizer Diagnostic)
    0x15, 0x00,                    //   LOGICAL_MINIMUM (0)
    0x75, 0x40,                    //   REPORT_SIZE (64)
    0x95, 0x01,                    //   REPORT_COUNT (1)
    0xb1, 0x02,                    //   FEATURE (Data,Var,Abs)

    // Feature Get/Set - Buttons
    0x85, HID_REPORTID_GETSET_BUTTONS,  //   REPORT_ID (HID_REPORTID_GETSET_BUTTONS)
    0x09, 0xa5,                    //   USAGE (Transducer Switches)
    0xa1, 0x02,                    //   COLLECTION (Logical)
    0x09, 0x38,                    //     USAGE (Transducer Index)
    0x75, 0x08,                    //     REPORT_SIZE (8)
    0x95, 0x01,                    //     REPORT_COUNT (1)
    0x25, MAX_SUPPORTED_STYLI,     //     LOGICAL_MAXIMUM (MAX_SUPPORTED_STYLI)
    0xb1, 0x02,                    //     FEATURE (Data,Var,Abs)
    0x15, 0x01,                    //     LOGICAL_MINIMUM (1)
    0x25, 0x05,                    //     LOGICAL_MAXIMUM (5)
    0x09, 0x44,                    //     USAGE (Barrel Switch)
    0xa1, 0x02,                    //     COLLECTION (Logical)
    0x09, 0xa4,                    //       USAGE (Switch Unimplemented)
    0x09, 0x44,                    //       USAGE (Barrel Switch)
    0x09, 0x5a,                    //       USAGE (Secondary Barrel Switch)
    0x09, 0x45,                    //       USAGE (Eraser)
    0x09, 0xa3,                    //       USAGE (Switch Disabled)
    0xb1, 0x20,                    //       FEATURE (Data,Ary,Abs,NPrf)
    0xc0,                          //     END_COLLECTION
    0x09, 0x5a,                    //     USAGE (Secondary Barrel Switch)
    0xa1, 0x02,                    //     COLLECTION (Logical)
    0x09, 0xa4,                    //       USAGE (Switch Unimplemented)
    0x09, 0x44,                    //       USAGE (Barrel Switch)
    0x09, 0x5a,                    //       USAGE (Secondary Barrel Switch)
    0x09, 0x45,                    //       USAGE (Eraser)
    0x09, 0xa3,                    //       USAGE (Switch Disabled)
    0xb1, 0x20,                    //       FEATURE (Data,Ary,Abs,NPrf)
    0xc0,                          //     END_COLLECTION
    0x09, 0x45,                    //     USAGE (Eraser)
    0xa1, 0x02,                    //     COLLECTION (Logical)
    0x09, 0xa4,                    //       USAGE (Switch Unimplemented)
    0x09, 0x44,                    //       USAGE (Barrel Switch)
    0x09, 0x5a,                    //       USAGE (Secondary Barrel Switch)
    0x09, 0x45,                    //       USAGE (Eraser)
    0x09, 0xa3,                    //       USAGE (Switch Disabled)
    0xb1, 0x20,                    //       FEATURE (Data,Ary,Abs,NPrf)
    0xc0,                          //     END_COLLECTION
    0xc0,                          //   END_COLLECTION

    // Feature Get - Firmware Version
    0x85, HID_REPORTID_GET_FIRMWARE,    //   REPORT_ID (HID_REPORTID_GET_FIRMWARE)
    0x75, 0x08,                    //   REPORT_SIZE (8)
    0x95, 0x01,                    //   REPORT_COUNT (1)
    0x05, 0x0d,                    //   USAGE_PAGE (Digitizers)
    0x09, 0x90,                    //   USAGE (Transducer Software Info.)
    0xa1, 0x02,                    //   COLLECTION (Logical)
    0x09, 0x38,                    //     USAGE (Transducer Index)
    0x15, 0x00,                    //     LOGICAL_MINIMUM (0)
    0x25, MAX_SUPPORTED_STYLI,     //     LOGICAL_MAXIMUM (MAX_SUPPORTED_STYLI)
    0xb1, 0x02,                    //     FEATURE (Data,Var,Abs)
    0x09, 0x5b,                    //     USAGE (Transducer Serial Number)
    0x17, 0x00, 0x00, 0x00, 0x80,  //     LOGICAL_MINIMUM(-2,147,483,648)
    0x27, 0xFF, 0xFF, 0xFF, 0x7F,  //     LOGICAL_MAXIMUM(2,147,483,647)
    0x75, 0x40,                    //     REPORT_SIZE (64)
    0xb1, 0x02,                    //     FEATURE (Data,Var,Abs)
    0x09, 0x6E,                    //     USAGE(Transducer Serial Number Part 2[110])
    0x75, 0x20,                    //     REPORT_SIZE (32)
    0xb1, 0x02,                    //     FEATURE (Data,Var,Abs)
    0x09, 0x91,                    //     USAGE (Transducer Vendor ID)
    0x75, 0x10,                    //     REPORT_SIZE (16)
    0x15, 0x00,                    //     LOGICAL_MINIMUM (0)
    0x26, 0xff, 0x0f,              //     LOGICAL_MAXIMUM (4095)
    0xb1, 0x02,                    //     FEATURE (Data,Var,Abs)
    0x09, 0x92,                    //     USAGE (Transducer Product ID)
    0x27, 0xff, 0xff, 0x00, 0x00,  //     LOGICAL_MAXIMUM (65535)
    0xb1, 0x02,                    //     FEATURE (Data,Var,Abs)
    0x05, 0x06,                    //     USAGE_PAGE (Generic Device)
    0x09, 0x2a,                    //     USAGE (Software Version)
    0x75, 0x08,                    //     REPORT_SIZE (8)
    0x26, 0xff, 0x00,              //     LOGICAL_MAXIMUM (255)
    0xa1, 0x02,                    //     COLLECTION (Logical)
    0x09, 0x2d,                    //       USAGE (Major)
    0xb1, 0x02,                    //       FEATURE (Data,Var,Abs)
    0x09, 0x2e,                    //       USAGE (Minor)
    0xb1, 0x02,                    //       FEATURE (Data,Var,Abs)
    0xc0,                          //     END_COLLECTION
    0xc0,                          //   END_COLLECTION

    // Feature Get - USI Version
    0x85, HID_REPORTID_GET_PROTOCOL,    //   REPORT_ID (HID_REPORTID_GET_PROTOCOL)
    0x05, 0x0d,                    //   USAGE_PAGE (Digitizers)
    0x25, MAX_SUPPORTED_STYLI,     //   LOGICAL_MAXIMUM (MAX_SUPPORTED_STYLI)
    0x09, 0x38,                    //   USAGE (Transducer Index)
    0xb1, 0x02,                    //   FEATURE (Data,Var,Abs)
    0x05, 0x06,                    //   USAGE_PAGE (Generic Device)
    0x09, 0x2b,                    //   USAGE (Protocol Version)
    0xa1, 0x02,                    //   COLLECTION (Logical)
    0x09, 0x2d,                    //     USAGE (Major)
    0x26, 0xff, 0x00,              //     LOGICAL_MAXIMUM (255)
    0xb1, 0x02,                    //     FEATURE (Data,Var,Abs)
    0x09, 0x2e,                    //     USAGE (Minor)
    0xb1, 0x02,                    //     FEATURE (Data,Var,Abs)
    0xc0,                          //   END_COLLECTION

    // Feature Get/Set - Vendor Specific
    0x85, HID_REPORTID_GETSET_VENDOR,   //   REPORT_ID (HID_REPORTID_GETSET_VENDOR)
    0x05, 0x0d,                    //   USAGE_PAGE (Digitizers)
    0x09, 0x38,                    //   USAGE (Transducer Index)
    0x75, 0x08,                    //   REPORT_SIZE (8)
    0x95, 0x01,                    //   REPORT_COUNT (1)
    0x25, MAX_SUPPORTED_STYLI,     //   LOGICAL_MAXIMUM (MAX_SUPPORTED_STYLI)
    0xb1, 0x02,                    //   FEATURE (Data,Var,Abs)
    0x06, 0x00, 0xff,              //   USAGE_PAGE (Vendor Defined Page 1)
    0x09, 0x01,                    //   USAGE (Vendor Usage 1)
    0x75, 0x10,                    //   REPORT_SIZE (16)
    0x27, 0xff, 0xff, 0x00, 0x00,  //   LOGICAL_MAXIMUM (65535)
    0xb1, 0x02,                    //   FEATURE (Data,Var,Abs)


    // Feature Set - Select Transducer Index
    0x85, HID_REPORTID_SET_TRANSDUCER,  //   REPORT_ID (HID_REPORTID_SET_TRANSDUCER)
    0x05, 0x0d,                    //   USAGE_PAGE (Digitizers)
    0x09, 0xa6,                    //   USAGE (Transducer Index Selector)
    0x75, 0x08,                    //   REPORT_SIZE (8)
    0x95, 0x01,                    //   REPORT_COUNT (1)
    0x15, 0x00,                    //   LOGICAL_MINIMUM (0)
    0x25, MAX_SUPPORTED_STYLI,     //   LOGICAL_MAXIMUM (MAX_SUPPORTED_STYLI)
    0xb1, 0x02,                    //   FEATURE (Data,Var,Abs)

    0xc0                           // END_COLLECTION
};

struct nvt_usi_context {
	/*
	 * Bit Fields:
	 * A bit represents if the corresponding read command is done or not.
	 */
	uint32_t stylus_read_map;

	/* responses from a paired stylus. */
	uint8_t stylus_cap[CAP_NUM];		/* C.GetCapability() */
	uint8_t stylus_GID[GID_NUM];		/* C.GetGID() */
	uint8_t stylus_fw_ver[FW_VER_NUM];	/* C.GetFirmwareVersion() */
	uint8_t stylus_battery;			/* C.GetBattery() */

	uint8_t stylus_hash_id[USI_HASH_ID_SIZE];
	uint8_t stylus_session_id[USI_SESSION_ID_SIZE];
	uint8_t stylus_freq_seed;
};

static struct nvt_usi_context *usi_ctx;

#define DEFAULT_STYLUS_INDEX		1

#define HID_MIN_REPORT_SIZE		2
#define HID_DIAGNOSTIC_RETURN_SIZE	3

#define SET_FEATURE_HOST_CMD		0x73
#define DIAGNOSTIC_HOST_CMD		0x74
#define GET_FEATURE_HOST_CMD		0x75

#define USI_VENDOR_ID			0x0603
#define USI_PRODUCT_ID			0xFFFF

#define HID_PEN_INFO_ADDR		0x2FE9E

#define HID_STYLUS_STYLE_NO_REFERENCE	6
#define USI_STYLUS_STYLE_NO_REFERENCE	255

/* SPI GET COMMANDS */
enum {
	SPI_INDEX_DIAGNOSTIC	= 0,
	SPI_INDEX_GET_COLOR8,
	SPI_INDEX_GET_WIDTH,
	SPI_INDEX_GET_STYLE,
	SPI_INDEX_GET_BUTTONS,
	SPI_INDEX_GET_GID,
	SPI_INDEX_NOT_USED,
	SPI_INDEX_GET_FIRMWARE,
	SPI_INDEX_GET_PROTOCOL,
	SPI_INDEX_GET_COLOR24,
};

/* SPI SET COMMANDS */
enum {
	SPI_INDEX_SET_COLOR8	= 1,
	SPI_INDEX_SET_WIDTH,
	SPI_INDEX_SET_STYLE,
	SPI_INDEX_SET_BUTTONS,
	SPI_INDEX_SET_COLOR24,
};

/* max feature report size + 1 - currently it's GET_FIRMWARE */
#define MAX_SPI_BUF_SIZE		(20 + 1)

struct hid_feature_report_info {
	uint8_t id;
	uint16_t size;
	int32_t vendor_get_cmd;
	int32_t vendor_set_cmd;
} hid_feature_report_infos[] = {
	{HID_REPORTID_GETSET_COLOR8,	4,	SPI_INDEX_GET_COLOR8,	SPI_INDEX_SET_COLOR8},
	{HID_REPORTID_GETSET_WIDTH,	4,	SPI_INDEX_GET_WIDTH,	SPI_INDEX_SET_WIDTH},
	{HID_REPORTID_GETSET_STYLE,	4,	SPI_INDEX_GET_STYLE,	SPI_INDEX_SET_STYLE},
	{HID_REPORTID_DIAGNOSE,		9,	SPI_INDEX_DIAGNOSTIC,	-1},
	{HID_REPORTID_GETSET_BUTTONS,	5,	SPI_INDEX_GET_BUTTONS,	SPI_INDEX_SET_BUTTONS},
	{HID_REPORTID_GET_FIRMWARE,	20,	SPI_INDEX_GET_GID,	-1},
	{HID_REPORTID_GET_PROTOCOL,	4,	SPI_INDEX_GET_PROTOCOL,	-1},
	{HID_REPORTID_GETSET_VENDOR,	4,	-1,			-1},
	{HID_REPORTID_SET_TRANSDUCER,	2,	-1,			-1},
	{HID_REPORTID_GETSET_COLOR24,	6,	SPI_INDEX_GET_COLOR24,	SPI_INDEX_SET_COLOR24},
};

static int32_t device_open(struct inode *inode, struct file *file)
{
	return 0;
}

static int32_t device_release(struct inode *inode, struct file *file)
{
	return 0;
}

int32_t nvt_usi_store_gid(const uint8_t *buf_gid)
{
	if (!usi_ctx)
		return -EINVAL;

	memcpy(usi_ctx->stylus_GID, buf_gid, sizeof(usi_ctx->stylus_GID));
	usi_ctx->stylus_read_map |= USI_GID_FLAG;

	return 0;
}

int32_t nvt_usi_store_fw_version(const uint8_t *buf_fw_ver)
{
	if (!usi_ctx)
		return -EINVAL;

	memcpy(usi_ctx->stylus_fw_ver, buf_fw_ver,
	       sizeof(usi_ctx->stylus_fw_ver));
	usi_ctx->stylus_read_map |= USI_FW_VERSION_FLAG;

	return 0;
}

int32_t nvt_usi_get_fw_version(uint8_t *buf_fw_ver)
{
	if (!usi_ctx)
		return -EINVAL;

	if (!(usi_ctx->stylus_read_map & USI_FW_VERSION_FLAG))
		return -ENODATA;

	memcpy(buf_fw_ver, usi_ctx->stylus_fw_ver, sizeof(usi_ctx->stylus_fw_ver));

	return 0;
}

int32_t nvt_usi_store_capability(const uint8_t *buf_cap)
{
	if (!usi_ctx)
		return -EINVAL;

	memcpy(usi_ctx->stylus_cap, buf_cap, sizeof(usi_ctx->stylus_cap));
	usi_ctx->stylus_read_map |= USI_CAPABILITY_FLAG;

	return 0;
}

int32_t nvt_usi_store_battery(const uint8_t *buf_bat)
{
	if (!usi_ctx)
		return -EINVAL;

	usi_ctx->stylus_battery = buf_bat[0];
	usi_ctx->stylus_read_map |= USI_BATTERY_FLAG;

	return 0;
}

int32_t nvt_usi_get_battery(uint8_t *bat)
{
	if (!usi_ctx)
		return -EINVAL;

	if (!(usi_ctx->stylus_read_map & USI_BATTERY_FLAG))
		return -ENODATA;

	*bat = usi_ctx->stylus_battery;

	return 0;
}

int32_t nvt_usi_get_serial_number(uint32_t *serial_high, uint32_t *serial_low)
{
	if (!usi_ctx)
		return -EINVAL;

	if (!(usi_ctx->stylus_read_map & USI_GID_FLAG))
		return -ENODATA;

	if (serial_low)
		*serial_low = usi_ctx->stylus_GID[0] |
				usi_ctx->stylus_GID[1] << 8 |
				usi_ctx->stylus_GID[2] << 16 |
				usi_ctx->stylus_GID[3] << 24;

	if (serial_high)
		*serial_high = usi_ctx->stylus_GID[4] |
				usi_ctx->stylus_GID[5] << 8 |
				usi_ctx->stylus_GID[6] << 16 |
				usi_ctx->stylus_GID[7] << 24;

	return 0;
}

int32_t nvt_usi_get_vid_pid(uint16_t *vid, uint16_t *pid)
{
	if (!usi_ctx)
		return -EINVAL;

	if (!(usi_ctx->stylus_read_map & USI_GID_FLAG))
		return -ENODATA;

	*vid = usi_ctx->stylus_GID[8] | usi_ctx->stylus_GID[9] << 8;
	*pid = usi_ctx->stylus_GID[10] | usi_ctx->stylus_GID[11] << 8;

	return 0;
}

int32_t nvt_usi_store_hash_id(const uint8_t *buf_hash_id)
{
	if (!usi_ctx)
		return -EINVAL;

	memcpy(usi_ctx->stylus_hash_id, buf_hash_id, sizeof(usi_ctx->stylus_hash_id));
	usi_ctx->stylus_read_map |= USI_HASH_ID_FLAG;

	return 0;
}

int32_t nvt_usi_get_hash_id(uint8_t *buf_hash_id)
{
	if (!usi_ctx)
		return -EINVAL;

	if (!(usi_ctx->stylus_read_map & USI_HASH_ID_FLAG))
		return -ENODATA;

	memcpy(buf_hash_id, usi_ctx->stylus_hash_id, sizeof(usi_ctx->stylus_hash_id));

	return 0;
}

int32_t nvt_usi_store_session_id(const uint8_t *buf_session_id)
{
	if (!usi_ctx)
		return -EINVAL;

	memcpy(usi_ctx->stylus_session_id, buf_session_id, sizeof(usi_ctx->stylus_session_id));
	usi_ctx->stylus_read_map |= USI_SESSION_ID_FLAG;

	return 0;
}

int32_t nvt_usi_get_session_id(uint8_t *buf_session_id)
{
	if (!usi_ctx)
		return -EINVAL;

	if (!(usi_ctx->stylus_read_map & USI_SESSION_ID_FLAG))
		return -ENODATA;

	memcpy(buf_session_id, usi_ctx->stylus_session_id, sizeof(usi_ctx->stylus_session_id));

	return 0;
}

int32_t nvt_usi_store_freq_seed(const uint8_t *buf_freq_seed)
{
	if (!usi_ctx)
		return -EINVAL;

	usi_ctx->stylus_freq_seed = *buf_freq_seed;
	usi_ctx->stylus_read_map |= USI_FREQ_SEED_FLAG;

	return 0;
}

int32_t nvt_usi_get_freq_seed(uint8_t *buf_freq_seed)
{
	if (!usi_ctx)
		return -EINVAL;

	if (!(usi_ctx->stylus_read_map & USI_FREQ_SEED_FLAG))
		return -ENODATA;

	*buf_freq_seed = usi_ctx->stylus_freq_seed;

	return 0;
}

int32_t nvt_usi_get_validity_flags(uint16_t *validity_flags)
{
	if (!usi_ctx)
		return -EINVAL;

	*validity_flags = 0;

	/*
	 * validity_flag is to show what data is available in the driver.
	 * the validity_flag is sent to the controller FW during the resume()
	 * so that the controller only asks stylus the data that the driver doesn't have.
	 */
	if (usi_ctx->stylus_read_map & USI_FW_VERSION_FLAG)
		*validity_flags = 1; /* Tell the controller that driver has the USI FW version */

	if (usi_ctx->stylus_read_map & USI_CAPABILITY_FLAG)
		*validity_flags |= 2; /* Tell the controller that driver has the USI Capability */

	if (usi_ctx->stylus_read_map & USI_GID_FLAG)
		*validity_flags |= 4; /* Tell the controller that driver has the USI GID */

	return 0;
}

int32_t nvt_usi_clear_stylus_read_map(void)
{
	if (!usi_ctx)
		return -EINVAL;

	usi_ctx->stylus_read_map = 0;

	return 0;
}

#define USI_HID_FIRMWARE_INFO_READY	(USI_GID_FLAG | USI_FW_VERSION_FLAG)
static int32_t get_hid_firmware_info(uint8_t *hid_buf)
{
	if ((usi_ctx->stylus_read_map & USI_HID_FIRMWARE_INFO_READY) !=
	    USI_HID_FIRMWARE_INFO_READY)
		return -ENODATA;

	/* USI 2.0 spec. 7.3.3.1.3 Get Stylus Firmware Info */
	/* 64bits Transducer Serial Number : GID0 ~ GID3 */
	memcpy(hid_buf + 2, usi_ctx->stylus_GID, 8);

	/* 32bits Transducer Serial Number Part 2 : GID2 ~ GID3 */
	memcpy(hid_buf + 10, usi_ctx->stylus_GID + 4, 4);

	/* VID/PID : GID4 ~ GID5 */
	memcpy(hid_buf + 14, usi_ctx->stylus_GID + 8, 4);

	/* FW version major/minor */
	hid_buf[18] = usi_ctx->stylus_fw_ver[1];
	hid_buf[19] = usi_ctx->stylus_fw_ver[0];

	return 0;
}

static struct hid_feature_report_info *get_feature_report_info(int32_t rpt_id)
{
	int32_t i;

	for (i = 0; i < ARRAY_SIZE(hid_feature_report_infos); i++) {
		if (hid_feature_report_infos[i].id == rpt_id)
			return &hid_feature_report_infos[i];
	}

	return NULL;
}

static int32_t get_usi_data(uint8_t *spi_buf, uint8_t usi_vendor_get_cmd,
			    uint8_t len)
{
	int32_t retry = 5;

	mutex_lock(&ts->lock);
	nvt_set_page(ts->mmap->EVENT_BUF_ADDR);
	spi_buf[0] = EVENT_MAP_HOST_CMD & (0x7F);
	spi_buf[1] = GET_FEATURE_HOST_CMD;
	spi_buf[2] = 0;
	spi_buf[3] = usi_vendor_get_cmd;
	CTP_SPI_WRITE(ts->client, spi_buf, 4);
	mutex_unlock(&ts->lock);

	spi_buf[2] = 0xFF;
	while (retry) {
		mutex_lock(&ts->lock);
		CTP_SPI_READ(ts->client, spi_buf, 3);
		mutex_unlock(&ts->lock);
		if (spi_buf[2] == 0xA0)
			break;
		NVT_ERR("retry get usi data : %d\n", retry);
		msleep(20);
		retry--;
	}
	if (!retry) {
		NVT_ERR("Pen get feature failed\n");
		return -EAGAIN;
	}

	mutex_lock(&ts->lock);
	nvt_set_page(HID_PEN_INFO_ADDR);
	spi_buf[0] = HID_PEN_INFO_ADDR & (0x7F);
	CTP_SPI_READ(ts->client, spi_buf, len + 1);
	nvt_set_page(ts->mmap->EVENT_BUF_ADDR);
	mutex_unlock(&ts->lock);

	return len + 1;
}

static int32_t get_usi_data_diag(uint8_t *spi_buf, uint8_t *hid_buf)
{
	int32_t retry = 10;

	mutex_lock(&ts->lock);
	// write the diag cmd inside get feature, host will send the buf same as set
	nvt_set_page(ts->mmap->EVENT_BUF_ADDR);
	spi_buf[0] = EVENT_MAP_HOST_CMD & (0x7F);
	spi_buf[1] = DIAGNOSTIC_HOST_CMD;
	// since we only have 5 bytes left to append host cmd
	// remap cmd to prevent overwriting the A0 check bits
	spi_buf[2] = hid_buf[1] & 1;
	spi_buf[3] = (hid_buf[1] >> 1) + ((hid_buf[2] & 1) << 7);
	spi_buf[4] = (hid_buf[2] >> 1) + ((hid_buf[3] & 1) << 7);
	spi_buf[5] = (hid_buf[3] >> 1) + ((hid_buf[4] & 1) << 7);
	spi_buf[6] = (hid_buf[4] >> 1) + ((hid_buf[5] & 1) << 7);
	CTP_SPI_WRITE(ts->client, spi_buf, 7);
	mutex_unlock(&ts->lock);

	while (retry) {
		mutex_lock(&ts->lock);
		CTP_SPI_READ(ts->client, spi_buf, 3);
		mutex_unlock(&ts->lock);
		if ((spi_buf[2] & 0xF0) == 0xA0)
			break;
		NVT_ERR("retry get usi data diag : %d\n", retry);
		msleep(20);
		retry--;
	}
	if (!retry) {
		NVT_ERR("Pen diagnostic failed\n");
		return -EAGAIN;
	}
	mutex_lock(&ts->lock);
	nvt_set_page(HID_PEN_INFO_ADDR);
	spi_buf[0] = HID_PEN_INFO_ADDR & (0x7F);
	CTP_SPI_READ(ts->client, spi_buf, HID_DIAGNOSTIC_RETURN_SIZE + 1);
	nvt_set_page(ts->mmap->EVENT_BUF_ADDR);
	mutex_unlock(&ts->lock);

	return HID_DIAGNOSTIC_RETURN_SIZE + 1;
}

static int32_t get_hid_feature_report(uint8_t *hid_buf, int32_t buf_size,
				      struct hid_feature_report_info *rpt_info)
{
	uint8_t spi_buf[MAX_SPI_BUF_SIZE];
	int32_t ret = -1;

	if (rpt_info->id != HID_REPORTID_DIAGNOSE)
		hid_buf[1] = DEFAULT_STYLUS_INDEX;

	switch (rpt_info->id) {
	case HID_REPORTID_GETSET_COLOR8:
	case HID_REPORTID_GETSET_WIDTH:
	case HID_REPORTID_GETSET_STYLE:
		ret = get_usi_data(spi_buf, rpt_info->vendor_get_cmd, 2);
		if (ret > 0) {
			memcpy(hid_buf + 2, spi_buf + 1, 2);
			ret = rpt_info->size;
		}

		/* USI to HID conversion for No Preference */
		if (rpt_info->id == HID_REPORTID_GETSET_STYLE &&
		    hid_buf[2] == USI_STYLUS_STYLE_NO_REFERENCE)
			hid_buf[2] = HID_STYLUS_STYLE_NO_REFERENCE;
		break;
	case HID_REPORTID_DIAGNOSE:
		ret = get_usi_data_diag(spi_buf, hid_buf);
		if (ret > 0) {
			memcpy(hid_buf + 1, spi_buf + 1,
			       HID_DIAGNOSTIC_RETURN_SIZE);
			ret = rpt_info->size;
		}
		break;
	case HID_REPORTID_GETSET_BUTTONS:
		ret = get_usi_data(spi_buf, rpt_info->vendor_get_cmd, 3);
		if (ret > 0) {
			/* 3 buttons : barrel, secondary, eraser */
			memcpy(hid_buf + 2, spi_buf + 1, 3);
			ret = rpt_info->size;
		}
		break;
	case HID_REPORTID_GET_FIRMWARE:
		ret = get_hid_firmware_info(hid_buf);
		if (!ret)
			ret = rpt_info->size;
		else /* data is not ready yet */
			ret = 0;
		break;
	case HID_REPORTID_GET_PROTOCOL:
		hid_buf[2] = 2;
		hid_buf[3] = 0;
		ret = rpt_info->size;
		break;
	case HID_REPORTID_GETSET_VENDOR:
		break;
	case HID_REPORTID_GETSET_COLOR24:
		ret = get_usi_data(spi_buf, rpt_info->vendor_get_cmd, 4);
		if (ret > 0) {
			memcpy(hid_buf + 2, spi_buf + 1, 4);
			ret = rpt_info->size;
		}
		break;
	}

	msleep(20);

	return ret;
}

static int32_t set_hid_feature_report(uint8_t *buf, int32_t buf_size,
				      struct hid_feature_report_info *rpt_info)
{
	uint8_t spi_buf[MAX_SPI_BUF_SIZE];
	int32_t res, i;

	spi_buf[0] = EVENT_MAP_HOST_CMD & (0x7F);
	spi_buf[1] = SET_FEATURE_HOST_CMD;
	spi_buf[2] = (uint8_t)rpt_info->vendor_set_cmd;
	memcpy(spi_buf + 3, buf + 2, buf_size - 2);

	/* HID to USI conversion */
	if (rpt_info->id == HID_REPORTID_GETSET_BUTTONS) {
		for (i = 3; i < 6; i++) {
			if (spi_buf[i] < 1 || spi_buf[i] > 5) {
				NVT_ERR("Invalid button input\n");
				return -EINVAL;
			}

			spi_buf[i] -= 1;
		}
	} else if (rpt_info->id == HID_REPORTID_GETSET_STYLE) {
		if (spi_buf[3] == HID_STYLUS_STYLE_NO_REFERENCE)
			spi_buf[3] = USI_STYLUS_STYLE_NO_REFERENCE;
	}

	mutex_lock(&ts->lock);
	nvt_set_page(ts->mmap->EVENT_BUF_ADDR);
	res = CTP_SPI_WRITE(ts->client, spi_buf, buf_size + 1);
	mutex_unlock(&ts->lock);

	if (res < 0)
		NVT_ERR("SPI error: cannot set feature report\n");

	msleep(20);

	return res;
}

static long device_ioctl(struct file *filep, uint32_t cmd, unsigned long arg)
{
	struct hid_feature_report_info *rpt_info;
	uint8_t *hid_buf;
	void __user *user_arg = (void __user *)arg;
	uint32_t hid_len;
	uint32_t len_arg;
	int32_t ret = 0;
	struct hidraw_devinfo dinfo;
	uint8_t rpt_id;

	switch (cmd) {
	case HIDIOCGRDESCSIZE:
		if (put_user(sizeof(USI_report_descriptor_v2_0), (int __user *)arg))
			ret = -EFAULT;
		break;
	case HIDIOCGRDESC:
		if (get_user(len_arg, (int __user *)arg))
			ret = -EFAULT;
		else if (len_arg > HID_MAX_DESCRIPTOR_SIZE - 1)
			ret = -EINVAL;
		else if (copy_to_user(user_arg + offsetof(
			 struct hidraw_report_descriptor, value[0]),
			 USI_report_descriptor_v2_0,
			 min((uint32_t)sizeof(USI_report_descriptor_v2_0),
			     len_arg)))
			ret = -EFAULT;
		break;
	case HIDIOCGRAWINFO:
		dinfo.bustype = BUS_SPI;
		dinfo.vendor = USI_VENDOR_ID;
		dinfo.product = USI_PRODUCT_ID;
		if (copy_to_user(user_arg, &dinfo, sizeof(dinfo)))
			ret = -EFAULT;
		break;
	default :
		if (ts->pen_format_id == 0xFF) {
			NVT_ERR("No pen detected\n");
			return 0;
		}

		hid_len = _IOC_SIZE(cmd);
		if (hid_len < HID_MIN_REPORT_SIZE) {
			NVT_ERR("The report is too small : %d\n", hid_len);
			return -EINVAL;
		}

		hid_buf = memdup_user(user_arg, hid_len);
		if (IS_ERR(hid_buf))
			return PTR_ERR(hid_buf);

		rpt_id = hid_buf[0];

		rpt_info = get_feature_report_info(rpt_id);
		if (!rpt_info) {
			NVT_ERR("Invalid report : %d\n", rpt_id);
			ret = -EINVAL;
			goto setget_error;
		}

		if (rpt_info->size > hid_len) {
			NVT_ERR("too small to handle : %d\n", hid_len);
			ret = -EINVAL;
			goto setget_error;
		}

		switch (_IOC_NR(cmd)) {
		case _IOC_NR(HIDIOCSFEATURE(0)):
			if (rpt_id == HID_REPORTID_SET_TRANSDUCER) {
				/* we only support one stylus */
				if (hid_buf[1] == 1) {
					ret = hid_len;
					break;
				} else {
					NVT_ERR("Invalid stylus index : %d\n",
						hid_buf[1]);
					ret = -EINVAL;
					break;
				}
			}

			if (rpt_id == HID_REPORTID_GET_FIRMWARE ||
			    rpt_id == HID_REPORTID_GET_PROTOCOL) {
					NVT_ERR("Invalid report id %d\n",
						rpt_id);
					ret = -EINVAL;
					break;
			}

			if (rpt_id != HID_REPORTID_DIAGNOSE)
				ret = set_hid_feature_report(hid_buf, hid_len,
							     rpt_info);
			else	/* diagnostic is handled by GET */
				ret = 0;

			if (ret == 0)
				ret = hid_len;

			break;
		case _IOC_NR(HIDIOCGFEATURE(0)):
			ret = get_hid_feature_report(hid_buf, hid_len,
						     rpt_info);
			if (ret < 0) {
				NVT_ERR("Error getting usi data\n");
				ret = -EINVAL;
				break;
			}

			if (copy_to_user(user_arg, hid_buf, ret))
				ret = -EFAULT;

			NVT_LOG("pen get feature completed : %d\n", ret);
			break;
		default:
			NVT_ERR("cmd %d is not supported\n", cmd);
			ret = -EINVAL;
			break;
		}
setget_error:
		kfree(hid_buf);
	}

	return ret;
}

const static struct file_operations fops = {
	.owner = THIS_MODULE,
	.open = device_open,
	.release = device_release,
	.unlocked_ioctl = device_ioctl
};

static struct miscdevice nvt_hid_usi_dev = {
	.name = "nvt_usi_hidraw",
	.mode = 0660,
	.fops = &fops,
};

int32_t nvt_extra_usi_init(void)
{
	int32_t ret;

	NVT_LOG("++\n");

	usi_ctx = kzalloc(sizeof(*usi_ctx), GFP_KERNEL);
	if (!usi_ctx)
		return -ENOMEM;

	ret = misc_register(&nvt_hid_usi_dev);
	if (ret < 0) {
		NVT_ERR("Register %s failed\n", nvt_hid_usi_dev.name);
		goto usi_init_error;
	}
	NVT_LOG("--\n");

	return ret;

usi_init_error:
	kfree(usi_ctx);
	usi_ctx = NULL;

	return ret;
}

void nvt_extra_usi_deinit(void)
{
	NVT_LOG("++\n");
	misc_deregister(&nvt_hid_usi_dev);
	kfree(usi_ctx);
	usi_ctx = NULL;
	NVT_LOG("--\n");
}
#endif
