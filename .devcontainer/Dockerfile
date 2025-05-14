<<<<<<< HEAD
# Base image Java 11 on Debian
ARG VARIANT="11"
FROM mcr.microsoft.com/vscode/devcontainers/java:${VARIANT}

# Installer unzip, curl, git pour le SDK Android
=======
ARG VARIANT="11"
FROM mcr.microsoft.com/vscode/devcontainers/java:\${VARIANT}

>>>>>>> 4e30801 (Ajout de la configuration Dev Container Android)
RUN apt-get update && \
    apt-get install -y --no-install-recommends unzip curl git && \
    rm -rf /var/lib/apt/lists/*

<<<<<<< HEAD
# Copier et exécuter le script d’installation du SDK
=======
>>>>>>> 4e30801 (Ajout de la configuration Dev Container Android)
COPY setup-android-sdk.sh /tmp/setup-android-sdk.sh
RUN chmod +x /tmp/setup-android-sdk.sh && \
    /tmp/setup-android-sdk.sh && \
    rm /tmp/setup-android-sdk.sh

<<<<<<< HEAD
WORKDIR /workspace
=======
WORKDIR /workspace
>>>>>>> 4e30801 (Ajout de la configuration Dev Container Android)
