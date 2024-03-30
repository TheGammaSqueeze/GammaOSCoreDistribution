#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Get an upstream patch to LLVM's PATCHES.json."""

import argparse
import json
import logging
import os
import shlex
import subprocess
import sys
import typing as t
from datetime import datetime

import dataclasses

import chroot
import get_llvm_hash
import git
import git_llvm_rev
import update_chromeos_llvm_hash

__DOC_EPILOGUE = """
Example Usage:
  get_upstream_patch --chroot_path ~/chromiumos --platform chromiumos \
--sha 1234567 --sha 890abdc
"""


class CherrypickError(ValueError):
  """A ValueError that highlights the cherry-pick has been seen before"""


def add_patch(patches_json_path: str, patches_dir: str,
              relative_patches_dir: str, start_version: git_llvm_rev.Rev,
              llvm_dir: str, rev: t.Union[git_llvm_rev.Rev, str], sha: str,
              package: str, platforms: t.List[str]):
  """Gets the start and end intervals in 'json_file'.

  Args:
    patches_json_path: The absolute path to PATCHES.json.
    patches_dir: The aboslute path to the directory patches are in.
    relative_patches_dir: The relative path to PATCHES.json.
    start_version: The base LLVM revision this patch applies to.
    llvm_dir: The path to LLVM checkout.
    rev: An LLVM revision (git_llvm_rev.Rev) for a cherrypicking, or a
    differential revision (str) otherwise.
    sha: The LLVM git sha that corresponds to the patch. For differential
    revisions, the git sha from  the local commit created by 'arc patch'
    is used.
    package: The LLVM project name this patch applies to.
    platforms: List of platforms this patch applies to.

  Raises:
    CherrypickError: A ValueError that highlights the cherry-pick has been
    seen before.
  """

  with open(patches_json_path, encoding='utf-8') as f:
    patches_json = json.load(f)

  is_cherrypick = isinstance(rev, git_llvm_rev.Rev)
  if is_cherrypick:
    file_name = f'{sha}.patch'
  else:
    file_name = f'{rev}.patch'
  rel_patch_path = os.path.join(relative_patches_dir, file_name)

  for p in patches_json:
    rel_path = p['rel_patch_path']
    if rel_path == rel_patch_path:
      raise CherrypickError(
          f'Patch at {rel_path} already exists in PATCHES.json')
    if is_cherrypick:
      if sha in rel_path:
        logging.warning(
            'Similarly-named patch already exists in PATCHES.json: %r',
            rel_path)

  with open(os.path.join(patches_dir, file_name), 'wb') as f:
    cmd = ['git', 'show', sha]
    # Only apply the part of the patch that belongs to this package, expect
    # LLVM. This is because some packages are built with LLVM ebuild on X86 but
    # not on the other architectures. e.g. compiler-rt. Therefore always apply
    # the entire patch to LLVM ebuild as a workaround.
    if package != 'llvm':
      cmd.append(package_to_project(package))
    subprocess.check_call(cmd, stdout=f, cwd=llvm_dir)

  commit_subject = subprocess.check_output(
      ['git', 'log', '-n1', '--format=%s', sha],
      cwd=llvm_dir,
      encoding='utf-8')

  end_vers = rev.number if isinstance(rev, git_llvm_rev.Rev) else None
  patch_props = {
      'rel_patch_path': rel_patch_path,
      'metadata': {
          'title': commit_subject.strip(),
          'info': [],
      },
      'platforms': sorted(platforms),
      'version_range': {
          'from': start_version.number,
          'until': end_vers,
      },
  }
  patches_json.append(patch_props)

  temp_file = patches_json_path + '.tmp'
  with open(temp_file, 'w', encoding='utf-8') as f:
    json.dump(patches_json,
              f,
              indent=4,
              separators=(',', ': '),
              sort_keys=True)
    f.write('\n')
  os.rename(temp_file, patches_json_path)


def parse_ebuild_for_assignment(ebuild_path: str, var_name: str) -> str:
  # '_pre' filters the LLVM 9.0 ebuild, which we never want to target, from
  # this list.
  candidates = [
      x for x in os.listdir(ebuild_path)
      if x.endswith('.ebuild') and '_pre' in x
  ]

  if not candidates:
    raise ValueError('No ebuilds found under %r' % ebuild_path)

  ebuild = os.path.join(ebuild_path, max(candidates))
  with open(ebuild, encoding='utf-8') as f:
    var_name_eq = var_name + '='
    for orig_line in f:
      if not orig_line.startswith(var_name_eq):
        continue

      # We shouldn't see much variety here, so do the simplest thing possible.
      line = orig_line[len(var_name_eq):]
      # Remove comments
      line = line.split('#')[0]
      # Remove quotes
      line = shlex.split(line)
      if len(line) != 1:
        raise ValueError('Expected exactly one quoted value in %r' % orig_line)
      return line[0].strip()

  raise ValueError('No %s= line found in %r' % (var_name, ebuild))


# Resolves a git ref (or similar) to a LLVM SHA.
def resolve_llvm_ref(llvm_dir: str, sha: str) -> str:
  return subprocess.check_output(
      ['git', 'rev-parse', sha],
      encoding='utf-8',
      cwd=llvm_dir,
  ).strip()


# Get the package name of an LLVM project
def project_to_package(project: str) -> str:
  if project == 'libunwind':
    return 'llvm-libunwind'
  return project


# Get the LLVM project name of a package
def package_to_project(package: str) -> str:
  if package == 'llvm-libunwind':
    return 'libunwind'
  return package


# Get the LLVM projects change in the specifed sha
def get_package_names(sha: str, llvm_dir: str) -> list:
  paths = subprocess.check_output(
      ['git', 'show', '--name-only', '--format=', sha],
      cwd=llvm_dir,
      encoding='utf-8').splitlines()
  # Some LLVM projects are built by LLVM ebuild on X86, so always apply the
  # patch to LLVM ebuild
  packages = {'llvm'}
  # Detect if there are more packages to apply the patch to
  for path in paths:
    package = project_to_package(path.split('/')[0])
    if package in ('compiler-rt', 'libcxx', 'libcxxabi', 'llvm-libunwind'):
      packages.add(package)
  packages = list(sorted(packages))
  return packages


def create_patch_for_packages(packages: t.List[str], symlinks: t.List[str],
                              start_rev: git_llvm_rev.Rev,
                              rev: t.Union[git_llvm_rev.Rev, str], sha: str,
                              llvm_dir: str, platforms: t.List[str]):
  """Create a patch and add its metadata for each package"""
  for package, symlink in zip(packages, symlinks):
    symlink_dir = os.path.dirname(symlink)
    patches_json_path = os.path.join(symlink_dir, 'files/PATCHES.json')
    relative_patches_dir = 'cherry' if package == 'llvm' else ''
    patches_dir = os.path.join(symlink_dir, 'files', relative_patches_dir)
    logging.info('Getting %s (%s) into %s', rev, sha, package)
    add_patch(patches_json_path,
              patches_dir,
              relative_patches_dir,
              start_rev,
              llvm_dir,
              rev,
              sha,
              package,
              platforms=platforms)


def make_cl(symlinks_to_uprev: t.List[str], llvm_symlink_dir: str, branch: str,
            commit_messages: t.List[str], reviewers: t.Optional[t.List[str]],
            cc: t.Optional[t.List[str]]):
  symlinks_to_uprev = sorted(set(symlinks_to_uprev))
  for symlink in symlinks_to_uprev:
    update_chromeos_llvm_hash.UprevEbuildSymlink(symlink)
    subprocess.check_output(['git', 'add', '--all'],
                            cwd=os.path.dirname(symlink))
  git.UploadChanges(llvm_symlink_dir, branch, commit_messages, reviewers, cc)
  git.DeleteBranch(llvm_symlink_dir, branch)


def resolve_symbolic_sha(start_sha: str, llvm_symlink_dir: str) -> str:
  if start_sha == 'llvm':
    return parse_ebuild_for_assignment(llvm_symlink_dir, 'LLVM_HASH')

  if start_sha == 'llvm-next':
    return parse_ebuild_for_assignment(llvm_symlink_dir, 'LLVM_NEXT_HASH')

  return start_sha


def find_patches_and_make_cl(
    chroot_path: str, patches: t.List[str], start_rev: git_llvm_rev.Rev,
    llvm_config: git_llvm_rev.LLVMConfig, llvm_symlink_dir: str,
    create_cl: bool, skip_dependencies: bool,
    reviewers: t.Optional[t.List[str]], cc: t.Optional[t.List[str]],
    platforms: t.List[str]):

  converted_patches = [
      _convert_patch(llvm_config, skip_dependencies, p) for p in patches
  ]
  potential_duplicates = _get_duplicate_shas(converted_patches)
  if potential_duplicates:
    err_msg = '\n'.join(f'{a.patch} == {b.patch}'
                        for a, b in potential_duplicates)
    raise RuntimeError(f'Found Duplicate SHAs:\n{err_msg}')

  # CL Related variables, only used if `create_cl`
  symlinks_to_uprev = []
  commit_messages = [
      'llvm: get patches from upstream\n',
  ]
  branch = f'get-upstream-{datetime.now().strftime("%Y%m%d%H%M%S%f")}'

  if create_cl:
    git.CreateBranch(llvm_symlink_dir, branch)

  for parsed_patch in converted_patches:
    # Find out the llvm projects changed in this commit
    packages = get_package_names(parsed_patch.sha, llvm_config.dir)
    # Find out the ebuild symlinks of the corresponding ChromeOS packages
    symlinks = chroot.GetChrootEbuildPaths(chroot_path, [
        'sys-devel/llvm' if package == 'llvm' else 'sys-libs/' + package
        for package in packages
    ])
    symlinks = chroot.ConvertChrootPathsToAbsolutePaths(chroot_path, symlinks)
    # Create a local patch for all the affected llvm projects
    create_patch_for_packages(packages,
                              symlinks,
                              start_rev,
                              parsed_patch.rev,
                              parsed_patch.sha,
                              llvm_config.dir,
                              platforms=platforms)
    if create_cl:
      symlinks_to_uprev.extend(symlinks)

      commit_messages.extend([
          parsed_patch.git_msg(),
          subprocess.check_output(
              ['git', 'log', '-n1', '--oneline', parsed_patch.sha],
              cwd=llvm_config.dir,
              encoding='utf-8')
      ])

    if parsed_patch.is_differential:
      subprocess.check_output(['git', 'reset', '--hard', 'HEAD^'],
                              cwd=llvm_config.dir)

  if create_cl:
    make_cl(symlinks_to_uprev, llvm_symlink_dir, branch, commit_messages,
            reviewers, cc)


@dataclasses.dataclass(frozen=True)
class ParsedPatch:
  """Class to keep track of bundled patch info."""
  patch: str
  sha: str
  is_differential: bool
  rev: t.Union[git_llvm_rev.Rev, str]

  def git_msg(self) -> str:
    if self.is_differential:
      return f'\n\nreviews.llvm.org/{self.patch}\n'
    return f'\n\nreviews.llvm.org/rG{self.sha}\n'


def _convert_patch(llvm_config: git_llvm_rev.LLVMConfig,
                   skip_dependencies: bool, patch: str) -> ParsedPatch:
  """Extract git revision info from a patch.

  Args:
    llvm_config: LLVM configuration object.
    skip_dependencies: Pass --skip-dependecies for to `arc`
    patch: A single patch referent string.

  Returns:
    A [ParsedPatch] object.
  """

  # git hash should only have lower-case letters
  is_differential = patch.startswith('D')
  if is_differential:
    subprocess.check_output(
        [
            'arc', 'patch', '--nobranch',
            '--skip-dependencies' if skip_dependencies else '--revision', patch
        ],
        cwd=llvm_config.dir,
    )
    sha = resolve_llvm_ref(llvm_config.dir, 'HEAD')
    rev = patch
  else:
    sha = resolve_llvm_ref(llvm_config.dir, patch)
    rev = git_llvm_rev.translate_sha_to_rev(llvm_config, sha)
  return ParsedPatch(patch=patch,
                     sha=sha,
                     rev=rev,
                     is_differential=is_differential)


def _get_duplicate_shas(patches: t.List[ParsedPatch]
                        ) -> t.List[t.Tuple[ParsedPatch, ParsedPatch]]:
  """Return a list of Patches which have duplicate SHA's"""
  return [(left, right) for i, left in enumerate(patches)
          for right in patches[i + 1:] if left.sha == right.sha]


def get_from_upstream(chroot_path: str,
                      create_cl: bool,
                      start_sha: str,
                      patches: t.List[str],
                      platforms: t.List[str],
                      skip_dependencies: bool = False,
                      reviewers: t.List[str] = None,
                      cc: t.List[str] = None):
  llvm_symlink = chroot.ConvertChrootPathsToAbsolutePaths(
      chroot_path, chroot.GetChrootEbuildPaths(chroot_path,
                                               ['sys-devel/llvm']))[0]
  llvm_symlink_dir = os.path.dirname(llvm_symlink)

  git_status = subprocess.check_output(['git', 'status', '-s'],
                                       cwd=llvm_symlink_dir,
                                       encoding='utf-8')

  if git_status:
    error_path = os.path.dirname(os.path.dirname(llvm_symlink_dir))
    raise ValueError(f'Uncommited changes detected in {error_path}')

  start_sha = resolve_symbolic_sha(start_sha, llvm_symlink_dir)
  logging.info('Base llvm hash == %s', start_sha)

  llvm_config = git_llvm_rev.LLVMConfig(
      remote='origin', dir=get_llvm_hash.GetAndUpdateLLVMProjectInLLVMTools())
  start_sha = resolve_llvm_ref(llvm_config.dir, start_sha)

  find_patches_and_make_cl(chroot_path=chroot_path,
                           patches=patches,
                           platforms=platforms,
                           start_rev=git_llvm_rev.translate_sha_to_rev(
                               llvm_config, start_sha),
                           llvm_config=llvm_config,
                           llvm_symlink_dir=llvm_symlink_dir,
                           create_cl=create_cl,
                           skip_dependencies=skip_dependencies,
                           reviewers=reviewers,
                           cc=cc)
  logging.info('Complete.')


def main():
  chroot.VerifyOutsideChroot()
  logging.basicConfig(
      format='%(asctime)s: %(levelname)s: %(filename)s:%(lineno)d: %(message)s',
      level=logging.INFO,
  )

  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter,
      epilog=__DOC_EPILOGUE)
  parser.add_argument('--chroot_path',
                      default=os.path.join(os.path.expanduser('~'),
                                           'chromiumos'),
                      help='the path to the chroot (default: %(default)s)')
  parser.add_argument(
      '--start_sha',
      default='llvm-next',
      help='LLVM SHA that the patch should start applying at. You can specify '
      '"llvm" or "llvm-next", as well. Defaults to %(default)s.')
  parser.add_argument('--sha',
                      action='append',
                      default=[],
                      help='The LLVM git SHA to cherry-pick.')
  parser.add_argument(
      '--differential',
      action='append',
      default=[],
      help='The LLVM differential revision to apply. Example: D1234')
  parser.add_argument(
      '--platform',
      action='append',
      required=True,
      help='Apply this patch to the give platform. Common options include '
      '"chromiumos" and "android". Can be specified multiple times to '
      'apply to multiple platforms')
  parser.add_argument('--create_cl',
                      action='store_true',
                      help='Automatically create a CL if specified')
  parser.add_argument(
      '--skip_dependencies',
      action='store_true',
      help="Skips a LLVM differential revision's dependencies. Only valid "
      'when --differential appears exactly once.')
  args = parser.parse_args()

  if not (args.sha or args.differential):
    parser.error('--sha or --differential required')

  if args.skip_dependencies and len(args.differential) != 1:
    parser.error("--skip_dependencies is only valid when there's exactly one "
                 'supplied differential')

  get_from_upstream(
      chroot_path=args.chroot_path,
      create_cl=args.create_cl,
      start_sha=args.start_sha,
      patches=args.sha + args.differential,
      skip_dependencies=args.skip_dependencies,
      platforms=args.platform,
  )


if __name__ == '__main__':
  sys.exit(main())
