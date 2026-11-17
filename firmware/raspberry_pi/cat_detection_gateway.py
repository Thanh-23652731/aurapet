#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AuraPet - camera phát hiện mèo bằng YOLOv8 (chạy trên Raspberry Pi 4 hoặc laptop có webcam USB).

Mỗi khi mèo đến gần camera (đủ số khung liên tiếp, nằm trong vùng ROI, đủ to trong khung hình):
1) Gửi sự kiện "có mèo / không có mèo" qua MQTT, topic <TOPIC_PREFIX>/vision. Backend sẽ
   cho ăn nếu đang ở chế độ Tự động VÀ khay < 10g (lưu đồ Hình 6).
2) Gửi hình trực tiếp (JPEG, có khung đánh dấu mèo) lên backend qua HTTP để xem trên web/app.
   Ảnh đi thẳng tới máy chủ AuraPet (không qua broker MQTT công cộng) và chỉ được giữ
   khung mới nhất trong RAM máy chủ.

Cài đặt:
    pip install -r firmware/raspberry_pi/requirements.txt
    (lần chạy đầu ultralytics tự tải yolov8n.pt nếu chưa có file cạnh script)

Cấu hình bằng biến môi trường:
    MQTT_HOST, MQTT_PORT, MQTT_TLS (0/1), MQTT_USERNAME, MQTT_PASSWORD,
    MQTT_TOPIC_PREFIX  (PHẢI giống .env backend và secrets.h của ESP32)
    AURAPET_SERVER     địa chỉ backend, vd. http://192.168.0.25:8080 hoặc https://<tunnel>.trycloudflare.com
    CAMERA_KEY         PHẢI giống CAMERA_KEY trong .env backend (bỏ trống = không gửi hình)
    CAMERA_INDEX       mặc định 0 (Pi). Laptop có webcam USB cắm ngoài thường là 1 hoặc 2
    CAMERA_WIDTH/HEIGHT  mặc định 640x480
    CAMERA_FPS         số khung/giây xin từ camera, mặc định 60 (camera không hỗ trợ thì tự lấy mức cao nhất của nó)
    STREAM_FPS         số khung/giây gửi lên web, mặc định 60
    STREAM_WIDTH (mặc định 640), JPEG_QUALITY (mặc định 70; giảm xuống 50-60 nếu Wi-Fi yếu)
    TORCH_THREADS      số nhân CPU cho YOLO, mặc định một nửa số nhân (để phần còn lại lo hình trực tiếp)

  Nhận diện (YOLOv8):
    YOLO_MODEL         đường dẫn model, mặc định yolov8n.pt cạnh script
    YOLO_IMGSZ         kích thước ảnh đưa vào model, mặc định 640 (Pi 4 nên dùng 320 cho nhanh)
    CAT_CONFIDENCE     ngưỡng tin cậy, mặc định 0.4 (cao hơn = chỉ nhận mèo ở gần/rõ)
    CROP_MARGIN        cắt rộng quanh khung mèo trước khi đọc tên bé, mặc định 0.3 (30% mỗi phía)
    Tầm nhìn xa phụ thuộc kích thước ảnh của yolov8n.onnx (xuất bằng export_onnx.py --imgsz,
    mặc định 640; 416 nhẹ hơn ~2 lần nếu Pi chạy chậm)
    CAT_STABLE_FRAMES  số lần nhận diện liên tiếp để đổi trạng thái, mặc định 3
    CAT_ROI            vùng quan sát "x1,y1,x2,y2" theo pixel của khung CAMERA_WIDTH x CAMERA_HEIGHT
                       (bỏ trống = cả khung hình). Tâm con mèo phải nằm trong vùng này.
    CAT_NEAR_AREA      mèo phải chiếm ít nhất tỉ lệ này của khung hình mới tính là "đến gần",
                       vd. 0.05 = 5%. Mặc định 0 = thấy là tính.
    SCAN_INTERVAL      giây giữa 2 lần nhận diện, mặc định 0.5
    REPORT_INTERVAL    khi mèo vẫn ở đó, nhắc lại tối đa mỗi N giây, mặc định 10
    HEARTBEAT_INTERVAL khi không có mèo, vẫn báo "camera đang chạy" mỗi N giây, mặc định 15
                       (backend dựa vào đây để ESP32 không tự nhả theo cảm biến mà chờ camera
                       nhận ra bé rồi nhả đúng khẩu phần của bé đó)
    AI_ENABLED=0       chỉ phát hình trực tiếp, không nhận diện mèo (khi YOLO/PyTorch lỗi trên máy này)
    SHOW_PREVIEW=1     mở cửa sổ xem trước trên máy (cần opencv-python, không phải bản headless).
                       Nhấn q để thoát.

Ví dụ (Pi):
    MQTT_TOPIC_PREFIX=aurapet/xxxx AURAPET_SERVER=http://192.168.0.25:8080 CAMERA_KEY=xxxx \
        YOLO_IMGSZ=320 python3 cat_detection_gateway.py
Ví dụ (laptop Windows, PowerShell):
    $env:MQTT_TOPIC_PREFIX="aurapet/xxxx"; $env:CAMERA_INDEX="1"; $env:SHOW_PREVIEW="1"
    python firmware\\raspberry_pi\\cat_detection_gateway.py
"""

import json
import os
import queue
import ssl
import sys
import threading
import time
import http.client
from pathlib import Path
from urllib.parse import urlsplit

import cv2
import paho.mqtt.client as mqtt


def _env_float(name, default):
    try:
        return float(os.getenv(name, str(default)))
    except ValueError:
        sys.exit(f"{name} phải là số")


def _parse_roi(text):
    if not text.strip():
        return None
    try:
        x1, y1, x2, y2 = (int(v) for v in text.replace(" ", "").split(","))
    except ValueError:
        sys.exit('CAT_ROI phải có dạng "x1,y1,x2,y2", vd. "100,80,540,460"')
    if x2 <= x1 or y2 <= y1:
        sys.exit("CAT_ROI không hợp lệ: cần x2 > x1 và y2 > y1")
    return x1, y1, x2, y2


# ----------------------------- CONFIG -----------------------------
MQTT_HOST = os.getenv("MQTT_HOST", "broker.emqx.io")
MQTT_PORT = int(os.getenv("MQTT_PORT", "1883"))
MQTT_TLS = os.getenv("MQTT_TLS", "0") in ("1", "true", "True")
MQTT_USERNAME = os.getenv("MQTT_USERNAME", "")
MQTT_PASSWORD = os.getenv("MQTT_PASSWORD", "")
TOPIC_PREFIX = os.getenv("MQTT_TOPIC_PREFIX", "").strip().rstrip("/")

CAMERA_INDEX = int(os.getenv("CAMERA_INDEX", "0"))
CAMERA_WIDTH = int(os.getenv("CAMERA_WIDTH", "640"))
CAMERA_HEIGHT = int(os.getenv("CAMERA_HEIGHT", "480"))
CAMERA_FPS = max(1.0, _env_float("CAMERA_FPS", 60))

AURAPET_SERVER = os.getenv("AURAPET_SERVER", "").strip().rstrip("/")
CAMERA_KEY = os.getenv("CAMERA_KEY", "").strip()
STREAM_FPS = max(1.0, _env_float("STREAM_FPS", 60))
STREAM_WIDTH = int(os.getenv("STREAM_WIDTH", "640"))
JPEG_QUALITY = int(os.getenv("JPEG_QUALITY", "70"))

_DEFAULT_MODEL = Path(__file__).resolve().parent / "yolov8n.pt"
YOLO_MODEL = os.getenv("YOLO_MODEL", "").strip() or (str(_DEFAULT_MODEL) if _DEFAULT_MODEL.exists() else "yolov8n.pt")
YOLO_IMGSZ = int(os.getenv("YOLO_IMGSZ", "640"))
CAT_CLASS_ID = 15            # lớp "cat" trong bộ COCO của YOLOv8
# 0.4: nhận được mèo ở xa hơn nhiều so với 0.6 (đo thử: mèo chiếm 20% bề ngang khung 11/45 -> 37/45 ảnh),
# không lo báo nhầm vì vẫn phải thấy CAT_STABLE_FRAMES lần liên tiếp
CAT_CONFIDENCE = _env_float("CAT_CONFIDENCE", 0.4)
# Cắt rộng thêm tỉ lệ này mỗi phía quanh khung mèo trước khi đọc tên bé (giúp đọc đúng hơn khi mèo ở xa)
CROP_MARGIN = max(0.0, _env_float("CROP_MARGIN", 0.3))
STABLE_FRAMES = max(1, int(os.getenv("CAT_STABLE_FRAMES", "3")))
CAT_ROI = _parse_roi(os.getenv("CAT_ROI", ""))
CAT_NEAR_AREA = max(0.0, _env_float("CAT_NEAR_AREA", 0))
TORCH_THREADS = max(1, int(os.getenv("TORCH_THREADS", str(max(1, (os.cpu_count() or 2) // 2)))))

# Nhận diện từng bé mèo cụ thể (Cat Identification / Classifier)
_DEFAULT_CLASSIFIER_DIR = Path(__file__).resolve().parent / "models"
_DEFAULT_CLASSIFIER = _DEFAULT_CLASSIFIER_DIR / "cat_classifier.pt"
_DEFAULT_CLASSIFIER_ONNX = _DEFAULT_CLASSIFIER_DIR / "cat_classifier.onnx"
CAT_CLASSIFIER_MODEL = os.getenv("CAT_CLASSIFIER_PATH", "").strip()
if not CAT_CLASSIFIER_MODEL:
    if _DEFAULT_CLASSIFIER_ONNX.exists():
        CAT_CLASSIFIER_MODEL = str(_DEFAULT_CLASSIFIER_ONNX)
    elif _DEFAULT_CLASSIFIER.exists():
        CAT_CLASSIFIER_MODEL = str(_DEFAULT_CLASSIFIER)
CAT_CLASSIFIER_CONF = _env_float("CAT_CLASSIFIER_CONF", 0.65)
# Cách chạy mô hình: auto (mặc định) | onnx (ONNX Runtime, không cần PyTorch) | torch (ultralytics)
INFERENCE = os.getenv("INFERENCE", "auto").strip().lower()
ONNX_DETECTOR = os.getenv("ONNX_DETECTOR", "").strip() or str(Path(__file__).resolve().parent / "yolov8n.onnx")
ONNX_CLASSIFIER = os.getenv("ONNX_CLASSIFIER", "").strip() or str(_DEFAULT_CLASSIFIER_ONNX)
DEMO_PET_NAME = os.getenv("DEMO_PET_NAME", "").strip()

SCAN_INTERVAL = max(0.1, _env_float("SCAN_INTERVAL", 0.5))
REPORT_INTERVAL = _env_float("REPORT_INTERVAL", 10)
HEARTBEAT_INTERVAL = _env_float("HEARTBEAT_INTERVAL", 15)
SHOW_PREVIEW = os.getenv("SHOW_PREVIEW", "0") in ("1", "true", "True")
# 0 = chỉ phát hình trực tiếp, không chạy YOLO (vd. PyTorch lỗi "Illegal instruction" trên Pi 4)
AI_ENABLED = os.getenv("AI_ENABLED", "1") not in ("0", "false", "False")
WINDOW_NAME = "AuraPet - Cat Detection"
# ------------------------------------------------------------------

if not TOPIC_PREFIX:
    sys.exit("Thiếu MQTT_TOPIC_PREFIX. Đặt cùng giá trị với file .env của backend.")

VISION_TOPIC = TOPIC_PREFIX + "/vision"
STREAM_ON = bool(AURAPET_SERVER and CAMERA_KEY)


# --------------------------- NHẬN DIỆN ----------------------------
class CatDetector:
    """
    Phát hiện mèo (detector) + phân loại danh tính từng bé (classifier nếu có).
    Hai cách chạy (INFERENCE):
      onnx  : ONNX Runtime, không cần PyTorch (Raspberry Pi 4: PyTorch mới lỗi "Illegal instruction")
      torch : ultralytics + PyTorch (laptop, Pi 5)
      auto  : (mặc định) có onnxruntime + yolov8n.onnx thì dùng onnx, không thì torch
    """

    def __init__(self):
        self.onnx = None
        self.model = None
        self.classifier = None
        mode = INFERENCE
        if mode == "auto":
            import onnx_infer
            mode = "onnx" if (onnx_infer.available() and Path(ONNX_DETECTOR).exists()) else "torch"
        if mode == "onnx":
            self._init_onnx()
        else:
            self._init_torch()
        if self.classifier is None and (self.onnx is None or self.onnx.cls is None) and DEMO_PET_NAME:
            print(f"[AI] Đang chạy chế độ mô phỏng nhận diện bé: '{DEMO_PET_NAME}'")

    def _init_onnx(self):
        import onnx_infer
        if not onnx_infer.available():
            sys.exit("Chưa cài onnxruntime: pip install -r requirements.txt")
        if not Path(ONNX_DETECTOR).exists():
            sys.exit(f"Thiếu {ONNX_DETECTOR}. Xuất trên máy tính bằng export_onnx.py rồi chép sang Pi.")
        cls_path = ONNX_CLASSIFIER if Path(ONNX_CLASSIFIER).exists() else None
        print(f"[AI] ONNX Runtime: {ONNX_DETECTOR}" + (f" + {cls_path}" if cls_path else "")
              + f" (dùng {TORCH_THREADS} nhân CPU, không cần PyTorch)")
        self.onnx = onnx_infer.OnnxCatDetector(ONNX_DETECTOR, cls_path, threads=TORCH_THREADS)
        print(f"[AI] Ảnh vào detector {self.onnx.det_w}x{self.onnx.det_h}")
        if self.onnx.cls is not None:
            print(f"[AI] Đã kích hoạt nhận diện riêng từng bé: {self.onnx.names}")

    def _init_torch(self):
        try:
            from ultralytics import YOLO
        except ImportError:
            sys.exit("Chưa cài ultralytics (hoặc dùng ONNX: pip install onnxruntime + chép yolov8n.onnx)")
        try:
            import torch
            torch.set_num_threads(TORCH_THREADS)  # chừa nhân CPU cho luồng hình trực tiếp
        except Exception:
            pass
        print(f"[AI] PyTorch: đang tải mô hình detector {YOLO_MODEL} (dùng {TORCH_THREADS} nhân CPU) ...")
        self.model = YOLO(YOLO_MODEL)
        if CAT_CLASSIFIER_MODEL and Path(CAT_CLASSIFIER_MODEL).exists():
            print(f"[AI] Đang tải mô hình phân loại bé mèo: {CAT_CLASSIFIER_MODEL} ...")
            try:
                self.classifier = YOLO(CAT_CLASSIFIER_MODEL)
                names = list(self.classifier.names.values()) if hasattr(self.classifier, "names") else []
                print(f"[AI] Đã kích hoạt nhận diện riêng từng bé: {names}")
            except Exception as e:
                print(f"[AI] Cảnh báo: Không tải được classifier: {e}")

    def classify_crop(self, crop):
        """Phân loại bé mèo cụ thể từ vùng ảnh đã cắt."""
        if crop.size > 0:
            try:
                if self.onnx is not None and self.onnx.cls is not None:
                    name, conf = self.onnx.classify(crop)
                    if conf >= CAT_CLASSIFIER_CONF:
                        return name, conf
                elif self.classifier is not None:
                    res = self.classifier(crop, imgsz=224, verbose=False)
                    if res and hasattr(res[0], "probs") and res[0].probs is not None:
                        conf = float(res[0].probs.top1conf)
                        if conf >= CAT_CLASSIFIER_CONF:
                            return res[0].names[int(res[0].probs.top1)], conf
            except Exception:
                pass
        if DEMO_PET_NAME:
            return DEMO_PET_NAME, 0.95
        return "Meo", 0.0

    def _raw_cats(self, frame):
        """[(x1, y1, x2, y2, độ tin cậy)] các con mèo trong khung hình."""
        if self.onnx is not None:
            return self.onnx.detect_cats(frame, CAT_CONFIDENCE)
        out = []
        for result in self.model(frame, imgsz=YOLO_IMGSZ, conf=CAT_CONFIDENCE, classes=[CAT_CLASS_ID], verbose=False):
            for box in result.boxes:
                if int(box.cls[0]) == CAT_CLASS_ID:
                    x1, y1, x2, y2 = (float(v) for v in box.xyxy[0])
                    out.append((x1, y1, x2, y2, float(box.conf[0])))
        return out

    def detect(self, frame):
        """Trả về (có_mèo, độ_tin_cậy_cao_nhất, danh_sách_khung, tên_bé_nổi_bật)."""
        h, w = frame.shape[:2]
        boxes = []
        for x1, y1, x2, y2, conf in self._raw_cats(frame):
            if conf < CAT_CONFIDENCE:
                continue
            if not _in_roi((x1 + x2) / 2.0, (y1 + y2) / 2.0):
                continue
            if (x2 - x1) * (y2 - y1) < CAT_NEAR_AREA * w * h:
                continue  # mèo còn ở xa

            # Cắt vùng ảnh con mèo (nới rộng thêm CROP_MARGIN mỗi phía) để nhận diện danh tính cụ thể
            mx, my = (x2 - x1) * CROP_MARGIN, (y2 - y1) * CROP_MARGIN
            crop = frame[max(0, int(y1 - my)):min(h, int(y2 + my)), max(0, int(x1 - mx)):min(w, int(x2 + mx))]
            cat_name, cat_conf = self.classify_crop(crop)
            boxes.append((int(x1), int(y1), int(x2 - x1), int(y2 - y1), conf, cat_name, cat_conf))

        best = max((b[4] for b in boxes), default=0.0)
        named = [b for b in boxes if b[5] != "Meo" and b[5] != ""]
        best_name = max(named, key=lambda b: b[6])[5] if named else (boxes[0][5] if boxes else "")
        return bool(boxes), best, boxes, best_name


def _in_roi(x, y):
    if CAT_ROI is None:
        return True
    x1, y1, x2, y2 = CAT_ROI
    return x1 <= x <= x2 and y1 <= y <= y2


class DetectionWorker(threading.Thread):
    """Chạy YOLO ở luồng riêng để hình trực tiếp không bị giật khi model đang suy luận."""

    def __init__(self, detector, client):
        super().__init__(daemon=True)
        self.detector = detector
        self.client = client
        self.q = queue.Queue(maxsize=1)
        self.lock = threading.Lock()
        self.found, self.conf, self.boxes = False, 0.0, []
        self.pet_name = ""
        self.present, self.streak, self.last_report = False, 0, 0.0

    def offer(self, frame):
        try:
            self.q.get_nowait()  # chỉ giữ khung mới nhất
        except queue.Empty:
            pass
        self.q.put_nowait(frame)

    def latest(self):
        with self.lock:
            return self.found, self.conf, list(self.boxes), self.present, self.pet_name

    def run(self):
        while True:
            frame = self.q.get()
            try:
                found, conf, boxes, pet_name = self.detector.detect(frame)
            except Exception as e:
                print(f"[AI] Lỗi nhận diện: {e}")
                continue
            now = time.time()
            with self.lock:
                self.found, self.conf, self.boxes = found, conf, boxes
                self.pet_name = pet_name if found else ""
                self.streak = self.streak + 1 if found != self.present else 0
                changed = self.streak >= STABLE_FRAMES
                if changed:
                    self.present, self.streak = found, 0
                # Có mèo: nhắc lại mỗi REPORT_INTERVAL. Không có mèo: vẫn báo camera còn sống (heartbeat)
                interval = REPORT_INTERVAL if self.present else HEARTBEAT_INTERVAL
                remind = not changed and now - self.last_report > interval
                present = self.present
                current_pet = self.pet_name
                if changed or remind:
                    self.last_report = now
            if changed or remind:
                publish(self.client, present, conf if present else 0.0, current_pet,
                        quiet=remind and not present)


# ----------------------------- MQTT -------------------------------
def make_client():
    try:
        client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=f"AuraPet_Pi_{os.getpid()}")
    except AttributeError:  # paho-mqtt < 2.0
        client = mqtt.Client(client_id=f"AuraPet_Pi_{os.getpid()}")
    if MQTT_USERNAME:
        client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)
    if MQTT_TLS:
        client.tls_set(cert_reqs=ssl.CERT_REQUIRED)
    client.reconnect_delay_set(min_delay=2, max_delay=30)
    return client


def publish(client, cat_present, confidence, pet_name="", quiet=False):
    payload = json.dumps({
        "catPresent": cat_present,
        "petName": pet_name if cat_present else "",
        "confidence": round(confidence, 2),
        "source": "pi-camera-yolov8",
        "ts": int(time.time())
    })
    client.publish(VISION_TOPIC, payload, qos=0)
    if quiet:
        return  # heartbeat lúc không có mèo: không in log
    who = f" [{pet_name}]" if pet_name and pet_name != "Meo" else ""
    print(("[Camera] MÈO ĐẾN GẦN" if cat_present else "[Camera] Mèo đã rời đi") + who, f"(độ tin cậy {confidence:.2f})")


# ------------------------- HÌNH TRỰC TIẾP -------------------------
class FrameUploader(threading.Thread):
    """Gửi khung mới nhất lên backend. Mạng chậm thì bỏ bớt khung cũ, không làm chậm nhận diện."""

    def __init__(self):
        super().__init__(daemon=True)
        self.q = queue.Queue(maxsize=1)
        u = urlsplit(AURAPET_SERVER + "/api/v1/camera/frame")
        self.https = u.scheme == "https"
        self.host, self.port, self.path = u.hostname, u.port, u.path
        self.conn = None
        self.ok = None
        self.sent = 0  # số khung đã gửi thành công (để in tốc độ thực tế)

    def offer(self, jpeg, cat, conf, pet_name=""):
        try:
            self.q.get_nowait()  # bỏ khung cũ chưa kịp gửi
        except queue.Empty:
            pass
        self.q.put_nowait((jpeg, cat, conf, pet_name))

    def _close(self):
        if self.conn is not None:
            self.conn.close()
            self.conn = None

    def _post(self, jpeg, headers):
        """Giữ 1 kết nối mở cho nhiều khung (keep-alive): nhanh hơn nhiều so với mở kết nối mới mỗi khung."""
        for attempt in (1, 2):
            reused = self.conn is not None
            if self.conn is None:
                cls = http.client.HTTPSConnection if self.https else http.client.HTTPConnection
                self.conn = cls(self.host, self.port, timeout=5)
            try:
                self.conn.request("POST", self.path, body=jpeg, headers=headers)
                res = self.conn.getresponse()
                res.read()
            except Exception:
                self._close()
                if reused and attempt == 1:
                    continue  # máy chủ đã đóng kết nối cũ, mở kết nối mới gửi lại
                raise
            if res.will_close:
                self._close()
            return res.status

    def run(self):
        while True:
            jpeg, cat, conf, pet_name = self.q.get()
            headers = {
                "Content-Type": "image/jpeg",
                "X-Camera-Key": CAMERA_KEY,
                "X-Cat-Present": "true" if cat else "false",
                "X-Cat-Confidence": f"{conf:.2f}",
                "X-Cat-Name": pet_name if cat else "",
            }
            try:
                status = self._post(jpeg, headers)
            except Exception as e:  # mất mạng, máy chủ tắt...
                if self.ok is not False:
                    print(f"[Stream] Không gửi được hình: {e}. Sẽ thử lại.")
                self.ok = False
                time.sleep(2)
                continue
            if 200 <= status < 300:
                if self.ok is not True:
                    print(f"[Stream] Đang gửi hình lên {AURAPET_SERVER}")
                self.ok = True
                self.sent += 1
            else:
                if self.ok is not False:
                    hint = {403: "sai CAMERA_KEY", 503: "máy chủ chưa đặt CAMERA_KEY"}.get(status, "")
                    print(f"[Stream] Máy chủ từ chối hình: HTTP {status} {hint}")
                self.ok = False
                time.sleep(3)


def annotate(frame, boxes, max_width=None):
    """Thu nhỏ ảnh, vẽ ROI và khung quanh mèo (chữ không dấu vì OpenCV không vẽ được tiếng Việt)."""
    h, w = frame.shape[:2]
    max_width = max_width or w
    scale = max_width / float(w) if w > max_width else 1.0
    out = cv2.resize(frame, (int(w * scale), int(h * scale))) if scale != 1.0 else frame.copy()
    if CAT_ROI is not None:
        x1, y1, x2, y2 = (int(v * scale) for v in CAT_ROI)
        cv2.rectangle(out, (x1, y1), (x2, y2), (200, 120, 60), 1)
    for b in boxes:
        x, y, bw, bh = int(b[0] * scale), int(b[1] * scale), int(b[2] * scale), int(b[3] * scale)
        conf = b[4]
        cat_name = b[5] if len(b) > 5 else "Meo"
        cat_conf = b[6] if len(b) > 6 else conf
        cv2.rectangle(out, (x, y), (x + bw, y + bh), (80, 200, 120), 2)
        if cat_name and cat_name != "Meo":
            label = f"{cat_name} {int(cat_conf * 100)}%"
        else:
            label = f"Meo {int(conf * 100)}%"
        (tw, th), _ = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.55, 1)
        cv2.rectangle(out, (x, max(0, y - th - 8)), (x + tw + 8, y), (80, 200, 120), -1)
        cv2.putText(out, label, (x + 4, max(th, y - 5)), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (20, 40, 30), 1, cv2.LINE_AA)
    return out


# ----------------------------- CAMERA -----------------------------
def open_camera():
    """Windows dùng DirectShow (tránh màn hình đen với webcam USB); index 1 không có thì thử 2."""
    candidates = [CAMERA_INDEX] + ([2] if CAMERA_INDEX == 1 else [])
    for idx in candidates:
        cap = None
        if os.name == "nt":
            cap = cv2.VideoCapture(idx, cv2.CAP_DSHOW)
            if not cap.isOpened():
                cap.release()
                cap = None
        if cap is None:
            cap = cv2.VideoCapture(idx)
        if cap.isOpened():
            # Webcam USB chỉ đạt 60 khung/giây ở chế độ nén MJPG (chế độ thô YUYV thường tối đa 30)
            cap.set(cv2.CAP_PROP_FOURCC, cv2.VideoWriter.fourcc(*"MJPG"))
            cap.set(cv2.CAP_PROP_FRAME_WIDTH, CAMERA_WIDTH)
            cap.set(cv2.CAP_PROP_FRAME_HEIGHT, CAMERA_HEIGHT)
            cap.set(cv2.CAP_PROP_FPS, CAMERA_FPS)
            cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)  # luôn lấy khung mới nhất, giảm độ trễ
            if idx != CAMERA_INDEX:
                print(f"[Camera] Không thấy camera {CAMERA_INDEX}, đang dùng camera {idx}")
            print(f"[Camera] Xin {CAMERA_FPS:g} khung/giây, camera báo hỗ trợ {cap.get(cv2.CAP_PROP_FPS):g}")
            return cap
        cap.release()
    return None


# ------------------------------------------------------------------
def main():
    detector = CatDetector() if AI_ENABLED else None
    client = make_client()
    print(f"[MQTT] Kết nối {MQTT_HOST}:{MQTT_PORT} (TLS={MQTT_TLS}) ...")
    client.connect_async(MQTT_HOST, MQTT_PORT, keepalive=30)
    client.loop_start()

    worker = None
    if detector is not None:
        worker = DetectionWorker(detector, client)
        worker.start()
        print(f"[AI] Ngưỡng {CAT_CONFIDENCE} | {STABLE_FRAMES} lần liên tiếp | ROI {CAT_ROI or 'cả khung'}"
              f" | mèo chiếm >= {CAT_NEAR_AREA:.0%} khung | quét mỗi {SCAN_INTERVAL:g}s")
    else:
        print("[AI] TẮT (AI_ENABLED=0): chỉ phát hình trực tiếp, không nhận diện mèo, không tự cho ăn bằng camera."
              " ESP32 vẫn tự cho ăn bằng cảm biến như bình thường.")

    uploader = None
    if STREAM_ON:
        uploader = FrameUploader()
        uploader.start()
        print(f"[Stream] Bật hình trực tiếp: {STREAM_FPS:g} khung/giây, rộng {STREAM_WIDTH}px")
    else:
        print("[Stream] Tắt hình trực tiếp (thiếu AURAPET_SERVER hoặc CAMERA_KEY). Vẫn gửi sự kiện mèo qua MQTT.")

    preview = SHOW_PREVIEW
    cap = None
    last_scan = 0.0
    captured, stats_from, stats_sent = 0, time.time(), 0
    frame_gap = 1.0 / STREAM_FPS if (uploader is not None or preview) else SCAN_INTERVAL
    try:
        while True:
            if cap is None or not cap.isOpened():
                cap = open_camera()
                if cap is None:
                    print("[Camera] Không mở được camera, thử lại sau 5 giây...")
                    time.sleep(5)
                    continue

            t0 = time.time()
            ok, frame = cap.read()
            if not ok:
                # KHÔNG giả lập "có mèo" khi lỗi camera (bản cũ làm máy nhả hạt liên tục)
                print("[Camera] Đọc khung hình lỗi, mở lại camera...")
                cap.release()
                cap = None
                time.sleep(2)
                continue

            captured += 1
            if t0 - stats_from >= 15:
                span = t0 - stats_from
                sent = (uploader.sent - stats_sent) if uploader is not None else 0
                print(f"[Stream] Camera {captured / span:.1f} khung/giây"
                      + (f", gửi lên web {sent / span:.1f} khung/giây" if uploader is not None else ""))
                captured, stats_from = 0, t0
                stats_sent = uploader.sent if uploader is not None else 0

            if frame.shape[1] != CAMERA_WIDTH or frame.shape[0] != CAMERA_HEIGHT:
                frame = cv2.resize(frame, (CAMERA_WIDTH, CAMERA_HEIGHT))  # để ROI luôn đúng toạ độ

            if worker is not None and t0 - last_scan >= SCAN_INTERVAL:
                last_scan = t0
                worker.offer(frame)

            if uploader is not None or preview:
                found, _, boxes, present, pet_name = (worker.latest() if worker is not None
                                                      else (False, 0.0, [], False, ""))
                shown = boxes if found else []
                if uploader is not None:
                    img = annotate(frame, shown, STREAM_WIDTH)
                    ok_jpg, buf = cv2.imencode(".jpg", img, [int(cv2.IMWRITE_JPEG_QUALITY), JPEG_QUALITY])
                    if ok_jpg:
                        best = max((b[4] for b in boxes), default=0.0)
                        uploader.offer(buf.tobytes(), present, best if present else 0.0, pet_name)
                if preview:
                    try:
                        cv2.imshow(WINDOW_NAME, annotate(frame, shown))
                        if cv2.waitKey(1) & 0xFF == ord("q"):
                            break
                    except cv2.error:
                        print("[Preview] OpenCV bản headless không mở được cửa sổ. Cài opencv-python để xem trước.")
                        preview = False

            time.sleep(max(0.0, frame_gap - (time.time() - t0)))
    except KeyboardInterrupt:
        print("\nĐã dừng gateway.")
    finally:
        if cap is not None:
            cap.release()
        if preview:
            cv2.destroyAllWindows()
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    main()
