#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "VPNBOSS: preparing the Xcode project..."

if ! command -v xcodebuild >/dev/null 2>&1; then
  echo "Xcode is not installed. Install it from the App Store and run this file again."
  read -r -p "Press Enter to close..."
  exit 1
fi

if ! command -v xcodegen >/dev/null 2>&1; then
  if command -v brew >/dev/null 2>&1; then
    echo "Installing XcodeGen..."
    brew install xcodegen
  else
    echo "Homebrew is required once to install XcodeGen."
    echo "Open https://brew.sh, install Homebrew, then run this file again."
    open "https://brew.sh"
    read -r -p "Press Enter to close..."
    exit 1
  fi
fi

echo "Generating VPNBOSS.xcodeproj..."
xcodegen generate --spec project.yml

echo "Downloading the iOS VPN core..."
xcodebuild -resolvePackageDependencies -project VPNBOSS.xcodeproj -scheme VPNBOSS

echo "Opening Xcode..."
open VPNBOSS.xcodeproj
