#!/bin/bash

# Script per avviare l'emulatore Android

echo "🚀 Avvio emulatore Android..."

# Path dell'emulatore
EMULATOR=~/Library/Android/sdk/emulator/emulator

# Lista emulatori disponibili
echo ""
echo "📱 Emulatori disponibili:"
$EMULATOR -list-avds

# Avvia emulatore (usa il primo disponibile o specifica uno)
AVD_NAME="Medium_Phone_API_34"

echo ""
echo "🎯 Avvio $AVD_NAME..."
$EMULATOR -avd $AVD_NAME -no-snapshot-load &

# Aspetta che sia pronto
echo ""
echo "⏳ Attendere 30-60 secondi per l'avvio completo..."
echo ""
echo "Quando l'emulatore è pronto, installa l'app con:"
echo "  ./gradlew installDebug"
echo ""
