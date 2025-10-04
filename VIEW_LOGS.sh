#!/bin/bash

# Script per visualizzare i log dell'app Realtime Chat
# Con colori e filtri per debugging

echo "======================================"
echo "📱 REALTIME CHAT - LIVE LOGS"
echo "======================================"
echo ""
echo "Premi CTRL+C per fermare"
echo ""

# Clear previous logs
~/Library/Android/sdk/platform-tools/adb logcat -c

# Start logging with colors
~/Library/Android/sdk/platform-tools/adb logcat | grep --line-buffered -E "MainViewModel|RealtimeClient|AudioManager|McpBridge" | while read line; do
    # Color coding
    if echo "$line" | grep -q "✅"; then
        echo -e "\033[0;32m$line\033[0m"  # Green
    elif echo "$line" | grep -q "❌\|ERROR"; then
        echo -e "\033[0;31m$line\033[0m"  # Red
    elif echo "$line" | grep -q "📤\|📨\|🔊\|🎤"; then
        echo -e "\033[0;36m$line\033[0m"  # Cyan
    elif echo "$line" | grep -q "🔧"; then
        echo -e "\033[0;35m$line\033[0m"  # Magenta
    else
        echo "$line"
    fi
done
