#!/bin/bash

# Script to prepare test artifacts for Docker deployment
# This script copies all necessary JARs to a deployment directory

DEPLOY_DIR="docker-deploy"
TARGET_DIR="target"
DEPENDENCIES_DIR="$TARGET_DIR/dependencies"

echo "Preparing test artifacts for Docker deployment..."

# Create deployment directory
mkdir -p "$DEPLOY_DIR/lib"

# Copy the test JAR
echo "Copying test JAR..."
cp "$TARGET_DIR/testng-simple-1.0.0-tests.jar" "$DEPLOY_DIR/"

# Copy all dependencies
echo "Copying dependency JARs..."
cp "$DEPENDENCIES_DIR"/*.jar "$DEPLOY_DIR/lib/"

# Copy testng.xml if it exists
if [ -f "testng.xml" ]; then
    echo "Copying testng.xml..."
    cp testng.xml "$DEPLOY_DIR/"
fi

# Copy testng_taurus.yaml if it exists
if [ -f "testng_taurus.yaml" ]; then
    echo "Copying testng_taurus.yaml..."
    cp testng_taurus.yaml "$DEPLOY_DIR/"
fi

# Count files
TEST_JAR_COUNT=$(ls "$DEPLOY_DIR"/*.jar 2>/dev/null | wc -l)
DEP_COUNT=$(ls "$DEPLOY_DIR/lib"/*.jar 2>/dev/null | wc -l)

echo ""
echo "✓ Deployment artifacts ready in '$DEPLOY_DIR/' directory"
echo "  - Test JAR: $TEST_JAR_COUNT file(s)"
echo "  - Dependencies: $DEP_COUNT JAR(s)"
echo ""
echo "Total size:"
du -sh "$DEPLOY_DIR"

echo ""
echo "To run tests in Docker, use:"
echo "  java -cp \"testng-simple-1.0.0-tests.jar:lib/*\" org.testng.TestNG testng.xml"
