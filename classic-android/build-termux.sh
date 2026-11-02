#!/data/data/com.termux/files/usr/bin/bash
# One-step build for Termux. Run from anywhere:  bash ~/DailyWorkLog/build-termux.sh
set -e
cd "$(dirname "$0")"
echo "sdk.dir=$HOME/android-sdk" > local.properties
grep -q aapt2FromMavenOverride gradle.properties || \
  echo "android.aapt2FromMavenOverride=$PREFIX/bin/aapt2" >> gradle.properties
# App owner key (made on the owner's phone under Approvals): build it in so only the owner can approve.
# A copy is kept in Termux too, in case the one in Downloads is cleaned up.
NEWKEY=$(ls -t ~/storage/downloads/dailyworklog-owner-key*.txt 2>/dev/null | head -1)
if [ -n "$NEWKEY" ]; then
  cp "$NEWKEY" ~/.dailyworklog-owner-key.txt
fi
if [ -f ~/.dailyworklog-owner-key.txt ]; then
  mkdir -p app/src/main/assets
  cp ~/.dailyworklog-owner-key.txt app/src/main/assets/owner_key.txt
  echo "Owner key included: other phones will need your approval."
fi
termux-wake-lock 2>/dev/null || true
~/gradle-8.10.2/bin/gradle assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/DailyWorkLog.apk
termux-wake-unlock 2>/dev/null || true
echo
echo "Done! DailyWorkLog.apk is in your Downloads folder."
