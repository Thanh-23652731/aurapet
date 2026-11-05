# Chuẩn bị file .env trước khi chạy backend (được start_aurapet.bat gọi tự động):
#  - Thay AURAPET_JWT_SECRET nếu thiếu, quá ngắn hoặc là giá trị đã bị lộ trên GitHub
#  - Thêm cấu hình MQTT, lấy TOPIC_PREFIX từ firmware/esp32_aurapet/secrets.h nếu .env chưa có
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $root '.env'
if (-not (Test-Path $envPath)) { Copy-Item (Join-Path $root '.env.example') $envPath }
$text = [IO.File]::ReadAllText($envPath)
$leaked = @('aurapet_super_secret_jwt_key_2026_very_long_secure_token_512bits_aiot',
            'd774dfc564fce6b2d9d56de4985e8152f0e3bb944f53c1f87bb15bc2d4a11ff2')
$m = [regex]::Match($text, '(?m)^AURAPET_JWT_SECRET=(.*)$')
$cur = if ($m.Success) { $m.Groups[1].Value.Trim() } else { '' }
if ($cur.Length -lt 32 -or $leaked -contains $cur) {
    $bytes = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $new = -join ($bytes | ForEach-Object { $_.ToString('x2') })
    if ($m.Success) { $text = $text.Replace($m.Value, "AURAPET_JWT_SECRET=$new") }
    else { $text = $text.TrimEnd() + "`r`nAURAPET_JWT_SECRET=$new`r`n" }
    Write-Host '[env] Da tao AURAPET_JWT_SECRET moi (gia tri cu da bi lo hoac qua ngan).'
}
if ($text -notmatch '(?m)^MQTT_TOPIC_PREFIX=\S') {
    $prefix = ''
    $secrets = Join-Path $root 'firmware\esp32_aurapet\secrets.h'
    if (Test-Path $secrets) {
        $s = [regex]::Match([IO.File]::ReadAllText($secrets), '#define\s+TOPIC_PREFIX\s+"([^"]+)"')
        if ($s.Success) { $prefix = $s.Groups[1].Value }
    }
    $text = [regex]::Replace($text, '(?m)^MQTT_TOPIC_PREFIX=.*\r?\n?', '')
    if ($text -notmatch '(?m)^MQTT_HOST=') {
        $text = $text.TrimEnd() + "`r`n`r`n# MQTT - phai khop firmware/esp32_aurapet/secrets.h`r`nMQTT_HOST=broker.emqx.io`r`nMQTT_PORT=1883`r`nMQTT_TLS=false`r`nMQTT_USERNAME=`r`nMQTT_PASSWORD="
    }
    $text = $text.TrimEnd() + "`r`nMQTT_TOPIC_PREFIX=$prefix`r`n"
    Write-Host "[env] Da them MQTT_TOPIC_PREFIX tu secrets.h."
}
[IO.File]::WriteAllText($envPath, $text, (New-Object Text.UTF8Encoding($false)))
