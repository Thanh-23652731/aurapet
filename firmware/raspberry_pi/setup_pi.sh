#!/usr/bin/env bash
# =============================================================================
# AuraPet - Cài đặt camera trên Raspberry Pi (chạy trên Pi, trong thư mục ~/aurapet)
#
#   ./setup_pi.sh            cài thư viện + kiểm tra camera
#   ./setup_pi.sh --service  thêm: tự chạy camera mỗi khi Pi khởi động (systemd)
#
# Cần Raspberry Pi OS 64-bit. Mô hình chạy bằng ONNX Runtime (không cần PyTorch).
# =============================================================================
set -e
cd "$(dirname "$0")"
DIR="$(pwd)"
VENV="$HOME/aurapet-venv"

echo "== AuraPet: cài đặt camera =="

# 1. Kiểm tra hệ điều hành 64-bit
ARCH="$(uname -m)"
if [ "$ARCH" != "aarch64" ] && [ "$ARCH" != "x86_64" ]; then
  echo "[X] Hệ điều hành đang là $ARCH (32-bit). ONNX Runtime cần Raspberry Pi OS 64-bit."
  echo "    Cài lại thẻ nhớ bằng Raspberry Pi Imager, chọn 'Raspberry Pi OS (64-bit)'."
  exit 1
fi
echo "[OK] Hệ điều hành $ARCH"

# 2. Gói hệ thống (venv, công cụ xem camera, thư viện OpenCV cần)
echo "[..] Cài gói hệ thống (cần mật khẩu sudo)..."
sudo apt-get update -qq
sudo apt-get install -y -qq python3-venv python3-pip v4l-utils libgl1 libglib2.0-0 >/dev/null
echo "[OK] Gói hệ thống"

# 3. Môi trường Python riêng + thư viện (vài phút)
# ONNX Runtime + OpenCV cần khoảng 500 MB khi cài
FREE_MB=$(df -Pm "$HOME" | awk 'NR==2 {print $4}')
if [ "${FREE_MB:-0}" -lt 800 ]; then
  echo "[X] Thẻ nhớ chỉ còn ${FREE_MB} MB trống, cần ít nhất ~800 MB để cài thư viện."
  echo "    Dọn bớt: sudo apt clean && rm -rf ~/.cache/pip && sudo apt autoremove -y"
  echo "    Thẻ chưa dùng hết dung lượng: sudo raspi-config -> Advanced Options -> Expand Filesystem"
  exit 1
fi
echo "[OK] Còn ${FREE_MB} MB trống"
if [ ! -d "$VENV" ]; then
  python3 -m venv --system-site-packages "$VENV"
fi
# shellcheck disable=SC1091
source "$VENV/bin/activate"
# /tmp trên Raspberry Pi OS mới nằm trong RAM (tmpfs), không đủ chỗ giải nén PyTorch
# -> dùng thư mục tạm trên thẻ nhớ và không giữ bộ nhớ đệm pip
PIP_TMP="$HOME/.cache/aurapet-pip-tmp"
mkdir -p "$PIP_TMP"
export TMPDIR="$PIP_TMP"
pip install --upgrade pip -q --no-cache-dir
# Pi chạy mô hình bằng ONNX Runtime: KHÔNG cần PyTorch (PyTorch mới lỗi "Illegal instruction" trên Pi 4)
echo "[..] Cài ONNX Runtime, OpenCV, paho-mqtt..."
pip install -r requirements-pi.txt -q --no-cache-dir
rm -rf "$PIP_TMP"
unset TMPDIR
echo "[OK] Thư viện Python: $(python3 -c 'import onnxruntime, cv2, numpy; print("onnxruntime", onnxruntime.__version__, "| opencv", cv2.__version__, "| numpy", numpy.__version__)')"
for f in yolov8n.onnx models/cat_classifier.onnx; do
  if [ -f "$f" ]; then echo "[OK] Có $f"; else echo "[!] Thiếu $f — xuất trên máy tính bằng export_onnx.py rồi chép sang (copy_to_pi.ps1)"; fi
done
if python3 -c 'import torch' 2>/dev/null; then
  echo "    (Máy có cài PyTorch từ lần trước nhưng không còn dùng. Gỡ để lấy lại vài GB:"
  echo "     pip uninstall -y torch torchvision ultralytics ultralytics-thop triton)"
fi

# 4. Camera USB đang ở đâu
echo
echo "== Camera đang cắm =="
v4l2-ctl --list-devices 2>/dev/null || ls /dev/video* 2>/dev/null || echo "Không thấy camera nào (/dev/video*)"
echo "-> Webcam USB thường là /dev/video0 (CAMERA_INDEX=0). Nếu tên webcam nằm ở /dev/videoN khác,"
echo "   sửa CAMERA_INDEX=N trong aurapet-camera.env"

# 5. Tự chạy khi khởi động (tuỳ chọn)
if [ "$1" = "--service" ]; then
  sudo tee /etc/systemd/system/aurapet-camera.service >/dev/null <<EOF
[Unit]
Description=AuraPet camera nhan dien meo
After=network-online.target
Wants=network-online.target

[Service]
User=$USER
WorkingDirectory=$DIR
ExecStart=$DIR/run_camera.sh
Environment=PYTHONUNBUFFERED=1
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
  sudo systemctl daemon-reload
  sudo systemctl enable --now aurapet-camera
  echo
  echo "[OK] Đã bật tự chạy. Xem log: journalctl -u aurapet-camera -f"
  echo "     Tắt:  sudo systemctl stop aurapet-camera   |  Bỏ tự chạy: sudo systemctl disable aurapet-camera"
  exit 0
fi

echo
echo "== Bước tiếp theo =="
echo "  source $VENV/bin/activate"
echo "  python3 test_camera.py              # kiểm tra camera + AI"
echo "  python3 test_camera.py --live 30    # đưa mèo / ảnh mèo trên điện thoại trước camera"
echo "  ./run_camera.sh                     # chạy thật: gửi sự kiện + hình trực tiếp lên web"
echo "  ./setup_pi.sh --service             # tự chạy mỗi khi Pi bật nguồn"
