#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AuraPet AIoT - Raspberry Pi 4 Edge Gateway & Cat Vision Detection
Lưu đồ thuật toán: Hình 4 (Cấp nước tự động) & Hình 6 (Cho ăn tự động)
"""

import time
import requests
import cv2

# ===================================================================
# CẤU HÌNH KẾT NỐI VỚI BACKEND WEBSITE
# ===================================================================
# Địa chỉ của Spring Boot Backend (Chạy trên cùng Pi, PC LAN hoặc Cloud):
BACKEND_URL = "http://localhost:8080" # Hoặc "http://10.10.59.64:8080"

# Khoảng thời gian nghỉ giữa các lần quét (giây)
SCAN_INTERVAL_SECONDS = 3

print("=" * 65)
print("   AURAPET AIoT - RASPBERRY PI 4 CAT DETECTION GATEWAY")
print("=" * 65)
print(f"[*] Ket noi toi Backend API: {BACKEND_URL}")

def trigger_food_flow_cycle(cat_detected=True, scale_weight=5):
    """Kích hoạt chu trình Hình 6: Cho ăn tự động khi phát hiện mèo và cân < 10g"""
    url = f"{BACKEND_URL}/api/v1/system/food/flow-cycle"
    params = {
        "catDetected": str(cat_detected).lower(),
        "scaleWeightGram": scale_weight,
        "manualButton": "false"
    }
    try:
        res = requests.post(url, params=params, timeout=3)
        if res.status_code == 200:
            data = res.json().get("data", {})
            print(f"[Food Cycle] Ket qua: Machines={data.get('machines')}, Percel={data.get('percel')}g, LedGreen={data.get('ledGreen')}")
        else:
            print(f"[Food Cycle] HTTP Error: {res.status_code}")
    except Exception as e:
        print(f"[Food Cycle] Khong the ket noi API: {e}")

def trigger_water_flow_cycle(cat_detected=True):
    """Kích hoạt chu trình Hình 4: Bơm nước tự động khi phát hiện mèo đến gần"""
    url = f"{BACKEND_URL}/api/v1/system/water/flow-cycle"
    params = {
        "catDetected": str(cat_detected).lower(),
        "manualButton": "false"
    }
    try:
        res = requests.post(url, params=params, timeout=3)
        if res.status_code == 200:
            data = res.json().get("data", {})
            print(f"[Water Cycle] Ket qua: Machines={data.get('machines')}, LedGreen={data.get('ledGreen')}")
        else:
            print(f"[Water Cycle] HTTP Error: {res.status_code}")
    except Exception as e:
        print(f"[Water Cycle] Khong the ket noi API: {e}")

def main():
    print("[*] Khoi dong Camera OpenCV...")
    cap = cv2.VideoCapture(0)
    
    # Sử dụng bộ phân loại Haar Cascade khuôn mặt mèo của OpenCV nếu có
    cat_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalcatface.xml')

    try:
        while True:
            cat_found = False
            ret, frame = cap.read()
            if ret:
                gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
                cats = cat_cascade.detectMultiScale(gray, scaleFactor=1.2, minNeighbors=3, minSize=(60, 60))
                if len(cats) > 0:
                    cat_found = True
                    print(f"\n[AI Vision] >>> PHAT HIEN THAY {len(cats)} BE MEO! <<<")
            else:
                # Nếu không có camera USB/PiCam thực tế: Chế độ giả lập cảm biến định kỳ
                cat_found = True

            if cat_found:
                # 1. Kích hoạt bơm nước tuần hoàn
                trigger_water_flow_cycle(cat_detected=True)
                # 2. Kiểm tra chu kỳ cho ăn tự động (giả lập lượng thức ăn trong khay = 4 gam < 10 gam)
                trigger_food_flow_cycle(cat_detected=True, scale_weight=4)

            time.sleep(SCAN_INTERVAL_SECONDS)

    except KeyboardInterrupt:
        print("\n[*] Da dung chuong trinh Gateway.")
    finally:
        cap.release()

if __name__ == "__main__":
    main()
