/*
 * STMicroelectronics Proximity Sensor Class
 *
 * Copyright 2015-2016 STMicroelectronics Inc.
 * Author: Denis Ciocca - <denis.ciocca@st.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

#include <fcntl.h>
#include <assert.h>
#include <signal.h>

#include "Proximity.h"

#ifdef CONFIG_ST_HAL_ACCEL_CALIB_ENABLED
#define CALIBRATION_FREQUENCY	25
#define CALIBRATION_PERIOD_MS	(1000.0f / CALIBRATION_FREQUENCY)

extern "C" {
	#include "STAccCalibration_API.h"
}
#endif /* CONFIG_ST_HAL_ACCEL_CALIB_ENABLED */

Proximity::Proximity(HWSensorBaseCommonData *data, const char *name,
		struct device_iio_sampling_freqs *sfa, int handle,
		unsigned int hw_fifo_len, float power_consumption, bool wakeup) :
			HWSensorBaseWithPollrate(data, name, sfa, handle,
			SENSOR_TYPE_PROXIMITY, hw_fifo_len, power_consumption)
{
#if (CONFIG_ST_HAL_ANDROID_VERSION > ST_HAL_KITKAT_VERSION)
	sensor_t_data.stringType = SENSOR_STRING_TYPE_PROXIMITY;
	sensor_t_data.flags |= SENSOR_FLAG_ON_CHANGE_MODE;

	(void)wakeup;
	sensor_t_data.flags |= SENSOR_FLAG_WAKE_UP;
#else /* CONFIG_ST_HAL_ANDROID_VERSION */
	(void)wakeup;
#endif /* CONFIG_ST_HAL_ANDROID_VERSION */

	sensor_t_data.resolution = 1.0f;
	sensor_t_data.maxRange = 9.0f;

	info = 0;
	if (!strcmp(name, "VL6180 Proximity Sensor"))
		info |= ST_PROXIMITY_VL6180;
	else if (!strcmp(name, "VL53L0X Proximity Sensor"))
		info |= ST_PROXIMITY_VL53L0X;
}

Proximity::~Proximity()
{

}

int Proximity::Enable(int handle, bool enable, bool lock_en_mutex)
{
#ifdef CONFIG_ST_HAL_ACCEL_CALIB_ENABLED
	int err;

	if (lock_en_mutex)
		pthread_mutex_lock(&enable_mutex);

	err = HWSensorBaseWithPollrate::Enable(handle, enable, false);
	if (err < 0) {
		if (lock_en_mutex)
			pthread_mutex_unlock(&enable_mutex);

		return err;
	}

	if (enable)
		ST_AccCalibration_API_Init(CALIBRATION_PERIOD_MS);
	else
		ST_AccCalibration_API_DeInit(CALIBRATION_PERIOD_MS);

	if (lock_en_mutex)
		pthread_mutex_unlock(&enable_mutex);

	return 0;
#else /* CONFIG_ST_HAL_ACCEL_CALIB_ENABLED */
	return HWSensorBaseWithPollrate::Enable(handle, enable, lock_en_mutex);
#endif /* CONFIG_ST_HAL_ACCEL_CALIB_ENABLED */
}

void Proximity::ProcessData(SensorBaseData *data)
{
#if (CONFIG_ST_HAL_DEBUG_LEVEL >= ST_HAL_DEBUG_EXTRA_VERBOSE)
	ALOGD("\"%s\": received new sensor data: x=%f y=%f z=%f, timestamp=%" PRIu64 "ns, deltatime=%" PRIu64 "ns (sensor type: %d).",
				sensor_t_data.name, data->raw[0], data->raw[1], data->raw[2],
				data->timestamp, data->timestamp - sensor_event.timestamp, sensor_t_data.type);
#endif /* CONFIG_ST_HAL_DEBUG_LEVEL */

	/* driver reports meter, scale to cm */
	if (info & ST_PROXIMITY_VL6180) {
		data->processed[0] = data->raw[1] * 100;
	} else if (info & ST_PROXIMITY_VL53L0X) {
		data->processed[0] = data->raw[0] * 100;
		if (data->processed[0] > ST_PROXIMITY_VL53L0X_MAX_RANGE_CM)
			data->processed[0] = ST_PROXIMITY_VL53L0X_MAX_RANGE_CM;
	} else {
		data->processed[0] = 0;
	}

	sensor_event.distance = data->processed[0];
	sensor_event.timestamp = data->timestamp;

	HWSensorBaseWithPollrate::WriteDataToPipe(data->pollrate_ns);
	HWSensorBaseWithPollrate::ProcessData(data);
}


#if (CONFIG_ST_HAL_ANDROID_VERSION >= ST_HAL_PIE_VERSION)
#if (CONFIG_ST_HAL_ADDITIONAL_INFO_ENABLED)
int Proximity::getSensorAdditionalInfoPayLoadFramesArray(additional_info_event_t **array_sensorAdditionalInfoPLFrames)
{
	additional_info_event_t* p_custom_SAI_Placement_event = nullptr;

	// place for ODM/OEM to fill custom_SAI_Placement_event
	// p_custom_SAI_Placement_event = &custom_SAI_Placement_event

/*  //Custom Placement example
	additional_info_event_t custom_SAI_Placement_event;
	custom_SAI_Placement_event = {
		.type = AINFO_SENSOR_PLACEMENT,
		.serial = 0,
		.data_float = {-1, 0, 0, 1, 0, -1, 0, 2, 0, 0, -1, 3},
	};
	p_custom_SAI_Placement_event = &custom_SAI_Placement_event;
*/

	return UseCustomAINFOSensorPlacementPLFramesArray(array_sensorAdditionalInfoPLFrames, p_custom_SAI_Placement_event);

}
#endif /* CONFIG_ST_HAL_ADDITIONAL_INFO_ENABLED */
#endif /* CONFIG_ST_HAL_ANDROID_VERSION */
