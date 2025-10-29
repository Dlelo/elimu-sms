#!/bin/bash
echo "🚀 Building Java ME app with Docker..."

# Clean previous builds
rm -rf docker-build

# Build Docker image
docker build -t elimu-me .

# Run container and copy results
docker run --name elimu-build elimu-me
docker cp elimu-build:/app/build ./docker-build
docker rm elimu-build

# Check if build was successful
if [ -f "docker-build/dist/ElimuSMS.jad" ]; then
    echo "✅ Build successful!"
    echo "📁 Output files in: docker-build/dist/"
    ls -la docker-build/dist/
else
    echo "❌ Build failed!"
    exit 1
fi