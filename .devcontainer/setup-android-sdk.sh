#!/usr/bin/env bash
set -e

ANDROID_SDK_ROOT=/usr/local/android-sdk
TOOLS_VERSION=9477386

# Crée le dossier des outils en ligne de commande
mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools

# Télécharge et installe les Android SDK command-line tools
curl -o sdk.zip https://dl.google.com/android/repository/commandlinetools-linux-${TOOLS_VERSION}_latest.zip
unzip sdk.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools
rm sdk.zip
mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest

# Définit les variables d’environnement et ajoute au PATH
export ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT}
export PATH=$PATH:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

# Accepte automatiquement toutes les licences
yes | sdkmanager --sdk_root=${ANDROID_SDK_ROOT} --licenses

# Installe les plateformes et outils de build nécessaires
sdkmanager --sdk_root=${ANDROID_SDK_ROOT} "platform-tools" "platforms;android-31" "build-tools;31.0.0"