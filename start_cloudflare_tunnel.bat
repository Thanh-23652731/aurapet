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
echo   1. Nhin ben duoi tim dong co link dang:
echo      https://[chu-ngau-nhien].trycloudflare.com
echo   2. Mo link do tren trinh duyet dien thoai / 4G
echo   3. Hoac tren App dien thoai, cham vao [May chu] va dan link do vao!
echo.
echo Nhan Ctrl+C de dung duong ham khi khong su dung.
echo ========================================================
echo.
"%~dp0cloudflared.exe" tunnel --url http://localhost:8080
pause
