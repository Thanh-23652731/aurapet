# -*- coding: utf-8 -*-
"""
AuraPet - Đăng ký nhận diện bé từ web (Re-ID), phía Raspberry Pi.

1) Chụp bé trước camera: chủ bấm "Đưa bé ra trước camera" trên web -> backend trả header X-Enroll-Pet trong phản hồi
   của lần gửi khung hình kế tiếp -> Pi cắt con mèo từ khung hình GỐC (không có khung vẽ), mỗi tư thế một ảnh,
   gửi lên /api/v1/camera/reid-shot cho tới khi đủ số ảnh.
2) Đồng bộ kho ảnh mẫu: mỗi SYNC_INTERVAL giây hỏi /api/v1/camera/reid-gallery; có ảnh mới thì tải về,
   cắt con mèo (ảnh tải lên), tính vector đặc trưng DINOv2, gộp với models/cat_gallery.npz rồi thay kho đang dùng
   mà không phải khởi động lại. Báo lại backend ảnh nào dùng được (web hiện "đã học" / "không thấy mèo").
Vector đã tính được lưu trong reid_cache/ để khởi động lại không phải tính lại.
"""

import http.client
import json
import queue
import threading
import time
from pathlib import Path
from urllib.parse import urlsplit

import cv2
import numpy as np

import onnx_infer

BASE = Path(__file__).resolve().parent
CACHE_DIR = BASE / "reid_cache"
SYNC_INTERVAL = 20          # giây giữa hai lần hỏi danh sách ảnh mẫu
REPORT_EVERY = 60           # giây: báo "Pi vẫn đồng bộ" kể cả khi không có gì mới
SHOT_GAP = 0.8              # giây tối thiểu giữa hai ảnh chụp
SHOT_SAME = 0.95            # cosine với ảnh trước cao hơn mức này = bé chưa đổi tư thế -> chờ
SHOT_MIN_SIDE = 64          # px: con mèo nhỏ hơn thế là quá xa
CROP_MARGIN = 0.1


class Api:
    """Gọi backend với X-Camera-Key (mỗi lần một kết nối, chỉ dùng cho việc thưa thớt)."""

    def __init__(self, server, key):
        u = urlsplit(server)
        self.https = u.scheme == "https"
        self.host, self.port, self.base = u.hostname, u.port, (u.path or "").rstrip("/")
        self.key = key

    def call(self, method, path, body=None, headers=None, timeout=10):
        cls = http.client.HTTPSConnection if self.https else http.client.HTTPConnection
        conn = cls(self.host, self.port, timeout=timeout)
        h = {"X-Camera-Key": self.key}
        h.update(headers or {})
        try:
            conn.request(method, self.base + path, body=body, headers=h)
            res = conn.getresponse()
            data = res.read()
        finally:
            conn.close()
        if res.status >= 300:
            raise RuntimeError(f"HTTP {res.status} {path}")
        return data

    def json(self, method, path, obj=None):
        body = None if obj is None else json.dumps(obj, ensure_ascii=False).encode("utf-8")
        data = self.call(method, path, body, {"Content-Type": "application/json"} if body else None)
        d = json.loads(data.decode("utf-8")) if data else {}
        return d.get("data", d) if isinstance(d, dict) else d


# ------------------------------------------------------------------ chụp bé trước camera
class Enroller(threading.Thread):
    """Nhận phiên chụp từ header phản hồi khung hình, chọn ảnh đủ khác nhau rồi gửi lên backend."""

    def __init__(self, api, embedder_fn, on_done):
        super().__init__(daemon=True)
        self.api = api
        self.embedder_fn = embedder_fn  # () -> OnnxCatEmbedder | None (để bỏ ảnh trùng tư thế)
        self.on_done = on_done
        self.lock = threading.Lock()
        self.pet_id = None
        self.left = 0
        self.seen_at = 0.0
        self.last_shot = 0.0
        self.last_vec = None
        self.q = queue.Queue(maxsize=2)

    def update_from_headers(self, pet_id, left):
        """FrameUploader gọi sau mỗi lần gửi khung hình."""
        with self.lock:
            if pet_id:
                if pet_id != self.pet_id:
                    print(f"[Đăng ký] Bắt đầu chụp bé (id {pet_id[:8]}…): cần {left} ảnh. Đưa bé ra trước camera.")
                    self.last_vec, self.last_shot = None, 0.0
                self.pet_id = pet_id
                try:
                    self.left = int(left)
                except (TypeError, ValueError):
                    self.left = 1
                self.seen_at = time.time()
            elif self.pet_id is not None:
                print("[Đăng ký] Phiên chụp đã kết thúc")
                self.pet_id = None
                self.on_done()

    def active(self):
        with self.lock:
            return self.pet_id is not None and self.left > 0 and time.time() - self.seen_at < 10

    def offer(self, frame, boxes):
        """Luồng nhận diện gọi với khung GỐC và các khung mèo (x, y, w, h, conf, ...)."""
        if not self.active() or len(boxes) != 1:
            return  # không thấy mèo hoặc nhiều mèo cùng lúc: không biết con nào là bé đang đăng ký
        now = time.time()
        if now - self.last_shot < SHOT_GAP:
            return
        x, y, w, h = boxes[0][:4]
        if min(w, h) < SHOT_MIN_SIDE:
            return
        crop = onnx_infer.crop_with_margin(frame, x, y, x + w, y + h, CROP_MARGIN)
        emb = self.embedder_fn()
        if emb is not None:
            v = emb.embed(crop)
            if self.last_vec is not None and float(v @ self.last_vec) > SHOT_SAME:
                return
            self.last_vec = v
        self.last_shot = now
        ok, buf = cv2.imencode(".jpg", crop, [int(cv2.IMWRITE_JPEG_QUALITY), 90])
        if ok:
            with self.lock:
                pet = self.pet_id
            try:
                self.q.put_nowait((pet, buf.tobytes()))
            except queue.Full:
                pass

    def run(self):
        while True:
            pet, jpeg = self.q.get()
            try:
                d = json.loads(self.api.call("POST", "/api/v1/camera/reid-shot", jpeg,
                                             {"Content-Type": "image/jpeg", "X-Pet-Id": pet}).decode("utf-8"))
                left = int((d.get("data") or {}).get("left", 0))
            except Exception as e:
                print(f"[Đăng ký] Không gửi được ảnh: {e}")
                continue
            with self.lock:
                if self.pet_id == pet:
                    self.left = left
            print(f"[Đăng ký] Đã gửi 1 ảnh, còn {left}")
            if left == 0:
                with self.lock:
                    self.pet_id = None
                self.on_done()


# ------------------------------------------------------------------ đồng bộ kho ảnh mẫu
class GallerySync(threading.Thread):
    """Tải ảnh mẫu từ web, tính vector đặc trưng, gộp với kho cục bộ rồi gọi set_gallery(CatGallery)."""

    def __init__(self, api, detector):
        super().__init__(daemon=True)
        self.api = api
        self.det = detector  # CatDetector: embedder, local_gallery, gallery_kw(), set_gallery(), cats_in()
        self.wake = threading.Event()
        self.version = None
        self.model_tag = None
        self.cache = {}  # photo id -> (vector | None nếu không thấy mèo)
        self.reported_at = 0.0
        self.last_error = None
        CACHE_DIR.mkdir(exist_ok=True)

    # ---------------------------------------------------------- cache vector trên đĩa
    def _cache_file(self):
        return CACHE_DIR / f"emb_{self.model_tag}.npz"

    def _load_cache(self):
        emb = self.det.embedder
        self.model_tag = "".join(c for c in f"{emb.meta.get('model', 'x')}_{emb.w}" if c.isalnum() or c in "_-")
        f = self._cache_file()
        if not f.exists():
            return
        try:
            d = np.load(str(f), allow_pickle=False)
            for i, ok, v in zip(d["ids"].tolist(), d["ok"].tolist(), d["vecs"]):
                self.cache[str(i)] = v.astype(np.float32) if ok else None
        except Exception as e:
            print(f"[Đăng ký] Bỏ cache vector hỏng: {e}")

    def _save_cache(self):
        if not self.cache:
            return
        ids = list(self.cache)
        dim = next((len(v) for v in self.cache.values() if v is not None), 1)
        vecs = np.stack([self.cache[i] if self.cache[i] is not None else np.zeros(dim, np.float32) for i in ids])
        np.savez_compressed(str(self._cache_file()), ids=np.array(ids), ok=np.array([self.cache[i] is not None for i in ids]),
                            vecs=vecs)

    # ---------------------------------------------------------- học một ảnh
    def _learn(self, photo_id, source):
        img_file = CACHE_DIR / f"{photo_id}.jpg"
        if img_file.exists():
            data = img_file.read_bytes()
        else:
            data = self.api.call("GET", f"/api/v1/camera/reid-photos/{photo_id}", timeout=20)
            img_file.write_bytes(data)
        img = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)
        if img is None:
            return None
        if source != "camera":  # ảnh chủ tải lên: tìm con mèo lớn nhất rồi cắt
            cats = self.det.cats_in(img)
            if not cats:
                return None
            x1, y1, x2, y2, _ = max(cats, key=lambda c: (c[2] - c[0]) * (c[3] - c[1]))
            img = onnx_infer.crop_with_margin(img, x1, y1, x2, y2, CROP_MARGIN)
            if img.size == 0:
                return None
        return self.det.embedder.embed(img, flip_tta=True)

    # ---------------------------------------------------------- một vòng đồng bộ
    def sync_once(self):
        if self.det.embedder is None:
            if time.time() - self.reported_at > REPORT_EVERY:
                self.api.json("POST", "/api/v1/camera/reid-report", {"ready": False, "pets": [], "photos": {}})
                self.reported_at = time.time()
            return
        g = self.api.json("GET", "/api/v1/camera/reid-gallery")
        version = g.get("version")
        if version == self.version:
            if time.time() - self.reported_at > REPORT_EVERY:
                self._report({})
            return
        names, vecs, result, learned = [], [], {}, set()
        new = errors = 0
        for pet in g.get("pets", []):
            for ph in pet.get("photos", []):
                pid = str(ph["id"])
                if pid not in self.cache:
                    try:
                        self.cache[pid] = self._learn(pid, ph.get("source"))
                        new += 1
                    except Exception as e:
                        print(f"[Đăng ký] Lỗi học ảnh {pid[:8]}…: {e}")
                        errors += 1
                        continue
                v = self.cache[pid]
                result[pid] = v is not None
                if v is not None:
                    names.append(pet["name"])
                    vecs.append(v)
                    learned.add(pet["name"])
        # Dọn ảnh đã bị xoá trên web
        alive = {str(ph["id"]) for pet in g.get("pets", []) for ph in pet.get("photos", [])}
        for pid in [k for k in self.cache if k not in alive]:
            del self.cache[pid]
            (CACHE_DIR / f"{pid}.jpg").unlink(missing_ok=True)
        self._save_cache()

        # Gộp với kho cục bộ (enroll_cats.py): bé nào đã đăng ký trên web thì dùng ảnh web
        local = self.det.local_gallery
        threshold = local.threshold if local is not None else None
        if local is not None:
            web = {n.lower() for n in learned}
            for n, v in zip(local.names, local.emb):
                if n.lower() not in web:
                    names.append(n)
                    vecs.append(v)
        kw = self.det.gallery_kw()
        if threshold is not None and "threshold" not in kw:
            kw["threshold"] = threshold
        gallery = onnx_infer.CatGallery(names, np.stack(vecs), **kw) if names else None
        self.det.set_gallery(gallery)
        if new:
            bad = sum(1 for k in result if not result[k])
            print(f"[Đăng ký] Đã học {new} ảnh mới từ web" + (f" ({bad} ảnh không thấy mèo)" if bad else "")
                  + f". Các bé trên web: {sorted(learned) or '(chưa có)'}")
        self.version = None if errors else version  # lỗi mạng: lần sau thử lại các ảnh còn thiếu
        self._report(result, learned)

    def _report(self, result, learned=None):
        if learned is None:
            r = self.det.reid
            learned = set(r[1].pets) if r is not None else set()
        self.api.json("POST", "/api/v1/camera/reid-report", {
            "ready": self.det.embedder is not None,
            "model": self.det.embedder.meta.get("model") if self.det.embedder is not None else None,
            "pets": sorted(learned),
            "photos": result,
        })
        self.reported_at = time.time()

    def run(self):
        if self.det.embedder is not None:
            self._load_cache()
        while True:
            try:
                self.sync_once()
                if self.last_error:
                    print("[Đăng ký] Đồng bộ ảnh mẫu với web đã ổn lại")
                self.last_error = None
            except Exception as e:
                msg = str(e)
                if msg != self.last_error:
                    print(f"[Đăng ký] Không đồng bộ được ảnh mẫu: {msg}")
                self.last_error = msg
            self.wake.wait(SYNC_INTERVAL)
            self.wake.clear()
