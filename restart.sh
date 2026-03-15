#!/bin/bash

# Configuration
APP_NAME="aibeary"
JAR_NAME="demo-0.0.1-SNAPSHOT.jar"
LOG_FILE="logs/nls.log"
PID_FILE="app.pid"

# Move to the project directory
cd "$(dirname "$0")"

# Create logs directory if it doesn't exist
mkdir -p logs

echo "--- Stopping $APP_NAME ---"
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p $PID > /dev/null; then
        kill -15 $PID
        echo "Sent SIGTERM to PID $PID. Waiting for it to exit..."
        sleep 5
        if ps -p $PID > /dev/null; then
            echo "Force killing PID $PID..."
            kill -9 $PID
        fi
    fi
    rm "$PID_FILE"
else
    # Fallback: check by JAR name
    PID=$(ps aux | grep "$JAR_NAME" | grep -v grep | awk '{print $2}')
    if [ ! -z "$PID" ]; then
        echo "Found running process $PID. Stopping..."
        kill -9 $PID
    fi
fi

echo "--- Rebuilding $APP_NAME ---"
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "Build failed! Aborting restart."
    exit 1
fi

echo "--- Starting $APP_NAME in background ---"
nohup java -jar target/$JAR_NAME > "$LOG_FILE" 2>&1 &

# Save the new PID
NEW_PID=$!
echo $NEW_PID > "$PID_FILE"

echo "$APP_NAME started with PID $NEW_PID."
echo "--- Tailing logs (Ctrl+C to stop tailing, but app will keep running) ---"
tail -f "$LOG_FILE"
