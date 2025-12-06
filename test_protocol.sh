#!/bin/bash

# Simple script to test the UNO server protocol with two clients
# This demonstrates creating a room, joining, starting game, and playing cards

echo "=== UNO Protocol Test ==="
echo ""
echo "Starting server in background..."

# Build classpath
mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt

# Start server in background
java -cp "target/classes:$(cat cp.txt)" uno_server.ServerLauncher > server.log 2>&1 &
SERVER_PID=$!
echo "Server started with PID $SERVER_PID"

# Wait for server to start
sleep 2

echo ""
echo "Running protocol test..."
echo ""

# Run the test client
java -cp "target/classes:$(cat cp.txt)" uno_server.protocol.ProtocolTest

# Kill the server
kill $SERVER_PID 2>/dev/null
wait $SERVER_PID 2>/dev/null

echo ""
echo "Test complete. Check server.log for detailed server logs."
