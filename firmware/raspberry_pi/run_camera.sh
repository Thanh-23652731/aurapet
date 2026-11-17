#!/usr/bin/env bash
# =============================================================================
# AuraPet - Chạy camera nhận diện mèo trên Raspberry Pi
# Đọc cấu hình từ aurapet-camera.env (MQTT, CAMERA_KEY, AURAPET_SERVER...) rồi chạy gateway.
# Dừng: Ctrl+C (hoặc sudo systemctl stop aurapet-camera nếu đã bật tự chạy)
# =============================================================================
cd "$(dirname "$0")"
VENV="$HOME/aurapet-venv"

if [ ! -f aurapet-camera.env ]; then
  echo "[X] Thiếu aurapet-camera.env (chép từ máy tính bằng scripts/copy_to_pi.ps1)"
  exit 1
fi
set -a
# shellcheck disable=SC1091
source ./aurapet-camera.env
set +a

if [ -f "$VENV/bin/activate" ]; then
  # shellcheck disable=SC1091
  source "$VENV/bin/activate"
else
  echo "[!] Chưa có $VENV — chạy ./setup_pi.sh trước"
fi

echo "AuraPet camera -> máy chủ ${AURAPET_SERVER:-<chưa đặt>}, broker ${MQTT_HOST}:${MQTT_PORT}"
exec python3 cat_detection_gateway.py
