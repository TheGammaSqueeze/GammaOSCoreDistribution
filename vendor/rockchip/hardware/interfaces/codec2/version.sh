#!/bin/bash
CURRENTDIR=`dirname $0`
cd $CURRENTDIR

VERSION=$(git log -1 --date=short --pretty=format:"git-%h-%s"|sed "s/\"//g")
CURRENT_TIME=`date +"%Y-%m-%d %H:%M:%S"`
PRODUCT=$TARGET_PRODUCT
VERSION_TARGET="$(cat version.h.template | sed  -e 's/\$GIT_BUILD_VERSION/'"$VERSION build-time: $CURRENT_TIME running on $PRODUCT"'/g' version.h.template)"

echo "${VERSION_TARGET}"
