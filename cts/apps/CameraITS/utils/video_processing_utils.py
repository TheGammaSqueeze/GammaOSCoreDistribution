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
"""Utility functions for processing video recordings.
"""
# Each item in this list corresponds to quality levels defined per
# CamcorderProfile. For Video ITS, we will currently test below qualities
# only if supported by the camera device.
import logging
import os.path
import subprocess
import error_util


ITS_SUPPORTED_QUALITIES = (
    'HIGH',
    '2160P',
    '1080P',
    '720P',
    '480P',
    'CIF',
    'QCIF',
    'QVGA',
    'LOW',
    'VGA'
)

LOW_RESOLUTION_SIZES = (
    '176x144',
    '192x144',
    '352x288',
    '384x288',
    '320x240',
)

LOWEST_RES_TESTED_AREA = 640*360

def get_ffmpeg_version():
  """Returns the ffmpeg version being used."""

  ffmpeg_version_cmd = ('ffmpeg -version')
  p = subprocess.Popen(ffmpeg_version_cmd, shell=True, stdout=subprocess.PIPE)
  output, _ = p.communicate()
  if p.poll() != 0:
    raise error_util.CameraItsError('Error running ffmpeg version cmd.')
  decoded_output = output.decode('utf-8')
  return decoded_output.split(' ')[2]


def extract_key_frames_from_video(log_path, video_file_name):
  """Returns a list of extracted key frames.

  Ffmpeg tool is used to extract key frames from the video at path
  os.path.join(log_path, video_file_name).
  The extracted key frames will have the name video_file_name with "_key_frame"
  suffix to identify the frames for video of each quality.Since there can be
  multiple key frames, each key frame image will be differentiated with it's
  frame index.All the extracted key frames will be available in  jpeg format
  at the same path as the video file.

  The run time flag '-loglevel quiet' hides the information from terminal.
  In order to see the detailed output of ffmpeg command change the loglevel
  option to 'info'.

  Args:
    log_path: path for video file directory
    video_file_name: name of the video file.
  Returns:
    key_frame_files: A list of paths for each key frame extracted from the
    video. Ex: VID_20220325_050918_0_CIF_352x288.mp4
  """
  ffmpeg_image_name = f"{video_file_name.split('.')[0]}_key_frame"
  ffmpeg_image_file_path = os.path.join(
      log_path, ffmpeg_image_name + '_%02d.png')
  cmd = ['ffmpeg',
         '-skip_frame',
         'nokey',
         '-i',
         os.path.join(log_path, video_file_name),
         '-vsync',
         'vfr',
         '-frame_pts',
         'true',
         ffmpeg_image_file_path,
         '-loglevel',
         'quiet',
        ]
  logging.debug('Extracting key frames from: %s', video_file_name)
  _ = subprocess.call(cmd)
  arr = os.listdir(os.path.join(log_path))
  key_frame_files = []
  for file in arr:
    if '.png' in file and not os.path.isdir(file) and ffmpeg_image_name in file:
      key_frame_files.append(file)

  logging.debug('Extracted key frames: %s', key_frame_files)
  logging.debug('Length of key_frame_files: %d', len(key_frame_files))
  if not key_frame_files:
    raise AssertionError('No key frames extracted. Check source video.')

  return key_frame_files


def get_key_frame_to_process(key_frame_files):
  """Returns the key frame file from the list of key_frame_files.

  If the size of the list is 1 then the file in the list will be returned else
  the file with highest frame_index will be returned for further processing.

  Args:
    key_frame_files: A list of key frame files.
  Returns:
    key_frame_file to be used for further processing.
  """
  if not key_frame_files:
    raise AssertionError('key_frame_files list is empty.')
  key_frame_files.sort()
  return key_frame_files[-1]


def extract_all_frames_from_video(log_path, video_file_name, img_format):
  """Extracts and returns a list of all extracted frames.

  Ffmpeg tool is used to extract all frames from the video at path
  <log_path>/<video_file_name>. The extracted key frames will have the name
  video_file_name with "_frame" suffix to identify the frames for video of each
  size. Each frame image will be differentiated with its frame index. All
  extracted key frames will be available in the provided img_format format at
  the same path as the video file.

  The run time flag '-loglevel quiet' hides the information from terminal.
  In order to see the detailed output of ffmpeg command change the loglevel
  option to 'info'.

  Args:
    log_path: str; path for video file directory
    video_file_name: str; name of the video file.
    img_format: str; type of image to export frames into. ex. 'png'
  Returns:
    key_frame_files: An ordered list of paths for each frame extracted from the
                     video
  """
  logging.debug('Extracting all frames')
  ffmpeg_image_name = f"{video_file_name.split('.')[0]}_frame"
  logging.debug('ffmpeg_image_name: %s', ffmpeg_image_name)
  ffmpeg_image_file_names = (
      f'{os.path.join(log_path, ffmpeg_image_name)}_%03d.{img_format}')
  cmd = [
      'ffmpeg', '-i', os.path.join(log_path, video_file_name),
      ffmpeg_image_file_names, '-loglevel', 'quiet'
  ]
  _ = subprocess.call(cmd)

  file_list = sorted(
      [_ for _ in os.listdir(log_path) if (_.endswith(img_format)
                                           and ffmpeg_image_name in _)])
  if not file_list:
    raise AssertionError('No frames extracted. Check source video.')

  return file_list
