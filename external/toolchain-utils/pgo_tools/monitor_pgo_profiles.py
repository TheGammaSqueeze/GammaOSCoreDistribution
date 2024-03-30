#!/usr/bin/env python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Emails the mage if PGO profile generation hasn't succeeded recently."""

import argparse
import datetime
import logging
import subprocess
import sys
from typing import List, NamedTuple, Optional, Tuple

PGO_BUILDBOT_LINK = ('https://ci.chromium.org/p/chromeos/builders/toolchain/'
                     'pgo-generate-llvm-next-orchestrator')


class ProfdataInfo(NamedTuple):
  """Data about an llvm profdata in our gs:// bucket."""
  date: datetime.datetime
  location: str


def parse_date(date: str) -> datetime.datetime:
  time_format = '%Y-%m-%dT%H:%M:%SZ'
  if not date.endswith('Z'):
    time_format += '%z'
  return datetime.datetime.strptime(date, time_format)


def fetch_most_recent_profdata(arch: str) -> ProfdataInfo:
  result = subprocess.run(
      [
          'gsutil.py',
          'ls',
          '-l',
          f'gs://chromeos-toolchain-artifacts/llvm-pgo/{arch}/'
          '*.profdata.tar.xz',
      ],
      check=True,
      stdout=subprocess.PIPE,
      encoding='utf-8',
  )

  # Each line will be a profdata; the last one is a summary, so drop it.
  infos = []
  for rec in result.stdout.strip().splitlines()[:-1]:
    _size, date, url = rec.strip().split()
    infos.append(ProfdataInfo(date=parse_date(date), location=url))
  return max(infos)


def compose_complaint(
    out_of_date_profiles: List[Tuple[datetime.datetime, ProfdataInfo]]
) -> Optional[str]:
  if not out_of_date_profiles:
    return None

  if len(out_of_date_profiles) == 1:
    body_lines = ['1 profile is out of date:']
  else:
    body_lines = [f'{len(out_of_date_profiles)} profiles are out of date:']

  for arch, profdata_info in out_of_date_profiles:
    body_lines.append(
        f'- {arch} (most recent profile was from {profdata_info.date} at '
        f'{profdata_info.location!r})')

  body_lines.append('\n')
  body_lines.append(
      'PTAL to see if the llvm-pgo-generate bots are functioning normally. '
      f'Their status can be found at {PGO_BUILDBOT_LINK}.')
  return '\n'.join(body_lines)


def main() -> None:
  logging.basicConfig(level=logging.INFO)

  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--max_age_days',
      # These builders run ~weekly. If we fail to generate two in a row,
      # something's probably wrong.
      default=15,
      type=int,
      help='How old to let profiles get before complaining, in days',
  )
  args = parser.parse_args()

  now = datetime.datetime.now()
  logging.info('Start time is %r', now)

  max_age = datetime.timedelta(days=args.max_age_days)
  out_of_date_profiles = []
  for arch in ('arm', 'arm64', 'amd64'):
    logging.info('Fetching most recent profdata for %r', arch)
    most_recent = fetch_most_recent_profdata(arch)
    logging.info('Most recent profdata for %r is %r', arch, most_recent)

    age = now - most_recent.date
    if age >= max_age:
      out_of_date_profiles.append((arch, most_recent))

  complaint = compose_complaint(out_of_date_profiles)
  if complaint:
    logging.error('%s', complaint)
    sys.exit(1)

  logging.info('Nothing seems wrong')


if __name__ == '__main__':
  sys.exit(main())
