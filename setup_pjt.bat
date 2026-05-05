@echo off
setlocal enabledelayedexpansion

echo ================================================
echo    🛡️  SISTEMA PJT - INSTALADOR WINDOWS  🛡️
echo ================================================

:: Configurações
set PKG_NAME=deltazero.amarok.foss
set ADMIN_RECEIVER=deltazero.amarok.receivers.AdminReceiver

:: 1. Verificar se ADB está no PATH
where adb >nul 2>nul
if %errorlevel% neq 0 (
    echo [X] Erro: ADB nao encontrado. Instale o Platform Tools e adicione ao PATH.
    pause
    exit /b
)

:: 2. Localizar o APK automaticamente
set APK_PATH=
for /r "app\build\outputs\apk\foss\debug" %%f in (*.apk) do (
    set APK_PATH=%%f
)

if "%APK_PATH%"=="" (
    echo [X] Erro: APK nao encontrado. Rode 'gradlew assembleFossDebug' primeiro.
    pause
    exit /b
)

echo [i] Dispositivo detectado?
adb get-state >nul 2>nul
if %errorlevel% neq 0 (
    echo [X] Erro: Nenhum dispositivo Android conectado ou Depuracao USB desativada.
    pause
    exit /b
)

echo [1/2] Instalando APK: %APK_PATH%
adb install -r "%APK_PATH%"

echo [2/2] Ativando Modo Deus (Device Owner)...
echo (Aviso: Remova as contas do Google do tablet antes deste passo)
adb shell dpm set-device-owner %PKG_NAME%/%ADMIN_RECEIVER%

if %errorlevel% equ 0 (
    echo ================================================
    echo    ✅ SUCESSO! O PJT AGORA E DONO DO DISPOSITIVO.
    echo ================================================
) else (
    echo ================================================
    echo    ❌ FALHA na ativacao. 
    echo    Verifique se removeu as contas do Google do tablet.
    echo ================================================
)

pause
