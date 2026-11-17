# =============================================================================
# AuraPet - Chép phần camera sang Raspberry Pi (chạy trên máy tính Windows)
#
# Chỉ chép những file Pi cần (~20 MB: code + mô hình .onnx), KHÔNG chép .venv / .cache / dataset.
# Pi chạy mô hình bằng ONNX Runtime nên không cần file .pt hay PyTorch.
# Cách dùng (PowerShell, tại thư mục dự án):
#   powershell -ExecutionPolicy Bypass -File scripts\copy_to_pi.ps1 -PiHost 192.168.99.50 -User pi
# Lần đầu SSH sẽ hỏi "Are you sure you want to continue connecting" -> gõ yes, rồi nhập mật khẩu Pi.
# =============================================================================
param(
    [string]$PiHost = "raspberrypi.local",  # IP hoặc tên máy của Pi
    [string]$User = "pi",                   # tài khoản đăng nhập Pi
    [string]$RemoteDir = "aurapet"          # thư mục trên Pi (trong ~)
)
$ErrorActionPreference = 'Stop'
$src = Join-Path (Split-Path -Parent $PSScriptRoot) 'firmware\raspberry_pi'

$files = @(
    'cat_detection_gateway.py', 'onnx_infer.py', 'test_camera.py', 'requirements-pi.txt',
    'setup_pi.sh', 'run_camera.sh', 'aurapet-camera.env', 'yolov8n.onnx'
)
$missing = $files | Where-Object { -not (Test-Path (Join-Path $src $_)) }
if ($missing -contains 'aurapet-camera.env') {
    Write-Host "[!] Thiếu firmware\raspberry_pi\aurapet-camera.env (cấu hình MQTT/CAMERA_KEY cho Pi)." -ForegroundColor Yellow
    exit 1
}
if ($missing -contains 'yolov8n.onnx') {
    Write-Host "[!] Thiếu yolov8n.onnx. Xuất trước: cd firmware\raspberry_pi; .\.venv\Scripts\python.exe export_onnx.py" -ForegroundColor Yellow
    exit 1
}
$files = $files | Where-Object { $missing -notcontains $_ }
$model = Join-Path $src 'models\cat_classifier.onnx'

$target = "$User@$PiHost"
Write-Host "[1/3] Tạo thư mục ~/$RemoteDir/models trên $target ..."
ssh $target "mkdir -p ~/$RemoteDir/models"
if ($LASTEXITCODE -ne 0) { Write-Host "[X] Không SSH được vào $target. Kiểm tra IP, tài khoản, và đã bật SSH trên Pi (sudo raspi-config -> Interface -> SSH)." -ForegroundColor Red; exit 1 }

Write-Host "[2/3] Chép $($files.Count) file ..."
scp ($files | ForEach-Object { Join-Path $src $_ }) "${target}:~/$RemoteDir/"
if ($LASTEXITCODE -ne 0) { exit 1 }
if (Test-Path $model) {
    scp $model "${target}:~/$RemoteDir/models/"
} else {
    Write-Host "    (Chưa có models\cat_classifier.onnx: camera chỉ biết 'có mèo'. Train rồi chạy export_onnx.py)"
}

Write-Host "[3/3] Cho phép chạy các script .sh ..."
ssh $target "cd ~/$RemoteDir && sed -i 's/\r$//' *.sh aurapet-camera.env && chmod +x *.sh"

Write-Host "`nXong. Tiếp theo đăng nhập Pi và cài đặt:" -ForegroundColor Green
Write-Host "  ssh $target"
Write-Host "  cd ~/$RemoteDir && ./setup_pi.sh"
