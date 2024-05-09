/* acquire HDMI EDID data 
 * from DRM(direct render manager) 
 * or traditional sys fs access ways
*/
#include <assert.h>
#include <errno.h>
#include <getopt.h>
#include <inttypes.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>

#include <sys/mman.h>
#include <drm_fourcc.h>
#include <xf86drm.h>
#include <xf86drmMode.h>
#include <utils/Log.h>

#ifdef __cplusplus

extern "C" {

#ifdef LINUX_DRM_SUPPORT
int hdmiedid_init(uint8_t *buf, uint32_t* buflen) {
     drmModeCrtcPtr crtc = NULL;
     drmModeObjectPropertiesPtr props=NULL;
     drmModePropertyPtr prop=NULL;
     drmModeResPtr res=NULL;
     drmModeConnectorPtr cur_connector = NULL;
     drmModePropertyBlobPtr edid_blob = NULL;
     int i, fd, ret;
     int found_crtc = 0;
     uint32_t fb_id;
     uint32_t flags = 0;
     int zpos_max = INT_MAX;

     fd = drmOpen("rockchip", NULL);
     if (fd < 0) {
	ALOGE("failed to open rockchip drm: %s\n",
	    strerror(errno));
	return fd;
     }

     ret = drmSetClientCap(fd, DRM_CLIENT_CAP_UNIVERSAL_PLANES, 1);
     if (ret) {
        ALOGE("Failed to set atomic cap %s", strerror(errno));
	return ret;
     }

     ret = drmSetClientCap(fd, DRM_CLIENT_CAP_ATOMIC, 1);
     if (ret) {
        ALOGE("Failed to set atomic cap %s", strerror(errno));
	return ret;
     }

     res = drmModeGetResources(fd);
     if (!res) {
	  ALOGE("Failed to get resources: %s\n",
			strerror(errno));
	  return -ENODEV;
     }

     ALOGD("find %d crtcs %d connectors", res->count_crtcs, res->count_connectors);

     for (i = 0; i < res->count_crtcs; ++i) {
	  uint32_t j;
	  crtc = drmModeGetCrtc(fd, res->crtcs[i]);
	  if (!crtc) {
		ALOGE("Could not get crtc %u: %s\n",
				res->crtcs[i], strerror(errno));
		continue;
	  }

	  props = drmModeObjectGetProperties(fd, crtc->crtc_id,
						   DRM_MODE_OBJECT_CRTC);
	  if (!props) {
		ALOGE("failed to found props crtc[%d] %s\n",
			crtc->crtc_id, strerror(errno));
		continue;
	  }
	  for (j = 0; j < props->count_props; j++) {
		prop = drmModeGetProperty(fd, props->props[j]);
		if (!strcmp(prop->name, "ACTIVE")) {
			if (props->prop_values[j]) {
                                ALOGD("found active crtc %d", crtc->crtc_id);
				found_crtc = 1;
				break;
			}
		}
	  }

	  if (found_crtc)
	     break;
     }

     if (i == res->count_crtcs) {
         ALOGE("failed to find usable crtc props\n");
	 return -ENODEV;
     }

     /* look for an EDID property */
     for (int i = 0; !ret && i < res->count_connectors; ++i) {
      	  drmModeConnectorPtr c = drmModeGetConnector(fd, res->connectors[i]);
    	  if (!c) {
      	      ALOGE("Failed to get connector %d", res->connectors[i]);
      	      ret = -ENODEV;
      	      break;
    	  }
          if (c->connection == DRM_MODE_CONNECTED) {
              cur_connector = c;
              break;
          }
     }
      
     for (int i=0; i < cur_connector->count_props; i++) {
          prop = drmModeGetProperty(fd, cur_connector->props[i]);
          if ((prop->flags & DRM_MODE_PROP_BLOB)  &&
              !strcmp(prop->name, "EDID")){
              edid_blob = drmModeGetPropertyBlob(fd, cur_connector->prop_values[i]);   
              if (edid_blob != NULL) {
                  ALOGD("edid data id: %d length: %d", edid_blob->id, edid_blob->length);    
                  memcpy(buf, edid_blob->data, edid_blob->length);
                  *buflen = edid_blob->length;
              }
          }
          drmModeFreeProperty(prop);
     }
     return 0;
      
}
#else
int hdmiedid_init() { return 0;}
#endif

}
#endif
