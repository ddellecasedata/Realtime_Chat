#!/bin/bash

echo "📱 Installazione Realtime Chat su Telefono Fisico"
echo ""

ADB=~/Library/Android/sdk/platform-tools/adb

# Chiudi emulatore se aperto
echo "🛑 Chiudo emulatore..."
$ADB -s emulator-5554 emu kill 2>/dev/null
sleep 2

# Riavvia adb server
echo "🔄 Riavvio ADB server..."
$ADB kill-server
$ADB start-server
sleep 2

# Lista dispositivi
echo ""
echo "📱 Dispositivi collegati:"
$ADB devices
echo ""

# Conta dispositivi (escludendo header e emulatori)
DEVICE_COUNT=$($ADB devices | grep -v "List of devices" | grep -v "emulator" | grep "device$" | wc -l)

if [ $DEVICE_COUNT -eq 0 ]; then
    echo "❌ Nessun telefono collegato!"
    echo ""
    echo "📋 Checklist:"
    echo "  1. ✅ Telefono collegato con cavo USB"
    echo "  2. ✅ Developer Options abilitato"
    echo "  3. ✅ USB Debugging abilitato"
    echo "  4. ✅ Popup 'Consenti debug USB' → Autorizza"
    echo ""
    exit 1
fi

echo "✅ Telefono trovato!"
echo ""

# Installa app
echo "📦 Installazione app..."
./gradlew installDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ App installata con successo!"
    echo ""
    echo "🚀 Avvio app..."
    $ADB shell am start -n com.things5.realtimechat/.MainActivity
    echo ""
    echo "✅ App avviata sul telefono!"
    echo ""
    echo "📊 Per vedere i log in tempo reale:"
    echo "  $ADB logcat | grep -E 'RealtimeClient|McpBridge|MainViewModel'"
else
    echo ""
    echo "❌ Errore durante l'installazione"
    exit 1
fi
