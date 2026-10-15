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

if exist "%LOCALAPPDATA%\Programs\Git\cmd\git.exe" (
    set "PATH=%LOCALAPPDATA%\Programs\Git\cmd;%PATH%"
    goto :GIT_FOUND
)
if exist "C:\Program Files\Git\cmd\git.exe" (
    set "PATH=C:\Program Files\Git\cmd;%PATH%"
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
echo [1/4] Kiem tra Git Repository...
if not exist "%~dp0.git" (
    echo   - Khoi tao Git repository moi...
    git init -b main
) else (
    echo   - Git repo da ton tai san.
)

echo.
echo [2/4] Them cac file vao Git (da loai tru file rac va bao mat qua .gitignore)...
git add .

echo.
echo [3/4] Tao Commit luu tru...
git commit -m "feat: complete AuraPet fullstack AIoT ecosystem with GitHub Actions and Supabase support"

echo.
echo [4/4] Cau hinh Remote GitHub...
git remote get-url origin >nul 2>nul
if %ERRORLEVEL% NEQ 0 goto :ASK_REMOTE_URL

echo   - Remote origin da duoc ket noi san.
echo   - Dang day code len GitHub (nhanh main)...
git branch -M main
git push -u origin main
goto :PUSH_DONE

:ASK_REMOTE_URL
echo.
echo Hay tao mot repository moi tren GitHub: https://github.com/new
echo (Luu y: Dat ten repo tuy thich, khong can tich chon Add README vi da co san).
echo.
set "REPO_URL="
set /p REPO_URL=Dan duong link repo cua ban (VD: https://github.com/tenban/aurapet.git): 

if "%REPO_URL%"=="" (
    echo.
    echo Ban chua nhap link repo. Ban co the push sau bang cac lenh:
    echo   git remote add origin ^<LINK_REPO^>
    echo   git branch -M main
    echo   git push -u origin main
    goto :END
)

git remote add origin %REPO_URL%
echo.
echo Dang day code len GitHub...
git branch -M main
git push -u origin main

:PUSH_DONE
echo.
echo ===================================================================
echo [THANH CONG] Code da duoc day len GitHub!
echo   - Xem Web tai: https://[username].github.io/[repo_name]/
echo   - Xem huong dan deploy tiep theo tai: docs/DEPLOY_GITHUB_AND_SUPABASE.md
echo ===================================================================

:END
echo.
echo Bam phim bat ky de thoat...
pause >nul
