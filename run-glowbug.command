#!/bin/bash
cd "$(dirname "$0")"
if ! command -v javac >/dev/null 2>&1 || ! javac -version >/dev/null 2>&1; then
  echo "Java JDK not found. Install with: brew install openjdk   (or from https://adoptium.net)"
  read -p "Press Enter to close"; exit 1
fi
javac GlowbugMaze.java && java GlowbugMaze
