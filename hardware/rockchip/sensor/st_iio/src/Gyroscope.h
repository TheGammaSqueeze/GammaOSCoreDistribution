/*
 * Copyright (C) 2015-2016 STMicroelectronics
 * Author: Denis Ciocca - <denis.ciocca@st.com>
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

#ifndef ANDROID_GYROSCOPE_SENSOR_H
#define ANDROID_GYROSCOPE_SENSOR_H

#include "HWSensorBase.h"

/*
 * class Gyroscope
 */
class Gyroscope : public HWSensorBaseWithPollrate {
private:
#if (CONFIG_ST_HAL_ANDROID_VERSION >= ST_HAL_PIE_VERSION)
#if (CONFIG_ST_HAL_ADDITIONAL_INFO_ENABLED)
	int getSensorAdditionalInfoPayLoadFramesArray(additional_info_event_t **array_sensorAdditionalInfoPLFrames);
#endif /* CONFIG_ST_HAL_ADDITIONAL_INFO_ENABLED */
#endif /* CONFIG_ST_HAL_ANDROID_VERSION */
public:
	Gyroscope(HWSensorBaseCommonData *data, const char *name,
			struct device_iio_sampling_freqs *sfa, int handle,
			unsigned int hw_fifo_len,
			float power_consumption, bool wakeup);
	~Gyroscope();

#ifdef CONFIG_ST_HAL_GYRO_GBIAS_ESTIMATION_ENABLED
	int64_t gbias_last_pollrate;
#endif /* CONFIG_ST_HAL_GYRO_GBIAS_ESTIMATION_ENABLED */

	virtual int CustomInit();
	virtual int Enable(int handle, bool enable, bool lock_en_mutex);
	virtual int SetDelay(int handle, int64_t period_ns, int64_t timeout, bool lock_en_mutex);
	virtual void ProcessData(SensorBaseData *data);
};

#endif /* ANDROID_GYROSCOPE_SENSOR_H */
