#!/bin/bash

VERSION="2.24.T16" # Define the version tag

echo "Starting Terrakube build process..."

# Build skip spring boot docker image
echo "Building Terrakube components without Docker images..."
mvn clean install -Dspring-boot.build-image.skip=true -T 8 -DskipTests
echo "Terrakube components build completed."

# Update Terrakube Version
echo "Updating Terrakube version to $VERSION..."
mvn -pl "api,registry,executor" versions:set-property -Dproperty=revision -DnewVersion=$VERSION -DgenerateBackupPoms=false -T 8 -DskipTests
echo "Terrakube version updated."

# Build Terrakube Images
echo "Building Docker images for Terrakube API, Registry, and Executor..."
mvn -pl "api,registry,executor" spring-boot:build-image -B --file pom.xml -T 8 -DskipTests
echo "Docker images for Terrakube components built successfully."

# Install other dependencies to use with Terrakube Extensions in terrakube executor creating a temporal image
echo "Installing additional dependencies in Executor image..."
docker run --user="root" --entrypoint launcher $(docker images executor -q) "apt-get update && apt-get install git jq curl -y"
echo "Dependencies installed in Executor image."

# Rollback to original entry point
echo "Rolling back Executor image to original entry point..."
docker commit --change='ENTRYPOINT ["/cnb/process/web"]' --change='USER cnb' $(docker ps -lq) executortemp
echo "Executor image rollback completed."

# Setup docker tags
echo "Tagging Docker images with version $VERSION..."
docker tag $(docker images api-server -q) azbuilder/api-server:$VERSION
docker tag $(docker images open-registry -q) azbuilder/open-registry:$VERSION
docker tag $(docker images executortemp -q) azbuilder/executor:$VERSION
echo "Docker images tagged successfully."

# Build Terrakube UI Image
echo "Building Terrakube UI image..."
cd ui

# Install UI dependencies
echo "Installing UI dependencies..."
yarn install
echo "UI dependencies installed."

# Build docker image
echo "Building Docker image for Terrakube UI..."
docker build -t terrakube-ui:$VERSION .
echo "Terrakube UI Docker image built successfully."

# Setup tags for UI
echo "Tagging Terrakube UI Docker image with version $VERSION..."
docker tag $(docker images terrakube-ui -q) azbuilder/terrakube-ui:$VERSION
echo "Terrakube UI Docker image tagged successfully."

echo "Terrakube build process completed."