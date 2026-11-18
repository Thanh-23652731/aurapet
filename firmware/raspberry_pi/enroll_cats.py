#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AuraPet - Đăng ký từng bé mèo cho Re-ID bằng Feature Embedding (DINOv2), KHÔNG cần train lại mô hình.

Chạy được trên máy tính lẫn Raspberry Pi (chỉ cần onnxruntime + opencv + numpy).
Cần có models/cat_embedder.onnx (xuất bằng export_embedder.py) và yolov8n.onnx (cắt đúng con mèo).

1) Đăng ký từ thư mục ảnh: mỗi bé một thư mục, tên thư mục = TÊN BÉ trên web (để chia khẩu phần đúng)
       gallery/Mimi/*.jpg   gallery/Tom/*.jpg   gallery/Khac/*.jpg (mèo lạ - tuỳ chọn, giúp loại mèo hàng xóm)
       python enroll_cats.py --src gallery
   Không có thư mục gallery/ thì dùng dataset/train.

2) Đăng ký trực tiếp bằng camera trên Pi (tắt dịch vụ camera trước: sudo systemctl stop aurapet-camera):
       python enroll_cats.py --camera --name Mimi --count 40
   Cho bé đi qua đi lại trước camera ~1 phút, đủ góc nghiêng/đứng/ngồi. Ảnh lưu vào gallery/Mimi/ và gộp vào kho.

3) Kiểm tra độ chính xác + tự chọn ngưỡng:  python enroll_cats.py --eval dataset/val
   (tự chạy sau bước 1 nếu có dataset/val)

Kết quả: models/cat_gallery.npz -> chép sang Pi cùng cat_embedder.onnx (scripts/copy_to_pi.ps1).
"""
import argparse
import os
import sys
import time
from pathlib import Path

import cv2
import numpy as np

import onnx_infer

BASE = Path(__file__).resolve().parent
EMBEDDER = BASE / "models" / "cat_embedder.onnx"
GALLERY = BASE / "models" / "cat_gallery.npz"
DETECTOR = BASE / "yolov8n.onnx"
IMG_EXT = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
CROP_MARGIN = 0.1  # Re-ID: cắt sát con mèo hơn classifier cũ để nền (sàn nhà, bát ăn) không lẫn vào đặc trưng


def _utf8():
    for s in (sys.stdout, sys.stderr):
        try:
            s.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass


def _read(path):
    data = np.fromfile(str(path), dtype=np.uint8)  # đọc được cả đường dẫn có dấu tiếng Việt trên Windows
    return cv2.imdecode(data, cv2.IMREAD_COLOR) if data.size else None


class Cropper:
    """Cắt con mèo lớn nhất trong ảnh bằng YOLOv8; không thấy mèo thì trả None (hoặc cả ảnh nếu whole=True)."""

    def __init__(self, detector_path, conf=0.3, whole=False):
        self.det = None
        if detector_path and Path(detector_path).exists():
            self.det = onnx_infer.OnnxCatDetector(str(detector_path))
        self.conf, self.whole = conf, whole

    def __call__(self, img):
        if self.det is None:
            return img
        cats = self.det.detect_cats(img, self.conf)
        if not cats:
            return img if self.whole else None
        x1, y1, x2, y2, _ = max(cats, key=lambda c: (c[2] - c[0]) * (c[3] - c[1]))
        return onnx_infer.crop_with_margin(img, x1, y1, x2, y2, CROP_MARGIN)


def embed_folder(root, emb, crop):
    names, vecs, skipped = [], [], 0
    for d in sorted(p for p in Path(root).iterdir() if p.is_dir()):
        n_ok = 0
        for f in sorted(d.iterdir()):
            if f.suffix.lower() not in IMG_EXT:
                continue
            img = _read(f)
            c = crop(img) if img is not None else None
            if c is None or c.size == 0:
                skipped += 1
                continue
            names.append(d.name)
            vecs.append(emb.embed(c, flip_tta=True))
            n_ok += 1
        tag = " (mèo lạ - mẫu đối chứng)" if onnx_infer.is_negative_name(d.name) else ""
        print(f"  {d.name:12s} {n_ok:4d} ảnh{tag}")
    if skipped:
        print(f"  (bỏ qua {skipped} ảnh không thấy mèo)")
    return names, vecs


def evaluate(gallery, root, emb, crop, quiet=False):
    """Đo độ chính xác trên thư mục ảnh kiểm tra và chọn ngưỡng tốt nhất. Trả về ngưỡng đề xuất."""
    rows = []  # (tên thật, điểm từng tên)
    for d in sorted(p for p in Path(root).iterdir() if p.is_dir()):
        for f in sorted(d.iterdir()):
            if f.suffix.lower() not in IMG_EXT:
                continue
            img = _read(f)
            c = crop(img) if img is not None else None
            if c is not None and c.size:
                rows.append((d.name, gallery.scores(emb.embed(c))))
    if not rows:
        print("[!] Không có ảnh kiểm tra")
        return gallery.threshold

    def run(th):
        g = gallery
        old = g.threshold
        g.threshold = th
        ok = 0
        wrong_feed = 0  # cho nhầm bé (nặng nhất: sai khẩu phần) hoặc cho mèo lạ ăn
        for truth, sc in rows:
            top = sorted(sc.items(), key=lambda kv: kv[1], reverse=True)
            name, best = top[0]
            second = top[1][1] if len(top) > 1 else -1.0
            pred = "" if (onnx_infer.is_negative_name(name) or best < th or best - second < g.margin) else name
            want = "" if onnx_infer.is_negative_name(truth) else truth
            ok += pred == want
            wrong_feed += pred != "" and pred != want
        g.threshold = old
        return ok / len(rows), wrong_feed

    best_th, best_key = gallery.threshold, None
    for th in np.arange(0.30, 0.91, 0.01):
        acc, wrong = run(th)
        # Cho nhầm bé (sai khẩu phần) hoặc cho mèo lạ ăn bị phạt nặng gấp 3 lần một lần không nhận ra
        key = (acc - 3 * wrong / len(rows), -abs(th - 0.6))
        if best_key is None or key > best_key:
            best_th, best_key = float(round(th, 2)), key
    acc, wrong = run(best_th)
    if not quiet:
        print(f"[KQ] {len(rows)} ảnh kiểm tra: đúng {acc * 100:.1f}%, nhận nhầm {wrong} ảnh, ngưỡng đề xuất {best_th:.2f}")
    return best_th


def capture(args, emb, crop):
    """Chụp ảnh bé từ camera, mỗi ảnh cách nhau ít nhất 0.5 s và phải khác ảnh trước (đủ góc nhìn)."""
    out = BASE / "gallery" / args.name
    out.mkdir(parents=True, exist_ok=True)
    cap = cv2.VideoCapture(int(os.getenv("CAMERA_INDEX", "0")))
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, int(os.getenv("CAMERA_WIDTH", "640")))
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, int(os.getenv("CAMERA_HEIGHT", "480")))
    if not cap.isOpened():
        sys.exit("Không mở được camera (dịch vụ aurapet-camera còn chạy? sudo systemctl stop aurapet-camera)")
    print(f"[..] Đang chụp {args.count} ảnh cho '{args.name}'. Cho bé đi lại trước camera. Ctrl+C để dừng sớm.")
    saved, last_v, last_t = [], None, 0.0
    try:
        while len(saved) < args.count:
            ok, frame = cap.read()
            if not ok:
                time.sleep(0.1)
                continue
            if time.time() - last_t < 0.5:
                continue
            c = crop(frame)
            if c is None or min(c.shape[:2]) < 48:
                continue
            v = emb.embed(c)
            if last_v is not None and float(v @ last_v) > 0.97:
                continue  # gần như y hệt ảnh trước -> chờ bé đổi tư thế
            f = out / f"{args.name}_{int(time.time() * 1000)}.jpg"
            cv2.imencode(".jpg", c)[1].tofile(str(f))
            saved.append(f)
            last_v, last_t = v, time.time()
            print(f"  [{len(saved)}/{args.count}] {f.name}")
    except KeyboardInterrupt:
        pass
    cap.release()
    return out.parent


def main():
    _utf8()
    p = argparse.ArgumentParser(description="Đăng ký từng bé mèo cho Re-ID (DINOv2 embedding)")
    p.add_argument("--src", help="Thư mục ảnh: <src>/<Tên bé>/*.jpg (mặc định gallery/ hoặc dataset/train)")
    p.add_argument("--camera", action="store_true", help="Chụp trực tiếp từ camera rồi đăng ký")
    p.add_argument("--name", help="Tên bé khi chụp bằng --camera (trùng tên trên web)")
    p.add_argument("--count", type=int, default=40)
    p.add_argument("--eval", nargs="?", const="dataset/val", help="Đo độ chính xác trên thư mục ảnh kiểm tra")
    p.add_argument("--whole", action="store_true", help="Ảnh đã cắt sẵn con mèo: không chạy YOLO, dùng cả ảnh")
    p.add_argument("--embedder", default=str(EMBEDDER))
    p.add_argument("--out", default=str(GALLERY))
    args = p.parse_args()

    if not Path(args.embedder).exists():
        sys.exit(f"Thiếu {args.embedder}. Chạy trên máy tính: python export_embedder.py")
    emb = onnx_infer.OnnxCatEmbedder(args.embedder)
    crop = Cropper(None if args.whole else DETECTOR, whole=args.whole)
    if crop.det is None and not args.whole:
        print("[!] Không có yolov8n.onnx: dùng nguyên ảnh (nên cắt sẵn con mèo)")
    print(f"[AI] Embedder {emb.meta.get('model', '?')} ảnh vào {emb.w}x{emb.h}")

    if args.eval and not args.src and not args.camera:
        g = onnx_infer.CatGallery.load(args.out)
        th = evaluate(g, BASE / args.eval if not Path(args.eval).is_absolute() else args.eval, emb, crop)
        g.save(args.out, threshold=th)
        print(f"[OK] Đã lưu ngưỡng {th:.2f} vào {args.out}")
        return

    if args.camera:
        if not args.name:
            sys.exit("Cần --name <Tên bé>")
        src = capture(args, emb, crop)
        crop = Cropper(None, whole=True)  # ảnh chụp đã được cắt sẵn
    else:
        src = Path(args.src) if args.src else (BASE / "gallery" if (BASE / "gallery").is_dir() else BASE / "dataset" / "train")
    if not src.is_absolute():
        src = BASE / src
    print(f"[..] Đăng ký từ {src}")
    names, vecs = embed_folder(src, emb, crop)
    if not names:
        sys.exit("Không có ảnh nào hợp lệ")
    g = onnx_infer.CatGallery(names, np.stack(vecs))
    print(f"[OK] Các bé: {', '.join(g.pets) or '(chưa có)'}")

    val = Path(args.eval) if args.eval else BASE / "dataset" / "val"
    if not val.is_absolute():
        val = BASE / val
    th = g.threshold
    if val.is_dir() and not args.camera:
        th = evaluate(g, val, emb, Cropper(None if args.whole else DETECTOR, whole=args.whole))
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    g.save(args.out, threshold=th)
    print(f"[OK] Đã lưu {len(names)} vector -> {args.out} (ngưỡng {th:.2f})")
    print("Chép sang Pi: powershell -File ..\\..\\scripts\\copy_to_pi.ps1 rồi khởi động lại dịch vụ camera")


if __name__ == "__main__":
    main()
