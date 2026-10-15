# ===================================================================
# AuraPet AIoT - Day Code Len GitHub & Supabase (PowerShell)
# ===================================================================

$Host.UI.RawUI.WindowTitle = "AuraPet - Push to GitHub & Supabase"
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "    AURAPET AIoT - KHOI TAO VA DAY CODE LEN GITHUB & SUPABASE     " -ForegroundColor Cyan
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Kiem tra Git
$gitCmd = Get-Command git -ErrorAction SilentlyContinue

if (-not $gitCmd) {
    # Thu kiem tra duong dan mac dinh
    $commonPaths = @(
        "$env:LOCALAPPDATA\Programs\Git\cmd\git.exe",
        "C:\Program Files\Git\cmd\git.exe",
        "C:\Program Files (x86)\Git\cmd\git.exe"
    )
    foreach ($p in $commonPaths) {
        if (Test-Path $p) {
            $gitDir = Split-Path $p
            $env:PATH = "$gitDir;$env:PATH"
            $gitCmd = Get-Command git -ErrorAction SilentlyContinue
            break
        }
    }
}

if (-not $gitCmd) {
    Write-Host "[CANH BAO] May tinh cua ban chua co cong cu Git!" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Ban co muon tu dong cai dat Git qua winget khong? (Y/N): " -NoNewline -ForegroundColor Green
    $choice = Read-Host
    if ($choice -match '^[Yy]') {
        Write-Host "Dang cai dat Git qua Windows Package Manager (winget)..." -ForegroundColor Cyan
        Start-Process "winget" -ArgumentList "install --id Git.Git -e --source winget --accept-package-agreements --accept-source-agreements" -Wait
        Write-Host ""
        Write-Host "[HOAN TAT] Vui long DONG cua so nay va MO LAI de PATH duoc cap nhat!" -ForegroundColor Green
    } else {
        Write-Host "Vui long tai Git tai: https://git-scm.com/download/win" -ForegroundColor Yellow
    }
    Read-Host "Nhan Enter de thoat"
    exit
}

Write-Host "[OK] Da tim thay Git: $(git --version)" -ForegroundColor Green
Write-Host ""

# 2. Kiem tra Git Repo
Set-Location -Path $PSScriptRoot
if (-not (Test-Path "$PSScriptRoot\.git")) {
    Write-Host "[1/4] Khoi tao Git Repository moi..." -ForegroundColor Cyan
    git init -b main
} else {
    Write-Host "[1/4] Git Repository da ton tai san." -ForegroundColor Cyan
}

# 3. Add files
Write-Host "[2/4] Them cac file vao Git (theo bo loc .gitignore)..." -ForegroundColor Cyan
git add .

# 4. Commit
Write-Host "[3/4] Tao Commit luu tru..." -ForegroundColor Cyan
git commit -m "feat: complete AuraPet fullstack AIoT ecosystem with GitHub Actions and Supabase support"

# 5. Remote & Push
Write-Host "[4/4] Kiem tra Remote Repository..." -ForegroundColor Cyan
$remotes = git remote
if (-not ($remotes -contains "origin")) {
    Write-Host ""
    Write-Host "Tao repository moi tai: https://github.com/new" -ForegroundColor Yellow
    Write-Host "Dan link repo cua ban (VD: https://github.com/username/aurapet.git): " -NoNewline -ForegroundColor Green
    $repoUrl = Read-Host
    if ([string]::IsNullOrWhiteSpace($repoUrl)) {
        Write-Host "Chua nhap link repo. Ban co the push sau bang: git push -u origin main" -ForegroundColor Yellow
        Read-Host "Nhan Enter de thoat"
        exit
    }
    git remote add origin $repoUrl
}

Write-Host "Dang day code len nhanh main cua GitHub..." -ForegroundColor Cyan
git branch -M main
git push -u origin main

Write-Host ""
Write-Host "===================================================================" -ForegroundColor Green
Write-Host "[THANH CONG] Toan bo du an da duoc day len GitHub!" -ForegroundColor Green
Write-Host "Huong dan tiep theo tai file: docs/DEPLOY_GITHUB_AND_SUPABASE.md" -ForegroundColor Cyan
Write-Host "===================================================================" -ForegroundColor Green
Read-Host "Nhan Enter de thoat"
