#!/bin/bash

#
# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# A script to interactively manage FastPairTestDataCache of FastPairTestDataProviderService.
#
# FastPairTestDataProviderService (../../clients/test_service/fastpair_seeker_data_provider/) is a
# run-Time configurable FastPairDataProviderService. It has a FastPairTestDataManager to receive
# Intent broadcast to add/clear the FastPairTestDataCache. This cache provides the data to return to
# the Nearby Mainline module for onXXX calls (ex: onLoadFastPairAntispoofKeyDeviceMetadata).
#
# To use this tool, make sure you:
# 1. Flash the ROM your built to the device
# 2. Build and install NearbyFastPairSeekerDataProvider to the device
# m NearbyFastPairSeekerDataProvider
# adb install -r -g ${ANDROID_PRODUCT_OUT}/system/app/NearbyFastPairSeekerDataProvider/NearbyFastPairSeekerDataProvider.apk
# 3. Check FastPairService can connect to the FastPairTestDataProviderService.
# adb logcat ServiceMonitor:* *:S
# (ex: ServiceMonitor: [FAST_PAIR_DATA_PROVIDER] connected to {
# android.nearby.fastpair.seeker.dataprovider/android.nearby.fastpair.seeker.dataprovider.FastPairTestDataProviderService})
#
# Sample Usages:
# 1. Send FastPairAntispoofKeyDeviceMetadata for PixelBuds-A to FastPairTestDataCache
# ./fast_pair_data_provider_shell.sh -m=718c17  -a=../test_data/fastpair/pixelbuds-a_antispoofkey_devicemeta_json.txt
# 2. Send FastPairAccountDevicesMetadata for PixelBuds-A to FastPairTestDataCache
# ./fast_pair_data_provider_shell.sh -d=../test_data/fastpair/pixelbuds-a_account_devicemeta_json.txt
# 3. Send FastPairAntispoofKeyDeviceMetadata for Provider Simulator to FastPairTestDataCache
# ./fast_pair_data_provider_shell.sh -m=00000c -a=../test_data/fastpair/simulator_antispoofkey_devicemeta_json.txt
# 4. Send FastPairAccountDevicesMetadata for Provider Simulator to FastPairTestDataCache
# ./fast_pair_data_provider_shell.sh -d=../test_data/fastpair/simulator_account_devicemeta_json.txt
# 5. Clear FastPairTestDataCache
# ./fast_pair_data_provider_shell.sh -c
#
# Check logcat:
# adb logcat FastPairTestDataManager:* FastPairTestDataProviderService:* *:S

for i in "$@"; do
  case $i in
    -a=*|--ask=*)
      ASK_FILE="${i#*=}"
      shift # past argument=value
      ;;
    -m=*|--model=*)
      MODEL_ID="${i#*=}"
      shift # past argument=value
      ;;
    -d=*|--adm=*)
      ADM_FILE="${i#*=}"
      shift # past argument=value
      ;;
    -c)
      CLEAR="true"
      shift # past argument
      ;;
    -*|--*)
      echo "Unknown option $i"
      exit 1
      ;;
    *)
      ;;
  esac
done

readonly ACTION_BASE="android.nearby.fastpair.seeker.action"
readonly ACTION_SEND_ANTISPOOF_KEY_DEVICE_METADATA="$ACTION_BASE.ACTION_SEND_ANTISPOOF_KEY_DEVICE_METADATA"
readonly ACTION_SEND_ACCOUNT_KEY_DEVICE_METADATA="$ACTION_BASE.ACCOUNT_KEY_DEVICE_METADATA"
readonly ACTION_RESET_TEST_DATA_CACHE="$ACTION_BASE.RESET"
readonly DATA_JSON_STRING_KEY="json"
readonly DATA_MODEL_ID_STRING_KEY="modelId"

if [[ -n "${ASK_FILE}" ]] && [[ -n "${MODEL_ID}" ]]; then
  echo "Sending AntispoofKeyDeviceMetadata for model ${MODEL_ID} to the FastPairTestDataCache..."
  ASK_JSON_TEXT=$(tr -d '\n' < "$ASK_FILE")
  CMD="am broadcast -a $ACTION_SEND_ANTISPOOF_KEY_DEVICE_METADATA "
  CMD+="-e $DATA_MODEL_ID_STRING_KEY '$MODEL_ID' "
  CMD+="-e $DATA_JSON_STRING_KEY '\"'$ASK_JSON_TEXT'\"'"
  CMD="adb shell \"$CMD\""
  echo "$CMD" && eval "$CMD"
fi

if [ -n "${ADM_FILE}" ]; then
  echo "Sending AccountKeyDeviceMetadata to the FastPairTestDataCache..."
  ADM_JSON_TEXT=$(tr -d '\n' < "$ADM_FILE")
  CMD="am broadcast -a $ACTION_SEND_ACCOUNT_KEY_DEVICE_METADATA "
  CMD+="-e $DATA_JSON_STRING_KEY '\"'$ADM_JSON_TEXT'\"'"
  CMD="adb shell \"$CMD\""
  echo "$CMD" && eval "$CMD"
fi

if [ -n "${CLEAR}" ]; then
  echo "Cleaning FastPairTestDataCache..."
  CMD="adb shell am broadcast -a $ACTION_RESET_TEST_DATA_CACHE"
  echo "$CMD" && eval "$CMD"
fi
