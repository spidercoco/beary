#!/bin/bash

# Configuration
APP_NAME="aibeary"
JAR_NAME="demo-0.0.1-SNAPSHOT.jar"
LOG_FILE="logs/nls.log"
PID_FILE="app.pid"
FRPC_PID_FILE="frpc.pid"

FRP_DIR="beary_info/frp"
FRPC_BIN="$FRP_DIR/frpc"
FRPC_CONFIG="$FRP_DIR/frpc.toml"

# Move to the project directory
cd "$(dirname "$0")"
mkdir -p logs

# 1. --- Extract Namespace from config ---
NAMESPACE=$(grep "^namespace=" beary_info/conf/application.properties | cut -d'=' -f2 | tr -d '\r\n ')
if [ -z "$NAMESPACE" ]; then
    NAMESPACE="bearylove" # Fallback
fi

# 2. --- Stop Existing Processes ---
echo "--- Stopping $APP_NAME & FRPC ---"
[ -f "$PID_FILE" ] && kill $(cat "$PID_FILE") 2>/dev/null
[ -f "$FRPC_PID_FILE" ] && kill $(cat "$FRPC_PID_FILE") 2>/dev/null
# Extra cleanup
pgrep -f "$JAR_NAME" | xargs kill -9 2>/dev/null
pgrep -f "frpc -c $FRPC_CONFIG" | xargs kill -9 2>/dev/null

# 3. --- Update FRPC Config ---
echo "--- Updating FRPC Config (Namespace: $NAMESPACE) ---"
cat > "$FRPC_CONFIG" <<EOF
serverAddr = "it.deepinmind.com"
serverPort = 7000
auth.token = "deepinmind_secret_666"

[[proxies]]
name = "${NAMESPACE}_http"
type = "http"
localIP = "127.0.0.1"
localPort = 8080
customDomains = ["it.deepinmind.com"]
locations = ["/aibeary/${NAMESPACE}"]
EOF

# 4. --- Start FRPC ---
if [ -f "$FRPC_BIN" ]; then
    echo "--- Starting FRPC ---"
    nohup "$FRPC_BIN" -c "$FRPC_CONFIG" > logs/frpc.log 2>&1 &
    echo $! > "$FRPC_PID_FILE"
    sleep 1
else
    echo "Error: FRPC binary missing ($FRPC_BIN)"
    exit 1
fi

# 5. --- Start Java ---
if [ ! -f "target/$JAR_NAME" ]; then
    echo "Error: JAR file not found (target/$JAR_NAME). Please run mvn clean package first."
    exit 1
fi

echo "--- Starting $APP_NAME ---"
nohup java -jar target/$JAR_NAME > "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

echo "Done. $APP_NAME started with PID $(cat $PID_FILE)."
echo "--- Tailing logs ---"
tail -f "$LOG_FILE"
