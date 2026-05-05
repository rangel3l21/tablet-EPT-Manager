#!/bin/bash

# --- Configurações do Sistema PJT ---
PACKAGE="deltazero.amarok.foss"
RECEIVER="deltazero.amarok.receivers.AdminReceiver"

echo "================================================"
echo "   🛡️  SISTEMA PJT - INSTALAÇÃO E ATIVAÇÃO  🛡️"
echo "================================================"

# 1. Compilação
echo "🔨 Compilando APK mais recente..."
./gradlew assembleFossDebug

# 2. Localização do APK
APK=$(find app/build/outputs/apk/foss/debug -name "*.apk" | head -n 1)

if [ -z "$APK" ]; then
    echo "❌ Erro: APK não encontrado. Verifique o build."
    exit 1
fi

# 3. Instalação
echo "📦 Instalando no tablet: $APK"
adb install -r "$APK"

# 4. Ativação do Modo Deus
echo "👑 Ativando Permissões de Device Owner..."
adb shell dpm set-device-owner "$PACKAGE/$RECEIVER"

if [ $? -eq 0 ]; then
    echo "================================================"
    echo "       ✅ CONFIGURAÇÃO CONCLUÍDA!        "
    echo "   O PJT já está operando no Modo Deus.  "
    echo "================================================"
else
    echo "================================================"
    echo "       ⚠️  AVISO DE ATIVAÇÃO ⚠️         "
    echo " Erro ao ativar Modo Deus. Verifique se:      "
    echo " 1. O tablet não tem contas do Google.        "
    echo " 2. Nenhum outro app é Device Owner.          "
    echo "================================================"
fi
