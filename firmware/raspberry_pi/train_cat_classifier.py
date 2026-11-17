#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AuraPet - Script tự động huấn luyện nhận diện danh tính từng bé mèo (Cat Recognition / Re-ID)
Sử dụng YOLOv8 Nano Classification (yolov8n-cls).

HƯỚNG DẪN SỬ DỤNG:
0. Cách nhanh: prepare_sample_dataset.py tự tạo dữ liệu (ảnh mẫu Oxford-IIIT Pet hoặc cắt từ video bé nhà bạn):
   python prepare_sample_dataset.py oxford --pets "Mimi=Siamese,Tom=Bengal"
   python prepare_sample_dataset.py video --name Mimi --video mimi.mp4

1. Chuẩn bị thư mục dữ liệu thủ công:
   python train_cat_classifier.py --setup --names "Mimi,Tom"
   (Lệnh này sẽ tạo thư mục dataset/train và dataset/val cho từng bé).

2. Bỏ ảnh chụp các bé vào thư mục tương ứng:
   - dataset/train/<Tên_Bé>/  (Khoảng 30 - 50 ảnh chụp các góc độ)
   - dataset/val/<Tên_Bé>/    (Khoảng 10 ảnh để kiểm thử)

3. Chạy huấn luyện (có thể chạy trên laptop hoặc Google Colab):
   python train_cat_classifier.py --train --epochs 40

4. Mô hình sau khi train xong sẽ tự động được lưu và xuất sang định dạng ONNX tại:
   firmware/raspberry_pi/models/cat_classifier.pt
   firmware/raspberry_pi/models/cat_classifier.onnx
"""

import argparse
import os
import shutil
import sys
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
DEFAULT_DATASET = BASE_DIR / "dataset"
OUTPUT_MODELS_DIR = BASE_DIR / "models"


def setup_dataset(names_str, dataset_dir=DEFAULT_DATASET):
    """Tạo cấu trúc thư mục phân loại cho các bé mèo."""
    names = [n.strip() for n in names_str.split(",") if n.strip()]
    if not names:
        print("[Lỗi] Vui lòng nhập ít nhất 1 tên bé mèo (ví dụ: --names 'Mimi,Tom')")
        return False

    for split in ["train", "val"]:
        for name in names:
            p = dataset_dir / split / name
            p.mkdir(parents=True, exist_ok=True)

    print(f"\n[Thành công] Đã tạo cây thư mục tại: {dataset_dir}")
    print("Vui lòng chép ảnh vào các thư mục sau:")
    for name in names:
        print(f"  • {dataset_dir / 'train' / name} (ảnh huấn luyện, 30-50 tấm)")
        print(f"  • {dataset_dir / 'val' / name}   (ảnh kiểm thử, 10 tấm)")
    print("\nSau khi copy ảnh xong, hãy chạy: python train_cat_classifier.py --train")
    return True


def check_dataset(dataset_dir=DEFAULT_DATASET):
    """Kiểm tra số lượng ảnh trước khi train."""
    train_dir = dataset_dir / "train"
    val_dir = dataset_dir / "val"
    if not train_dir.exists() or not val_dir.exists():
        print(f"[Lỗi] Chưa thấy thư mục dữ liệu tại {dataset_dir}. Hãy chạy --setup trước!")
        return False

    classes = [d.name for d in train_dir.iterdir() if d.is_dir()]
    if len(classes) < 2:
        print(f"[Cảnh báo] Cần ít nhất 2 lớp (2 bé mèo hoặc 1 bé mèo + 1 thư mục 'Khac') để phân loại.")
        print(f"Hiện tại chỉ thấy: {classes}")
        return False

    valid_extensions = {".jpg", ".jpeg", ".png", ".webp"}
    for c in classes:
        train_imgs = [f for f in (train_dir / c).iterdir() if f.suffix.lower() in valid_extensions]
        val_imgs = [f for f in (val_dir / c).iterdir() if f.suffix.lower() in valid_extensions]
        print(f"  • Bé [{c}]: {len(train_imgs)} ảnh train, {len(val_imgs)} ảnh val")
        if len(train_imgs) == 0:
            print(f"[Lỗi] Thư mục {train_dir / c} chưa có ảnh nào!")
            return False

    return True


def train_model(epochs=40, imgsz=224, batch=16, dataset_dir=DEFAULT_DATASET):
    """Tiến hành huấn luyện mô hình YOLOv8n-cls và xuất sang ONNX."""
    try:
        from ultralytics import YOLO
    except ImportError:
        print("[Lỗi] Thiếu thư viện ultralytics. Hãy chạy: pip install ultralytics")
        return False

    if not check_dataset(dataset_dir):
        return False

    print(f"\n[AI] Bắt đầu huấn luyện mô hình phân loại mèo (YOLOv8 Nano, {epochs} epochs) ...")
    model = YOLO("yolov8n-cls.pt")

    # Huấn luyện
    results = model.train(
        data=str(dataset_dir),
        epochs=epochs,
        imgsz=imgsz,
        batch=batch,
        project=str(BASE_DIR / "runs"),
        name="cat_classifier",
        exist_ok=True,
        verbose=True
    )

    # Lấy đường dẫn model tốt nhất
    best_pt = BASE_DIR / "runs" / "cat_classifier" / "weights" / "best.pt"
    if not best_pt.exists():
        print("[Lỗi] Không tìm thấy file trọng số sau khi huấn luyện.")
        return False

    OUTPUT_MODELS_DIR.mkdir(parents=True, exist_ok=True)
    target_pt = OUTPUT_MODELS_DIR / "cat_classifier.pt"
    shutil.copy(best_pt, target_pt)
    print(f"\n[Thành công] Đã lưu mô hình PyTorch tại: {target_pt}")

    # Xuất sang ONNX để tối ưu CPU cho Raspberry Pi
    print("[AI] Đang xuất mô hình sang định dạng ONNX cho Raspberry Pi...")
    try:
        trained_model = YOLO(str(target_pt))
        onnx_file = trained_model.export(format="onnx", imgsz=imgsz)
        target_onnx = OUTPUT_MODELS_DIR / "cat_classifier.onnx"
        if Path(onnx_file).exists() and Path(onnx_file) != target_onnx:
            shutil.copy(onnx_file, target_onnx)
        print(f"[Thành công] Đã xuất mô hình ONNX tại: {target_onnx}")
    except Exception as e:
        print(f"[Cảnh báo] Chưa xuất được ONNX (vẫn dùng được file .pt): {e}")

    print("\n[Hoàn tất] Khởi động lại cat_detection_gateway.py để sử dụng tính năng nhận diện danh tính mèo!")
    return True


def test_model(image_path, model_path=None):
    """Kiểm tra mô hình đã train trên 1 ảnh bất kỳ."""
    try:
        from ultralytics import YOLO
    except ImportError:
        print("[Lỗi] Thiếu thư viện ultralytics.")
        return

    m_path = model_path or (OUTPUT_MODELS_DIR / "cat_classifier.pt")
    if not Path(m_path).exists():
        print(f"[Lỗi] Không tìm thấy mô hình tại {m_path}. Hãy chạy train trước!")
        return

    model = YOLO(str(m_path))
    res = model.predict(source=image_path, imgsz=224)
    if res and res[0].probs is not None:
        top1 = int(res[0].probs.top1)
        conf = float(res[0].probs.top1conf)
        name = res[0].names[top1]
        print(f"\n=> Kết quả nhận diện: Bé [{name}] với độ tin cậy {conf:.2%}")


def main():
    parser = argparse.ArgumentParser(description="AuraPet - Huấn luyện mô hình nhận diện từng bé mèo")
    parser.add_argument("--setup", action="store_true", help="Tạo thư mục dataset")
    parser.add_argument("--names", type=str, default="Mimi,Tom", help="Danh sách tên các bé mèo (ngăn cách bởi dấu phẩy)")
    parser.add_argument("--train", action="store_true", help="Huấn luyện mô hình")
    parser.add_argument("--epochs", type=int, default=40, help="Số lượt huấn luyện (mặc định 40)")
    parser.add_argument("--imgsz", type=int, default=224, help="Kích thước ảnh đầu vào (mặc định 224)")
    parser.add_argument("--test", type=str, help="Đường dẫn ảnh để test mô hình")

    args = parser.parse_args()

    if args.setup:
        setup_dataset(args.names)
    elif args.train:
        train_model(epochs=args.epochs, imgsz=args.imgsz)
    elif args.test:
        test_model(args.test)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
