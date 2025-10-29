#!/bin/bash
echo "🚀 ElimuSMS with MicroEmulator"
echo "=============================="

# MicroEmulator location
MICROEMU_JAR="$HOME/Downloads/microemulator-2.0.4/microemulator.jar"

# Check if MicroEmulator exists
if [ ! -f "$MICROEMU_JAR" ]; then
    echo "❌ MicroEmulator not found at: $MICROEMU_JAR"
    echo "💡 Download it to ~/Downloads/microemulator-2.0.4/microemulator.jar"
    exit 1
fi

# Build project first
echo "🏗️ Building project..."
~/apache-ant/bin/ant clean build

if [ ! -f "ElimuSMS.jad" ]; then
    echo "❌ Build failed - ElimuSMS.jad not created"
    exit 1
fi

# Run with MicroEmulator
echo "🎮 Starting ElimuSMS..."
echo "MicroEmulator: $MICROEMU_JAR"
echo "JAD File: ElimuSMS.jad"
echo ""

java -jar "$MICROEMU_JAR" -Xdescriptor:ElimuSMS.jad