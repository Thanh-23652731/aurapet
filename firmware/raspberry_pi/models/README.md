# Thư mục chứa mô hình nhận diện danh tính mèo (Cat Classifier)

Khi bạn huấn luyện mô hình bằng lệnh:
```bash
python firmware/raspberry_pi/train_cat_classifier.py --train
```

Hai file sau sẽ được lưu tại đây:
- `cat_classifier.pt`: Mô hình PyTorch gốc.
- `cat_classifier.onnx`: Mô hình định dạng ONNX tối ưu CPU cho Raspberry Pi.

Script `cat_detection_gateway.py` sẽ tự động quét và nạp mô hình từ thư mục này.
Nếu chưa huấn luyện mô hình, bạn có thể chạy chế độ mô phỏng bằng cách thêm biến môi trường:
```bash
# Ví dụ test trên PowerShell:
$env:DEMO_PET_NAME="Mimi"
python firmware\raspberry_pi\cat_detection_gateway.py
```
