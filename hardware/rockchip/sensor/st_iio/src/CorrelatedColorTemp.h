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

#ifndef ST_CCT_SENSOR_H
#define ST_CCT_SENSOR_H

#include "HWSensorBase.h"

/* Transformation Matrix */
#define X_A (-0.121714589f)
#define X_B (6.266768206f)
#define X_C (-4.368804016f)

#define Y_A (-2.360768324f)
#define Y_B (8.38127456f)
#define Y_C (-4.476796103f)

#define Z_A (-5.097694084f)
#define Z_B (-1.42651259f)
#define Z_C (10.32534666f)

/*
 * class CorrelatedColorTemp
 */
class CorrelatedColorTemp : public HWSensorBaseWithPollrate {
private:
#if (CONFIG_ST_HAL_ANDROID_VERSION >= ST_HAL_PIE_VERSION)
#if (CONFIG_ST_HAL_ADDITIONAL_INFO_ENABLED)
	int getSensorAdditionalInfoPayLoadFramesArray(additional_info_event_t **array_sensorAdditionalInfoPLFrames);
#endif /* CONFIG_ST_HAL_ADDITIONAL_INFO_ENABLED */
#endif /* CONFIG_ST_HAL_ANDROID_VERSION */
public:
	CorrelatedColorTemp(HWSensorBaseCommonData *data, const char *name,
			struct device_iio_sampling_freqs *sfa, int handle,
			unsigned int hw_fifo_len,
			float power_consumption, bool wakeup);
	~CorrelatedColorTemp();

	virtual int Enable(int handle, bool enable, bool lock_en_mutex);
	virtual void ProcessData(SensorBaseData *data);
};

#endif /* ST_CCT_SENSOR_H */
