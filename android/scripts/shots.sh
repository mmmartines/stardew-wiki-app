#!/bin/bash
# Prints automáticos do APK de teste no emulador do GitHub (branch "mods").
set -x
PKG=io.github.mmmartines.guiadovale.debug
ACT=$PKG/com.stardew.wikipt.MainActivity
OUT=shots
mkdir -p $OUT
adb install -r app-debug.apk
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell cmd locale set-app-locales $PKG --locales pt-BR || true
# idioma já escolhido (pula a primeira abertura)
adb shell am start -W -n $ACT; sleep 4; adb shell am force-stop $PKG
cat > /tmp/wiki.xml <<'X'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="lang">pt</string>
    <boolean name="migrated_v2" value="true" />
    <boolean name="pending_welcome" value="false" />
</map>
X
adb push /tmp/wiki.xml /data/local/tmp/wiki.xml
adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cp /data/local/tmp/wiki.xml shared_prefs/wiki.xml'"

shot() { # nome, espera, extras...
  name=$1; wait=$2; shift 2
  adb shell am force-stop $PKG
  adb shell am start -W -n $ACT "$@"
  sleep "$wait"
  adb exec-out screencap -p > "$OUT/$name.png"
}
shot 01-inicio-jogo-base 12
shot 02-inicio-sve 12 --es shot_src sve
shot 03-inicio-ridgeside 12 --es shot_src rsv
shot 04-inicio-vapius 12 --es shot_src mtv
shot 05-busca-global-sophia 20 --es shot_q Sophia
shot 06-busca-global-parsnip 20 --es shot_q Parsnip
shot 07-artigo-sve-sophia 16 --es shot_page Sophia --es shot_page_src sve
shot 08-artigo-sunberry-maia 16 --es shot_page Maia --es shot_page_src sbv
shot 09-artigo-base-abigail 16 --es shot_page Abigail
adb shell cmd uimode night yes
shot 10-inicio-sve-escuro 12 --es shot_src sve
shot 11-artigo-rsv-escuro 16 --es shot_page Lenny --es shot_page_src rsv
adb logcat -d -s AndroidRuntime:E > $OUT/crash.txt || true
