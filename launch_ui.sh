#!/bin/bash
# Launcher script for UNO JavaFX UI client
# This compiles the project and launches the UI

echo "=== UNO JavaFX Client Launcher ==="
echo ""
echo "Compiling project..."
mvn compile -q

if [ $? -ne 0 ]; then
    echo "ERROR: Compilation failed!"
    exit 1
fi

echo "Launching UI..."
echo "(Make sure the server is running on localhost:9090)"
echo ""

mvn exec:java -Dexec.mainClass=uno_ui.MainApp
