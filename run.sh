#!/bin/bash
# ElimuSMS Tier1 - Mac Run Script

echo "🚀 ElimuSMS Tier1 Optimized (80/20 Solution)"
echo "============================================="

# Check if WTK_HOME is set
if [ -z "$WTK_HOME" ]; then
    echo "❌ ERROR: WTK_HOME environment variable not set"
    echo "Please set it: export WTK_HOME=/path/to/javame-sdk-8.3"
    exit 1
fi

# Check if Java ME SDK exists
if [ ! -f "$WTK_HOME/bin/emulator" ]; then
    echo "❌ ERROR: Java ME SDK not found at $WTK_HOME"
    echo "Please download from: https://www.oracle.com/java/technologies/javame-sdk-downloads.html"
    exit 1
fi

echo "📦 Building project..."
ant clean build

if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    echo "📊 Checking size..."
    ant size-report

    echo ""
    echo "🎮 Starting emulator..."
    $WTK_HOME/bin/emulator -Xdevice:DefaultCldcPhone1 -Xdescriptor:ElimuSMS.jad

else
    echo "❌ Build failed!"
    exit 1
fi