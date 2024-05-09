/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <errno.h>
#include <inttypes.h>
#include <log/log.h>
#include <stdint.h>
#include <string>
#include <linux/videodev2.h>

#include "xf86drm.h"
#include "xf86drmMode.h"
#include "drm_fourcc.h"
#include "drm_mode.h"

#define ARRAY_SIZE(arr) (sizeof(arr) / sizeof((arr)[0]))

struct crtc {
	drmModeCrtc *crtc;
	drmModeObjectProperties *props;
	drmModePropertyRes **props_info;
	drmModeModeInfo *mode;
};

struct encoder {
	drmModeEncoder *encoder;
};

struct connector {
	drmModeConnector *connector;
	drmModeObjectProperties *props;
	drmModePropertyRes **props_info;
	char *name;
};

struct fb {
	drmModeFB *fb;
};

struct plane {
	drmModePlane *plane;
	drmModeObjectProperties *props;
	drmModePropertyRes **props_info;
};

struct resources {
	drmModeRes *res;
	drmModePlaneRes *plane_res;

	struct crtc *crtcs;
	struct encoder *encoders;
	struct connector *connectors;
	struct fb *fbs;
	struct plane *planes;
};

struct device {
	int fd;

	struct resources *resources;

	struct {
		unsigned int width;
		unsigned int height;

		unsigned int fb_id;
		struct bo *bo;
		struct bo *cursor_bo;
	} mode;

	int use_atomic;
	drmModeAtomicReq *req;
};

struct type_name {
	unsigned int type;
	const char *name;
};

class DrmApi {
 public:
 DrmApi();
 ~DrmApi();
	int set_3x1d_gamma(uint32_t size, uint16_t* r, uint16_t* g, uint16_t* b);
	//int set_cubic_lut(uint32_t size, uint16_t* r, uint16_t* g, uint16_t* b);
	int set_bcsh(uint16_t brightness, uint16_t ccontrast, uint16_t saturation, uint16_t hue);
	int get_primary_info(int *fd, uint32_t *crtc_id, uint32_t *connector_type, uint32_t *connector_type_id, uint32_t *connector_id);
	int set_color_space(uint32_t plane_id, uint32_t color_space);
	int getResolutionInfo(uint32_t* width, uint32_t* height);
};
