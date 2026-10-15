@echo off
title AuraPet - Smart Pet Care Backend
color 0A
echo ========================================================
echo         AURAPET - HE THONG CHAM SOC MEO THONG MINH
echo ========================================================
echo.

:: 1. Kiem tra va khoi dong MariaDB neu chua chay
echo [1/3] Kiem tra dich vu MariaDB...
net start MariaDB >nul 2>&1
echo       MariaDB dang hoat dong o cong 3306.

:: 2. Hien thi thong tin ket noi
set "CURRENT_IP=10.10.59.64"
for /f "tokens=*" %%i in ('powershell -NoProfile -Command "(Get-NetIPAddress -InterfaceAlias 'Wi-Fi*' -AddressFamily IPv4 -ErrorAction SilentlyContinue).IPAddress"') do set "CURRENT_IP=%%i"
echo [2/3] Thong tin ket noi HeidiSQL va App Dien Thoai:
echo       - MariaDB Host: 127.0.0.1 : 3306 (User: root / Pass: 1234)
echo       - Database: aurapetdb
echo       - IP Wi-Fi may tinh cho Dien Thoai: http://%CURRENT_IP%:8080
echo       - Swagger UI: http://localhost:8080/swagger-ui.html
echo.
echo       * LUU Y: Dien thoai phai ket noi chung mang Wi-Fi voi may tinh!
echo       * Tren App Dien Thoai: Bam nut [May chu] o duoi cung va nhap: http://%CURRENT_IP%:8080
echo.

:: 3. Doc file .env neu ton tai
if exist "%~dp0.env" (
    echo [3/4] Nap bien moi truong tu file .env...
    for /f "usebackq tokens=1* delims==" %%A in ("%~dp0.env") do (
        echo %%A | findstr /r "^#" >nul
        if errorlevel 1 (
            if not "%%A"=="" set "%%A=%%B"
        )
    )
)

:: 4. Chay Backend
echo [4/4] Dang khoi chay AuraPet Backend...
echo       Nhan Ctrl+C de tat he thong khi khong su dung.
echo --------------------------------------------------------
java -jar "%~dp0backend\target\aurapet-backend-1.0.0-SNAPSHOT.jar"

pause
