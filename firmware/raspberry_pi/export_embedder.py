#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AuraPet - Xuất mô hình trích đặc trưng (Feature Embedding) DINOv2 sang ONNX cho Re-ID từng bé mèo.
Chạy trên MÁY TÍNH (cần torch + onnx, có Internet lần đầu để tải trọng số), không chạy trên Pi:

    python export_embedder.py                 # DINOv2 ViT-S/14, ảnh vào 224 -> models/cat_embedder.onnx
    python export_embedder.py --imgsz 168     # nhanh hơn ~1.8 lần trên Pi, kém chính xác hơn chút

Mô hình ONNX nhận ảnh RGB [1, 3, S, S] giá trị 0..1 (giống classifier cũ), tự chuẩn hoá ImageNet bên trong,
trả về vector ĐÃ chuẩn hoá L2 (cosine similarity = tích vô hướng): ghép [CLS token ; trung bình patch token]
= 768 chiều với ViT-S. CLS giữ hình dáng tổng thể, trung bình patch giữ màu lông/hoa văn -> phân biệt cá thể tốt hơn.

Sau đó: python enroll_cats.py để đăng ký ảnh từng bé, rồi chép sang Pi bằng scripts/copy_to_pi.ps1.
"""
import argparse
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parent
MODELS = {"s": "dinov2_vits14", "b": "dinov2_vitb14"}


def main():
    for s in (sys.stdout, sys.stderr):
        try:
            s.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass
    p = argparse.ArgumentParser(description="Xuất DINOv2 sang ONNX (trích đặc trưng Re-ID mèo)")
    p.add_argument("--imgsz", type=int, default=224, help="Bội số của 14: 224 (mặc định), 168 nhanh hơn")
    p.add_argument("--size", choices=sorted(MODELS), default="s",
                   help="s = ViT-S/14 22M tham số (mặc định, hợp Pi 4); b = ViT-B/14 86M (chậm ~4 lần)")
    p.add_argument("--out", default=str(BASE / "models" / "cat_embedder.onnx"))
    args = p.parse_args()
    if args.imgsz % 14:
        sys.exit("--imgsz phải là bội số của 14 (vd 168, 224)")

    import torch
    import torch.nn.functional as F

    name = MODELS[args.size]
    print(f"[..] Tải {name} (torch.hub facebookresearch/dinov2) ...")
    backbone = torch.hub.load("facebookresearch/dinov2", name, trust_repo=True).eval()

    # Nội suy sẵn bảng vị trí cho đúng kích thước ảnh: phép nội suy bicubic antialias trong forward
    # của DINOv2 không xuất được sang ONNX, nên tính trước một lần rồi thay bằng hằng số.
    n = args.imgsz // 14
    with torch.no_grad():
        dummy = torch.zeros(1, 3, args.imgsz, args.imgsz)
        fixed_pos = backbone.interpolate_pos_encoding(backbone.patch_embed(dummy), args.imgsz, args.imgsz).clone()
    assert fixed_pos.shape[1] == 1 + n * n, fixed_pos.shape
    backbone.interpolate_pos_encoding = lambda x, w, h: fixed_pos

    class Embedder(torch.nn.Module):
        def __init__(self, bb):
            super().__init__()
            self.bb = bb
            self.register_buffer("mean", torch.tensor([0.485, 0.456, 0.406]).view(1, 3, 1, 1))
            self.register_buffer("std", torch.tensor([0.229, 0.224, 0.225]).view(1, 3, 1, 1))

        def forward(self, x):
            x = (x - self.mean) / self.std
            f = self.bb.forward_features(x)
            cls = F.normalize(f["x_norm_clstoken"], dim=-1)
            patch = F.normalize(f["x_norm_patchtokens"].mean(dim=1), dim=-1)
            return F.normalize(torch.cat([cls, patch], dim=-1), dim=-1)

    model = Embedder(backbone).eval()
    x = torch.rand(1, 3, args.imgsz, args.imgsz)
    with torch.no_grad():
        ref = model(x).numpy()
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    torch.onnx.export(model, (x,), str(out), input_names=["images"], output_names=["embedding"],
                      opset_version=17, dynamo=False)

    import onnx
    m = onnx.load(str(out))
    meta = {"model": name, "imgsz": str(args.imgsz), "dim": str(ref.shape[1]), "kind": "cat_embedder"}
    for k, v in meta.items():
        e = m.metadata_props.add()
        e.key, e.value = k, v
    onnx.save(m, str(out))

    try:
        import numpy as np
        import onnxruntime as ort
        got = ort.InferenceSession(str(out), providers=["CPUExecutionProvider"]).run(None, {"images": x.numpy()})[0]
        print(f"[OK] Sai khác PyTorch/ONNX: {float(np.abs(got - ref).max()):.2e}")
    except ImportError:
        print("[i] Không có onnxruntime để kiểm tra lại file ONNX (không sao)")
    print(f"[OK] {name} -> {out} ({out.stat().st_size / 1e6:.1f} MB, ảnh vào {args.imgsz}, vector {ref.shape[1]} chiều)")
    print("Tiếp theo: python enroll_cats.py   (đăng ký ảnh từng bé -> models/cat_gallery.npz)")


if __name__ == "__main__":
    main()
