FROM gcr.io/cloud-builders/javac:8

RUN apt-get update && apt-get install -y \
    cmake \
    curl \
    ninja-build \
    python3-pip
RUN pip3 install meson
RUN curl -o ndk.zip \
    https://dl.google.com/android/repository/android-ndk-r21e-linux-x86_64.zip
RUN unzip ndk.zip
RUN mv android-ndk-r21e /ndk
RUN curl -L -o platform-tools.zip \
    https://dl.google.com/android/repository/platform-tools-latest-linux.zip
RUN unzip platform-tools.zip platform-tools/adb
RUN mv platform-tools/adb /usr/bin/adb

WORKDIR /src
ENTRYPOINT ["./gradlew"]
CMD ["--stacktrace", "-PndkPath=/ndk", "release"]
