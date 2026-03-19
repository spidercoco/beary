#!/bin/bash

# Configuration (Ensure these match your server paths)
APP_DIR="/home/spidercoco/beary"
JAR_NAME="target/demo-0.0.1-SNAPSHOT.jar"

FRP_DIR="beary_info/frp"
FRPC_BIN="$FRP_DIR/frpc"
FRPC_CONFIG="$FRP_DIR/frpc.toml"

cd $APP_DIR
mkdir -p logs

# 1. --- Extract Namespace ---
NAMESPACE=$(grep "^namespace=" beary_info/conf/application.properties | cut -d'=' -f2 | tr -d '\r\n ')
if [ -z "$NAMESPACE" ]; then NAMESPACE="bearylove"; fi

# 2. --- Stop Old Processes (Cleanup) ---
# 注意：在 systemd 模式下，旧进程通常由 systemd 清理，但这里保留兜底
pgrep -f "$JAR_NAME" | xargs kill -9 2>/dev/null
pgrep -f "frpc -c $FRPC_CONFIG" | xargs kill -9 2>/dev/null

# 3. --- Update FRPC Config ---
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

# 4. --- Start FRPC (Background) ---
echo "Starting FRPC..."
chmod +x "$FRPC_BIN"
# FRPC 在后台运行
"$FRPC_BIN" -c "$FRPC_CONFIG" > logs/frpc.log 2>&1 &

# 5. --- Start Java (Foreground for systemd) ---
echo "Starting Aibeary Java App..."
# 使用 exec 使 Java 进程接管当前 Shell 进程，方便 systemd 管理
exec java -jar "$JAR_NAME"
