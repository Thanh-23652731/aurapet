#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AuraPet - Chuẩn bị dữ liệu ảnh để huấn luyện mô hình nhận diện từng bé mèo
(train_cat_classifier.py). Ghi ảnh vào dataset/train/<Tên>/ và dataset/val/<Tên>/.

HAI CÁCH LẤY ẢNH

1) Dữ liệu mẫu từ Oxford-IIIT Pet (để chạy thử cả quy trình khi chưa có ảnh bé nhà mình)
   Mỗi "bé" là một giống mèo trông rất khác nhau, ví dụ Siamese (lông sáng, mặt sẫm) và Bengal (vằn).
   Bộ ảnh: https://www.robots.ox.ac.uk/~vgg/data/pets/  (giấy phép CC BY-SA 4.0, ~792 MB)
   TRÁNH giống Sphynx (không lông): YOLOv8 gốc thường nhìn nhầm thành "dog" nên camera không
   phát hiện ra mèo (đo thử: chỉ 2/15 ảnh, các giống khác 13-15/15).

     python prepare_sample_dataset.py oxford
     python prepare_sample_dataset.py oxford --pets "Mimi=Siamese,Tom=Bengal" --with-other
     python prepare_sample_dataset.py oxford --archive D:/Downloads/images.tar.gz   (đã tải sẵn)

   --with-other thêm lớp "Khac" gồm mèo các giống còn lại, để mô hình biết khi nào
   KHÔNG phải bé nhà mình (nên dùng khi train bé thật).

2) Cắt ảnh từ video quay bé mèo nhà bạn (cách tốt nhất để dùng thật)
     python prepare_sample_dataset.py video --name Mimi --video mimi1.mp4 mimi2.mp4 --fps 1

Sau đó:  python train_cat_classifier.py --train
"""

import argparse
import io
import random
import shutil
import sys
import tarfile
import urllib.request
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
DEFAULT_DATASET = BASE_DIR / "dataset"
CACHE_DIR = BASE_DIR / ".cache"

OXFORD_URL = "https://www.robots.ox.ac.uk/~vgg/data/pets/data/images.tar.gz"
OXFORD_SIZE = 791_918_971  # byte, để biết file tải về có đủ không

# 12 giống mèo trong Oxford-IIIT Pet (tên file ảnh mèo viết hoa: Bengal_12.jpg; chó viết thường)
CAT_BREEDS = [
    "Abyssinian", "Bengal", "Birman", "Bombay", "British_Shorthair", "Egyptian_Mau",
    "Maine_Coon", "Persian", "Ragdoll", "Russian_Blue", "Siamese", "Sphynx",
]
OTHER_CLASS = "Khac"
IMAGE_EXT = {".jpg", ".jpeg", ".png", ".webp"}


# ---------------------------------------------------------------------------
# Tiện ích
# ---------------------------------------------------------------------------
def is_valid_jpeg(data: bytes) -> bool:
    """Bộ Oxford có vài file .jpg hỏng hoặc thực chất là PNG/GIF: chỉ giữ JPEG mở được."""
    if len(data) < 1024 or data[:2] != b"\xff\xd8":
        return False
    try:
        from PIL import Image  # có sẵn khi đã cài ultralytics
    except ImportError:
        return True
    try:
        with Image.open(io.BytesIO(data)) as im:
            im.verify()
        with Image.open(io.BytesIO(data)) as im:
            return im.mode in ("RGB", "L") and min(im.size) >= 64
    except Exception:
        return False


def prepare_class_dirs(dataset_dir: Path, names, clean: bool) -> bool:
    """Tạo dataset/{train,val}/<tên>; không ghi đè ảnh có sẵn trừ khi --clean."""
    for name in names:
        for split in ("train", "val"):
            d = dataset_dir / split / name
            if d.exists() and any(f.suffix.lower() in IMAGE_EXT for f in d.iterdir()):
                if not clean:
                    print(f"[Dừng] {d} đã có ảnh. Thêm --clean để xoá và tạo lại lớp '{name}'.")
                    return False
                shutil.rmtree(d)
            d.mkdir(parents=True, exist_ok=True)
    return True


def split_counts(n_total: int, val_ratio: float):
    n_val = max(1, round(n_total * val_ratio)) if n_total >= 2 else 0
    return n_total - n_val, n_val


def print_summary(dataset_dir: Path, names):
    print("\n[Xong] Dữ liệu đã sẵn sàng:")
    for name in names:
        tr = len(list((dataset_dir / "train" / name).glob("*")))
        va = len(list((dataset_dir / "val" / name).glob("*")))
        print(f"  • {name}: {tr} ảnh train, {va} ảnh val")
    print(f"\nBước tiếp theo:  python {Path(__file__).with_name('train_cat_classifier.py').name} --train")


# ---------------------------------------------------------------------------
# 1) Oxford-IIIT Pet
# ---------------------------------------------------------------------------
def download_oxford(target: Path) -> bool:
    if target.exists() and target.stat().st_size == OXFORD_SIZE:
        print(f"[Tải] Dùng lại file đã tải: {target}")
        return True
    target.parent.mkdir(parents=True, exist_ok=True)
    part = target.with_suffix(target.suffix + ".part")
    print(f"[Tải] {OXFORD_URL}\n       (~{OXFORD_SIZE / 1e6:.0f} MB, chỉ tải 1 lần, lưu tại {target})")
    try:
        req = urllib.request.Request(OXFORD_URL, headers={"User-Agent": "AuraPet-dataset-script"})
        with urllib.request.urlopen(req, timeout=60) as res, open(part, "wb") as out:
            total = int(res.headers.get("Content-Length") or OXFORD_SIZE)
            done, last = 0, -1
            while True:
                chunk = res.read(1 << 20)
                if not chunk:
                    break
                out.write(chunk)
                done += len(chunk)
                pct = done * 100 // total
                if pct != last:
                    last = pct
                    print(f"\r       {pct:3d}%  {done / 1e6:6.0f} / {total / 1e6:.0f} MB", end="", flush=True)
        print()
    except Exception as e:
        print(f"\n[Lỗi] Không tải được: {e}\n       Có thể tải tay rồi chạy lại với --archive <đường dẫn images.tar.gz>")
        return False
    if part.stat().st_size != OXFORD_SIZE:
        print(f"[Lỗi] File tải về thiếu ({part.stat().st_size} / {OXFORD_SIZE} byte). Chạy lại để tải lại.")
        return False
    part.replace(target)
    return True


def parse_pets(spec: str):
    """'Mimi=Sphynx,Tom=Bengal' -> {'Mimi': 'Sphynx', 'Tom': 'Bengal'}"""
    pets = {}
    lower = {b.lower(): b for b in CAT_BREEDS}
    for part in spec.split(","):
        if not part.strip():
            continue
        if "=" not in part:
            raise ValueError(f"Sai định dạng '{part}'. Dùng Tên=Giống, ví dụ Mimi=Sphynx")
        name, breed = (s.strip() for s in part.split("=", 1))
        breed_ok = lower.get(breed.replace(" ", "_").lower())
        if not name or not breed_ok:
            raise ValueError(f"Giống mèo không có trong bộ ảnh: '{breed}'. Chọn một trong: {', '.join(CAT_BREEDS)}")
        if name == OTHER_CLASS:
            raise ValueError(f"Tên '{OTHER_CLASS}' dành cho lớp mèo lạ, hãy đặt tên khác")
        if breed_ok == "Sphynx":
            print("[Cảnh báo] Sphynx (mèo không lông) thường bị YOLOv8 nhìn nhầm thành chó, camera sẽ "
                  "hiếm khi phát hiện ra bé. Nên chọn giống khác, vd. Siamese.")
        pets[name] = breed_ok
    if len(set(pets.values())) != len(pets):
        raise ValueError("Mỗi bé phải là một giống khác nhau")
    return pets


def sample_from_archive(archive: Path, class_of_breed, per_class: int, seed: int):
    """
    Đọc file nén MỘT lần, lấy ngẫu nhiên đều per_class ảnh hợp lệ cho mỗi lớp
    (reservoir sampling: không cần giữ cả bộ ảnh trong RAM).
    Chỉ đọc nội dung ảnh, KHÔNG giải nén theo đường dẫn trong file nén (tránh ghi ra ngoài thư mục).
    """
    rng = random.Random(seed)
    pools = {}   # lớp -> [(tên file, bytes)]
    seen = {}    # lớp -> số ảnh hợp lệ đã gặp
    skipped = 0
    with tarfile.open(archive, "r:gz") as tar:
        for m in tar:
            if not m.isfile():
                continue
            fname = Path(m.name).name
            if not fname.lower().endswith(".jpg") or "_" not in fname:
                continue
            breed = fname.rsplit("_", 1)[0]
            cls = class_of_breed.get(breed)
            if cls is None:
                continue
            f = tar.extractfile(m)
            data = f.read() if f else b""
            if not is_valid_jpeg(data):
                skipped += 1
                continue
            k = seen.get(cls, 0)
            seen[cls] = k + 1
            pool = pools.setdefault(cls, [])
            if len(pool) < per_class:
                pool.append((fname, data))
            else:
                j = rng.randint(0, k)
                if j < per_class:
                    pool[j] = (fname, data)
    if skipped:
        print(f"[Lọc] Bỏ {skipped} ảnh hỏng/không phải JPEG")
    return pools


def cmd_oxford(args) -> int:
    try:
        pets = parse_pets(args.pets)
    except ValueError as e:
        print(f"[Lỗi] {e}")
        return 1

    class_of_breed = {breed: name for name, breed in pets.items()}
    names = list(pets.keys())
    if args.with_other:
        for b in CAT_BREEDS:
            class_of_breed.setdefault(b, OTHER_CLASS)
        names.append(OTHER_CLASS)
    if not names:
        print("[Lỗi] Chưa chọn bé nào. Dùng --pets \"Mimi=Sphynx,Tom=Bengal\" hoặc --with-other")
        return 1
    if len(names) < 2:
        print("[Lưu ý] Chỉ tạo 1 lớp. Mô hình cần ít nhất 2 lớp: thêm bé khác (vd. cắt từ video) hoặc --with-other")

    archive = Path(args.archive) if args.archive else CACHE_DIR / "oxford_pets_images.tar.gz"
    if args.archive:
        if not archive.exists():
            print(f"[Lỗi] Không thấy file {archive}")
            return 1
    elif not download_oxford(archive):
        return 1

    dataset_dir = Path(args.dataset)
    if not prepare_class_dirs(dataset_dir, names, args.clean):
        return 1

    per_class = args.train + args.val
    print("[Đọc] Đang lọc ảnh trong file nén (mất khoảng 1 phút)...")
    pools = sample_from_archive(archive, class_of_breed, per_class, args.seed)

    rng = random.Random(args.seed)
    for name in names:
        items = pools.get(name, [])
        if not items:
            print(f"[Lỗi] Không lấy được ảnh nào cho lớp '{name}'")
            return 1
        rng.shuffle(items)
        n_val = min(args.val, max(1, len(items) // 5))
        for i, (fname, data) in enumerate(items):
            split = "val" if i < n_val else "train"
            (dataset_dir / split / name / fname).write_bytes(data)
        src = pets.get(name, "các giống còn lại")
        print(f"  • {name} ← {src}: {len(items) - n_val} train / {n_val} val")

    print_summary(dataset_dir, names)
    print("Lưu ý: đây là ảnh mẫu khác giống nhau, nên độ chính xác sẽ cao hơn khi phân biệt 2 bé cùng giống ngoài đời.")
    return 0


# ---------------------------------------------------------------------------
# 2) Cắt ảnh từ video
# ---------------------------------------------------------------------------
def cmd_video(args) -> int:
    try:
        import cv2
    except ImportError:
        print("[Lỗi] Thiếu OpenCV. Cài: pip install opencv-python")
        return 1

    name = args.name.strip()
    if not name:
        print("[Lỗi] Cần --name")
        return 1
    dataset_dir = Path(args.dataset)
    if not prepare_class_dirs(dataset_dir, [name], args.clean):
        return 1

    frames = []  # (tên file, ảnh)
    for vpath in args.video:
        cap = cv2.VideoCapture(vpath)
        if not cap.isOpened():
            print(f"[Bỏ qua] Không mở được video {vpath}")
            continue
        src_fps = cap.get(cv2.CAP_PROP_FPS) or 30
        step = max(1, round(src_fps / max(0.1, args.fps)))
        idx = kept = 0
        stem = Path(vpath).stem
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            if idx % step == 0:
                h, w = frame.shape[:2]
                scale = args.max_side / max(h, w)
                if scale < 1:
                    frame = cv2.resize(frame, (int(w * scale), int(h * scale)), interpolation=cv2.INTER_AREA)
                # Bỏ khung bị nhoè (bé chạy nhanh / máy rung)
                gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
                if cv2.Laplacian(gray, cv2.CV_64F).var() >= args.min_sharpness:
                    frames.append((f"{stem}_{idx:06d}.jpg", frame))
                    kept += 1
            idx += 1
        cap.release()
        print(f"  • {vpath}: lấy {kept} khung")

    if len(frames) < 10:
        print(f"[Lỗi] Chỉ có {len(frames)} ảnh. Quay thêm video hoặc tăng --fps")
        return 1

    random.Random(args.seed).shuffle(frames)
    n_train, n_val = split_counts(len(frames), args.val_ratio)
    for i, (fname, frame) in enumerate(frames):
        split = "val" if i < n_val else "train"
        cv2.imwrite(str(dataset_dir / split / name / fname), frame, [cv2.IMWRITE_JPEG_QUALITY, 92])

    print_summary(dataset_dir, [name])
    print(f"Lưu ý: cần ít nhất 2 lớp. Nếu chỉ có 1 bé, chạy thêm:  python {Path(__file__).name} oxford --pets \"\" --with-other")
    return 0


# ---------------------------------------------------------------------------
def main() -> int:
    # Console Windows cũ không in được tiếng Việt khi chuyển hướng ra file
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass
    p = argparse.ArgumentParser(description="AuraPet - chuẩn bị ảnh để huấn luyện nhận diện từng bé mèo")
    p.add_argument("--dataset", default=str(DEFAULT_DATASET), help="Thư mục dataset (mặc định firmware/raspberry_pi/dataset)")
    p.add_argument("--clean", action="store_true", help="Xoá ảnh cũ của các lớp sắp tạo")
    p.add_argument("--seed", type=int, default=42, help="Hạt giống ngẫu nhiên (cùng seed = cùng bộ ảnh)")
    sub = p.add_subparsers(dest="cmd", required=True)

    o = sub.add_parser("oxford", help="Lấy ảnh mẫu từ bộ Oxford-IIIT Pet")
    o.add_argument("--pets", default="Mimi=Siamese,Tom=Bengal",
                   help=f"Tên=Giống, cách nhau dấu phẩy. Giống: {', '.join(CAT_BREEDS)}")
    o.add_argument("--with-other", action="store_true", help=f"Thêm lớp '{OTHER_CLASS}' (mèo các giống khác)")
    o.add_argument("--train", type=int, default=60, help="Số ảnh train mỗi lớp (mặc định 60)")
    o.add_argument("--val", type=int, default=15, help="Số ảnh val mỗi lớp (mặc định 15)")
    o.add_argument("--archive", help="Đường dẫn images.tar.gz đã tải sẵn (bỏ qua bước tải)")
    o.set_defaults(func=cmd_oxford)

    v = sub.add_parser("video", help="Cắt ảnh từ video quay bé mèo nhà bạn")
    v.add_argument("--name", required=True, help="Tên bé (trùng tên trong app AuraPet)")
    v.add_argument("--video", nargs="+", required=True, help="Một hoặc nhiều file video")
    v.add_argument("--fps", type=float, default=1.0, help="Số ảnh lấy mỗi giây video (mặc định 1)")
    v.add_argument("--val-ratio", type=float, default=0.2, help="Tỉ lệ ảnh dùng để kiểm thử (mặc định 0.2)")
    v.add_argument("--max-side", type=int, default=640, help="Thu nhỏ ảnh về cạnh dài tối đa (mặc định 640)")
    v.add_argument("--min-sharpness", type=float, default=60.0, help="Bỏ khung nhoè (độ nét Laplacian < mức này)")
    v.set_defaults(func=cmd_video)

    args = p.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
