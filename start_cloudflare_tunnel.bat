@echo off
title AuraPet - Cloudflare HTTPS Tunnel
color 0B
echo ========================================================
echo       AURAPET - TAO DUONG HAM HTTPS CLOUDFLARE
echo ========================================================
echo.
echo Dang mo duong ham HTTPS cho backend (http://localhost:8080)...
echo.
echo * HUONG DAN SU DUNG:
echo   1. QUAN TRONG: Hay chac chan ban DA KHOI CHAY file "start_aurapet.bat" truoc!
echo   2. Nhin ben duoi tim dong co link dang:
echo      https://[chu-ngau-nhien].trycloudflare.com
echo   3. Mo link do tren trinh duyet dien thoai / 4G (hoac dan vao code ESP32)
echo   4. Hoac tren App dien thoai, cham vao [May chu] va dan link do vao!
echo.
echo Nhan Ctrl+C de dung duong ham khi khong su dung.
echo ========================================================
echo.
"%~dp0cloudflared.exe" tunnel --url http://127.0.0.1:8080
pause
