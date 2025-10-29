#!/bin/bash
# ElimuSMS Tier1 - Simplified Run Script (No Auto-Install)
# For when Homebrew/Xcode issues occur

set -e

echo "🚀 ElimuSMS Tier1 Optimized"
echo "============================"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Simple output functions
info() { echo -e "${BLUE}==>${NC} $1"; }
success() { echo -e "${GREEN}✅${NC} $1"; }
warn() { echo -e "${YELLOW}⚠️${NC} $1"; }
error() { echo -e "${RED}❌${NC} $1"; }

# Manual WTK_HOME setup
setup_wtk_home() {
    info "Setting up Java ME SDK..."

    # Common installation locations
    local locations=(
        "$HOME/javame-sdk-8.3"
        "$HOME/Java_ME_sdk_8.3"
        "/Applications/javame-sdk-8.3"
        "$WTK_HOME"
    )

    for loc in "${locations[@]}"; do
        if [[ -f "$loc/bin/emulator" ]]; then
            export WTK_HOME="$loc"
            success "Found Java ME SDK: $WTK_HOME"
            return 0
        fi
    done

    error "Java ME SDK not found!"
    echo ""
    echo "To fix this:"
    echo "1. Download from: https://www.oracle.com/java/technologies/javame-sdk-downloads.html"
    echo "2. Install it, then set WTK_HOME manually:"
    echo "   export WTK_HOME=\"/path/to/javame-sdk-8.3\""
    echo ""
    echo "Quick manual setup:"
    echo "  cd ~/Downloads"
    echo "  curl -O https://download.oracle.com/otn/java/javame/8.3/sdk-8-3-macosx.zip"
    echo "  unzip sdk-8-3-macosx.zip"
    echo "  ./javame_sdk-8-3-macosx.sh"
    echo "  export WTK_HOME=\"\$HOME/javame-sdk-8.3\""
    echo ""
    exit 1
}

# Check basic requirements
check_requirements() {
    info "Checking requirements..."

    # Check Java
    if ! command -v java &> /dev/null; then
        error "Java not found!"
        echo "Download from: https://openjdk.org/install/"
        exit 1
    fi

    # Check Ant
    if ! command -v ant &> /dev/null; then
        error "Ant not found!"
        echo ""
        echo "Quick install options:"
        echo "1. Download manually: https://ant.apache.org/bindownload.cgi"
        echo "2. Or install via package manager:"
        echo "   - If you have Homebrew: brew install ant"
        echo "   - If you have MacPorts: sudo port install apache-ant"
        echo ""
        exit 1
    fi

    success "Java: $(java -version 2>&1 | head -1)"
    success "Ant: $(ant -version 2>&1 | head -1)"
}

# Build project
build_project() {
    info "Building project..."

    if ant clean build; then
        success "Build successful!"
    else
        error "Build failed!"
        echo ""
        warn "Common fixes:"
        echo "1. Make sure JAVA_HOME is set: export JAVA_HOME=\$(/usr/libexec/java_home)"
        echo "2. Check WTK_HOME: echo \$WTK_HOME"
        echo "3. Verify project structure has src/com/elimu/ files"
        exit 1
    fi
}

# Show results
show_results() {
    info "Build results:"

    if [[ -f "ElimuSMS.jar" ]]; then
        size=$(wc -c < ElimuSMS.jar)
        size_kb=$((size / 1024))
        echo "📦 JAR Size: $size bytes (${size_kb}KB)"

        if [[ $size -lt 45000 ]]; then
            success "🎉 Perfect! Under 45KB target"
        elif [[ $size -lt 50000 ]]; then
            success "✅ Good! Close to target"
        else
            warn "⚠️  Larger than expected - but still usable"
        fi
    else
        error "ElimuSMS.jar not found!"
        exit 1
    fi
}

# Run emulator
run_emulator() {
    info "Starting emulator in 3 seconds..."
    echo "Press Ctrl+C to stop"
    sleep 3
    "$WTK_HOME/bin/emulator" -Xdevice:DefaultCldcPhone1 -Xdescriptor:ElimuSMS.jad
}

# Main
main() {
    setup_wtk_home
    check_requirements
    build_project
    show_results
    run_emulator
}

main