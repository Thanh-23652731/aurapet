@echo off
title AuraPet AIoT - Web Dashboard Quan Ly Cho An & Uong
color 0B
echo ===================================================================
echo     AURAPET AIoT - TRANG WEB QUAN LY & THEO DOI CHO AN/UONG
echo                   (Muc 3.5 - Raspberry Pi 4 + ESP32)
echo ===================================================================
echo.
echo [1/2] Dang mo Dashboard tren trinh duyet...
start http://localhost:8080/index.html

echo [2/2] Mo HeidiSQL kiem tra co so du lieu MariaDB...
if exist "C:\Program Files\Common Files\MariaDBShared\HeidiSQL\heidisql.exe" (
    start "" "C:\Program Files\Common Files\MariaDBShared\HeidiSQL\heidisql.exe" -d="AuraPet DB"
)

set "CURRENT_IP=10.10.59.64"
for /f "tokens=*" %%i in ('powershell -NoProfile -Command "(Get-NetIPAddress -InterfaceAlias 'Wi-Fi*' -AddressFamily IPv4 -ErrorAction SilentlyContinue).IPAddress"') do set "CURRENT_IP=%%i"

echo.
echo Web Dashboard dang chay tai dia chi:
echo   - Local PC:   http://localhost:8080/index.html
echo   - Mang LAN:   http://%CURRENT_IP%:8080/index.html
echo.
echo Bam phim bat ky de dong cua so nay...
pause >nul
