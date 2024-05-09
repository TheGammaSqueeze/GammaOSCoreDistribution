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

#ifndef ST_SW_LIGHT_H
#define ST_SW_LIGHT_H

#include "SWSensorBase.h"

class SWLight : public SWSensorBaseWithPollrate {
protected:
	SensorBaseData outdata;

public:
	SWLight(const char *name, int handle);
	~SWLight();

	// virtual int CustomInit();
	virtual int Enable(int handle, bool enable, bool lock_en_mutex);
	virtual int SetDelay(int handle, int64_t period_ns, int64_t timeout, bool lock_en_mutex);
	virtual void ProcessData(SensorBaseData *data);
};

#endif /* ST_SW_LIGHT_H */
