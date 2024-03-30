# Copyright 2014 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Verify image and inertial sensor events are well synchronized."""


import json
import logging
import math
import multiprocessing
import os
import sys
import time

from matplotlib import pylab
import matplotlib.pyplot
from mobly import test_runner
import numpy as np
import scipy.spatial

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import sensor_fusion_utils

_CAM_FRAME_RANGE_MAX = 9.0  # Seconds: max allowed camera frame range.
_GYRO_SAMP_RATE_MIN = 100.0  # Samples/second: min gyro sample rate.
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_ARDUINO_ANGLES = (0, 90)
_ARDUINO_INIT_WAIT_TIME = 3.0  # Seconds to wait for Arduino comm
_ARDUINO_MOVE_TIME = 2
_ARDUINO_SERVO_SPEED = 20
_NUM_ROTATIONS = 10
_START_FRAME = 1
_FRAME_DELTA_TOL = 1.5  # 50% margin over nominal FPS of captures

# Constants to convert between different units (for clarity).
_SEC_TO_MSEC = 1000
_MSEC_TO_NSEC = 1000*1000
_NSEC_TO_SEC = 1.0E-9
_CM_TO_M = 1E-2
_RADS_TO_DEGS = 180/math.pi

# PASS/FAIL thresholds.
_CORR_DIST_THRESH_MAX = 0.005
_OFFSET_MS_THRESH_MAX = 1  # mseconds

# Set maximum exposure time to reduce motion blur
# blur = velocity * exp_time * N_pixels / FOV
# blur: 3 pixels, v: chart R (17.7cm) / rot_time (1.5s), N: 640, FOV: 30cm
_EXP_MAX = 4*_MSEC_TO_NSEC  # 4 ms
_GYRO_INIT_WAIT_TIME = 0.5  # Seconds to wait for gyro to stabilize.
_GYRO_POST_WAIT_TIME = 0.2  # Seconds to wait to capture some extra gyro data.
_IMG_SIZE_MAX = 640 * 480  # Maximum image size.
_NUM_FRAMES_MAX = 300  # fps*test_length should be < this for smooth captures.


def _collect_data(cam, fps, w, h, test_length, rot_rig, chart_dist, log_path):
  """Capture a new set of data from the device.

  Captures camera frames while the user is moving the device in the proscribed
  manner. Note since the capture request is for a manual request, optical
  image stabilization (OIS) is disabled.

  Args:
    cam: camera object.
    fps: frames per second capture rate.
    w: pixel width of frames.
    h: pixel height of frames.
    test_length: length of time for test in seconds.
    rot_rig: dict with 'cntl' and 'ch' defined.
    chart_dist: float value of distance to chart in meters.
    log_path: location to save data.

  Returns:
    frames: list of RGB images as numpy arrays.
  """
  logging.debug('Starting sensor event collection')
  props = cam.get_camera_properties()
  props = cam.override_with_hidden_physical_camera_props(props)
  camera_properties_utils.skip_unless(
      camera_properties_utils.sensor_fusion_capable(props) and
      cam.get_sensors().get('gyro'))

  # Start camera rotation.
  p = multiprocessing.Process(
      target=sensor_fusion_utils.rotation_rig,
      args=(rot_rig['cntl'], rot_rig['ch'], _NUM_ROTATIONS,
            _ARDUINO_ANGLES, _ARDUINO_SERVO_SPEED, _ARDUINO_MOVE_TIME,))
  p.start()

  cam.start_sensor_events()

  # Sleep a while for gyro events to stabilize.
  time.sleep(_GYRO_INIT_WAIT_TIME)
  if rot_rig['cntl'].lower() == 'arduino':
    time.sleep(_ARDUINO_INIT_WAIT_TIME)

  # Capture frames.
  facing = props['android.lens.facing']
  if (facing != camera_properties_utils.LENS_FACING_FRONT and
      facing != camera_properties_utils.LENS_FACING_BACK):
    raise AssertionError(f'Unknown lens facing: {facing}.')

  fmt = {'format': 'yuv', 'width': w, 'height': h}
  s, e, _, _, _ = cam.do_3a(get_results=True, do_af=False)
  logging.debug('3A ISO: %d, exp: %.3fms', s, e/_MSEC_TO_NSEC)
  if e > _EXP_MAX:
    logging.debug('Re-assigning exposure time')
    s *= int(round(e / _EXP_MAX))
    e = _EXP_MAX
    s_max = props['android.sensor.info.sensitivityRange'][1]
    if s > s_max:
      logging.debug('Calculated ISO too large! ISO: %d, MAX: %d '
                    'Setting ISO to max analog gain value.', s, s_max)
      s = s_max
  req = capture_request_utils.manual_capture_request(s, e)
  capture_request_utils.turn_slow_filters_off(props, req)
  fd_min = props['android.lens.info.minimumFocusDistance']
  fd_chart = 1 / chart_dist
  fd_meas = min(fd_min, fd_chart)
  logging.debug('fd_min: %.3f, fd_chart: %.3f, fd_meas: %.3f',
                fd_min, fd_chart, fd_meas)
  req['android.lens.focusDistance'] = fd_meas
  req['android.control.aeTargetFpsRange'] = [fps, fps]
  req['android.sensor.frameDuration'] = int(1 / _NSEC_TO_SEC / fps)
  logging.debug('Capturing %dx%d with ISO %d, exp %.3fms at %dfps',
                w, h, s, e/_MSEC_TO_NSEC, fps)
  caps = cam.do_capture([req] * int(fps * test_length), fmt)

  # Capture a bit more gyro samples for use in get_best_alignment_offset
  time.sleep(_GYRO_POST_WAIT_TIME)

  # Get the gyro events.
  logging.debug('Reading out inertial sensor events')
  gyro = cam.get_sensor_events()['gyro']
  logging.debug('Number of gyro samples %d', len(gyro))

  # Combine the gyro and camera events into a single structure.
  logging.debug('Dumping event data')
  starts = [cap['metadata']['android.sensor.timestamp'] for cap in caps]
  exptimes = [cap['metadata']['android.sensor.exposureTime'] for cap in caps]
  readouts = [cap['metadata']['android.sensor.rollingShutterSkew']
              for cap in caps]
  events = {'gyro': gyro, 'cam': list(zip(starts, exptimes, readouts)),
            'facing': facing}
  with open('%s_events.txt' % os.path.join(log_path, _NAME), 'w') as f:
    f.write(json.dumps(events))

  # Convert frames to RGB.
  logging.debug('Dumping frames')
  frames = []
  for i, cap in enumerate(caps):
    img = image_processing_utils.convert_capture_to_rgb_image(cap)
    frames.append(img)
    image_processing_utils.write_image(img, '%s_frame%03d.png' % (
        os.path.join(log_path, _NAME), i))
  return events, frames


def _get_cam_times(cam_events, fps):
  """Get the camera frame times.

  Assign a time to each frame. Assumes the image is instantly captured in the
  middle of exposure.

  Args:
    cam_events: List of (start_exposure, exposure_time, readout_duration)
                tuples, one per captured frame, with times in nanoseconds.
    fps: float of frames per second value

  Returns:
    frame_times: Array of N times, one corresponding to the 'middle' of the
                 exposure of each frame.
  """
  starts = np.array([start for start, exptime, readout in cam_events])
  max_frame_delta_ms = (np.amax(np.subtract(starts[1:], starts[0:-1])) /
                        _MSEC_TO_NSEC)
  logging.debug('Maximum frame delta: %.3f ms', max_frame_delta_ms)
  frame_delta_tol_ms = _FRAME_DELTA_TOL * (1 / fps) * _SEC_TO_MSEC
  if max_frame_delta_ms > frame_delta_tol_ms:
    raise AssertionError(f'Frame drop! Max delta: {max_frame_delta_ms:.3f}ms, '
                         f'ATOL: {frame_delta_tol_ms}ms')
  exptimes = np.array([exptime for start, exptime, readout in cam_events])
  if not np.all(exptimes == exptimes[0]):
    raise AssertionError(f'Exposure times vary in frames! {exptimes}')
  readouts = np.array([readout for start, exptime, readout in cam_events])
  if not np.all(readouts == readouts[0]):
    raise AssertionError(f'Rolling shutter not always equal! {readouts}')
  frame_times = starts + (exptimes + readouts) / 2.0
  return frame_times


def _plot_best_shift(best, coeff, x, y, log_path):
  """Saves a plot the best offset, fit data and x,y data.

  Args:
    best: x value of best fit data.
    coeff: 3 element np array. Return of np.polyfit(x,y) for 2nd order fit.
    x: np array of x data that was fit.
    y: np array of y data that was fit.
    log_path: where to store data.
  """
  xfit = np.arange(x[0], x[-1], 0.05).tolist()
  yfit = [coeff[0]*x*x + coeff[1]*x + coeff[2] for x in xfit]
  pylab.figure()
  pylab.title(f'{_NAME} Gyro/Camera Time Correlation')
  pylab.plot(x, y, 'ro', label='data', alpha=0.7)
  pylab.plot(xfit, yfit, 'b', label='fit', alpha=0.7)
  pylab.plot(best, min(yfit), 'g*', label='best', markersize=10)
  pylab.ticklabel_format(axis='y', style='sci', scilimits=(-3, -3))
  pylab.xlabel('Relative horizontal shift between curves (ms)')
  pylab.ylabel('Correlation distance')
  pylab.legend()
  matplotlib.pyplot.savefig(
      '%s_plot_shifts.png' % os.path.join(log_path, _NAME))


def _plot_rotations(cam_rots, gyro_rots, log_path):
  """Saves a plot of the camera vs. gyro rotational measurements.

  Args:
    cam_rots: Array of camera rotation measurements (rads).
    gyro_rots: Array of gyro rotation measurements (rads).
    log_path: Location to store data.
  """
  # For plot, scale rotations to degrees.
  pylab.figure()
  pylab.title(f'{_NAME} Gyro/Camera Rotations')
  pylab.plot(range(len(cam_rots)), cam_rots*_RADS_TO_DEGS, '-r.',
             label='camera', alpha=0.7)
  pylab.plot(range(len(gyro_rots)), gyro_rots*_RADS_TO_DEGS, '-b.',
             label='gyro', alpha=0.7)
  pylab.xlabel('Camera frame number')
  pylab.ylabel('Angular displacement between adjacent camera frames (degrees)')
  pylab.xlim([0, len(cam_rots)])
  pylab.legend()
  matplotlib.pyplot.savefig(
      '%s_plot_rotations.png' % os.path.join(log_path, _NAME))


def load_data():
  """Load a set of previously captured data.

  Returns:
    events: Dictionary containing all gyro events and cam timestamps.
    frames: List of RGB images as numpy arrays.
    w:      Pixel width of frames
    h:      Pixel height of frames
  """
  with open(f'{_NAME}_events.txt', 'r') as f:
    events = json.loads(f.read())
  n = len(events['cam'])
  frames = []
  for i in range(n):
    img = image_processing_utils.read_image(f'{_NAME}_frame{i:03d}.png')
    w, h = img.size[0:2]
    frames.append(np.array(img).reshape((h, w, 3)) / 255)
  return events, frames, w, h


class SensorFusionTest(its_base_test.ItsBaseTest):
  """Tests if image and motion sensor events are well synchronized.

  Tests gyro and camera timestamp differences while camera is rotating.
  Test description is in SensorFusion.pdf file. Test rotates phone in proscribed
  manner and captures images.

  Camera rotation is determined from images and from gyroscope events.
  Timestamp offset between gyro and camera is determined using scipy
  spacial correlation distance. The min value is determined as the optimum.

  PASS/FAIL based on the offset and also the correlation distance.
  """

  def _assert_gyro_encompasses_camera(self, cam_times, gyro_times):
    """Confirms the camera events are bounded by the gyroscope events.

    Also ensures:
      1. Camera frame range is less than MAX_CAMERA_FRAME_RANGE. When camera
      frame range is significantly larger than spec, the system is usually in a
      busy/bad state.
      2. Gyro samples per second are greater than GYRO_SAMP_RATE_MIN

    Args:
      cam_times: numpy array of camera times.
      gyro_times: List of 'gyro' times.
    """
    min_cam_time = min(cam_times) * _NSEC_TO_SEC
    max_cam_time = max(cam_times) * _NSEC_TO_SEC
    min_gyro_time = min(gyro_times) * _NSEC_TO_SEC
    max_gyro_time = max(gyro_times) * _NSEC_TO_SEC
    if not (min_cam_time > min_gyro_time and max_cam_time < max_gyro_time):
      raise AssertionError(
          f'Camera timestamps [{min_cam_time}, {max_cam_time}] not '
          f'enclosed by gyro timestamps [{min_gyro_time}, {max_gyro_time}]')

    cam_frame_range = max_cam_time - min_cam_time
    logging.debug('Camera frame range: %f', cam_frame_range)

    gyro_time_range = max_gyro_time - min_gyro_time
    gyro_smp_per_sec = len(gyro_times) / gyro_time_range
    logging.debug('Gyro samples per second: %f', gyro_smp_per_sec)
    if cam_frame_range > _CAM_FRAME_RANGE_MAX:
      raise AssertionError(f'Camera frame range, {cam_frame_range}s, too high!')
    if gyro_smp_per_sec < _GYRO_SAMP_RATE_MIN:
      raise AssertionError(f'Gyro sample rate, {gyro_smp_per_sec}S/s, low!')

  def test_sensor_fusion(self):
    # Determine if '--replay' in cmd line
    replay = False
    for s in list(sys.argv[1:]):
      if '--replay' in s:
        replay = True
        logging.info('test_sensor_fusion.py not run. Using existing data set.')

    rot_rig = {}
    fps = float(self.fps)
    img_w, img_h = self.img_w, self.img_h
    test_length = float(self.test_length)
    log_path = self.log_path
    chart_distance = self.chart_distance * _CM_TO_M

    if img_w * img_h > _IMG_SIZE_MAX or fps * test_length > _NUM_FRAMES_MAX:
      logging.debug(
          'Warning: Your test parameters may require fast write speeds'
          ' to run smoothly.  If you run into problems, consider'
          " smaller values of 'w', 'h', 'fps', or 'test_length'.")

    if replay:
      events, frames, _, _ = load_data()
    else:
      with its_session_utils.ItsSession(
          device_id=self.dut.serial,
          camera_id=self.camera_id,
          hidden_physical_id=self.hidden_physical_id) as cam:

        rot_rig['cntl'] = self.rotator_cntl
        rot_rig['ch'] = self.rotator_ch
        events, frames = _collect_data(cam, fps, img_w, img_h, test_length,
                                       rot_rig, chart_distance, log_path)
    logging.debug('Start frame: %d', _START_FRAME)

    sensor_fusion_utils.plot_gyro_events(events['gyro'], _NAME, log_path)

    # Validity check on gyro/camera timestamps
    cam_times = _get_cam_times(
        events['cam'][_START_FRAME:len(events['cam'])], fps)
    gyro_times = [e['time'] for e in events['gyro']]
    self._assert_gyro_encompasses_camera(cam_times, gyro_times)

    # Compute cam rotation displacement(rads) between pairs of adjacent frames.
    cam_rots = sensor_fusion_utils.get_cam_rotations(
        frames[_START_FRAME:len(frames)], events['facing'], img_h,
        os.path.join(log_path, _NAME), _START_FRAME)
    logging.debug('cam_rots: %s', str(cam_rots))
    gyro_rots = sensor_fusion_utils.get_gyro_rotations(
        events['gyro'], cam_times)
    _plot_rotations(cam_rots, gyro_rots, log_path)

    # Find the best offset.
    offset_ms, coeffs, candidates, distances = sensor_fusion_utils.get_best_alignment_offset(
        cam_times, cam_rots, events['gyro'])
    _plot_best_shift(offset_ms, coeffs, candidates, distances, log_path)

    # Calculate correlation distance with best offset.
    corr_dist = scipy.spatial.distance.correlation(cam_rots, gyro_rots)
    logging.debug('Best correlation of %f at shift of %.3fms',
                  corr_dist, offset_ms)

    # Assert PASS/FAIL criteria.
    if corr_dist > _CORR_DIST_THRESH_MAX:
      raise AssertionError(f'Poor gyro/camera correlation: {corr_dist:.6f}, '
                           f'TOL: {_CORR_DIST_THRESH_MAX}.')
    if abs(offset_ms) > _OFFSET_MS_THRESH_MAX:
      raise AssertionError('Offset too large. Measured (ms): '
                           f'{offset_ms:.3f}, TOL: {_OFFSET_MS_THRESH_MAX}.')

if __name__ == '__main__':
  test_runner.main()
