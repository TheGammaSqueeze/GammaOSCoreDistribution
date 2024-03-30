# Copyright 2022 The Android Open Source Project
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
"""Verify preview is stable during phone movement."""

import logging
import multiprocessing
import os
import time

from mobly import test_runner

import its_base_test
import camera_properties_utils
import image_processing_utils
import its_session_utils
import opencv_processing_utils
import sensor_fusion_utils
import video_processing_utils

_ARDUINO_ANGLES = (10, 25)  # degrees
_ARDUINO_MOVE_TIME = 0.30  # seconds
_ARDUINO_SERVO_SPEED = 10
_ASPECT_RATIO_16_9 = 16/9  # determine if preview fmt > 16:9
_IMG_FORMAT = 'png'
_MIN_PHONE_MOVEMENT_ANGLE = 5  # degrees
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_ROTATIONS = 24
_START_FRAME = 30  # give 3A some frames to warm up
_TABLET_SERVO_SPEED = 20
_VIDEO_DELAY_TIME = 5.5  # seconds
_VIDEO_DURATION = 5.5  # seconds
_PREVIEW_STABILIZATION_FACTOR = 0.7  # 70% of gyro movement allowed
_PREVIEW_STABILIZATION_MODE_PREVIEW = 2


def _collect_data(cam, tablet_device, video_size, rot_rig):
  """Capture a new set of data from the device.

  Captures camera preview frames while the user is moving the device in
  the prescribed manner.

  Args:
    cam: camera object
    tablet_device: boolean; based on config file
    video_size: str; video resolution. ex. '1920x1080'
    rot_rig: dict with 'cntl' and 'ch' defined

  Returns:
    recording object as described by cam.do_preview_recording
  """

  logging.debug('Starting sensor event collection')

  # Start camera vibration
  if tablet_device:
    servo_speed = _TABLET_SERVO_SPEED
  else:
    servo_speed = _ARDUINO_SERVO_SPEED
  p = multiprocessing.Process(
      target=sensor_fusion_utils.rotation_rig,
      args=(
          rot_rig['cntl'],
          rot_rig['ch'],
          _NUM_ROTATIONS,
          _ARDUINO_ANGLES,
          servo_speed,
          _ARDUINO_MOVE_TIME,
      ),
  )
  p.start()

  cam.start_sensor_events()
  # Record video and return recording object
  time.sleep(_VIDEO_DELAY_TIME)  # allow time for rig to start moving

  recording_obj = cam.do_preview_recording(video_size, _VIDEO_DURATION, True)
  logging.debug('Recorded output path: %s', recording_obj['recordedOutputPath'])
  logging.debug('Tested quality: %s', recording_obj['quality'])

  # Wait for vibration to stop
  p.join()

  return recording_obj


class PreviewStabilizationTest(its_base_test.ItsBaseTest):
  """Tests if preview is stabilized.

  Camera is moved in sensor fusion rig on an arc of 15 degrees.
  Speed is set to mimic hand movement (and not be too fast).
  Preview is captured after rotation rig starts moving, and the
  gyroscope data is dumped.

  The recorded preview is processed to dump all of the frames to
  PNG files. Camera movement is extracted from frames by determining
  max angle of deflection in video movement vs max angle of deflection
  in gyroscope movement. Test is a PASS if rotation is reduced in video.
  """

  def test_preview_stability(self):
    rot_rig = {}
    log_path = self.log_path

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:

      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      camera_properties_utils.skip_unless(
          first_api_level >= its_session_utils.ANDROID13_API_LEVEL,
          'First API level should be {} or higher. Found {}.'.format(
              its_session_utils.ANDROID13_API_LEVEL, first_api_level))

      supported_stabilization_modes = props[
          'android.control.availableVideoStabilizationModes'
      ]

      camera_properties_utils.skip_unless(
          supported_stabilization_modes is not None
          and _PREVIEW_STABILIZATION_MODE_PREVIEW
          in supported_stabilization_modes,
          'Preview Stabilization not supported',
      )

      # Calculate camera FoV and convert from string to float
      camera_fov = float(cam.calc_camera_fov(props))

      # Get ffmpeg version being used
      ffmpeg_version = video_processing_utils.get_ffmpeg_version()
      logging.debug('ffmpeg_version: %s', ffmpeg_version)

      # Raise error if not FRONT or REAR facing camera
      facing = props['android.lens.facing']
      if (facing != camera_properties_utils.LENS_FACING_BACK
          and facing != camera_properties_utils.LENS_FACING_FRONT):
        raise AssertionError('Unknown lens facing: {facing}.')

      # Initialize rotation rig
      rot_rig['cntl'] = self.rotator_cntl
      rot_rig['ch'] = self.rotator_ch
      if rot_rig['cntl'].lower() != 'arduino':
        raise AssertionError(
            f'You must use the arduino controller for {_NAME}.')

      # List of video resolutions to test
      lowest_res_tested = video_processing_utils.LOWEST_RES_TESTED_AREA
      resolution_to_area = lambda s: int(s.split('x')[0])*int(s.split('x')[1])
      supported_preview_sizes = cam.get_supported_preview_sizes(self.camera_id)
      supported_preview_sizes = [size for size in supported_preview_sizes
                                 if resolution_to_area(size)
                                 >= lowest_res_tested]
      logging.debug('Supported preview resolutions: %s',
                    supported_preview_sizes)

      max_cam_gyro_angles = {}

      for video_size in supported_preview_sizes:
        recording_obj = _collect_data(cam, self.tablet_device, video_size, rot_rig)

        # Grab the video from the save location on DUT
        self.dut.adb.pull([recording_obj['recordedOutputPath'], log_path])
        file_name = recording_obj['recordedOutputPath'].split('/')[-1]
        logging.debug('recorded file name: %s', file_name)

        # Get gyro events
        logging.debug('Reading out inertial sensor events')
        gyro_events = cam.get_sensor_events()['gyro']
        logging.debug('Number of gyro samples %d', len(gyro_events))

        # Get all frames from the video
        file_list = video_processing_utils.extract_all_frames_from_video(
            log_path, file_name, _IMG_FORMAT
        )
        frames = []

        logging.debug('Number of frames %d', len(file_list))
        for file in file_list:
          img = image_processing_utils.convert_image_to_numpy_array(
              os.path.join(log_path, file)
          )
          frames.append(img / 255)
        frame_shape = frames[0].shape
        logging.debug('Frame size %d x %d', frame_shape[1], frame_shape[0])

        # Extract camera rotations
        img_h = frames[0].shape[0]
        file_name_stem = f'{os.path.join(log_path, _NAME)}_{video_size}'
        cam_rots = sensor_fusion_utils.get_cam_rotations(
            frames[_START_FRAME : len(frames)],
            facing,
            img_h,
            file_name_stem,
            _START_FRAME,
            stabilized_video=True
        )
        sensor_fusion_utils.plot_camera_rotations(cam_rots, _START_FRAME,
                                                  video_size, file_name_stem)
        max_camera_angle = sensor_fusion_utils.calc_max_rotation_angle(
            cam_rots, 'Camera')

        # Extract gyro rotations
        sensor_fusion_utils.plot_gyro_events(
            gyro_events, f'{_NAME}_{video_size}', log_path
        )
        gyro_rots = sensor_fusion_utils.conv_acceleration_to_movement(
            gyro_events, _VIDEO_DELAY_TIME
        )
        max_gyro_angle = sensor_fusion_utils.calc_max_rotation_angle(
            gyro_rots, 'Gyro')
        logging.debug(
            'Max deflection (degrees) %s: video: %.3f, gyro: %.3f ratio: %.4f',
            video_size, max_camera_angle, max_gyro_angle,
            max_camera_angle / max_gyro_angle)
        max_cam_gyro_angles[video_size] = {'gyro': max_gyro_angle,
                                           'cam': max_camera_angle}

        # Assert phone is moved enough during test
        if max_gyro_angle < _MIN_PHONE_MOVEMENT_ANGLE:
          raise AssertionError(
              f'Phone not moved enough! Movement: {max_gyro_angle}, '
              f'THRESH: {_MIN_PHONE_MOVEMENT_ANGLE} degrees')

      # Assert PASS/FAIL criteria
      test_failures = []
      for preview_size, max_angles in max_cam_gyro_angles.items():
        w_x_h = preview_size.split('x')
        if int(w_x_h[0])/int(w_x_h[1]) > _ASPECT_RATIO_16_9:
          preview_stabilization_factor = _PREVIEW_STABILIZATION_FACTOR * 1.1
        else:
          preview_stabilization_factor = _PREVIEW_STABILIZATION_FACTOR
        if max_angles['cam'] >= max_angles['gyro']*preview_stabilization_factor:
          test_failures.append(
              f'{preview_size} preview not stabilized enough! '
              f"Max preview angle:  {max_angles['cam']:.3f}, "
              f"Max gyro angle: {max_angles['gyro']:.3f}, "
              f"ratio: {max_angles['cam']/max_angles['gyro']:.3f} "
              f'THRESH: {preview_stabilization_factor}.')

      if test_failures:
        raise AssertionError(test_failures)


if __name__ == '__main__':
  test_runner.main()

