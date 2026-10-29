#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CLIENT_DIR="$TT_ROOT/client"
OUTPUT_DIR="$SCRIPT_DIR/output"

echo "=== Building TeamTalk Next Sample Plugins ==="
echo "Project root: $TT_ROOT"
echo "Output dir:   $OUTPUT_DIR"

mkdir -p "$OUTPUT_DIR"

# Classpath dependencies
CLIENT_CLASSES="$CLIENT_DIR/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"
if [ ! -d "$CLIENT_CLASSES" ] && [ -f "/opt/android-sdk/ttnext-plugin-api.jar" ]; then
    CLIENT_CLASSES="/opt/android-sdk/ttnext-plugin-api.jar"
fi

TT_JAR="$CLIENT_DIR/libs/TeamTalk5.jar"
ANDROID_JAR="/root/Android/Sdk/platforms/android-36/android.jar"
if [ ! -f "$ANDROID_JAR" ]; then
    ANDROID_JAR="$(find /root/Android/Sdk/platforms -name "android.jar" 2>/dev/null | sort -V | tail -n 1)"
fi
if [ ! -f "$ANDROID_JAR" ] && [ -f "/opt/android-sdk/android.jar" ]; then
    ANDROID_JAR="/opt/android-sdk/android.jar"
fi

D8="$(find /root/Android/Sdk/build-tools -name "d8" 2>/dev/null | sort -V | tail -n 1)"
if [ -z "$D8" ]; then
    D8="$(which d8 2>/dev/null || true)"
fi

if [ -z "$D8" ]; then
    echo "Error: d8 tool not found in /root/Android/Sdk/build-tools"
    exit 1
fi

echo "Using d8: $D8"
echo "Using android.jar: $ANDROID_JAR"

CP="$CLIENT_CLASSES:$TT_JAR:$ANDROID_JAR"

build_plugin() {
    local PLUGIN_DIR="$1"
    local JAR_NAME="$2"
    local BUILD_TMP="$PLUGIN_DIR/build_tmp"

    echo "--- Building: $JAR_NAME ---"
    rm -rf "$BUILD_TMP"
    mkdir -p "$BUILD_TMP/classes" "$BUILD_TMP/dex"

    # 1. Compile Java sources
    find "$PLUGIN_DIR/src" -name "*.java" > "$BUILD_TMP/sources.txt"
    javac -cp "$CP" -source 17 -target 17 -d "$BUILD_TMP/classes" @"$BUILD_TMP/sources.txt"

    # 2. Translate classes to Dalvik DEX
    find "$BUILD_TMP/classes" -name "*.class" > "$BUILD_TMP/classes.txt"
    "$D8" --output "$BUILD_TMP/dex" --classpath "$CLIENT_CLASSES" --classpath "$TT_JAR" --classpath "$ANDROID_JAR" $(cat "$BUILD_TMP/classes.txt")

    # 3. Package into final JAR
    cp "$PLUGIN_DIR/plugin.json" "$BUILD_TMP/dex/plugin.json"
    cd "$BUILD_TMP/dex"
    jar -cf "$OUTPUT_DIR/$JAR_NAME" classes.dex plugin.json
    cd "$SCRIPT_DIR"

    rm -rf "$BUILD_TMP"
    echo "Successfully generated: $OUTPUT_DIR/$JAR_NAME ($(ls -lh "$OUTPUT_DIR/$JAR_NAME" | awk '{print $5}'))"
}

build_plugin "$SCRIPT_DIR/sample_echo_bot" "EchoBotPlugin.jar"
build_plugin "$SCRIPT_DIR/sample_utility_commands" "UtilityCommandsPlugin.jar"
build_plugin "$SCRIPT_DIR/sample_channel_logger" "ChannelLoggerPlugin.jar"
build_plugin "$SCRIPT_DIR/sample_sub_flipper" "SubFlipperPlugin.jar"

echo "=== All sample plugins built successfully! ==="
ls -lh "$OUTPUT_DIR"
