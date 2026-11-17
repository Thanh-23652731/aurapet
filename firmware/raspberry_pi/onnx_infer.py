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
    so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
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


def crop_with_margin(frame, x1, y1, x2, y2, margin):
    """Cắt vùng quanh khung (x1, y1, x2, y2), nới thêm margin * kích thước khung mỗi phía."""
    h, w = frame.shape[:2]
    mx, my = (x2 - x1) * margin, (y2 - y1) * margin
    return frame[int(max(0, y1 - my)):int(min(h, y2 + my)), int(max(0, x1 - mx)):int(min(w, x2 + mx))]
