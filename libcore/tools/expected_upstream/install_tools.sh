#!/bin/bash

# prerequisite to run the script
pip3 install GitPython

git fetch aosp upstream-openjdk7u
git fetch aosp upstream-openjdk8u
git fetch aosp upstream-openjdk9
git fetch aosp upstream-openjdk11u
git fetch aosp upstream-openjdk17u

THIS_DIR=$(realpath $(dirname ${BASH_SOURCE[0]:-$0}))
alias ojluni_refresh_files=${THIS_DIR}/ojluni_refresh_files.py
alias ojluni_modify_expectation=${THIS_DIR}/ojluni_modify_expectation.py
alias ojluni_run_tool_tests='PYTHONPATH=${PYTHONPATH}:${THIS_DIR} python3 -B -m unittest discover -v -s tests -p "*_test.py"'
alias ojluni_upgrade_identicals=${THIS_DIR}/ojluni_upgrade_identicals.py
alias ojluni_versions_report=${THIS_DIR}/ojluni_versions_report.py


_ojluni_modify_expectation ()
{
  COMPREPLY=( $(ojluni_modify_expectation --autocomplete $COMP_CWORD ${COMP_WORDS[@]:1}))

  return 0
}

complete -o nospace -F _ojluni_modify_expectation ojluni_modify_expectation
