#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AuraPet - Xuất mô hình sang ONNX để Raspberry Pi chạy bằng ONNX Runtime (không cần PyTorch).
Chạy trên MÁY TÍNH (đã cài ultralytics + onnx + onnxslim), không chạy trên Pi:

    pip install ultralytics onnx onnxslim
    python export_onnx.py              # yolov8n.onnx (640) + models/cat_classifier.onnx (224)
    python export_onnx.py --imgsz 416  # nhẹ hơn ~2 lần nếu Pi chạy chậm (tầm nhìn gần hơn)

Sau đó chép sang Pi:  powershell -File ..\\..\\scripts\\copy_to_pi.ps1 -PiHost <IP> -User <tài khoản>
"""
import argparse
import shutil
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parent


def main():
    for s in (sys.stdout, sys.stderr):
        try:
            s.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass
    p = argparse.ArgumentParser(description="Xuất YOLOv8 + cat_classifier sang ONNX")
    p.add_argument("--imgsz", type=int, default=640,
                   help="Kích thước ảnh cho detector: 640 (mặc định) thấy mèo ở xa gấp ~3 lần 320; "
                        "416 nếu Pi chạy chậm")
    args = p.parse_args()
    try:
        from ultralytics import YOLO
    except ImportError:
        sys.exit("Cần cài: pip install ultralytics onnx onnxslim")

    jobs = [(BASE / "yolov8n.pt" if (BASE / "yolov8n.pt").exists() else Path("yolov8n.pt"), args.imgsz, BASE / "yolov8n.onnx")]
    cls = BASE / "models" / "cat_classifier.pt"
    if cls.exists():
        jobs.append((cls, 224, BASE / "models" / "cat_classifier.onnx"))
    else:
        print("[!] Chưa có models/cat_classifier.pt: chỉ xuất detector (camera chỉ biết 'có mèo')")

    for src, size, dst in jobs:
        out = Path(YOLO(str(src)).export(format="onnx", imgsz=size, dynamic=False, simplify=True, opset=17))
        if out.resolve() != dst.resolve():
            shutil.move(str(out), dst)
        print(f"[OK] {src.name} -> {dst} ({dst.stat().st_size / 1e6:.1f} MB, ảnh vào {size}x{size})")
    print("\nXong. Chép sang Pi bằng scripts/copy_to_pi.ps1 (sẽ tự chép các file .onnx).")


if __name__ == "__main__":
    main()
