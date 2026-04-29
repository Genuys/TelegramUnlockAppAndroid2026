#!/system/bin/sh
# TG Proxy Core — Magisk service.sh
# Runs in Original mode (direct WebSocket to Telegram DCs). No Python layer.
# Listens on 127.0.0.1:1080 (SOCKS5, no auth required from localhost).

MODDIR="${0%/*}"
LOG_DIR="/data/adb/modules/tgproxy_core/logs"
BINARY="/data/adb/modules/tgproxy_core/system/bin/tgproxy"
PIDFILE="/data/adb/modules/tgproxy_core/tgproxy.pid"

mkdir -p "$LOG_DIR"
LOG="$LOG_DIR/tgproxy_$(date +%Y%m%d).log"

log_msg() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOG"
}

# --- Wait for network (max 60 seconds) ---
wait_for_network() {
    local retries=0
    while [ $retries -lt 30 ]; do
        if ping -c1 -W2 8.8.8.8 > /dev/null 2>&1 \
        || ping -c1 -W2 149.154.167.220 > /dev/null 2>&1; then
            log_msg "Network available after ${retries}x2s"
            return 0
        fi
        sleep 2
        retries=$((retries + 1))
    done
    log_msg "Network wait timeout — starting anyway"
    return 1
}

# --- Kill stale instance ---
if [ -f "$PIDFILE" ]; then
    OLD_PID=$(cat "$PIDFILE")
    kill "$OLD_PID" 2>/dev/null
    sleep 1
fi

log_msg "=== TG Proxy Core starting ==="
wait_for_network

if [ ! -x "$BINARY" ]; then
    log_msg "ERROR: binary not found at $BINARY"
    exit 1
fi

# Start proxy (replace with actual invocation for your Go binary)
"$BINARY" \
    --mode original \
    --bind 127.0.0.1:1080 \
    --log "$LOG" \
    >> "$LOG" 2>&1 &

PROXY_PID=$!
echo "$PROXY_PID" > "$PIDFILE"
log_msg "Started with PID $PROXY_PID"

# Rotate logs older than 7 days
find "$LOG_DIR" -name "tgproxy_*.log" -mtime +7 -delete 2>/dev/null
