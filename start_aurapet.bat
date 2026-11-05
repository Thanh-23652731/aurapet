@echo off
chcp 65001 >nul
title AuraPet - Backend
color 0A
cd /d "%~dp0"
echo ========================================================
echo         AURAPET - HE THONG CHAM SOC MEO THONG MINH
echo ========================================================
echo.

:: 1. Chuan bi va nap bien moi truong tu .env
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\prepare_env.ps1"
if errorlevel 1 (
    echo [X] Khong chuan bi duoc file .env
    pause & exit /b 1
)
for /f "usebackq eol=# tokens=1* delims==" %%A in (".env") do (
    if not "%%A"=="" set "%%A=%%B"
)
if "%AURAPET_JWT_SECRET%"=="" (
    echo [X] Thieu AURAPET_JWT_SECRET trong .env
    pause & exit /b 1
)
if "%MQTT_TOPIC_PREFIX%"=="" echo [!] Chua co MQTT_TOPIC_PREFIX trong .env - se KHONG dieu khien duoc may cho an.

:: 2. Khoi dong MariaDB (neu cai dat dang service)
net start MariaDB >nul 2>&1

:: 3. Xac dinh trinh bien dich Maven
set "MVN=mvn"
where mvn >nul 2>nul
if errorlevel 1 (
    if exist "%USERPROFILE%\maven\apache-maven-3.9.9\bin\mvn.cmd" (
        set "MVN=%USERPROFILE%\maven\apache-maven-3.9.9\bin\mvn.cmd"
        set "PATH=%USERPROFILE%\maven\apache-maven-3.9.9\bin;%PATH%"
    ) else (
        for /d %%D in ("%ProgramFiles%\apache-maven-*") do (
            if exist "%%D\bin\mvn.cmd" (
                set "MVN=%%D\bin\mvn.cmd"
                set "PATH=%%D\bin;%PATH%"
            )
        )
    )
)

:: 4. Build lai neu ma nguon moi hon file JAR hoac chua co JAR
set "JAR=backend\target\aurapet-backend-1.0.0-SNAPSHOT.jar"
powershell -NoProfile -Command "$j=Get-Item '%JAR%' -EA SilentlyContinue; $s=Get-ChildItem backend\src,backend\pom.xml -Recurse -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1; if(-not $j -or $s.LastWriteTime -gt $j.LastWriteTime){exit 1}else{exit 0}"
if errorlevel 1 (
    echo [*] Ma nguon da thay doi - dang build lai backend...
    call "%MVN%" -f backend\pom.xml clean package -DskipTests
    if errorlevel 1 (
        echo.
        echo [X] Build that bai. Kiem tra Maven va loi phia tren.
        pause & exit /b 1
    )
    echo [*] Build backend thanh cong!
)

:: 5. Thong tin ket noi
set "LAN_IP="
for /f "tokens=*" %%i in ('powershell -NoProfile -Command "(Get-NetIPAddress -AddressFamily IPv4 -PrefixOrigin Dhcp -ErrorAction SilentlyContinue | Select-Object -First 1).IPAddress"') do set "LAN_IP=%%i"
echo.
echo   Web dashboard : http://localhost:%PORT%
if not "%LAN_IP%"=="" echo   Dien thoai    : http://%LAN_IP%:%PORT%  (cung mang Wi-Fi)
echo   Tai lieu API  : http://localhost:%PORT%/swagger-ui.html
echo   Tai khoan mau : admin@aurapet.com / Admin@123
echo --------------------------------------------------------
java -jar "%JAR%"
pause
