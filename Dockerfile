FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy application files
COPY . .

# Install required dependencies
RUN apt-get update && apt-get install -y \
    git \
    wget \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Download and install Android SDK
RUN wget https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip -O android-sdk.zip && \
    mkdir -p /opt/android-sdk && \
    unzip android-sdk.zip -d /opt/android-sdk && \
    rm android-sdk.zip

# Accept Android SDK licenses
RUN echo "y" | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk "platform-tools" "platforms;android-34"

# Set environment variables
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Build the app
RUN ./gradlew assembleDebug

# Copy the APK
RUN cp app/build/outputs/apk/debug/app-debug.apk /app/app-debug.apk

# Default command
CMD ["./gradlew", "assembleDebug"]
