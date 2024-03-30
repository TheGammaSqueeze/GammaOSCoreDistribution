#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tool to automatically generate a new Rust uprev CL.

This tool is intended to automatically generate a CL to uprev Rust to a
newer version in Chrome OS, including creating a new Rust version or
removing an old version. It's based on
src/third_party/chromiumos-overlay/dev-lang/rust/UPGRADE.md. When using
the tool, the progress can be saved to a JSON file, so the user can resume
the process after a failing step is fixed. Example usage to create a new
version:

1. (inside chroot) $ ./rust_tools/rust_uprev.py
                     --state_file /tmp/state-file.json
                     create --rust_version 1.45.0
2. Step "compile rust" failed due to the patches can't apply to new version
3. Manually fix the patches
4. Execute the command in step 1 again.
5. Iterate 1-4 for each failed step until the tool passes.

Replace `create --rust_version 1.45.0` with `remove --rust_version 1.43.0`
if you want to remove all 1.43.0 related stuff in the same CL. Remember to
use a different state file if you choose to run different subcommands.

If you want a hammer that can do everything for you, use the subcommand
`roll`. It can create a Rust uprev CL with `create` and `remove` and upload
the CL to chromium code review.

See `--help` for all available options.
"""

import argparse
import pathlib
import json
import logging
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any, Callable, Dict, List, NamedTuple, Optional, T, Tuple

from llvm_tools import chroot, git

EQUERY = 'equery'
GSUTIL = 'gsutil.py'
MIRROR_PATH = 'gs://chromeos-localmirror/distfiles'
RUST_PATH = Path(
    '/mnt/host/source/src/third_party/chromiumos-overlay/dev-lang/rust')


def get_command_output(command: List[str], *args, **kwargs) -> str:
  return subprocess.check_output(command, encoding='utf-8', *args,
                                 **kwargs).strip()


def get_command_output_unchecked(command: List[str], *args, **kwargs) -> str:
  return subprocess.run(command,
                        check=False,
                        stdout=subprocess.PIPE,
                        encoding='utf-8',
                        *args,
                        **kwargs).stdout.strip()


class RustVersion(NamedTuple):
  """NamedTuple represents a Rust version"""
  major: int
  minor: int
  patch: int

  def __str__(self):
    return f'{self.major}.{self.minor}.{self.patch}'

  @staticmethod
  def parse_from_ebuild(ebuild_name: str) -> 'RustVersion':
    input_re = re.compile(r'^rust-'
                          r'(?P<major>\d+)\.'
                          r'(?P<minor>\d+)\.'
                          r'(?P<patch>\d+)'
                          r'(:?-r\d+)?'
                          r'\.ebuild$')
    m = input_re.match(ebuild_name)
    assert m, f'failed to parse {ebuild_name!r}'
    return RustVersion(int(m.group('major')), int(m.group('minor')),
                       int(m.group('patch')))

  @staticmethod
  def parse(x: str) -> 'RustVersion':
    input_re = re.compile(r'^(?:rust-)?'
                          r'(?P<major>\d+)\.'
                          r'(?P<minor>\d+)\.'
                          r'(?P<patch>\d+)'
                          r'(?:.ebuild)?$')
    m = input_re.match(x)
    assert m, f'failed to parse {x!r}'
    return RustVersion(int(m.group('major')), int(m.group('minor')),
                       int(m.group('patch')))


def compute_rustc_src_name(version: RustVersion) -> str:
  return f'rustc-{version}-src.tar.gz'


def compute_rust_bootstrap_prebuilt_name(version: RustVersion) -> str:
  return f'rust-bootstrap-{version}.tbz2'


def find_ebuild_for_package(name: str) -> os.PathLike:
  """Returns the path to the ebuild for the named package."""
  return get_command_output([EQUERY, 'w', name])


def find_ebuild_path(directory: Path,
                     name: str,
                     version: Optional[RustVersion] = None) -> Path:
  """Finds an ebuild in a directory.

  Returns the path to the ebuild file. Asserts if there is not
  exactly one match. The match is constrained by name and optionally
  by version, but can match any patch level. E.g. "rust" version
  1.3.4 can match rust-1.3.4.ebuild but also rust-1.3.4-r6.ebuild.
  """
  if version:
    pattern = f'{name}-{version}*.ebuild'
  else:
    pattern = f'{name}-*.ebuild'
  matches = list(Path(directory).glob(pattern))
  assert len(matches) == 1, matches
  return matches[0]


def get_rust_bootstrap_version():
  """Get the version of the current rust-bootstrap package."""
  bootstrap_ebuild = find_ebuild_path(rust_bootstrap_path(), 'rust-bootstrap')
  m = re.match(r'^rust-bootstrap-(\d+).(\d+).(\d+)', bootstrap_ebuild.name)
  assert m, bootstrap_ebuild.name
  return RustVersion(int(m.group(1)), int(m.group(2)), int(m.group(3)))


def parse_commandline_args() -> argparse.Namespace:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--state_file',
      required=True,
      help='A state file to hold previous completed steps. If the file '
      'exists, it needs to be used together with --continue or --restart. '
      'If not exist (do not use --continue in this case), we will create a '
      'file for you.',
  )
  parser.add_argument(
      '--restart',
      action='store_true',
      help='Restart from the first step. Ignore the completed steps in '
      'the state file',
  )
  parser.add_argument(
      '--continue',
      dest='cont',
      action='store_true',
      help='Continue the steps from the state file',
  )

  create_parser_template = argparse.ArgumentParser(add_help=False)
  create_parser_template.add_argument(
      '--template',
      type=RustVersion.parse,
      default=None,
      help='A template to use for creating a Rust uprev from, in the form '
      'a.b.c The ebuild has to exist in the chroot. If not specified, the '
      'tool will use the current Rust version in the chroot as template.',
  )
  create_parser_template.add_argument(
      '--skip_compile',
      action='store_true',
      help='Skip compiling rust to test the tool. Only for testing',
  )

  subparsers = parser.add_subparsers(dest='subparser_name')
  subparser_names = []
  subparser_names.append('create')
  create_parser = subparsers.add_parser(
      'create',
      parents=[create_parser_template],
      help='Create changes uprevs Rust to a new version',
  )
  create_parser.add_argument(
      '--rust_version',
      type=RustVersion.parse,
      required=True,
      help='Rust version to uprev to, in the form a.b.c',
  )

  subparser_names.append('remove')
  remove_parser = subparsers.add_parser(
      'remove',
      help='Clean up old Rust version from chroot',
  )
  remove_parser.add_argument(
      '--rust_version',
      type=RustVersion.parse,
      default=None,
      help='Rust version to remove, in the form a.b.c If not '
      'specified, the tool will remove the oldest version in the chroot',
  )

  subparser_names.append('remove-bootstrap')
  remove_bootstrap_parser = subparsers.add_parser(
      'remove-bootstrap',
      help='Remove an old rust-bootstrap version',
  )
  remove_bootstrap_parser.add_argument(
      '--version',
      type=RustVersion.parse,
      required=True,
      help='rust-bootstrap version to remove',
  )

  subparser_names.append('roll')
  roll_parser = subparsers.add_parser(
      'roll',
      parents=[create_parser_template],
      help='A command can create and upload a Rust uprev CL, including '
      'preparing the repo, creating new Rust uprev, deleting old uprev, '
      'and upload a CL to crrev.',
  )
  roll_parser.add_argument(
      '--uprev',
      type=RustVersion.parse,
      required=True,
      help='Rust version to uprev to, in the form a.b.c',
  )
  roll_parser.add_argument(
      '--remove',
      type=RustVersion.parse,
      default=None,
      help='Rust version to remove, in the form a.b.c If not '
      'specified, the tool will remove the oldest version in the chroot',
  )
  roll_parser.add_argument(
      '--skip_cross_compiler',
      action='store_true',
      help='Skip updating cross-compiler in the chroot',
  )
  roll_parser.add_argument(
      '--no_upload',
      action='store_true',
      help='If specified, the tool will not upload the CL for review',
  )

  args = parser.parse_args()
  if args.subparser_name not in subparser_names:
    parser.error('one of %s must be specified' % subparser_names)

  if args.cont and args.restart:
    parser.error('Please select either --continue or --restart')

  if os.path.exists(args.state_file):
    if not args.cont and not args.restart:
      parser.error('State file exists, so you should either --continue '
                   'or --restart')
  if args.cont and not os.path.exists(args.state_file):
    parser.error('Indicate --continue but the state file does not exist')

  if args.restart and os.path.exists(args.state_file):
    os.remove(args.state_file)

  return args


def prepare_uprev(rust_version: RustVersion, template: Optional[RustVersion]
                  ) -> Optional[Tuple[RustVersion, str, RustVersion]]:
  if template is None:
    ebuild_path = find_ebuild_for_package('rust')
    ebuild_name = os.path.basename(ebuild_path)
    template_version = RustVersion.parse_from_ebuild(ebuild_name)
  else:
    ebuild_path = find_ebuild_for_rust_version(template)
    template_version = template

  bootstrap_version = get_rust_bootstrap_version()

  if rust_version <= template_version:
    logging.info(
        'Requested version %s is not newer than the template version %s.',
        rust_version, template_version)
    return None

  logging.info('Template Rust version is %s (ebuild: %r)', template_version,
               ebuild_path)
  logging.info('rust-bootstrap version is %s', bootstrap_version)

  return template_version, ebuild_path, bootstrap_version


def copy_patches(directory: Path, template_version: RustVersion,
                 new_version: RustVersion) -> None:
  patch_path = directory.joinpath('files')
  prefix = '%s-%s-' % (directory.name, template_version)
  new_prefix = '%s-%s-' % (directory.name, new_version)
  for f in os.listdir(patch_path):
    if not f.startswith(prefix):
      continue
    logging.info('Copy patch %s to new version', f)
    new_name = f.replace(str(template_version), str(new_version))
    shutil.copyfile(
        os.path.join(patch_path, f),
        os.path.join(patch_path, new_name),
    )

  subprocess.check_call(['git', 'add', f'{new_prefix}*.patch'], cwd=patch_path)


def create_ebuild(template_ebuild: str, new_version: RustVersion) -> str:
  shutil.copyfile(template_ebuild,
                  RUST_PATH.joinpath(f'rust-{new_version}.ebuild'))
  subprocess.check_call(['git', 'add', f'rust-{new_version}.ebuild'],
                        cwd=RUST_PATH)
  return os.path.join(RUST_PATH, f'rust-{new_version}.ebuild')


def update_bootstrap_ebuild(new_bootstrap_version: RustVersion) -> None:
  old_ebuild = find_ebuild_path(rust_bootstrap_path(), 'rust-bootstrap')
  m = re.match(r'^rust-bootstrap-(\d+).(\d+).(\d+)', old_ebuild.name)
  assert m, old_ebuild.name
  old_version = RustVersion(m.group(1), m.group(2), m.group(3))
  new_ebuild = old_ebuild.parent.joinpath(
      f'rust-bootstrap-{new_bootstrap_version}.ebuild')
  old_text = old_ebuild.read_text(encoding='utf-8')
  new_text, changes = re.subn(r'(RUSTC_RAW_FULL_BOOTSTRAP_SEQUENCE=\([^)]*)',
                              f'\\1\t{old_version}\n',
                              old_text,
                              flags=re.MULTILINE)
  assert changes == 1, 'Failed to update RUSTC_RAW_FULL_BOOTSTRAP_SEQUENCE'
  new_ebuild.write_text(new_text, encoding='utf-8')


def update_ebuild(ebuild_file: str,
                  new_bootstrap_version: RustVersion) -> None:
  contents = open(ebuild_file, encoding='utf-8').read()
  contents, subs = re.subn(r'^BOOTSTRAP_VERSION=.*$',
                           'BOOTSTRAP_VERSION="%s"' %
                           (new_bootstrap_version, ),
                           contents,
                           flags=re.MULTILINE)
  if not subs:
    raise RuntimeError('BOOTSTRAP_VERSION not found in rust ebuild')
  open(ebuild_file, 'w', encoding='utf-8').write(contents)
  logging.info('Rust ebuild file has BOOTSTRAP_VERSION updated to %s',
               new_bootstrap_version)


def flip_mirror_in_ebuild(ebuild_file: Path, add: bool) -> None:
  restrict_re = re.compile(
      r'(?P<before>RESTRICT=")(?P<values>"[^"]*"|.*)(?P<after>")')
  with open(ebuild_file, encoding='utf-8') as f:
    contents = f.read()
  m = restrict_re.search(contents)
  assert m, 'failed to find RESTRICT variable in Rust ebuild'
  values = m.group('values')
  if add:
    if 'mirror' in values:
      return
    values += ' mirror'
  else:
    if 'mirror' not in values:
      return
    values = values.replace(' mirror', '')
  new_contents = restrict_re.sub(r'\g<before>%s\g<after>' % values, contents)
  with open(ebuild_file, 'w', encoding='utf-8') as f:
    f.write(new_contents)


def ebuild_actions(package: str, actions: List[str],
                   sudo: bool = False) -> None:
  ebuild_path_inchroot = find_ebuild_for_package(package)
  cmd = ['ebuild', ebuild_path_inchroot] + actions
  if sudo:
    cmd = ['sudo'] + cmd
  subprocess.check_call(cmd)


def fetch_distfile_from_mirror(name: str) -> None:
  """Gets the named file from the local mirror.

  This ensures that the file exists on the mirror, and
  that we can read it. We overwrite any existing distfile
  to ensure the checksums that update_manifest() records
  match the file as it exists on the mirror.

  This function also attempts to verify the ACL for
  the file (which is expected to have READER permission
  for allUsers). We can only see the ACL if the user
  gsutil runs with is the owner of the file. If not,
  we get an access denied error. We also count this
  as a success, because it means we were able to fetch
  the file even though we don't own it.
  """
  mirror_file = MIRROR_PATH + '/' + name
  local_file = Path(get_distdir(), name)
  cmd = [GSUTIL, 'cp', mirror_file, local_file]
  logging.info('Running %r', cmd)
  rc = subprocess.call(cmd)
  if rc != 0:
    logging.error(
        """Could not fetch %s

If the file does not yet exist at %s
please download the file, verify its integrity
with something like:

curl -O https://static.rust-lang.org/dist/%s
gpg --verify %s.asc

You may need to import the signing key first, e.g.:

gpg --recv-keys 85AB96E6FA1BE5FE

Once you have verify the integrity of the file, upload
it to the local mirror using gsutil cp.
""", mirror_file, MIRROR_PATH, name, name)
    raise Exception(f'Could not fetch {mirror_file}')
  # Check that the ACL allows allUsers READER access.
  # If we get an AccessDeniedAcception here, that also
  # counts as a success, because we were able to fetch
  # the file as a non-owner.
  cmd = [GSUTIL, 'acl', 'get', mirror_file]
  logging.info('Running %r', cmd)
  output = get_command_output_unchecked(cmd, stderr=subprocess.STDOUT)
  acl_verified = False
  if 'AccessDeniedException:' in output:
    acl_verified = True
  else:
    acl = json.loads(output)
    for x in acl:
      if x['entity'] == 'allUsers' and x['role'] == 'READER':
        acl_verified = True
        break
  if not acl_verified:
    logging.error('Output from acl get:\n%s', output)
    raise Exception('Could not verify that allUsers has READER permission')


def fetch_bootstrap_distfiles(old_version: RustVersion,
                              new_version: RustVersion) -> None:
  """Fetches rust-bootstrap distfiles from the local mirror

  Fetches the distfiles for a rust-bootstrap ebuild to ensure they
  are available on the mirror and the local copies are the same as
  the ones on the mirror.
  """
  fetch_distfile_from_mirror(compute_rust_bootstrap_prebuilt_name(old_version))
  fetch_distfile_from_mirror(compute_rustc_src_name(new_version))


def fetch_rust_distfiles(version: RustVersion) -> None:
  """Fetches rust distfiles from the local mirror

  Fetches the distfiles for a rust ebuild to ensure they
  are available on the mirror and the local copies are
  the same as the ones on the mirror.
  """
  fetch_distfile_from_mirror(compute_rustc_src_name(version))


def get_distdir() -> os.PathLike:
  """Returns portage's distdir."""
  return get_command_output(['portageq', 'distdir'])


def update_manifest(ebuild_file: os.PathLike) -> None:
  """Updates the MANIFEST for the ebuild at the given path."""
  ebuild = Path(ebuild_file)
  logging.info('Added "mirror" to RESTRICT to %s', ebuild.name)
  flip_mirror_in_ebuild(ebuild, add=True)
  ebuild_actions(ebuild.parent.name, ['manifest'])
  logging.info('Removed "mirror" to RESTRICT from %s', ebuild.name)
  flip_mirror_in_ebuild(ebuild, add=False)


def update_rust_packages(rust_version: RustVersion, add: bool) -> None:
  package_file = RUST_PATH.joinpath(
      '../../profiles/targets/chromeos/package.provided')
  with open(package_file, encoding='utf-8') as f:
    contents = f.read()
  if add:
    rust_packages_re = re.compile(r'dev-lang/rust-(\d+\.\d+\.\d+)')
    rust_packages = rust_packages_re.findall(contents)
    # Assume all the rust packages are in alphabetical order, so insert the new
    # version to the place after the last rust_packages
    new_str = f'dev-lang/rust-{rust_version}'
    new_contents = contents.replace(rust_packages[-1],
                                    f'{rust_packages[-1]}\n{new_str}')
    logging.info('%s has been inserted into package.provided', new_str)
  else:
    old_str = f'dev-lang/rust-{rust_version}\n'
    assert old_str in contents, f'{old_str!r} not found in package.provided'
    new_contents = contents.replace(old_str, '')
    logging.info('%s has been removed from package.provided', old_str)

  with open(package_file, 'w', encoding='utf-8') as f:
    f.write(new_contents)


def update_virtual_rust(template_version: RustVersion,
                        new_version: RustVersion) -> None:
  template_ebuild = find_ebuild_path(RUST_PATH.joinpath('../../virtual/rust'),
                                     'rust', template_version)
  virtual_rust_dir = template_ebuild.parent
  new_name = f'rust-{new_version}.ebuild'
  new_ebuild = virtual_rust_dir.joinpath(new_name)
  shutil.copyfile(template_ebuild, new_ebuild)
  subprocess.check_call(['git', 'add', new_name], cwd=virtual_rust_dir)


def perform_step(state_file: pathlib.Path,
                 tmp_state_file: pathlib.Path,
                 completed_steps: Dict[str, Any],
                 step_name: str,
                 step_fn: Callable[[], T],
                 result_from_json: Optional[Callable[[Any], T]] = None,
                 result_to_json: Optional[Callable[[T], Any]] = None) -> T:
  if step_name in completed_steps:
    logging.info('Skipping previously completed step %s', step_name)
    if result_from_json:
      return result_from_json(completed_steps[step_name])
    return completed_steps[step_name]

  logging.info('Running step %s', step_name)
  val = step_fn()
  logging.info('Step %s complete', step_name)
  if result_to_json:
    completed_steps[step_name] = result_to_json(val)
  else:
    completed_steps[step_name] = val

  with tmp_state_file.open('w', encoding='utf-8') as f:
    json.dump(completed_steps, f, indent=4)
  tmp_state_file.rename(state_file)
  return val


def prepare_uprev_from_json(
    obj: Any) -> Optional[Tuple[RustVersion, str, RustVersion]]:
  if not obj:
    return None
  version, ebuild_path, bootstrap_version = obj
  return RustVersion(*version), ebuild_path, RustVersion(*bootstrap_version)


def create_rust_uprev(rust_version: RustVersion,
                      maybe_template_version: Optional[RustVersion],
                      skip_compile: bool, run_step: Callable[[], T]) -> None:
  template_version, template_ebuild, old_bootstrap_version = run_step(
      'prepare uprev',
      lambda: prepare_uprev(rust_version, maybe_template_version),
      result_from_json=prepare_uprev_from_json,
  )
  if template_ebuild is None:
    return

  # The fetch steps will fail (on purpose) if the files they check for
  # are not available on the mirror. To make them pass, fetch the
  # required files yourself, verify their checksums, then upload them
  # to the mirror.
  run_step(
      'fetch bootstrap distfiles', lambda: fetch_bootstrap_distfiles(
          old_bootstrap_version, template_version))
  run_step('fetch rust distfiles', lambda: fetch_rust_distfiles(rust_version))
  run_step('update bootstrap ebuild', lambda: update_bootstrap_ebuild(
      template_version))
  run_step(
      'update bootstrap manifest', lambda: update_manifest(rust_bootstrap_path(
      ).joinpath(f'rust-bootstrap-{template_version}.ebuild')))
  run_step('copy patches', lambda: copy_patches(RUST_PATH, template_version,
                                                rust_version))
  ebuild_file = run_step(
      'create ebuild', lambda: create_ebuild(template_ebuild, rust_version))
  run_step(
      'update ebuild', lambda: update_ebuild(ebuild_file, template_version))
  run_step('update manifest to add new version', lambda: update_manifest(
      Path(ebuild_file)))
  if not skip_compile:
    run_step(
        'emerge rust', lambda: subprocess.check_call(
            ['sudo', 'emerge', 'dev-lang/rust']))
  run_step('insert version into rust packages', lambda: update_rust_packages(
      rust_version, add=True))
  run_step('upgrade virtual/rust', lambda: update_virtual_rust(
      template_version, rust_version))


def find_rust_versions_in_chroot() -> List[Tuple[RustVersion, str]]:
  return [(RustVersion.parse_from_ebuild(x), os.path.join(RUST_PATH, x))
          for x in os.listdir(RUST_PATH) if x.endswith('.ebuild')]


def find_oldest_rust_version_in_chroot() -> Tuple[RustVersion, str]:
  rust_versions = find_rust_versions_in_chroot()
  if len(rust_versions) <= 1:
    raise RuntimeError('Expect to find more than one Rust versions')
  return min(rust_versions)


def find_ebuild_for_rust_version(version: RustVersion) -> str:
  rust_ebuilds = [
      ebuild for x, ebuild in find_rust_versions_in_chroot() if x == version
  ]
  if not rust_ebuilds:
    raise ValueError(f'No Rust ebuilds found matching {version}')
  if len(rust_ebuilds) > 1:
    raise ValueError(f'Multiple Rust ebuilds found matching {version}: '
                     f'{rust_ebuilds}')
  return rust_ebuilds[0]


def remove_files(filename: str, path: str) -> None:
  subprocess.check_call(['git', 'rm', filename], cwd=path)


def remove_rust_bootstrap_version(version: RustVersion,
                                  run_step: Callable[[], T]) -> None:
  prefix = f'rust-bootstrap-{version}'
  run_step('remove old bootstrap ebuild', lambda: remove_files(
      f'{prefix}*.ebuild', rust_bootstrap_path()))
  ebuild_file = find_ebuild_for_package('rust-bootstrap')
  run_step('update bootstrap manifest to delete old version', lambda:
           update_manifest(ebuild_file))


def remove_rust_uprev(rust_version: Optional[RustVersion],
                      run_step: Callable[[], T]) -> None:
  def find_desired_rust_version():
    if rust_version:
      return rust_version, find_ebuild_for_rust_version(rust_version)
    return find_oldest_rust_version_in_chroot()

  def find_desired_rust_version_from_json(obj: Any) -> Tuple[RustVersion, str]:
    version, ebuild_path = obj
    return RustVersion(*version), ebuild_path

  delete_version, delete_ebuild = run_step(
      'find rust version to delete',
      find_desired_rust_version,
      result_from_json=find_desired_rust_version_from_json,
  )
  run_step(
      'remove patches', lambda: remove_files(
          f'files/rust-{delete_version}-*.patch', RUST_PATH))
  run_step('remove ebuild', lambda: remove_files(delete_ebuild, RUST_PATH))
  ebuild_file = find_ebuild_for_package('rust')
  run_step('update manifest to delete old version', lambda: update_manifest(
      ebuild_file))
  run_step('remove version from rust packages', lambda: update_rust_packages(
      delete_version, add=False))
  run_step('remove virtual/rust', lambda: remove_virtual_rust(delete_version))


def remove_virtual_rust(delete_version: RustVersion) -> None:
  ebuild = find_ebuild_path(RUST_PATH.joinpath('../../virtual/rust'), 'rust',
                            delete_version)
  subprocess.check_call(['git', 'rm', str(ebuild.name)], cwd=ebuild.parent)


def rust_bootstrap_path() -> Path:
  return RUST_PATH.joinpath('../rust-bootstrap')


def create_new_repo(rust_version: RustVersion) -> None:
  output = get_command_output(['git', 'status', '--porcelain'], cwd=RUST_PATH)
  if output:
    raise RuntimeError(
        f'{RUST_PATH} has uncommitted changes, please either discard them '
        'or commit them.')
  git.CreateBranch(RUST_PATH, f'rust-to-{rust_version}')


def build_cross_compiler() -> None:
  # Get target triples in ebuild
  rust_ebuild = find_ebuild_for_package('rust')
  with open(rust_ebuild, encoding='utf-8') as f:
    contents = f.read()

  target_triples_re = re.compile(r'RUSTC_TARGET_TRIPLES=\(([^)]+)\)')
  m = target_triples_re.search(contents)
  assert m, 'RUST_TARGET_TRIPLES not found in rust ebuild'
  target_triples = m.group(1).strip().split('\n')

  compiler_targets_to_install = [
      target.strip() for target in target_triples if 'cros-' in target
  ]
  for target in target_triples:
    if 'cros-' not in target:
      continue
    target = target.strip()

  # We also always need arm-none-eabi, though it's not mentioned in
  # RUSTC_TARGET_TRIPLES.
  compiler_targets_to_install.append('arm-none-eabi')

  logging.info('Emerging cross compilers %s', compiler_targets_to_install)
  subprocess.check_call(
      ['sudo', 'emerge', '-j', '-G'] +
      [f'cross-{target}/gcc' for target in compiler_targets_to_install])


def create_new_commit(rust_version: RustVersion) -> None:
  subprocess.check_call(['git', 'add', '-A'], cwd=RUST_PATH)
  messages = [
      f'[DO NOT SUBMIT] dev-lang/rust: upgrade to Rust {rust_version}',
      '',
      'This CL is created by rust_uprev tool automatically.'
      '',
      'BUG=None',
      'TEST=Use CQ to test the new Rust version',
  ]
  git.UploadChanges(RUST_PATH, f'rust-to-{rust_version}', messages)


def main() -> None:
  if not chroot.InChroot():
    raise RuntimeError('This script must be executed inside chroot')

  logging.basicConfig(level=logging.INFO)

  args = parse_commandline_args()

  state_file = pathlib.Path(args.state_file)
  tmp_state_file = state_file.with_suffix('.tmp')

  try:
    with state_file.open(encoding='utf-8') as f:
      completed_steps = json.load(f)
  except FileNotFoundError:
    completed_steps = {}

  def run_step(
      step_name: str,
      step_fn: Callable[[], T],
      result_from_json: Optional[Callable[[Any], T]] = None,
      result_to_json: Optional[Callable[[T], Any]] = None,
  ) -> T:
    return perform_step(state_file, tmp_state_file, completed_steps, step_name,
                        step_fn, result_from_json, result_to_json)

  if args.subparser_name == 'create':
    create_rust_uprev(args.rust_version, args.template, args.skip_compile,
                      run_step)
  elif args.subparser_name == 'remove':
    remove_rust_uprev(args.rust_version, run_step)
  elif args.subparser_name == 'remove-bootstrap':
    remove_rust_bootstrap_version(args.version, run_step)
  else:
    # If you have added more subparser_name, please also add the handlers above
    assert args.subparser_name == 'roll'
    run_step('create new repo', lambda: create_new_repo(args.uprev))
    if not args.skip_cross_compiler:
      run_step('build cross compiler', build_cross_compiler)
    create_rust_uprev(args.uprev, args.template, args.skip_compile, run_step)
    remove_rust_uprev(args.remove, run_step)
    bootstrap_version = prepare_uprev_from_json(
        completed_steps['prepare uprev'])[2]
    remove_rust_bootstrap_version(bootstrap_version, run_step)
    if not args.no_upload:
      run_step('create rust uprev CL', lambda: create_new_commit(args.uprev))


if __name__ == '__main__':
  sys.exit(main())
