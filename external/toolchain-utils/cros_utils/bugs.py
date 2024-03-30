#!/usr/bin/env python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Utilities to file bugs."""

import base64
import datetime
import enum
import json
import os
from typing import Any, Dict, List, Optional

X20_PATH = '/google/data/rw/teams/c-compiler-chrome/prod_bugs'


class WellKnownComponents(enum.IntEnum):
  """A listing of "well-known" components recognized by our infra."""
  CrOSToolchainPublic = -1
  CrOSToolchainPrivate = -2


def _WriteBugJSONFile(object_type: str, json_object: Dict[str, Any]):
  """Writes a JSON file to X20_PATH with the given bug-ish object."""
  final_object = {
      'type': object_type,
      'value': json_object,
  }

  # The name of this has two parts:
  # - An easily sortable time, to provide uniqueness and let our service send
  #   things in the order they were put into the outbox.
  # - 64 bits of entropy, so two racing bug writes don't clobber the same file.
  now = datetime.datetime.utcnow().isoformat('T', 'seconds') + 'Z'
  entropy = base64.urlsafe_b64encode(os.getrandom(8))
  entropy_str = entropy.rstrip(b'=').decode('utf-8')
  file_path = os.path.join(X20_PATH, f'{now}_{entropy_str}.json')

  temp_path = file_path + '.in_progress'
  try:
    with open(temp_path, 'w') as f:
      json.dump(final_object, f)
    os.rename(temp_path, file_path)
  except:
    os.remove(temp_path)
    raise
  return file_path


def AppendToExistingBug(bug_id: int, body: str):
  """Sends a reply to an existing bug."""
  _WriteBugJSONFile('AppendToExistingBugRequest', {
      'body': body,
      'bug_id': bug_id,
  })


def CreateNewBug(component_id: int,
                 title: str,
                 body: str,
                 assignee: Optional[str] = None,
                 cc: Optional[List[str]] = None):
  """Sends a request to create a new bug.

  Args:
    component_id: The component ID to add. Anything from WellKnownComponents
      also works.
    title: Title of the bug. Must be nonempty.
    body: Body of the bug. Must be nonempty.
    assignee: Assignee of the bug. Must be either an email address, or a
      "well-known" assignee (detective, mage).
    cc: A list of emails to add to the CC list. Must either be an email
      address, or a "well-known" individual (detective, mage).
  """
  obj = {
      'component_id': component_id,
      'subject': title,
      'body': body,
  }

  if assignee:
    obj['assignee'] = assignee

  if cc:
    obj['cc'] = cc

  _WriteBugJSONFile('FileNewBugRequest', obj)


def SendCronjobLog(cronjob_name: str, failed: bool, message: str):
  """Sends the record of a cronjob to our bug infra.

  cronjob_name: The name of the cronjob. Expected to remain consistent over
    time.
  failed: Whether the job failed or not.
  message: Any seemingly relevant context. This is pasted verbatim in a bug, if
    the cronjob infra deems it worthy.
  """
  _WriteBugJSONFile('ChrotomationCronjobUpdate', {
      'name': cronjob_name,
      'message': message,
      'failed': failed,
  })
