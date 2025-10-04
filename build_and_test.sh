#!/bin/bash

# Script di build e test per Realtime Chat
# Uso: ./build_and_test.sh [opzioni]

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Functions
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

print_header() {
    echo ""
    echo "================================"
    echo "$1"
    echo "================================"
}

# Check if gradlew exists
if [ ! -f "./gradlew" ]; then
    print_error "gradlew not found. Are you in the project root?"
    exit 1
fi

# Make gradlew executable
chmod +x ./gradlew

# Default options
CLEAN=false
BUILD=true
TEST=true
LINT=false
INSTALL=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --clean)
            CLEAN=true
            shift
            ;;
        --no-build)
            BUILD=false
            shift
            ;;
        --no-test)
            TEST=false
            shift
            ;;
        --lint)
            LINT=true
            shift
            ;;
        --install)
            INSTALL=true
            shift
            ;;
        --all)
            CLEAN=true
            BUILD=true
            TEST=true
            LINT=true
            shift
            ;;
        --help)
            echo "Usage: ./build_and_test.sh [options]"
            echo ""
            echo "Options:"
            echo "  --clean      Clean build before building"
            echo "  --no-build   Skip build step"
            echo "  --no-test    Skip test step"
            echo "  --lint       Run lint checks"
            echo "  --install    Install APK on connected device"
            echo "  --all        Run all steps (clean, build, test, lint)"
            echo "  --help       Show this help message"
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Start
print_header "Realtime Chat - Build & Test"

# Clean
if [ "$CLEAN" = true ]; then
    print_info "Cleaning project..."
    ./gradlew clean
    print_success "Clean completed"
fi

# Build
if [ "$BUILD" = true ]; then
    print_info "Building project..."
    ./gradlew assembleDebug
    print_success "Build completed"
fi

# Test
if [ "$TEST" = true ]; then
    print_info "Running tests..."
    ./gradlew test
    
    # Check test results
    if [ $? -eq 0 ]; then
        print_success "All tests passed"
    else
        print_error "Some tests failed"
        exit 1
    fi
fi

# Lint
if [ "$LINT" = true ]; then
    print_info "Running lint checks..."
    ./gradlew lint
    
    if [ $? -eq 0 ]; then
        print_success "Lint checks passed"
    else
        print_error "Lint found issues"
        print_info "Check report at: app/build/reports/lint-results.html"
    fi
fi

# Install
if [ "$INSTALL" = true ]; then
    print_info "Installing APK on device..."
    
    # Check if device is connected
    if ! adb devices | grep -q "device$"; then
        print_error "No Android device connected"
        exit 1
    fi
    
    ./gradlew installDebug
    
    if [ $? -eq 0 ]; then
        print_success "APK installed successfully"
        print_info "You can now launch the app from your device"
    else
        print_error "Installation failed"
        exit 1
    fi
fi

# Summary
print_header "Build Summary"
echo "Steps executed:"
[ "$CLEAN" = true ] && echo "  ✓ Clean"
[ "$BUILD" = true ] && echo "  ✓ Build"
[ "$TEST" = true ] && echo "  ✓ Test"
[ "$LINT" = true ] && echo "  ✓ Lint"
[ "$INSTALL" = true ] && echo "  ✓ Install"

print_success "All steps completed successfully!"

# Show APK location
if [ "$BUILD" = true ]; then
    echo ""
    print_info "APK location: app/build/outputs/apk/debug/app-debug.apk"
fi

# Show test report location
if [ "$TEST" = true ]; then
    print_info "Test report: app/build/reports/tests/testDebugUnitTest/index.html"
fi

echo ""
print_success "Done! 🚀"
