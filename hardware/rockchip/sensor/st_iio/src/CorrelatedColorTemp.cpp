/*
 * Ultra Capteur Color-Based Light Sensor Class
 *
 * Copyright 2022 Rockchip Electronics Co., Ltd
 * Author: Jason Zhang - <jason.zhang@rock-chips.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

#include <fcntl.h>
#include <assert.h>
#include <signal.h>

#include "CorrelatedColorTemp.h"

CorrelatedColorTemp::CorrelatedColorTemp(HWSensorBaseCommonData *data, const char *name,
		struct device_iio_sampling_freqs *sfa, int handle,
		unsigned int hw_fifo_len, float power_consumption, bool wakeup) :
			HWSensorBaseWithPollrate(data, name, sfa, handle,
			SENSOR_TYPE_CCT, hw_fifo_len, power_consumption)
{
#if (CONFIG_ST_HAL_ANDROID_VERSION > ST_HAL_KITKAT_VERSION)
	sensor_t_data.stringType = SENSOR_STRING_TYPE_CCT;
	sensor_t_data.flags |= SENSOR_FLAG_ON_CHANGE_MODE;

	if (wakeup)
		sensor_t_data.flags |= SENSOR_FLAG_WAKE_UP;
#else /* CONFIG_ST_HAL_ANDROID_VERSION */
	(void)wakeup;
#endif /* CONFIG_ST_HAL_ANDROID_VERSION */

	sensor_t_data.resolution = 1.0f;
	sensor_t_data.maxRange = 9.0f;
}

CorrelatedColorTemp::~CorrelatedColorTemp()
{

}

int CorrelatedColorTemp::Enable(int handle, bool enable, bool lock_en_mutex)
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

void CorrelatedColorTemp::ProcessData(SensorBaseData *data)
{
	float cyan;
	float magenta;
	float yellow;
	float red;
	float green;
	float blue;
	float X, Y, Z; /* CIE XYZ */
	float x, y, n; /* CIE xyY */
	float cct;
#if (CONFIG_ST_HAL_DEBUG_LEVEL >= ST_HAL_DEBUG_EXTRA_VERBOSE)
	ALOGD("\"%s\": red=%f green=%f blue=%f white=%f, timestamp=%" PRIu64 "ns, deltatime=%" PRIu64,
		sensor_t_data.name, data->raw[0], data->raw[1], data->raw[2], data->raw[3],
		data->timestamp, data->timestamp - sensor_event.timestamp);
#endif /* CONFIG_ST_HAL_DEBUG_LEVEL */

	/* Complementary Color Method */
	cyan = data->raw[3] - data->raw[0];
	magenta = data->raw[3] - data->raw[1];
	yellow = data->raw[3] - data->raw[2];

	red = magenta + yellow;
	green = cyan + yellow;
	blue = cyan + magenta;

	/* Calculate CIE XYZ */
	X = red * X_A + green * X_B + blue * X_C;
	Y = red * Y_A + green * Y_B + blue * Y_C;
	Z = red * Z_A + green * Z_B + blue * Z_C;

#ifdef CONFIG_ST_HAL_SW_LIGHT_ENABLED
	/* NOTE: Pass the illumination in Lux to dependency */
	data->processed[0] = Y;
#endif /* CONFIG_ST_HAL_SW_LIGHT_ENABLED */

	/* Calculate CIE xyY */
	x = X / (X + Y + Z);
	y = Y / (X + Y + Z);
	n = (x - 0.332) / (y - 0.1858);

	/* Calculate CCT */
	cct = -449 * pow(n, 3) + 3525 * pow(n, 2) - 6823.3 * n + 5520.33;

	/*
	 * The first value is Correlated Color Temperature in Kelvins (K).
	 * The rest of values are in lux and measure the R, G, B channel.
	 */
	sensor_event.data[0] = cct;
	sensor_event.data[1] = data->raw[0];
	sensor_event.data[2] = data->raw[1];
	sensor_event.data[3] = data->raw[2];
	sensor_event.data[4] = data->raw[3];
	sensor_event.data[5] = data->orig[0];
	sensor_event.data[6] = data->orig[1];
	sensor_event.data[7] = data->orig[2];
	sensor_event.data[8] = data->orig[3];
	sensor_event.timestamp = data->timestamp;

	HWSensorBaseWithPollrate::WriteDataToPipe(data->pollrate_ns);
	HWSensorBaseWithPollrate::ProcessData(data);
}
