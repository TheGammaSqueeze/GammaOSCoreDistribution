# Copyright 2020 Google Inc.
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
#
################################################################################
"""Utility module for Google Cloud Build scripts."""
import base64
import collections
import os
import six.moves.urllib.parse as urlparse
import sys
import time

import requests

import google.auth
import googleapiclient.discovery
from oauth2client.service_account import ServiceAccountCredentials

BUILD_TIMEOUT = 12 * 60 * 60

# Needed for reading public target.list.* files.
GCS_URL_BASENAME = 'https://storage.googleapis.com/'

GCS_UPLOAD_URL_FORMAT = '/{0}/{1}/{2}'

# Where corpus backups can be downloaded from.
CORPUS_BACKUP_URL = ('/{project}-backup.clusterfuzz-external.appspot.com/'
                     'corpus/libFuzzer/{fuzzer}/latest.zip')

# Cloud Builder has a limit of 100 build steps and 100 arguments for each step.
CORPUS_DOWNLOAD_BATCH_SIZE = 100

TARGETS_LIST_BASENAME = 'targets.list'

EngineInfo = collections.namedtuple(
    'EngineInfo',
    ['upload_bucket', 'supported_sanitizers', 'supported_architectures'])

ENGINE_INFO = {
    'libfuzzer':
        EngineInfo(upload_bucket='clusterfuzz-builds',
                   supported_sanitizers=['address', 'memory', 'undefined'],
                   supported_architectures=['x86_64', 'i386']),
    'afl':
        EngineInfo(upload_bucket='clusterfuzz-builds-afl',
                   supported_sanitizers=['address'],
                   supported_architectures=['x86_64']),
    'honggfuzz':
        EngineInfo(upload_bucket='clusterfuzz-builds-honggfuzz',
                   supported_sanitizers=['address'],
                   supported_architectures=['x86_64']),
    'dataflow':
        EngineInfo(upload_bucket='clusterfuzz-builds-dataflow',
                   supported_sanitizers=['dataflow'],
                   supported_architectures=['x86_64']),
    'none':
        EngineInfo(upload_bucket='clusterfuzz-builds-no-engine',
                   supported_sanitizers=['address'],
                   supported_architectures=['x86_64']),
}


def get_targets_list_filename(sanitizer):
  """Returns target list filename."""
  return TARGETS_LIST_BASENAME + '.' + sanitizer


def get_targets_list_url(bucket, project, sanitizer):
  """Returns target list url."""
  filename = get_targets_list_filename(sanitizer)
  url = GCS_UPLOAD_URL_FORMAT.format(bucket, project, filename)
  return url


def get_upload_bucket(engine, architecture, testing):
  """Returns the upload bucket for |engine| and architecture. Returns the
  testing bucket if |testing|."""
  bucket = ENGINE_INFO[engine].upload_bucket
  if architecture != 'x86_64':
    bucket += '-' + architecture
  if testing:
    bucket += '-testing'
  return bucket


def _get_targets_list(project_name, testing):
  """Returns target list."""
  # libFuzzer ASan 'x86_84' is the default configuration, get list of targets
  # from it.
  bucket = get_upload_bucket('libfuzzer', 'x86_64', testing)
  url = get_targets_list_url(bucket, project_name, 'address')

  url = urlparse.urljoin(GCS_URL_BASENAME, url)
  response = requests.get(url)
  if not response.status_code == 200:
    sys.stderr.write('Failed to get list of targets from "%s".\n' % url)
    sys.stderr.write('Status code: %d \t\tText:\n%s\n' %
                     (response.status_code, response.text))
    return None

  return response.text.split()


# pylint: disable=no-member
def get_signed_url(path, method='PUT', content_type=''):
  """Returns signed url."""
  timestamp = int(time.time() + BUILD_TIMEOUT)
  blob = f'{method}\n\n{content_type}\n{timestamp}\n{path}'

  service_account_path = os.environ.get('GOOGLE_APPLICATION_CREDENTIALS')
  if service_account_path:
    creds = ServiceAccountCredentials.from_json_keyfile_name(
        os.environ['GOOGLE_APPLICATION_CREDENTIALS'])
    client_id = creds.service_account_email
    signature = base64.b64encode(creds.sign_blob(blob)[1])
  else:
    credentials, project = google.auth.default()
    iam = googleapiclient.discovery.build('iamcredentials',
                                          'v1',
                                          credentials=credentials,
                                          cache_discovery=False)
    client_id = project + '@appspot.gserviceaccount.com'
    service_account = f'projects/-/serviceAccounts/{client_id}'
    response = iam.projects().serviceAccounts().signBlob(
        name=service_account,
        body={
            'delegates': [],
            'payload': base64.b64encode(blob.encode('utf-8')).decode('utf-8'),
        }).execute()
    signature = response['signedBlob']

  values = {
      'GoogleAccessId': client_id,
      'Expires': timestamp,
      'Signature': signature,
  }
  return f'https://storage.googleapis.com{path}?{urlparse.urlencode(values)}'


def download_corpora_steps(project_name, testing):
  """Returns GCB steps for downloading corpora backups for the given project.
  """
  fuzz_targets = _get_targets_list(project_name, testing)
  if not fuzz_targets:
    sys.stderr.write('No fuzz targets found for project "%s".\n' % project_name)
    return None

  steps = []
  # Split fuzz targets into batches of CORPUS_DOWNLOAD_BATCH_SIZE.
  for i in range(0, len(fuzz_targets), CORPUS_DOWNLOAD_BATCH_SIZE):
    download_corpus_args = []
    for binary_name in fuzz_targets[i:i + CORPUS_DOWNLOAD_BATCH_SIZE]:
      qualified_name = binary_name
      qualified_name_prefix = '%s_' % project_name
      if not binary_name.startswith(qualified_name_prefix):
        qualified_name = qualified_name_prefix + binary_name

      url = get_signed_url(CORPUS_BACKUP_URL.format(project=project_name,
                                                    fuzzer=qualified_name),
                           method='GET')

      corpus_archive_path = os.path.join('/corpus', binary_name + '.zip')
      download_corpus_args.append('%s %s' % (corpus_archive_path, url))

    steps.append({
        'name': 'gcr.io/oss-fuzz-base/base-runner',
        'entrypoint': 'download_corpus',
        'args': download_corpus_args,
        'volumes': [{
            'name': 'corpus',
            'path': '/corpus'
        }],
    })

  return steps


def http_upload_step(data, signed_url, content_type):
  """Returns a GCB step to upload data to the given URL via GCS HTTP API."""
  step = {
      'name':
          'gcr.io/cloud-builders/curl',
      'args': [
          '-H',
          'Content-Type: ' + content_type,
          '-X',
          'PUT',
          '-d',
          data,
          signed_url,
      ],
  }
  return step


def gsutil_rm_rf_step(url):
  """Returns a GCB step to recursively delete the object with given GCS url."""
  step = {
      'name': 'gcr.io/cloud-builders/gsutil',
      'entrypoint': 'sh',
      'args': [
          '-c',
          'gsutil -m rm -rf %s || exit 0' % url,
      ],
  }
  return step


def get_pull_test_images_steps(test_image_suffix):
  """Returns steps to pull testing versions of base-images and tag them so that
  they are used in builds."""
  images = [
      'gcr.io/oss-fuzz-base/base-builder',
      'gcr.io/oss-fuzz-base/base-builder-swift',
      'gcr.io/oss-fuzz-base/base-builder-jvm',
      'gcr.io/oss-fuzz-base/base-builder-go',
      'gcr.io/oss-fuzz-base/base-builder-python',
      'gcr.io/oss-fuzz-base/base-builder-rust',
  ]
  steps = []
  for image in images:
    test_image = image + '-' + test_image_suffix
    steps.append({
        'name': 'gcr.io/cloud-builders/docker',
        'args': [
            'pull',
            test_image,
        ],
        'waitFor': '-'  # Start this immediately, don't wait for previous step.
    })

    # This step is hacky but gives us great flexibility. OSS-Fuzz has hardcoded
    # references to gcr.io/oss-fuzz-base/base-builder (in dockerfiles, for
    # example) and gcr.io/oss-fuzz-base-runner (in this build code). But the
    # testing versions of those images are called e.g.
    # gcr.io/oss-fuzz-base/base-builder-testing and
    # gcr.io/oss-fuzz-base/base-runner-testing. How can we get the build to use
    # the testing images instead of the real ones? By doing this step: tagging
    # the test image with the non-test version, so that the test version is used
    # instead of pulling the real one.
    steps.append({
        'name': 'gcr.io/cloud-builders/docker',
        'args': ['tag', test_image, image],
    })
  return steps


def get_srcmap_step_id():
  """Returns the id for the srcmap step."""
  return 'srcmap'


def project_image_steps(name,
                        image,
                        language,
                        branch=None,
                        test_image_suffix=None):
  """Returns GCB steps to build OSS-Fuzz project image."""
  clone_step = {
      'args': [
          'clone', 'https://github.com/google/oss-fuzz.git', '--depth', '1'
      ],
      'name': 'gcr.io/cloud-builders/git',
  }
  if branch:
    # Do this to support testing other branches.
    clone_step['args'].extend(['--branch', branch])

  steps = [clone_step]
  if test_image_suffix:
    steps.extend(get_pull_test_images_steps(test_image_suffix))

  srcmap_step_id = get_srcmap_step_id()
  steps += [{
      'name': 'gcr.io/cloud-builders/docker',
      'args': [
          'build',
          '-t',
          image,
          '.',
      ],
      'dir': 'oss-fuzz/projects/' + name,
  }, {
      'name': image,
      'args': [
          'bash', '-c',
          'srcmap > /workspace/srcmap.json && cat /workspace/srcmap.json'
      ],
      'env': [
          'OSSFUZZ_REVISION=$REVISION_ID',
          'FUZZING_LANGUAGE=%s' % language,
      ],
      'id': srcmap_step_id
  }]

  return steps
