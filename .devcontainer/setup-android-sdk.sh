#!/usr/bin/env bash
set -e

ANDROID_SDK_ROOT=/usr/local/android-sdk
TOOLS_VERSION=9477386

# Installer l’Android SDK command-line tools
mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools
curl -o sdk.zip https://dl.google.com/android/repository/commandlinetools-linux-${TOOLS_VERSION}_latest.zip
unzip sdk.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools
rm sdk.zip
mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest

# Ajouter au PATH et accepter les licences
export ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT}
export PATH=$PATH:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools
yes | sdkmanager --sdk_root=${ANDROID_SDK_ROOT} --licenses

# Installer la plateforme et build-tools
sdkmanager --sdk_root=${ANDROID_SDK_ROOT} "platform-tools" "platforms;android-31" "build-tools;31.0.0"