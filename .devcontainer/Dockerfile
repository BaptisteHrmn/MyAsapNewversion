FROM mcr.microsoft.com/vscode/devcontainers/java:11

RUN apt-get update && \
    apt-get install -y --no-install-recommends unzip curl git && \
    rm -rf /var/lib/apt/lists/*

# Définit la racine du SDK Android et met à jour le PATH
ENV ANDROID_SDK_ROOT=/usr/local/android-sdk
ENV PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:${PATH}"

# Copier et exécuter le script d’installation du SDK
COPY setup-android-sdk.sh /tmp/setup-android-sdk.sh
RUN chmod +x /tmp/setup-android-sdk.sh && \
    /tmp/setup-android-sdk.sh && \
    rm /tmp/setup-android-sdk.sh

# Assure que le répertoire SDK est accessible à l’utilisateur vscode
RUN chown -R vscode:vscode ${ANDROID_SDK_ROOT}

# Positionne le dossier de travail
WORKDIR /workspaces/MyAsapNewversion