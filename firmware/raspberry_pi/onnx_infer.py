# -*- coding: utf-8 -*-
"""
AuraPet - Nhận diện mèo bằng ONNX Runtime (KHÔNG cần PyTorch / ultralytics khi chạy).

Dùng trên Raspberry Pi 4: PyTorch bản mới bị lỗi "Illegal instruction" trên CPU Cortex-A72,
còn ONNX Runtime chạy ổn định và nhanh hơn trên CPU ARM.

Mô hình được xuất sẵn trên máy tính (cần ultralytics + onnx):
    yolov8n.onnx                 phát hiện vật thể COCO (lớp 15 = mèo), ảnh vào 640x640 (mặc định)
    models/cat_classifier.onnx   nhận ra từng bé (Khac, Mimi, Tom...), ảnh vào 224x224
Xuất lại:  python export_onnx.py

Tiền xử lý / hậu xử lý làm giống ultralytics để kết quả khớp với bản PyTorch:
    detector   : letterbox giữ tỉ lệ, viền xám 114, RGB, /255 -> đầu ra [1, 4+80, N] -> lọc lớp mèo -> NMS
    classifier : thu cạnh ngắn về 224, cắt giữa 224x224, RGB, /255 -> xác suất từng bé
"""

import ast
import os

import cv2
import numpy as np

CAT_CLASS_ID = 15  # lớp "cat" trong bộ COCO


def _session(path, threads):
    import onnxruntime as ort
    so = ort.SessionOptions()
    so.intra_op_num_threads = max(1, int(threads))
    so.inter_op_num_threads = 1
    # BASIC: mức ALL/EXTENDED gộp SiLU của YOLOv8 thành nút QuickGelu, bản onnxruntime trên Pi (ARM)
    # chạy nút này bị lỗi "GetElementType is not implemented" rồi Aborted. Đặt ORT_OPT=all để thử lại mức cao.
    level = os.getenv("ORT_OPT", "basic").strip().lower()
    so.graph_optimization_level = {
        "all": ort.GraphOptimizationLevel.ORT_ENABLE_ALL,
        "extended": ort.GraphOptimizationLevel.ORT_ENABLE_EXTENDED,
        "none": ort.GraphOptimizationLevel.ORT_DISABLE_ALL,
    }.get(level, ort.GraphOptimizationLevel.ORT_ENABLE_BASIC)
    return ort.InferenceSession(str(path), sess_options=so, providers=["CPUExecutionProvider"])


def _meta(session):
    return session.get_modelmeta().custom_metadata_map or {}


def _input_size(session, default):
    shape = session.get_inputs()[0].shape  # [1, 3, H, W]
    try:
        return int(shape[2]), int(shape[3])
    except (TypeError, ValueError, IndexError):
        return default, default


def available():
    """True nếu máy có onnxruntime."""
    try:
        import onnxruntime  # noqa: F401
        return True
    except ImportError:
        return False


class OnnxCatDetector:
    """Phát hiện mèo (YOLOv8 ONNX) + nhận ra từng bé (classifier ONNX, tuỳ chọn)."""

    def __init__(self, detector_path, classifier_path=None, threads=None, iou=0.7, crop_margin=0.3):
        threads = threads or max(1, (os.cpu_count() or 2) // 2)
        self.det = _session(detector_path, threads)
        self.det_in = self.det.get_inputs()[0].name
        self.det_h, self.det_w = _input_size(self.det, 320)
        self.iou = iou
        # Cắt rộng quanh khung mèo trước khi đọc tên: mô hình nhận tên được train trên ảnh có cả nền
        # xung quanh. Đo trên ảnh mẫu: +30% mỗi phía giúp đọc đúng hơn, nhất là khi mèo ở xa.
        self.crop_margin = crop_margin
        self.cls = None
        self.names = []
        if classifier_path and os.path.exists(classifier_path):
            self.cls = _session(classifier_path, threads)
            self.cls_in = self.cls.get_inputs()[0].name
            self.cls_h, self.cls_w = _input_size(self.cls, 224)
            raw = _meta(self.cls).get("names", "")
            try:
                names = ast.literal_eval(raw) if raw else {}
                self.names = [names[i] for i in sorted(names)]
            except (ValueError, SyntaxError):
                self.names = []

    # ------------------------------------------------------------------ phát hiện
    def _letterbox(self, frame):
        h, w = frame.shape[:2]
        r = min(self.det_h / h, self.det_w / w)
        nw, nh = int(round(w * r)), int(round(h * r))
        img = cv2.resize(frame, (nw, nh), interpolation=cv2.INTER_LINEAR) if (nw, nh) != (w, h) else frame
        dw, dh = (self.det_w - nw) / 2, (self.det_h - nh) / 2
        top, bottom = int(round(dh - 0.1)), int(round(dh + 0.1))
        left, right = int(round(dw - 0.1)), int(round(dw + 0.1))
        img = cv2.copyMakeBorder(img, top, bottom, left, right, cv2.BORDER_CONSTANT, value=(114, 114, 114))
        return img, r, left, top

    def detect_cats(self, frame, conf=0.4):
        """Trả về [(x1, y1, x2, y2, độ tin cậy)] theo toạ độ ảnh gốc."""
        h, w = frame.shape[:2]
        img, r, pad_x, pad_y = self._letterbox(frame)
        blob = img[:, :, ::-1].transpose(2, 0, 1)[None].astype(np.float32) / 255.0  # BGR->RGB, HWC->NCHW
        out = self.det.run(None, {self.det_in: np.ascontiguousarray(blob)})[0][0]  # [84, N]
        scores = out[4 + CAT_CLASS_ID]
        keep = scores >= conf
        if not np.any(keep):
            return []
        cx, cy, bw, bh = out[0][keep], out[1][keep], out[2][keep], out[3][keep]
        s = scores[keep]
        x1 = (cx - bw / 2 - pad_x) / r
        y1 = (cy - bh / 2 - pad_y) / r
        x2 = (cx + bw / 2 - pad_x) / r
        y2 = (cy + bh / 2 - pad_y) / r
        boxes_xywh = [[float(a), float(b), float(c - a), float(d - b)] for a, b, c, d in zip(x1, y1, x2, y2)]
        idx = cv2.dnn.NMSBoxes(boxes_xywh, s.astype(float).tolist(), conf, self.iou)
        result = []
        for i in np.array(idx).flatten():
            bx1 = int(max(0, min(w, x1[i])))
            by1 = int(max(0, min(h, y1[i])))
            bx2 = int(max(0, min(w, x2[i])))
            by2 = int(max(0, min(h, y2[i])))
            if bx2 > bx1 and by2 > by1:
                result.append((bx1, by1, bx2, by2, float(s[i])))
        return result

    # ------------------------------------------------------------------ nhận ra từng bé
    def classify(self, crop):
        """Trả về (tên bé, độ tin cậy) hoặc ("", 0.0) nếu chưa có mô hình nhận diện."""
        if self.cls is None or crop is None or crop.size == 0:
            return "", 0.0
        h, w = crop.shape[:2]
        size = min(self.cls_h, self.cls_w)
        r = size / min(h, w)  # thu cạnh ngắn về 224 rồi cắt giữa
        nh, nw = max(size, int(round(h * r))), max(size, int(round(w * r)))
        img = cv2.resize(crop, (nw, nh), interpolation=cv2.INTER_LINEAR)
        top, left = (nh - self.cls_h) // 2, (nw - self.cls_w) // 2
        img = img[top:top + self.cls_h, left:left + self.cls_w]
        blob = img[:, :, ::-1].transpose(2, 0, 1)[None].astype(np.float32) / 255.0
        p = self.cls.run(None, {self.cls_in: np.ascontiguousarray(blob)})[0][0].astype(np.float64)
        if abs(p.sum() - 1.0) > 1e-3 or p.min() < 0:  # đầu ra chưa qua softmax
            p = np.exp(p - p.max())
            p /= p.sum()
        k = int(p.argmax())
        name = self.names[k] if k < len(self.names) else str(k)
        return name, float(p[k])

    def detect(self, frame, conf=0.4):
        """Phát hiện + nhận ra: [(x1, y1, x2, y2, độ tin cậy, tên bé, độ tin cậy tên)]."""
        cats = []
        for x1, y1, x2, y2, c in self.detect_cats(frame, conf):
            name, nconf = self.classify(crop_with_margin(frame, x1, y1, x2, y2, self.crop_margin))
            cats.append((x1, y1, x2, y2, c, name, nconf))
        return cats


# ====================================================================== Re-ID bằng Feature Embedding
class OnnxCatEmbedder:
    """
    Trích đặc trưng (DINOv2 ONNX, xuất bằng export_embedder.py): ảnh mèo -> vector đã chuẩn hoá L2.
    Hai ảnh cùng một bé cho vector gần nhau (cosine cao), khác bé thì xa nhau -> không cần train lại
    mô hình khi thêm bé mới, chỉ cần đăng ký vài chục ảnh (enroll_cats.py).
    """

    def __init__(self, path, threads=None):
        threads = threads or max(1, (os.cpu_count() or 2) // 2)
        self.sess = _session(path, threads)
        self.inp = self.sess.get_inputs()[0].name
        self.h, self.w = _input_size(self.sess, 224)
        self.meta = _meta(self.sess)

    def _blob(self, crop, flip=False):
        # Đệm thành hình vuông (không cắt mất tai/đuôi như cắt giữa) rồi thu về kích thước mô hình
        h, w = crop.shape[:2]
        s = max(h, w)
        top, left = (s - h) // 2, (s - w) // 2
        img = cv2.copyMakeBorder(crop, top, s - h - top, left, s - w - left, cv2.BORDER_CONSTANT, value=(124, 116, 104))
        img = cv2.resize(img, (self.w, self.h), interpolation=cv2.INTER_AREA if s > self.w else cv2.INTER_LINEAR)
        if flip:
            img = img[:, ::-1]
        return np.ascontiguousarray(img[:, :, ::-1].transpose(2, 0, 1)[None].astype(np.float32) / 255.0)

    def embed(self, crop, flip_tta=False):
        """Vector đặc trưng (1 chiều, chuẩn hoá L2). flip_tta=True: trung bình thêm ảnh lật ngang (chậm gấp đôi)."""
        if crop is None or crop.size == 0:
            return None
        v = self.sess.run(None, {self.inp: self._blob(crop)})[0][0].astype(np.float32)
        if flip_tta:
            v = v + self.sess.run(None, {self.inp: self._blob(crop, flip=True)})[0][0].astype(np.float32)
        n = float(np.linalg.norm(v))
        return v / n if n > 0 else v


UNKNOWN_PREFIXES = ("khac", "_", "unknown", "other")


def is_negative_name(name):
    """Thư mục 'Khac' / '_...' trong gallery = mèo lạ dùng làm mẫu đối chứng, không phải một bé được cho ăn."""
    return str(name).strip().lower().startswith(UNKNOWN_PREFIXES)


class CatGallery:
    """
    Kho đặc trưng các bé đã đăng ký (models/cat_gallery.npz: names[i] ứng với embeddings[i]).
    So khớp: với mỗi bé lấy trung bình top-k cosine giữa ảnh mới và các ảnh đã đăng ký của bé đó.
    Nhận là bé A khi điểm A >= threshold VÀ hơn bé đứng thứ hai ít nhất `margin`; ngược lại là mèo lạ.
    """

    def __init__(self, names, embeddings, threshold=0.6, margin=0.03, topk=3):
        self.names = [str(n) for n in names]
        e = np.asarray(embeddings, dtype=np.float32)
        if e.ndim != 2 or len(self.names) != len(e):
            raise ValueError("gallery lỗi: số tên và số vector không khớp")
        self.emb = e / np.maximum(np.linalg.norm(e, axis=1, keepdims=True), 1e-12)
        self.threshold, self.margin, self.topk = float(threshold), float(margin), int(topk)
        self.labels = sorted(set(self.names))
        self._idx = {n: np.array([i for i, m in enumerate(self.names) if m == n]) for n in self.labels}

    @classmethod
    def load(cls, path, **kw):
        d = np.load(str(path), allow_pickle=False)
        g = cls(d["names"].tolist(), d["embeddings"], **kw)
        if "threshold" in d.files and "threshold" not in kw:
            g.threshold = float(d["threshold"])
        return g

    def save(self, path, threshold=None):
        np.savez_compressed(str(path), names=np.array(self.names), embeddings=self.emb,
                            threshold=np.float32(self.threshold if threshold is None else threshold))

    @property
    def pets(self):
        return [n for n in self.labels if not is_negative_name(n)]

    def scores(self, v):
        """{tên: điểm} (trung bình top-k cosine) cho từng tên trong gallery."""
        sims = self.emb @ np.asarray(v, dtype=np.float32)
        out = {}
        for n, idx in self._idx.items():
            s = np.sort(sims[idx])[::-1][:self.topk]
            out[n] = float(s.mean())
        return out

    def match(self, v):
        """(tên bé, điểm) hoặc ("", điểm cao nhất) nếu là mèo lạ / không chắc."""
        if v is None or not self.labels:
            return "", 0.0
        sc = sorted(self.scores(v).items(), key=lambda kv: kv[1], reverse=True)
        name, best = sc[0]
        second = sc[1][1] if len(sc) > 1 else -1.0
        if is_negative_name(name) or best < self.threshold or best - second < self.margin:
            return "", best
        return name, best


def crop_with_margin(frame, x1, y1, x2, y2, margin):
    """Cắt vùng quanh khung (x1, y1, x2, y2), nới thêm margin * kích thước khung mỗi phía."""
    h, w = frame.shape[:2]
    mx, my = (x2 - x1) * margin, (y2 - y1) * margin
    return frame[int(max(0, y1 - my)):int(min(h, y2 + my)), int(max(0, x1 - mx)):int(min(w, x2 + mx))]
