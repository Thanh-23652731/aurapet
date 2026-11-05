@echo off
title AuraPet - Mo Web Dashboard
:: Backend phai dang chay (start_aurapet.bat)
set "PORT=8080"
for /f "usebackq eol=# tokens=1* delims==" %%A in ("%~dp0.env") do if /i "%%A"=="PORT" set "PORT=%%B"
start "" http://localhost:%PORT%/
