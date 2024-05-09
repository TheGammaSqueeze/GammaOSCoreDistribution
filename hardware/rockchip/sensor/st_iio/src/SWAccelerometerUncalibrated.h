/*
 * Copyright (C) 2017 STMicroelectronics
 * Author: Armando Visconti <armando.visconti@st.com>
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

#ifndef ST_SWACCELEROMETER_UNCALIBRATED_H
#define ST_SWACCELEROMETER_UNCALIBRATED_H

#include "SWSensorBase.h"

class SWAccelerometerUncalibrated : public SWSensorBaseWithPollrate {
public:
	SWAccelerometerUncalibrated(const char *name, int handle);
	~SWAccelerometerUncalibrated();

	virtual void ProcessData(SensorBaseData *data);

#if (CONFIG_ST_HAL_ANDROID_VERSION >= ST_HAL_PIE_VERSION)
#if (CONFIG_ST_HAL_ADDITIONAL_INFO_ENABLED)
	virtual int getSensorAdditionalInfoPayLoadFramesArray(additional_info_event_t **array_sensorAdditionalInfoPLFrames);
#endif /* CONFIG_ST_HAL_ADDITIONAL_INFO_ENABLED */
#endif /* CONFIG_ST_HAL_ANDROID_VERSION */
};

#endif /* ST_SWACCELEROMETER_UNCALIBRATED_H */
