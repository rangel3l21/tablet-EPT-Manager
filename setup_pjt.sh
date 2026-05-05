#!/bin/bash

# --- Configurações ---
PKG_NAME="deltazero.amarok.foss"
ADMIN_RECEIVER="deltazero.amarok.receivers.AdminReceiver"
# Busca o APK mais recente na pasta de build
APK_PATH=$(find app/build/outputs/apk/foss/debug -name "*.apk" | head -n 1)

echo "------------------------------------------------"
echo "🚀 PJT - Iniciando Setup do Modo Deus"
echo "------------------------------------------------"

# Verificar se o dispositivo está conectado
if ! adb get-state 1>/dev/null 2>&1; then
    echo "❌ Erro: Nenhum dispositivo Android detectado via ADB."
    echo "Conecte o tablet e ative a Depuração USB."
    exit 1
fi

# Verificar se o APK existe
if [ -z "$APK_PATH" ]; then
    echo "❌ Erro: APK não encontrado. Rode './gradlew assembleFossDebug' primeiro."
    exit 1
fi

echo "📦 Instalando APK: $APK_PATH..."
adb install -r "$APK_PATH"

if [ $? -eq 0 ]; then
    echo "✅ Instalação concluída com sucesso."
else
    echo "❌ Erro na instalação."
    exit 1
fi

echo "🛡️ Ativando Modo Deus (Device Owner)..."
echo "Aviso: Se houver contas do Google no tablet, o comando abaixo pode falhar."

adb shell dpm set-device-owner "$PKG_NAME/$ADMIN_RECEIVER"

if [ $? -eq 0 ]; then
    echo "------------------------------------------------"
    echo "✅ SUCESSO! O PJT agora é dono do dispositivo."
    echo "Ocultação de apps e Firewall agora estão ativos."
    echo "------------------------------------------------"
else
    echo "------------------------------------------------"
    echo "❌ FALHA ao ativar Modo Deus."
    echo "DICA: Remova todas as contas do Google nas Configurações do tablet e tente novamente."
    echo "------------------------------------------------"
fi
