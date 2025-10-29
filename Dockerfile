FROM --platform=linux/arm64 eclipse-temurin:8-jdk

# Install ant on ARM64
RUN apt-get update && \
    apt-get install -y --no-install-recommends ant && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY . .

CMD ["ant", "clean", "build"]