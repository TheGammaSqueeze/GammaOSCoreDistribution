/*
 * Virtual Light Sensor Class
 *
 * Copyright (C) 2022 Rockchip Electronics Co., Ltd.
 * Author: Jason Zhang - <jason.zhang@rock-chips.com>
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

#define __STDC_LIMIT_MACROS
#define __STDINT_LIMITS

#include <fcntl.h>
#include <assert.h>
#include <signal.h>
#include <unistd.h>

#include "SWLight.h"

SWLight::SWLight(const char *name, int handle) :
		SWSensorBaseWithPollrate(name, handle, SENSOR_TYPE_LIGHT,
			false, false, true, false)
{
#if (CONFIG_ST_HAL_ANDROID_VERSION > ST_HAL_KITKAT_VERSION)
	sensor_t_data.flags |= SENSOR_FLAG_CONTINUOUS_MODE;
	sensor_t_data.maxDelay = FREQUENCY_TO_US(FLT_MAX);
#endif /* CONFIG_ST_HAL_ANDROID_VERSION */

	sensor_t_data.resolution = ST_SENSOR_FUSION_RESOLUTION(1.0f);
	sensor_t_data.maxRange = 1.0f;

	dependencies_type_list[SENSOR_DEPENDENCY_ID_0] = SENSOR_TYPE_CCT;
	id_sensor_trigger = SENSOR_DEPENDENCY_ID_0;

#if (CONFIG_ST_HAL_ANDROID_VERSION >= ST_HAL_PIE_VERSION)
#if (CONFIG_ST_HAL_ADDITIONAL_INFO_ENABLED)
	supportsSensorAdditionalInfo = true;
	sensor_t_data.flags |= SENSOR_FLAG_ADDITIONAL_INFO;
#endif /* CONFIG_ST_HAL_ADDITIONAL_INFO_ENABLED */
#endif /* CONFIG_ST_HAL_ANDROID_VERSION */
}

SWLight::~SWLight()
{

}

int SWLight::Enable(int handle, bool enable, bool lock_en_mutex)
{
	int err;
	bool old_status;
	bool old_status_no_handle;

	if (lock_en_mutex)
		pthread_mutex_lock(&enable_mutex);

	old_status = GetStatus(false);
	old_status_no_handle = GetStatusExcludeHandle(handle);

	err = SWSensorBaseWithPollrate::Enable(handle, enable, false);
	if (err < 0) {
		if (lock_en_mutex)
			pthread_mutex_unlock(&enable_mutex);

		return err;
	}

	if ((enable && !old_status) || (!enable && !old_status_no_handle)) {
		if (enable)
			sensor_global_enable = android::elapsedRealtimeNano();
		else
			sensor_global_disable = android::elapsedRealtimeNano();
	}

	if (lock_en_mutex)
		pthread_mutex_unlock(&enable_mutex);

	return 0;
}

int SWLight::SetDelay(int handle, int64_t period_ns, int64_t timeout, bool lock_en_mutex)
{
	int err;

	if ((period_ns > FREQUENCY_TO_NS(FLT_MAX)) && (period_ns != INT64_MAX))
		period_ns = FREQUENCY_TO_NS(FLT_MAX);

	if (lock_en_mutex)
		pthread_mutex_lock(&enable_mutex);

	err = SWSensorBaseWithPollrate::SetDelay(handle, period_ns, timeout, false);
	if (err < 0){
		if (lock_en_mutex)
			pthread_mutex_unlock(&enable_mutex);

		return err;
	}

	if (lock_en_mutex)
		pthread_mutex_unlock(&enable_mutex);

	return 0;
}

void SWLight::ProcessData(SensorBaseData *data)
{
#if (CONFIG_ST_HAL_DEBUG_LEVEL >= ST_HAL_DEBUG_EXTRA_VERBOSE)
	ALOGD("\"%s\": received new sensor data from trigger: x=%f y=%f z=%f, timestamp=%" PRIu64 "ns, deltatime=%" PRIu64 "ns (sensor type: %d).",
				sensor_t_data.name, data->raw[0], data->raw[1], data->raw[2],
				data->timestamp, data->timestamp - sensor_event.timestamp, sensor_t_data.type);
#endif /* CONFIG_ST_HAL_DEBUG_LEVEL */

	sensor_event.light = data->processed[0];
	sensor_event.timestamp = data->timestamp;
	SWSensorBaseWithPollrate::WriteDataToPipe(data->pollrate_ns);
}
