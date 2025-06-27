#!/bin/bash

VERSION="2.24.T10" # Define the version tag

echo "Starting Terrakube build process..."

# Build skip spring boot docker image
echo "Building Terrakube components without Docker images..."
mvn clean install -Dspring-boot.build-image.skip=true -T 8 -DskipTests
echo "Terrakube components build completed."

# Update Terrakube Version
echo "Updating Terrakube version to $VERSION..."
mvn -pl "api" versions:set-property -Dproperty=revision -DnewVersion=$VERSION -DgenerateBackupPoms=false -T 8 -DskipTests
echo "Terrakube version updated."

# Build Terrakube Images
echo "Building Docker images for Terrakube API, Registry, and Executor..."
export DOCKER_BUILDKIT=1
mvn -pl "api" spring-boot:build-image -B --file pom.xml -T 8 -DskipTests
echo "Docker images for Terrakube components built successfully."

# Setup docker tags
echo "Tagging Docker images with version $VERSION..."
docker tag $(docker images api-server -q) azbuilder/api-server:$VERSION
echo "Docker images tagged successfully."
