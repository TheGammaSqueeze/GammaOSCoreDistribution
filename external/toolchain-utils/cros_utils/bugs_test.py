#!/usr/bin/env python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# We're testing protected methods, so allow protected access.
# pylint: disable=protected-access

"""Tests bug filing bits."""

import json
import tempfile
import unittest
from unittest.mock import patch

import bugs


class Tests(unittest.TestCase):
  """Tests for the bugs module."""
  def testWritingJSONFileSeemsToWork(self):
    """Tests JSON file writing."""
    old_x20_path = bugs.X20_PATH

    def restore_x20_path():
      bugs.X20_PATH = old_x20_path

    self.addCleanup(restore_x20_path)

    with tempfile.TemporaryDirectory() as tempdir:
      bugs.X20_PATH = tempdir
      file_path = bugs._WriteBugJSONFile(
          'ObjectType', {
              'foo': 'bar',
              'baz': bugs.WellKnownComponents.CrOSToolchainPublic,
          })

      self.assertTrue(file_path.startswith(tempdir),
                      f'Expected {file_path} to start with {tempdir}')

      with open(file_path) as f:
        self.assertEqual(
            json.load(f),
            {
                'type': 'ObjectType',
                'value': {
                    'foo': 'bar',
                    'baz': int(bugs.WellKnownComponents.CrOSToolchainPublic),
                },
            },
        )

  @patch('bugs._WriteBugJSONFile')
  def testAppendingToBugsSeemsToWork(self, mock_write_json_file):
    """Tests AppendToExistingBug."""
    bugs.AppendToExistingBug(1234, 'hello, world!')
    mock_write_json_file.assert_called_once_with(
        'AppendToExistingBugRequest',
        {
            'body': 'hello, world!',
            'bug_id': 1234,
        },
    )

  @patch('bugs._WriteBugJSONFile')
  def testBugCreationSeemsToWork(self, mock_write_json_file):
    """Tests CreateNewBug."""
    test_case_additions = (
        {},
        {
            'component_id': bugs.WellKnownComponents.CrOSToolchainPublic,
        },
        {
            'assignee': 'foo@gbiv.com',
            'cc': ['bar@baz.com'],
        },
    )

    for additions in test_case_additions:
      test_case = {
          'component_id': 123,
          'title': 'foo',
          'body': 'bar',
          **additions,
      }

      bugs.CreateNewBug(**test_case)

      expected_output = {
          'component_id': test_case['component_id'],
          'subject': test_case['title'],
          'body': test_case['body'],
      }

      assignee = test_case.get('assignee')
      if assignee:
        expected_output['assignee'] = assignee

      cc = test_case.get('cc')
      if cc:
        expected_output['cc'] = cc

      mock_write_json_file.assert_called_once_with(
          'FileNewBugRequest',
          expected_output,
      )
      mock_write_json_file.reset_mock()

  @patch('bugs._WriteBugJSONFile')
  def testCronjobLogSendingSeemsToWork(self, mock_write_json_file):
    """Tests SendCronjobLog."""
    bugs.SendCronjobLog('my_name', False, 'hello, world!')
    mock_write_json_file.assert_called_once_with(
        'ChrotomationCronjobUpdate',
        {
            'name': 'my_name',
            'message': 'hello, world!',
            'failed': False,
        },
    )


if __name__ == '__main__':
  unittest.main()
