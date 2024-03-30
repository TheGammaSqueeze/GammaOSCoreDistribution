#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unit tests for retrieving the LLVM hash."""

from __future__ import print_function

import subprocess
import unittest
import unittest.mock as mock

import get_llvm_hash
from get_llvm_hash import LLVMHash

# We grab protected stuff from get_llvm_hash. That's OK.
# pylint: disable=protected-access


def MakeMockPopen(return_code):

  def MockPopen(*_args, **_kwargs):
    result = mock.MagicMock()
    result.returncode = return_code

    communicate_result = result.communicate.return_value
    # Communicate returns stdout, stderr.
    communicate_result.__iter__.return_value = (None, 'some stderr')
    return result

  return MockPopen


class TestGetLLVMHash(unittest.TestCase):
  """The LLVMHash test class."""

  @mock.patch.object(subprocess, 'Popen')
  def testCloneRepoSucceedsWhenGitSucceeds(self, popen_mock):
    popen_mock.side_effect = MakeMockPopen(return_code=0)
    llvm_hash = LLVMHash()

    into_tempdir = '/tmp/tmpTest'
    llvm_hash.CloneLLVMRepo(into_tempdir)
    popen_mock.assert_called_with(
        ['git', 'clone', get_llvm_hash._LLVM_GIT_URL, into_tempdir],
        stderr=subprocess.PIPE)

  @mock.patch.object(subprocess, 'Popen')
  def testCloneRepoFailsWhenGitFails(self, popen_mock):
    popen_mock.side_effect = MakeMockPopen(return_code=1)

    with self.assertRaises(ValueError) as err:
      LLVMHash().CloneLLVMRepo('/tmp/tmp1')

    self.assertIn('Failed to clone', str(err.exception.args))
    self.assertIn('some stderr', str(err.exception.args))

  @mock.patch.object(get_llvm_hash, 'GetGitHashFrom')
  def testGetGitHashWorks(self, mock_get_git_hash):
    mock_get_git_hash.return_value = 'a13testhash2'

    self.assertEqual(
        get_llvm_hash.GetGitHashFrom('/tmp/tmpTest', 100), 'a13testhash2')

    mock_get_git_hash.assert_called_once()

  @mock.patch.object(LLVMHash, 'GetLLVMHash')
  @mock.patch.object(get_llvm_hash, 'GetGoogle3LLVMVersion')
  def testReturnGoogle3LLVMHash(self, mock_google3_llvm_version,
                                mock_get_llvm_hash):
    mock_get_llvm_hash.return_value = 'a13testhash3'
    mock_google3_llvm_version.return_value = 1000
    self.assertEqual(LLVMHash().GetGoogle3LLVMHash(), 'a13testhash3')
    mock_get_llvm_hash.assert_called_once_with(1000)

  @mock.patch.object(LLVMHash, 'GetLLVMHash')
  @mock.patch.object(get_llvm_hash, 'GetGoogle3LLVMVersion')
  def testReturnGoogle3UnstableLLVMHash(self, mock_google3_llvm_version,
                                        mock_get_llvm_hash):
    mock_get_llvm_hash.return_value = 'a13testhash3'
    mock_google3_llvm_version.return_value = 1000
    self.assertEqual(LLVMHash().GetGoogle3UnstableLLVMHash(), 'a13testhash3')
    mock_get_llvm_hash.assert_called_once_with(1000)

  @mock.patch.object(subprocess, 'check_output')
  def testSuccessfullyGetGitHashFromToTOfLLVM(self, mock_check_output):
    mock_check_output.return_value = 'a123testhash1 path/to/main\n'
    self.assertEqual(LLVMHash().GetTopOfTrunkGitHash(), 'a123testhash1')
    mock_check_output.assert_called_once()

  @mock.patch.object(subprocess, 'Popen')
  def testCheckoutBranch(self, mock_popen):
    mock_popen.return_value = mock.MagicMock(
        communicate=lambda: (None, None), returncode=0)
    get_llvm_hash.CheckoutBranch('fake/src_dir', 'fake_branch')
    self.assertEqual(
        mock_popen.call_args_list[0][0],
        (['git', '-C', 'fake/src_dir', 'checkout', 'fake_branch'],))
    self.assertEqual(mock_popen.call_args_list[1][0],
                     (['git', '-C', 'fake/src_dir', 'pull'],))

  def testParseLLVMMajorVersion(self):
    cmakelist_42 = ('set(CMAKE_BUILD_WITH_INSTALL_NAME_DIR ON)\n'
                    'if(NOT DEFINED LLVM_VERSION_MAJOR)\n'
                    '  set(LLVM_VERSION_MAJOR 42)\n'
                    'endif()')
    self.assertEqual(get_llvm_hash.ParseLLVMMajorVersion(cmakelist_42), '42')

  def testParseLLVMMajorVersionInvalid(self):
    invalid_cmakelist = 'invalid cmakelist.txt contents'
    with self.assertRaises(ValueError):
      get_llvm_hash.ParseLLVMMajorVersion(invalid_cmakelist)

  @mock.patch.object(get_llvm_hash, 'GetAndUpdateLLVMProjectInLLVMTools')
  @mock.patch.object(get_llvm_hash, 'ParseLLVMMajorVersion')
  @mock.patch.object(get_llvm_hash, 'CheckCommand')
  @mock.patch.object(get_llvm_hash, 'CheckoutBranch')
  @mock.patch(
      'get_llvm_hash.open',
      mock.mock_open(read_data='mock contents'),
      create=True)
  def testGetLLVMMajorVersion(self, mock_checkout_branch, mock_git_checkout,
                              mock_major_version, mock_llvm_project_path):
    mock_llvm_project_path.return_value = 'path/to/llvm-project'
    mock_major_version.return_value = '1234'
    self.assertEqual(get_llvm_hash.GetLLVMMajorVersion('314159265'), '1234')
    # Second call should be memoized
    self.assertEqual(get_llvm_hash.GetLLVMMajorVersion('314159265'), '1234')
    mock_llvm_project_path.assert_called_once()
    mock_major_version.assert_called_with('mock contents')
    mock_git_checkout.assert_called_once_with(
        ['git', '-C', 'path/to/llvm-project', 'checkout', '314159265'])
    mock_checkout_branch.assert_called_once_with('path/to/llvm-project', 'main')


if __name__ == '__main__':
  unittest.main()
