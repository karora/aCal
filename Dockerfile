FROM eclipse-temurin:25-jdk-noble

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
        wget unzip ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# What the emulator needs to run without a display, for the instrumented tests
RUN apt-get update && apt-get install -y --no-install-recommends \
        libpulse0 libnss3 libgl1 libx11-6 libxkbfile1 libxcb1 libxcb-cursor0 \
        libxcomposite1 libxcursor1 libxdamage1 libxext6 libxfixes3 libxi6 \
        libxrender1 libxtst6 libasound2t64 libdbus-1-3 libdrm2 libbsd0 \
    && rm -rf /var/lib/apt/lists/*

ARG CMDLINE_TOOLS_VERSION=16111833
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && wget -q "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" -O /tmp/cmdtools.zip \
    && unzip -q /tmp/cmdtools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm /tmp/cmdtools.zip

ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install \
        "platforms;android-37.0" \
        "build-tools;37.0.0" \
        "platform-tools" > /dev/null \
    && chmod -R a+rX ${ANDROID_HOME}

# The emulator and the device the instrumented tests run on.  Keep the image
# in step with the managed device in build.gradle.
RUN sdkmanager --install \
        "emulator" \
        "system-images;android-36;aosp_atd;x86_64" > /dev/null \
    && chmod -R a+rX ${ANDROID_HOME}

WORKDIR /workspace
