@echo off
chcp 65001 >nul
title AuraPet - Day Len GitHub va Supabase
color 0B

echo ===================================================================
echo     AURAPET AIoT - KHOI TAO VA DAY CODE LEN GITHUB VA SUPABASE
echo ===================================================================
echo.

:: 1. Kiem tra xem Git da duoc cai dat hay chua
where git >nul 2>nul
if %ERRORLEVEL% EQU 0 goto :GIT_FOUND

if exist "C:\Program Files\Git\cmd\git.exe" (
    set "PATH=C:\Program Files\Git\cmd;%PATH%"
    goto :GIT_FOUND
)
if exist "%LOCALAPPDATA%\Programs\Git\cmd\git.exe" (
    set "PATH=%LOCALAPPDATA%\Programs\Git\cmd;%PATH%"
    goto :GIT_FOUND
)
if exist "C:\Program Files (x86)\Git\cmd\git.exe" (
    set "PATH=C:\Program Files (x86)\Git\cmd;%PATH%"
    goto :GIT_FOUND
)

goto :GIT_NOT_FOUND

:GIT_NOT_FOUND
echo [THONG BAO] May tinh cua ban chua co cong cu Git (hoac chua them vao PATH).
echo.
echo Ban co 2 cach don gian sau de cai Git:
echo   1. Bam Y de he thong tu dong tai va cai dat Git bang Windows Package Manager.
echo   2. Hoac tai thu cong file cai dat tai: https://git-scm.com/download/win
echo.
set "INSTALL_CHOICE="
set /p INSTALL_CHOICE=Ban co muon cai dat Git tu dong ngay bay gio khong? [Y/N]: 
if /i "%INSTALL_CHOICE%"=="Y" goto :RUN_INSTALL_GIT
echo.
echo Vui long cai dat Git tai https://git-scm.com/download/win roi chay lai file nay!
goto :END

:RUN_INSTALL_GIT
echo.
echo Dang khoi dong trinh cai dat Git, vui long nhan 'Yes' neu Windows hoi quyen Administrator...
echo.
winget install --id Git.Git -e --source winget --accept-package-agreements --accept-source-agreements
echo.
echo ===================================================================
echo [HOAN TAT] Neu da cai dat xong Git, vui long DONG CUA SO NAY
echo va MO LAI file setup_git_and_push.bat de he thong nhan dien Git!
echo ===================================================================
goto :END

:GIT_FOUND
cd /d "%~dp0"

:: 2. Cau hinh dinh danh tac gia (tranh loi Author identity unknown)
git config user.name >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [Cau hinh Git] Dat ten tac gia: Thanh-23652731
    git config --global user.name "Thanh-23652731"
)

git config user.email >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [Cau hinh Git] Dat email tac gia: thanh.dev@aurapet.com
    git config --global user.email "thanh.dev@aurapet.com"
)

echo [1/3] Kiem tra Git Repository...
if not exist "%~dp0.git" (
    echo   - Khoi tao Git repository moi...
    git init -b main
) else (
    echo   - Git repo da ton tai san.
)

echo.
echo [2/3] Kiem tra commit du an...
git add .
git status --porcelain | findstr /R "." >nul
if %ERRORLEVEL% EQU 0 (
    echo   - Dang tao commit cac thay doi moi nhat...
    git commit -m "feat: complete AuraPet fullstack AIoT ecosystem with Supabase and GitHub CI/CD"
) else (
    echo   - Cac file da duoc commit day du san sang!
)

git branch -M main

echo.
echo [3/3] Ket noi va day code len GitHub...
git remote get-url origin >nul 2>nul
if %ERRORLEVEL% NEQ 0 goto :ASK_REMOTE_URL

echo   - Remote origin: https://github.com/Thanh-23652731/aurapet
echo   - Dang day code len GitHub (nhanh main)...
echo.
echo * CHU Y: Neu trinh duyet bat len cua so GitHub, hay bam [Sign in with your browser] de cap quyen!
echo.
git push -u origin main
if %ERRORLEVEL% EQU 0 goto :PUSH_DONE
goto :PUSH_ERROR

:ASK_REMOTE_URL
echo.
echo Hay tao mot repository moi tren GitHub: https://github.com/new
set "REPO_URL="
set /p REPO_URL=Dan duong link repo cua ban (VD: https://github.com/Thanh-23652731/aurapet): 

if "%REPO_URL%"=="" (
    echo.
    echo Ban chua nhap link repo. Ban co the push sau bang: git push -u origin main
    goto :END
)

git remote add origin %REPO_URL%
echo.
echo Dang day code len GitHub...
echo * CHU Y: Neu trinh duyet bat len cua so GitHub, hay bam [Sign in with your browser] de cap quyen!
echo.
git push -u origin main
if %ERRORLEVEL% EQU 0 goto :PUSH_DONE
goto :PUSH_ERROR

:PUSH_DONE
echo.
echo ===================================================================
echo [THANH CONG] Toan bo du an da duoc day len GitHub!
echo   - Repo:    https://github.com/Thanh-23652731/aurapet
echo   - Web App: https://Thanh-23652731.github.io/aurapet/
echo   - Huong dan tiep theo tai: docs/DEPLOY_GITHUB_AND_SUPABASE.md
echo ===================================================================
goto :END

:PUSH_ERROR
echo.
echo ===================================================================
echo [CHU Y] Neu gap loi tu choi quyen (Permission denied / 403):
echo   1. Hay dam bao repository https://github.com/Thanh-23652731/aurapet da duoc tao tren GitHub.
echo   2. Chon 'Sign in with your browser' tren cua so Git Credential Manager vua hien len.
echo   3. Neu dung Personal Access Token: nhap Token lam Password.
echo ===================================================================

:END
echo.
echo Bam phim bat ky de thoat...
pause >nul
