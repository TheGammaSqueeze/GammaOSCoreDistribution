#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tests for rust_uprev.py"""

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from llvm_tools import git

import rust_uprev
from rust_uprev import RustVersion


def _fail_command(cmd, *_args, **_kwargs):
  err = subprocess.CalledProcessError(returncode=1, cmd=cmd)
  err.stderr = b'mock failure'
  raise err


class FetchDistfileTest(unittest.TestCase):
  """Tests rust_uprev.fetch_distfile_from_mirror()"""

  @mock.patch.object(rust_uprev, 'get_distdir', return_value='/fake/distfiles')
  @mock.patch.object(subprocess, 'call', side_effect=_fail_command)
  def test_fetch_difstfile_fail(self, *_args) -> None:
    with self.assertRaises(subprocess.CalledProcessError):
      rust_uprev.fetch_distfile_from_mirror('test_distfile.tar.gz')

  @mock.patch.object(rust_uprev,
                     'get_command_output_unchecked',
                     return_value='AccessDeniedException: Access denied.')
  @mock.patch.object(rust_uprev, 'get_distdir', return_value='/fake/distfiles')
  @mock.patch.object(subprocess, 'call', return_value=0)
  def test_fetch_distfile_acl_access_denied(self, *_args) -> None:
    rust_uprev.fetch_distfile_from_mirror('test_distfile.tar.gz')

  @mock.patch.object(
      rust_uprev,
      'get_command_output_unchecked',
      return_value='[ { "entity": "allUsers", "role": "READER" } ]')
  @mock.patch.object(rust_uprev, 'get_distdir', return_value='/fake/distfiles')
  @mock.patch.object(subprocess, 'call', return_value=0)
  def test_fetch_distfile_acl_ok(self, *_args) -> None:
    rust_uprev.fetch_distfile_from_mirror('test_distfile.tar.gz')

  @mock.patch.object(
      rust_uprev,
      'get_command_output_unchecked',
      return_value='[ { "entity": "___fake@google.com", "role": "OWNER" } ]')
  @mock.patch.object(rust_uprev, 'get_distdir', return_value='/fake/distfiles')
  @mock.patch.object(subprocess, 'call', return_value=0)
  def test_fetch_distfile_acl_wrong(self, *_args) -> None:
    with self.assertRaisesRegex(Exception, 'allUsers.*READER'):
      with self.assertLogs(level='ERROR') as log:
        rust_uprev.fetch_distfile_from_mirror('test_distfile.tar.gz')
        self.assertIn(
            '[ { "entity": "___fake@google.com", "role": "OWNER" } ]',
            '\n'.join(log.output))


class FindEbuildPathTest(unittest.TestCase):
  """Tests for rust_uprev.find_ebuild_path()"""

  def test_exact_version(self):
    with tempfile.TemporaryDirectory() as tmpdir:
      ebuild = Path(tmpdir, 'test-1.3.4.ebuild')
      ebuild.touch()
      Path(tmpdir, 'test-1.2.3.ebuild').touch()
      result = rust_uprev.find_ebuild_path(tmpdir, 'test',
                                           rust_uprev.RustVersion(1, 3, 4))
      self.assertEqual(result, ebuild)

  def test_no_version(self):
    with tempfile.TemporaryDirectory() as tmpdir:
      ebuild = Path(tmpdir, 'test-1.2.3.ebuild')
      ebuild.touch()
      result = rust_uprev.find_ebuild_path(tmpdir, 'test')
      self.assertEqual(result, ebuild)

  def test_patch_version(self):
    with tempfile.TemporaryDirectory() as tmpdir:
      ebuild = Path(tmpdir, 'test-1.3.4-r3.ebuild')
      ebuild.touch()
      Path(tmpdir, 'test-1.2.3.ebuild').touch()
      result = rust_uprev.find_ebuild_path(tmpdir, 'test',
                                           rust_uprev.RustVersion(1, 3, 4))
      self.assertEqual(result, ebuild)


class RustVersionTest(unittest.TestCase):
  """Tests for RustVersion class"""

  def test_str(self):
    obj = rust_uprev.RustVersion(major=1, minor=2, patch=3)
    self.assertEqual(str(obj), '1.2.3')

  def test_parse_version_only(self):
    expected = rust_uprev.RustVersion(major=1, minor=2, patch=3)
    actual = rust_uprev.RustVersion.parse('1.2.3')
    self.assertEqual(expected, actual)

  def test_parse_ebuild_name(self):
    expected = rust_uprev.RustVersion(major=1, minor=2, patch=3)
    actual = rust_uprev.RustVersion.parse_from_ebuild('rust-1.2.3.ebuild')
    self.assertEqual(expected, actual)

    actual = rust_uprev.RustVersion.parse_from_ebuild('rust-1.2.3-r1.ebuild')
    self.assertEqual(expected, actual)

  def test_parse_fail(self):
    with self.assertRaises(AssertionError) as context:
      rust_uprev.RustVersion.parse('invalid-rust-1.2.3')
    self.assertEqual("failed to parse 'invalid-rust-1.2.3'",
                     str(context.exception))


class PrepareUprevTest(unittest.TestCase):
  """Tests for prepare_uprev step in rust_uprev"""

  def setUp(self):
    self.bootstrap_version = rust_uprev.RustVersion(1, 1, 0)
    self.version_old = rust_uprev.RustVersion(1, 2, 3)
    self.version_new = rust_uprev.RustVersion(1, 3, 5)

  @mock.patch.object(rust_uprev,
                     'find_ebuild_for_rust_version',
                     return_value='/path/to/ebuild')
  @mock.patch.object(rust_uprev, 'find_ebuild_path')
  @mock.patch.object(rust_uprev, 'get_command_output')
  def test_success_with_template(self, mock_command, mock_find_ebuild,
                                 _ebuild_for_version):
    bootstrap_ebuild_path = Path(
        '/path/to/rust-bootstrap/',
        f'rust-bootstrap-{self.bootstrap_version}.ebuild')
    mock_find_ebuild.return_value = bootstrap_ebuild_path
    expected = (self.version_old, '/path/to/ebuild', self.bootstrap_version)
    actual = rust_uprev.prepare_uprev(rust_version=self.version_new,
                                      template=self.version_old)
    self.assertEqual(expected, actual)
    mock_command.assert_not_called()

  @mock.patch.object(rust_uprev,
                     'find_ebuild_for_rust_version',
                     return_value='/path/to/ebuild')
  @mock.patch.object(rust_uprev,
                     'get_rust_bootstrap_version',
                     return_value=RustVersion(0, 41, 12))
  @mock.patch.object(rust_uprev, 'get_command_output')
  def test_return_none_with_template_larger_than_input(self, mock_command,
                                                       *_args):
    ret = rust_uprev.prepare_uprev(rust_version=self.version_old,
                                   template=self.version_new)
    self.assertIsNone(ret)
    mock_command.assert_not_called()

  @mock.patch.object(rust_uprev, 'find_ebuild_path')
  @mock.patch.object(os.path, 'exists')
  @mock.patch.object(rust_uprev, 'get_command_output')
  def test_success_without_template(self, mock_command, mock_exists,
                                    mock_find_ebuild):
    rust_ebuild_path = f'/path/to/rust/rust-{self.version_old}-r3.ebuild'
    mock_command.return_value = rust_ebuild_path
    bootstrap_ebuild_path = Path(
        '/path/to/rust-bootstrap',
        f'rust-bootstrap-{self.bootstrap_version}.ebuild')
    mock_find_ebuild.return_value = bootstrap_ebuild_path
    expected = (self.version_old, rust_ebuild_path, self.bootstrap_version)
    actual = rust_uprev.prepare_uprev(rust_version=self.version_new,
                                      template=None)
    self.assertEqual(expected, actual)
    mock_command.assert_called_once_with(['equery', 'w', 'rust'])
    mock_exists.assert_not_called()

  @mock.patch.object(rust_uprev,
                     'get_rust_bootstrap_version',
                     return_value=RustVersion(0, 41, 12))
  @mock.patch.object(os.path, 'exists')
  @mock.patch.object(rust_uprev, 'get_command_output')
  def test_return_none_with_ebuild_larger_than_input(self, mock_command,
                                                     mock_exists, *_args):
    mock_command.return_value = f'/path/to/rust/rust-{self.version_new}.ebuild'
    ret = rust_uprev.prepare_uprev(rust_version=self.version_old,
                                   template=None)
    self.assertIsNone(ret)
    mock_exists.assert_not_called()

  def test_prepare_uprev_from_json(self):
    ebuild_path = '/path/to/the/ebuild'
    json_result = (list(self.version_new), ebuild_path,
                   list(self.bootstrap_version))
    expected = (self.version_new, ebuild_path, self.bootstrap_version)
    actual = rust_uprev.prepare_uprev_from_json(json_result)
    self.assertEqual(expected, actual)


class UpdateEbuildTest(unittest.TestCase):
  """Tests for update_ebuild step in rust_uprev"""
  ebuild_file_before = """
BOOTSTRAP_VERSION="1.2.0"
    """
  ebuild_file_after = """
BOOTSTRAP_VERSION="1.3.6"
    """

  def test_success(self):
    mock_open = mock.mock_open(read_data=self.ebuild_file_before)
    # ebuild_file and new bootstrap version are deliberately different
    ebuild_file = '/path/to/rust/rust-1.3.5.ebuild'
    with mock.patch('builtins.open', mock_open):
      rust_uprev.update_ebuild(ebuild_file,
                               rust_uprev.RustVersion.parse('1.3.6'))
    mock_open.return_value.__enter__().write.assert_called_once_with(
        self.ebuild_file_after)

  def test_fail_when_ebuild_misses_a_variable(self):
    mock_open = mock.mock_open(read_data='')
    ebuild_file = '/path/to/rust/rust-1.3.5.ebuild'
    with mock.patch('builtins.open', mock_open):
      with self.assertRaises(RuntimeError) as context:
        rust_uprev.update_ebuild(ebuild_file,
                                 rust_uprev.RustVersion.parse('1.2.0'))
    self.assertEqual('BOOTSTRAP_VERSION not found in rust ebuild',
                     str(context.exception))


class UpdateManifestTest(unittest.TestCase):
  """Tests for update_manifest step in rust_uprev"""

  # pylint: disable=protected-access
  def _run_test_flip_mirror(self, before, after, add, expect_write):
    mock_open = mock.mock_open(read_data=f'RESTRICT="{before}"')
    with mock.patch('builtins.open', mock_open):
      rust_uprev.flip_mirror_in_ebuild('', add=add)
    if expect_write:
      mock_open.return_value.__enter__().write.assert_called_once_with(
          f'RESTRICT="{after}"')

  def test_add_mirror_in_ebuild(self):
    self._run_test_flip_mirror(before='variable1 variable2',
                               after='variable1 variable2 mirror',
                               add=True,
                               expect_write=True)

  def test_remove_mirror_in_ebuild(self):
    self._run_test_flip_mirror(before='variable1 variable2 mirror',
                               after='variable1 variable2',
                               add=False,
                               expect_write=True)

  def test_add_mirror_when_exists(self):
    self._run_test_flip_mirror(before='variable1 variable2 mirror',
                               after='variable1 variable2 mirror',
                               add=True,
                               expect_write=False)

  def test_remove_mirror_when_not_exists(self):
    self._run_test_flip_mirror(before='variable1 variable2',
                               after='variable1 variable2',
                               add=False,
                               expect_write=False)

  @mock.patch.object(rust_uprev, 'flip_mirror_in_ebuild')
  @mock.patch.object(rust_uprev, 'ebuild_actions')
  def test_update_manifest(self, mock_run, mock_flip):
    ebuild_file = Path('/path/to/rust/rust-1.1.1.ebuild')
    rust_uprev.update_manifest(ebuild_file)
    mock_run.assert_called_once_with('rust', ['manifest'])
    mock_flip.assert_has_calls(
        [mock.call(ebuild_file, add=True),
         mock.call(ebuild_file, add=False)])


class UpdateBootstrapEbuildTest(unittest.TestCase):
  """Tests for rust_uprev.update_bootstrap_ebuild()"""

  def test_update_bootstrap_ebuild(self):
    # The update should do two things:
    # 1. Create a copy of rust-bootstrap's ebuild with the new version number.
    # 2. Add the old PV to RUSTC_RAW_FULL_BOOTSTRAP_SEQUENCE.
    with tempfile.TemporaryDirectory() as tmpdir_str, \
         mock.patch.object(rust_uprev, 'find_ebuild_path') as mock_find_ebuild:
      tmpdir = Path(tmpdir_str)
      bootstrapdir = Path.joinpath(tmpdir, 'rust-bootstrap')
      bootstrapdir.mkdir()
      old_ebuild = bootstrapdir.joinpath('rust-bootstrap-1.45.2.ebuild')
      old_ebuild.write_text(encoding='utf-8',
                            data="""
some text
RUSTC_RAW_FULL_BOOTSTRAP_SEQUENCE=(
\t1.43.1
\t1.44.1
)
some more text
""")
      mock_find_ebuild.return_value = old_ebuild
      rust_uprev.update_bootstrap_ebuild(rust_uprev.RustVersion(1, 46, 0))
      new_ebuild = bootstrapdir.joinpath('rust-bootstrap-1.46.0.ebuild')
      self.assertTrue(new_ebuild.exists())
      text = new_ebuild.read_text()
      self.assertEqual(
          text, """
some text
RUSTC_RAW_FULL_BOOTSTRAP_SEQUENCE=(
\t1.43.1
\t1.44.1
\t1.45.2
)
some more text
""")


class UpdateRustPackagesTests(unittest.TestCase):
  """Tests for update_rust_packages step."""

  def setUp(self):
    self.old_version = rust_uprev.RustVersion(1, 1, 0)
    self.current_version = rust_uprev.RustVersion(1, 2, 3)
    self.new_version = rust_uprev.RustVersion(1, 3, 5)
    self.ebuild_file = os.path.join(rust_uprev.RUST_PATH,
                                    'rust-{self.new_version}.ebuild')

  def test_add_new_rust_packages(self):
    package_before = (f'dev-lang/rust-{self.old_version}\n'
                      f'dev-lang/rust-{self.current_version}')
    package_after = (f'dev-lang/rust-{self.old_version}\n'
                     f'dev-lang/rust-{self.current_version}\n'
                     f'dev-lang/rust-{self.new_version}')
    mock_open = mock.mock_open(read_data=package_before)
    with mock.patch('builtins.open', mock_open):
      rust_uprev.update_rust_packages(self.new_version, add=True)
    mock_open.return_value.__enter__().write.assert_called_once_with(
        package_after)

  def test_remove_old_rust_packages(self):
    package_before = (f'dev-lang/rust-{self.old_version}\n'
                      f'dev-lang/rust-{self.current_version}\n'
                      f'dev-lang/rust-{self.new_version}')
    package_after = (f'dev-lang/rust-{self.current_version}\n'
                     f'dev-lang/rust-{self.new_version}')
    mock_open = mock.mock_open(read_data=package_before)
    with mock.patch('builtins.open', mock_open):
      rust_uprev.update_rust_packages(self.old_version, add=False)
    mock_open.return_value.__enter__().write.assert_called_once_with(
        package_after)


class RustUprevOtherStagesTests(unittest.TestCase):
  """Tests for other steps in rust_uprev"""

  def setUp(self):
    self.old_version = rust_uprev.RustVersion(1, 1, 0)
    self.current_version = rust_uprev.RustVersion(1, 2, 3)
    self.new_version = rust_uprev.RustVersion(1, 3, 5)
    self.ebuild_file = os.path.join(rust_uprev.RUST_PATH,
                                    'rust-{self.new_version}.ebuild')

  @mock.patch.object(shutil, 'copyfile')
  @mock.patch.object(os, 'listdir')
  @mock.patch.object(subprocess, 'check_call')
  def test_copy_patches(self, mock_call, mock_ls, mock_copy):
    mock_ls.return_value = [
        f'rust-{self.old_version}-patch-1.patch',
        f'rust-{self.old_version}-patch-2-old.patch',
        f'rust-{self.current_version}-patch-1.patch',
        f'rust-{self.current_version}-patch-2-new.patch'
    ]
    rust_uprev.copy_patches(rust_uprev.RUST_PATH, self.current_version,
                            self.new_version)
    mock_copy.assert_has_calls([
        mock.call(
            os.path.join(rust_uprev.RUST_PATH, 'files',
                         f'rust-{self.current_version}-patch-1.patch'),
            os.path.join(rust_uprev.RUST_PATH, 'files',
                         f'rust-{self.new_version}-patch-1.patch'),
        ),
        mock.call(
            os.path.join(rust_uprev.RUST_PATH, 'files',
                         f'rust-{self.current_version}-patch-2-new.patch'),
            os.path.join(rust_uprev.RUST_PATH, 'files',
                         f'rust-{self.new_version}-patch-2-new.patch'))
    ])
    mock_call.assert_called_once_with(
        ['git', 'add', f'rust-{self.new_version}-*.patch'],
        cwd=rust_uprev.RUST_PATH.joinpath('files'))

  @mock.patch.object(shutil, 'copyfile')
  @mock.patch.object(subprocess, 'check_call')
  def test_create_ebuild(self, mock_call, mock_copy):
    template_ebuild = f'/path/to/rust-{self.current_version}-r2.ebuild'
    rust_uprev.create_ebuild(template_ebuild, self.new_version)
    mock_copy.assert_called_once_with(
        template_ebuild,
        rust_uprev.RUST_PATH.joinpath(f'rust-{self.new_version}.ebuild'))
    mock_call.assert_called_once_with(
        ['git', 'add', f'rust-{self.new_version}.ebuild'],
        cwd=rust_uprev.RUST_PATH)

  @mock.patch.object(rust_uprev, 'find_ebuild_for_package')
  @mock.patch.object(subprocess, 'check_call')
  def test_remove_rust_bootstrap_version(self, mock_call, *_args):
    bootstrap_path = os.path.join(rust_uprev.RUST_PATH, '..', 'rust-bootstrap')
    rust_uprev.remove_rust_bootstrap_version(self.old_version, lambda *x: ())
    mock_call.has_calls([
        [
            'git', 'rm',
            os.path.join(bootstrap_path, 'files',
                         f'rust-bootstrap-{self.old_version}-*.patch')
        ],
        [
            'git', 'rm',
            os.path.join(bootstrap_path,
                         f'rust-bootstrap-{self.old_version}.ebuild')
        ],
    ])

  @mock.patch.object(rust_uprev, 'find_ebuild_path')
  @mock.patch.object(subprocess, 'check_call')
  def test_remove_virtual_rust(self, mock_call, mock_find_ebuild):
    ebuild_path = Path(
        f'/some/dir/virtual/rust/rust-{self.old_version}.ebuild')
    mock_find_ebuild.return_value = Path(ebuild_path)
    rust_uprev.remove_virtual_rust(self.old_version)
    mock_call.assert_called_once_with(
        ['git', 'rm', str(ebuild_path.name)], cwd=ebuild_path.parent)

  @mock.patch.object(rust_uprev, 'find_ebuild_path')
  @mock.patch.object(shutil, 'copyfile')
  @mock.patch.object(subprocess, 'check_call')
  def test_update_virtual_rust(self, mock_call, mock_copy, mock_find_ebuild):
    ebuild_path = Path(
        f'/some/dir/virtual/rust/rust-{self.current_version}.ebuild')
    mock_find_ebuild.return_value = Path(ebuild_path)
    rust_uprev.update_virtual_rust(self.current_version, self.new_version)
    mock_call.assert_called_once_with(
        ['git', 'add', f'rust-{self.new_version}.ebuild'],
        cwd=ebuild_path.parent)
    mock_copy.assert_called_once_with(
        ebuild_path.parent.joinpath(f'rust-{self.current_version}.ebuild'),
        ebuild_path.parent.joinpath(f'rust-{self.new_version}.ebuild'))

  @mock.patch.object(os, 'listdir')
  def test_find_oldest_rust_version_in_chroot_pass(self, mock_ls):
    oldest_version_name = f'rust-{self.old_version}.ebuild'
    mock_ls.return_value = [
        oldest_version_name, f'rust-{self.current_version}.ebuild',
        f'rust-{self.new_version}.ebuild'
    ]
    actual = rust_uprev.find_oldest_rust_version_in_chroot()
    expected = (self.old_version,
                os.path.join(rust_uprev.RUST_PATH, oldest_version_name))
    self.assertEqual(expected, actual)

  @mock.patch.object(os, 'listdir')
  def test_find_oldest_rust_version_in_chroot_fail_with_only_one_ebuild(
      self, mock_ls):
    mock_ls.return_value = [f'rust-{self.new_version}.ebuild']
    with self.assertRaises(RuntimeError) as context:
      rust_uprev.find_oldest_rust_version_in_chroot()
    self.assertEqual('Expect to find more than one Rust versions',
                     str(context.exception))

  @mock.patch.object(rust_uprev, 'get_command_output')
  @mock.patch.object(git, 'CreateBranch')
  def test_create_new_repo(self, mock_branch, mock_output):
    mock_output.return_value = ''
    rust_uprev.create_new_repo(self.new_version)
    mock_branch.assert_called_once_with(rust_uprev.RUST_PATH,
                                        f'rust-to-{self.new_version}')

  @mock.patch.object(rust_uprev, 'get_command_output')
  @mock.patch.object(subprocess, 'check_call')
  def test_build_cross_compiler(self, mock_call, mock_output):
    mock_output.return_value = f'rust-{self.new_version}.ebuild'
    cros_targets = [
        'x86_64-cros-linux-gnu',
        'armv7a-cros-linux-gnueabihf',
        'aarch64-cros-linux-gnu',
    ]
    all_triples = ['x86_64-pc-linux-gnu'] + cros_targets
    rust_ebuild = 'RUSTC_TARGET_TRIPLES=(' + '\n\t'.join(all_triples) + ')'
    mock_open = mock.mock_open(read_data=rust_ebuild)
    with mock.patch('builtins.open', mock_open):
      rust_uprev.build_cross_compiler()

    mock_call.assert_called_once_with(
        ['sudo', 'emerge', '-j', '-G'] +
        [f'cross-{x}/gcc' for x in cros_targets + ['arm-none-eabi']])


if __name__ == '__main__':
  unittest.main()
