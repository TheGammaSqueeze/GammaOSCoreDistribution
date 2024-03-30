#!/bin/bash

set -e

echo "Run native build:"

make -f .ci/Makefile -j12

echo "Run style check:"

./.ci/.gitlab-ci-checkcommit.sh

echo -e "\n\e[32m --- SUCCESS ---"
