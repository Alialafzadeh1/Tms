#!/usr/bin/env bash
# TMS VPN — Ubuntu 24.04 LTS Xray Core (VLESS Reality) + Per-User Provisioning Agent Installer
# Usage: sudo bash ubuntu24_tms_xray_setup.sh <PUBLIC_IP_OR_DOMAIN> <ADMIN_API_TOKEN>
set -euo pipefail

PUBLIC_HOST="${1:-$(curl -fsS https://api.ipify.org)}"
API_TOKEN="${2:-$(openssl rand -hex 24)}"
SNI_TARGET="${3:-www.microsoft.com}"

echo "[1/5] Installing dependencies and Xray Core..."
apt-get update -y
apt-get install -y curl unzip openssl python3 python3-venv ufw jq
bash -c "$(curl -L https://github.com/XTLS/Xray-install/raw/main/install-release.sh)" @ install

echo "[2/5] Generating VLESS Reality x25519 keys..."
KEYS=$(xray x25519)
PRIVATE_KEY=$(echo "$KEYS" | awk '/Private key:/ {print $3}')
PUBLIC_KEY=$(echo "$KEYS" | awk '/Public key:/ {print $3}')
SHORT_ID=$(openssl rand -hex 8)
MASTER_UUID=$(cat /proc/sys/kernel/random/uuid)

mkdir -p /etc/tms-vpn /usr/local/etc/xray
cat > /etc/tms-vpn/node.env <<EOF
PUBLIC_HOST=${PUBLIC_HOST}
API_TOKEN=${API_TOKEN}
SNI_TARGET=${SNI_TARGET}
REALITY_PRIVATE_KEY=${PRIVATE_KEY}
REALITY_PUBLIC_KEY=${PUBLIC_KEY}
REALITY_SHORT_ID=${SHORT_ID}
EOF
chmod 600 /etc/tms-vpn/node.env

cat > /usr/local/etc/xray/config.json <<EOF
{
  "log": { "loglevel": "warning" },
  "inbounds": [
    {
      "tag": "vless-reality-in",
      "listen": "0.0.0.0",
      "port": 443,
      "protocol": "vless",
      "settings": {
        "clients": [
          { "id": "${MASTER_UUID}", "flow": "xtls-rprx-vision", "email": "admin@tmsvpn.local" }
        ],
        "decryption": "none"
      },
      "streamSettings": {
        "network": "tcp",
        "security": "reality",
        "realitySettings": {
          "show": false,
          "dest": "${SNI_TARGET}:443",
          "xver": 0,
          "serverNames": ["${SNI_TARGET}"],
          "privateKey": "${PRIVATE_KEY}",
          "shortIds": ["${SHORT_ID}"]
        }
      },
      "sniffing": { "enabled": true, "destOverride": ["http", "tls", "quic"] }
    }
  ],
  "outbounds": [
    { "tag": "direct", "protocol": "freedom" },
    { "tag": "block", "protocol": "blackhole" }
  ]
}
EOF

echo "[3/5] Creating TMS Node Provisioning Control API..."
python3 -m venv /opt/tms-agent
/opt/tms-agent/bin/pip install --quiet fastapi uvicorn

cat > /opt/tms-agent/agent.py <<'PYEOF'
import json, os, subprocess, time, uuid
from pathlib import Path
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel

XRAY_CFG = Path("/usr/local/etc/xray/config.json")
START_TIME = time.time()
API_TOKEN = os.environ["API_TOKEN"]
PUBLIC_HOST = os.environ["PUBLIC_HOST"]
SNI_TARGET = os.environ["SNI_TARGET"]
PUBLIC_KEY = os.environ["REALITY_PUBLIC_KEY"]
SHORT_ID = os.environ["REALITY_SHORT_ID"]

app = FastAPI(title="TMS VPN Node Control Agent")

class ProvisionRequest(BaseModel):
    user_id: str
    email: str
    quota_gb: int = 100

def check_auth(authorization: str | None):
    if not authorization or authorization != f"Bearer {API_TOKEN}":
        raise HTTPException(status_code=401, detail="Unauthorized")

@app.get("/api/v1/health")
def health(authorization: str | None = Header(default=None)):
    check_auth(authorization)
    cfg = json.loads(XRAY_CFG.read_text())
    clients = cfg["inbounds"][0]["settings"]["clients"]
    xray_ok = subprocess.run(["systemctl", "is-active", "--quiet", "xray"]).returncode == 0
    return {
        "xray_running": xray_ok,
        "active_users": len(clients),
        "uptime_seconds": int(time.time() - START_TIME),
        "public_host": PUBLIC_HOST,
    }

@app.post("/api/v1/users/provision")
def provision(req: ProvisionRequest, authorization: str | None = Header(default=None)):
    check_auth(authorization)
    cfg = json.loads(XRAY_CFG.read_text())
    clients = cfg["inbounds"][0]["settings"]["clients"]
    existing = next((c for c in clients if c.get("email") == req.email), None)
    client_uuid = existing["id"] if existing else str(uuid.uuid4())
    if not existing:
        clients.append({"id": client_uuid, "flow": "xtls-rprx-vision", "email": req.email})
        XRAY_CFG.write_text(json.dumps(cfg, indent=2))
        subprocess.run(["systemctl", "restart", "xray"], check=True)
    vless_uri = (
        f"vless://{client_uuid}@{PUBLIC_HOST}:443"
        f"?encryption=none&flow=xtls-rprx-vision&security=reality"
        f"&sni={SNI_TARGET}&fp=chrome&pbk={PUBLIC_KEY}&sid={SHORT_ID}&type=tcp#TMS-{req.user_id[:6]}"
    )
    return {"uuid": client_uuid, "host": PUBLIC_HOST, "port": 443, "vless_uri": vless_uri}
PYEOF

echo "[4/5] Enabling systemd services..."
cat > /etc/systemd/system/tms-node-agent.service <<EOF
[Unit]
Description=TMS VPN Ubuntu 24.04 Node Control API
After=network.target xray.service

[Service]
EnvironmentFile=/etc/tms-vpn/node.env
ExecStart=/opt/tms-agent/bin/uvicorn agent:app --app-dir /opt/tms-agent --host 0.0.0.0 --port 8443
Restart=always

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now xray tms-node-agent

echo "[5/5] Configuring UFW firewall..."
ufw allow 22/tcp
ufw allow 443/tcp
ufw allow 8443/tcp
ufw --force enable || true

echo "=== TMS VPN Ubuntu 24.04 Node Ready ==="
echo "Control API URL : http://${PUBLIC_HOST}:8443"
echo "Bearer API Token: ${API_TOKEN}"
echo "Public Key (pbk): ${PUBLIC_KEY}"
