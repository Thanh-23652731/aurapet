#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AuraPet - Kiểm tra camera trên Raspberry Pi (hoặc laptop) TRƯỚC khi chạy cat_detection_gateway.py.

Chạy lần lượt từng bước, mỗi bước báo  [OK] / [LỖI] / [BỎ QUA]  kèm cách sửa:
  1. Camera   : mở camera, đọc thử ~3 giây, đo khung/giây, phát hiện ảnh đen, lưu test_snapshot.jpg
  2. AI       : nạp YOLOv8, tìm mèo trong ảnh, đo thời gian mỗi lần nhận diện, lưu test_detect.jpg
               (có models/cat_classifier.pt thì đọc luôn tên từng bé, vd. Mimi / Tom)
  3. Kết nối  : (nếu có AURAPET_SERVER / MQTT_HOST) thử gọi backend và kết nối MQTT broker
  4. --live N : nhận diện liên tục N giây, in ra mỗi khi thấy mèo (đưa mèo/ảnh mèo trước camera)

Không gửi lệnh hay sự kiện nào lên hệ thống, nên chạy thoải mái không sợ máy nhả hạt.

CÀI ĐẶT TRÊN PI (Raspberry Pi OS 64-bit):
    Máy tính:  powershell -ExecutionPolicy Bypass -File scripts\\copy_to_pi.ps1 -PiHost <IP Pi> -User <tài khoản>
    Pi:        cd ~/aurapet && ./setup_pi.sh && source ~/aurapet-venv/bin/activate
    (camera module cắm cáp dẹt CSI: thêm  sudo apt install -y python3-picamera2  và chạy với --picam)

VÍ DỤ:
    python3 test_camera.py                       # kiểm tra đủ 3 bước
    python3 test_camera.py --live 30             # thêm 30 giây nhận diện liên tục
    python3 test_camera.py --picam               # ép dùng camera module CSI (Picamera2)
    python3 test_camera.py --camera 1            # webcam USB thứ hai
    python3 test_camera.py --image meo.jpg       # không có camera: thử AI trên 1 ảnh
    python3 test_camera.py --no-ai               # chỉ kiểm tra camera (Pi yếu, chưa cài ultralytics)
    AURAPET_SERVER=http://192.168.1.10:8080 MQTT_HOST=broker.emqx.io python3 test_camera.py
"""

import argparse
import os
import socket
import sys
import time
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
CAT_CLASS_ID = 15  # lớp "cat" trong bộ COCO của YOLOv8

RESULTS = []  # (bước, trạng thái, ghi chú)


def report(step, status, note=""):
    RESULTS.append((step, status, note))
    print(f"  [{status}] {step}" + (f" — {note}" if note else ""))


def fix(msg):
    print(f"         → {msg}")


# ---------------------------------------------------------------------------
# 1. CAMERA
# ---------------------------------------------------------------------------
class Camera:
    """Bọc chung webcam USB (OpenCV) và camera module CSI (Picamera2). read() trả ảnh BGR hoặc None."""

    def __init__(self, kind, handle, name):
        self.kind, self.handle, self.name = kind, handle, name

    def read(self):
        if self.kind == "picam":
            import cv2
            rgb = self.handle.capture_array()
            return cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)
        ok, frame = self.handle.read()
        return frame if ok else None

    def close(self):
        try:
            if self.kind == "picam":
                self.handle.stop()
                self.handle.close()
            else:
                self.handle.release()
        except Exception:
            pass


def open_opencv(index, width, height):
    import cv2
    backends = [cv2.CAP_DSHOW, cv2.CAP_ANY] if os.name == "nt" else [cv2.CAP_V4L2, cv2.CAP_ANY]
    for be in backends:
        cap = cv2.VideoCapture(index, be)
        if cap.isOpened():
            cap.set(cv2.CAP_PROP_FRAME_WIDTH, width)
            cap.set(cv2.CAP_PROP_FRAME_HEIGHT, height)
            ok, frame = cap.read()
            if ok and frame is not None:
                return Camera("opencv", cap, f"OpenCV camera {index}")
        cap.release()
    return None


def open_picam(width, height):
    try:
        from picamera2 import Picamera2
    except ImportError:
        return None, "chưa có thư viện picamera2"
    try:
        cam = Picamera2()
        cam.configure(cam.create_video_configuration(main={"size": (width, height), "format": "RGB888"}))
        cam.start()
        time.sleep(1.0)  # chờ cân bằng sáng
        return Camera("picam", cam, "Picamera2 (camera module CSI)"), ""
    except Exception as e:
        return None, str(e)


def list_video_devices():
    devs = sorted(Path("/dev").glob("video*")) if os.name != "nt" else []
    return [d.name for d in devs]


def step_camera(args):
    print("\n1) CAMERA")
    try:
        import cv2
    except ImportError:
        report("OpenCV", "LỖI", "chưa cài opencv")
        fix("pip install opencv-python-headless   (laptop muốn xem cửa sổ: opencv-python)")
        return None

    cam, picam_err = None, ""
    if not args.picam:
        cam = open_opencv(args.camera, args.width, args.height)
    if cam is None and (args.picam or os.name != "nt"):
        cam, picam_err = open_picam(args.width, args.height)

    if cam is None:
        report("Mở camera", "LỖI", f"không mở được camera {args.camera}")
        devs = list_video_devices()
        if devs:
            fix(f"Thiết bị video đang có: {', '.join(devs)}. Thử --camera 1, 2...")
        elif os.name != "nt":
            fix("Không thấy /dev/video*: kiểm tra cáp camera, chạy 'rpicam-hello --list-cameras' (hoặc 'libcamera-hello --list-cameras')")
        if picam_err:
            fix(f"Picamera2: {picam_err}. Camera module CSI: sudo apt install -y python3-picamera2")
        fix("Webcam USB: rút ra cắm lại, chạy 'lsusb' xem có nhận không. Laptop: thử --camera 1")
        return None
    report("Mở camera", "OK", cam.name)

    # Đọc liên tục ~3 giây để đo tốc độ và độ sáng
    frames, dark, t0, last = 0, 0, time.time(), None
    while time.time() - t0 < args.seconds:
        frame = cam.read()
        if frame is None:
            break
        frames += 1
        last = frame
        if frame.mean() < 8:
            dark += 1
    span = max(0.001, time.time() - t0)

    if last is None:
        report("Đọc khung hình", "LỖI", "camera mở được nhưng không trả về hình")
        fix("Camera đang bị chương trình khác dùng? Tắt cat_detection_gateway.py / rpicam rồi chạy lại")
        cam.close()
        return None

    h, w = last.shape[:2]
    fps = frames / span
    report("Đọc khung hình", "OK", f"{w}x{h}, {fps:.1f} khung/giây ({frames} khung trong {span:.1f}s)")
    if fps < 5:
        fix("Chậm: giảm độ phân giải (--width 640 --height 480) hoặc kiểm tra nguồn điện Pi (cần 5V/3A)")
    if dark > frames * 0.8:
        report("Độ sáng", "LỖI", "ảnh gần như đen hoàn toàn")
        fix("Còn nắp che ống kính? Phòng quá tối? Camera USB đôi khi cần vài giây đầu để chỉnh sáng")
    else:
        report("Độ sáng", "OK", f"trung bình {last.mean():.0f}/255")

    out = BASE_DIR / "test_snapshot.jpg"
    cv2.imwrite(str(out), last)
    print(f"         Ảnh chụp thử: {out}")
    return cam


# ---------------------------------------------------------------------------
# 2. AI (YOLOv8 + bộ nhận diện từng bé)
# ---------------------------------------------------------------------------
class Brain:
    """Giống cat_detection_gateway: ưu tiên ONNX Runtime (không cần PyTorch), không có thì dùng ultralytics."""

    def __init__(self, conf, imgsz, inference="auto"):
        self.conf, self.imgsz = conf, imgsz
        self.onnx = None
        det_onnx = BASE_DIR / "yolov8n.onnx"
        if inference in ("auto", "onnx"):
            import onnx_infer
            if onnx_infer.available() and det_onnx.exists():
                cls_onnx = BASE_DIR / "models" / "cat_classifier.onnx"
                self.onnx = onnx_infer.OnnxCatDetector(det_onnx, cls_onnx if cls_onnx.exists() else None)
                self.backend = f"ONNX Runtime, ảnh vào {self.onnx.det_w}x{self.onnx.det_h}"
                self.names = self.onnx.names
                return
            if inference == "onnx":
                raise ImportError("cần onnxruntime và yolov8n.onnx (xuất bằng export_onnx.py trên máy tính)")
        from ultralytics import YOLO
        self.backend = f"PyTorch, ảnh vào {imgsz}"
        local = BASE_DIR / "yolov8n.pt"
        self.detector = YOLO(str(local) if local.exists() else "yolov8n.pt")
        self.classifier = None
        for name in ("cat_classifier.onnx", "cat_classifier.pt"):
            p = BASE_DIR / "models" / name
            if p.exists():
                self.classifier = YOLO(str(p), task="classify")
                self.classifier_path = p
                break
        self.names = list(self.classifier.names.values()) if self.classifier is not None else []

    def detect(self, frame):
        """Trả về [(x1, y1, x2, y2, độ tin cậy, tên bé, độ tin cậy tên)]."""
        if self.onnx is not None:
            return self.onnx.detect(frame, self.conf)
        res = self.detector(frame, imgsz=self.imgsz, conf=self.conf, classes=[CAT_CLASS_ID], verbose=False)
        h, w = frame.shape[:2]
        cats = []
        for r in res:
            for b in r.boxes:
                x1, y1, x2, y2 = (int(v) for v in b.xyxy[0])
                name, nconf = "", 0.0
                if self.classifier is not None:
                    crop = frame[max(0, y1):min(h, y2), max(0, x1):min(w, x2)]
                    if crop.size:
                        c = self.classifier(crop, imgsz=224, verbose=False)[0]
                        name, nconf = c.names[int(c.probs.top1)], float(c.probs.top1conf)
                cats.append((x1, y1, x2, y2, float(b.conf[0]), name, nconf))
        return cats


def draw(frame, cats):
    import cv2
    out = frame.copy()
    for x1, y1, x2, y2, conf, name, nconf in cats:
        cv2.rectangle(out, (x1, y1), (x2, y2), (80, 200, 120), 2)
        label = f"{name} {nconf:.0%}" if name else f"Meo {conf:.0%}"
        cv2.putText(out, label, (x1 + 4, max(18, y1 - 6)), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (80, 200, 120), 2, cv2.LINE_AA)
    return out


def describe(cats):
    if not cats:
        return "không thấy mèo"
    parts = []
    for *_, conf, name, nconf in cats:
        parts.append(f"{name} ({nconf:.0%})" if name else f"mèo ({conf:.0%})")
    return ", ".join(parts)


def step_ai(args, cam):
    print("\n2) AI NHẬN DIỆN MÈO")
    if args.no_ai:
        report("YOLOv8", "BỎ QUA", "--no-ai")
        return None
    try:
        t0 = time.time()
        brain = Brain(args.conf, args.imgsz, args.inference)
        report("Nạp mô hình", "OK", f"YOLOv8n — {brain.backend} ({time.time() - t0:.1f}s)"
               + (f" + nhận diện từng bé: {', '.join(brain.names)}" if brain.names else ""))
    except ImportError as e:
        report("Nạp mô hình", "LỖI", str(e) or "chưa cài thư viện AI")
        fix("pip install -r requirements.txt  (Pi: cần onnxruntime + yolov8n.onnx chép từ máy tính)")
        return None
    except Exception as e:
        report("Nạp mô hình", "LỖI", str(e))
        fix("Chép yolov8n.onnx + models/cat_classifier.onnx từ máy tính (scripts/copy_to_pi.ps1)")
        return None
    if not brain.names:
        print("         (Chưa có models/cat_classifier.onnx: chỉ biết 'có mèo', chưa đọc được tên từng bé)")

    import cv2
    if args.image:
        frame = cv2.imread(args.image)
        if frame is None:
            report("Đọc ảnh", "LỖI", f"không mở được {args.image}")
            return brain
    elif cam is not None:
        frame = cam.read()
    else:
        report("Nhận diện", "BỎ QUA", "không có camera (dùng --image <ảnh mèo> để thử AI)")
        return brain

    times, cats = [], []
    for _ in range(3):  # lần đầu luôn chậm (khởi động), lấy trung bình 2 lần sau
        t0 = time.time()
        cats = brain.detect(frame)
        times.append(time.time() - t0)
    per = sum(times[1:]) / max(1, len(times) - 1)
    size = brain.onnx.det_w if brain.onnx is not None else args.imgsz
    report("Nhận diện", "OK", f"{per * 1000:.0f} ms/lần (ảnh vào {size}) — {describe(cats)}")
    if per > 1.0:
        fix("Hơi chậm: vẫn dùng được (gateway nhận diện ở luồng riêng, hình trực tiếp không bị giật). "
            "Muốn nhanh hơn ~2 lần: trên máy tính chạy export_onnx.py --imgsz 416 rồi chép lại sang Pi")
    if not cats:
        print("         Đưa mèo (hoặc ảnh mèo trên điện thoại) trước camera rồi chạy --live 30 để thử tiếp")
    out = BASE_DIR / "test_detect.jpg"
    cv2.imwrite(str(out), draw(frame, cats))
    print(f"         Ảnh có khung nhận diện: {out}")
    return brain


# ---------------------------------------------------------------------------
# 3. KẾT NỐI (chỉ kiểm tra, không gửi sự kiện)
# ---------------------------------------------------------------------------
def step_network():
    print("\n3) KẾT NỐI")
    server = os.getenv("AURAPET_SERVER", "").strip().rstrip("/")
    if server:
        try:
            import json
            import urllib.request
            with urllib.request.urlopen(server + "/api/v1/ping", timeout=5) as r:
                data = json.loads(r.read().decode("utf-8"))
            if data.get("data", {}).get("app") == "aurapet":
                report("Backend", "OK", server)
            else:
                report("Backend", "LỖI", f"{server} trả lời nhưng không phải AuraPet")
        except Exception as e:
            report("Backend", "LỖI", f"{server}: {e}")
            fix("Pi và máy chạy backend phải cùng mạng; mở cổng 8080 trên tường lửa Windows; thử ping IP máy đó")
        if not os.getenv("CAMERA_KEY", "").strip():
            print("         (Chưa đặt CAMERA_KEY: gateway sẽ không gửi hình trực tiếp lên web)")
    else:
        report("Backend", "BỎ QUA", "chưa đặt AURAPET_SERVER")

    host = os.getenv("MQTT_HOST", "").strip()
    if host:
        port = int(os.getenv("MQTT_PORT", "1883"))
        try:
            with socket.create_connection((host, port), timeout=5):
                pass
            report("MQTT broker", "OK", f"mở được kết nối tới {host}:{port}")
        except Exception as e:
            report("MQTT broker", "LỖI", f"{host}:{port}: {e}")
            fix("Kiểm tra MQTT_HOST/MQTT_PORT giống file .env của backend; mạng có chặn cổng 1883/8883 không")
        if not os.getenv("MQTT_TOPIC_PREFIX", "").strip():
            print("         (Chưa đặt MQTT_TOPIC_PREFIX: gateway sẽ không chạy — phải giống .env backend)")
    else:
        report("MQTT broker", "BỎ QUA", "chưa đặt MQTT_HOST")


# ---------------------------------------------------------------------------
# 4. CHẠY LIÊN TỤC
# ---------------------------------------------------------------------------
def step_live(args, cam, brain):
    if not args.live:
        return
    print(f"\n4) NHẬN DIỆN LIÊN TỤC {args.live} GIÂY (Ctrl+C để dừng)")
    if cam is None or brain is None:
        report("Chạy liên tục", "BỎ QUA", "cần cả camera và AI hoạt động")
        return
    import cv2
    seen, last_desc, t_end, n = 0, None, time.time() + args.live, 0
    show = args.preview
    try:
        while time.time() < t_end:
            frame = cam.read()
            if frame is None:
                print("  Mất hình từ camera")
                break
            cats = brain.detect(frame)
            n += 1
            desc = describe(cats)
            if desc != last_desc:
                print(f"  {time.strftime('%H:%M:%S')}  {desc}")
                last_desc = desc
            if cats:
                seen += 1
            if show:
                cv2.imshow("AuraPet - test camera (q de thoat)", draw(frame, cats))
                if cv2.waitKey(1) & 0xFF == ord("q"):
                    break
    except KeyboardInterrupt:
        pass
    finally:
        if show:
            cv2.destroyAllWindows()
    report("Chạy liên tục", "OK", f"{n} lần nhận diện, thấy mèo {seen} lần")


# ---------------------------------------------------------------------------
def main():
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass
    p = argparse.ArgumentParser(description="AuraPet - kiểm tra camera và AI nhận diện mèo")
    p.add_argument("--camera", type=int, default=int(os.getenv("CAMERA_INDEX", "0")), help="Số thứ tự camera (mặc định 0)")
    p.add_argument("--picam", action="store_true", help="Dùng camera module CSI qua Picamera2")
    p.add_argument("--width", type=int, default=int(os.getenv("CAMERA_WIDTH", "640")))
    p.add_argument("--height", type=int, default=int(os.getenv("CAMERA_HEIGHT", "480")))
    p.add_argument("--seconds", type=float, default=3.0, help="Thời gian đọc thử camera (mặc định 3 giây)")
    p.add_argument("--no-ai", action="store_true", help="Bỏ qua bước AI")
    p.add_argument("--image", help="Thử AI trên 1 ảnh thay vì camera")
    p.add_argument("--imgsz", type=int, default=int(os.getenv("YOLO_IMGSZ", "320")), help="Kích thước ảnh cho YOLO (mặc định 320)")
    p.add_argument("--conf", type=float, default=float(os.getenv("CAT_CONFIDENCE", "0.4")), help="Ngưỡng tin cậy (mặc định 0.4)")
    p.add_argument("--inference", choices=["auto", "onnx", "torch"], default=os.getenv("INFERENCE", "auto"),
                   help="auto (mặc định): có ONNX Runtime + yolov8n.onnx thì dùng, không thì PyTorch")
    p.add_argument("--live", type=int, default=0, help="Nhận diện liên tục N giây")
    p.add_argument("--preview", action="store_true", help="Mở cửa sổ xem hình khi --live (cần màn hình)")
    args = p.parse_args()

    print("=" * 60)
    print(" AuraPet - KIỂM TRA CAMERA")
    print("=" * 60)
    cam = None if args.image else step_camera(args)
    brain = step_ai(args, cam)
    step_network()
    step_live(args, cam, brain)
    if cam is not None:
        cam.close()

    errors = [r for r in RESULTS if r[1] == "LỖI"]
    print("\n" + "=" * 60)
    if errors:
        print(f" CÓ {len(errors)} LỖI: " + "; ".join(f"{s}" for s, _, _ in errors))
        print(" Sửa theo gợi ý '→' ở trên rồi chạy lại.")
    else:
        print(" TẤT CẢ ĐỀU ỔN. Có thể chạy cat_detection_gateway.py")
    print("=" * 60)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
