# -------------------------------------------------------------------
# MyAsap Android Dev Container
# -------------------------------------------------------------------
FROM mcr.microsoft.com/vscode/devcontainers/java:11

# 1) Mise à jour et outils de base
RUN apt-get update && \
    apt-get install -y --no-install-recommends unzip curl git && \
    rm -rf /var/lib/apt/lists/*

# 2) Variables SDK Android
ENV ANDROID_SDK_ROOT=/usr/local/android-sdk
ENV TOOLS_VERSION=9477386
ENV PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:${PATH}"

# 3) Installation Android SDK et licences + plateformes/build-tools
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    curl -o /tmp/commandlinetools.zip \
      https://dl.google.com/android/repository/commandlinetools-linux-${TOOLS_VERSION}_latest.zip && \
    unzip /tmp/commandlinetools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools && \
    rm /tmp/commandlinetools.zip && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    yes | sdkmanager --sdk_root=${ANDROID_SDK_ROOT} --licenses && \
    sdkmanager --sdk_root=${ANDROID_SDK_ROOT} \
      "platform-tools" \
      "platforms;android-33" \
      "build-tools;30.0.3"

# 4) Droits sur le SDK pour l’utilisateur vscode
RUN chown -R vscode:vscode ${ANDROID_SDK_ROOT}

# 5) Dossier de travail
WORKDIR /workspaces/MyAsapNewversion