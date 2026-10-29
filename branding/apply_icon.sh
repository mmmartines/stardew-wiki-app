#!/usr/bin/env bash
# Gera os ícones do app a partir de branding/app-icon.png (se existir). Usa ImageMagick.
set -e
SRC=branding/app-icon.png
[ -f "$SRC" ] || { echo "Sem branding/app-icon.png: mantendo o ícone próprio."; exit 0; }
BG="#78BEF0"; [ -f branding/app-icon-bg.txt ] && BG=$(tr -d ' \n\r' < branding/app-icon-bg.txt)
RES=app/src/main/res
declare -A SIZE=( [mdpi]=48 [hdpi]=72 [xhdpi]=96 [xxhdpi]=144 [xxxhdpi]=192 )
for d in "${!SIZE[@]}"; do
  s=${SIZE[$d]}; inner=$((s*80/100)); fg=$((s*108/48)); fgin=$((fg*58/100))
  mkdir -p $RES/mipmap-$d
  # ícone legado (quadrado arredondado e redondo)
  convert -size ${s}x${s} xc:"$BG" \( "$SRC" -filter point -resize ${inner}x${inner} \) -gravity center -composite \
    \( -size ${s}x${s} xc:none -draw "roundrectangle 0,0,$((s-1)),$((s-1)),$((s/6)),$((s/6))" \) -compose DstIn -composite $RES/mipmap-$d/ic_launcher.png
  convert -size ${s}x${s} xc:"$BG" \( "$SRC" -filter point -resize ${inner}x${inner} \) -gravity center -composite \
    \( -size ${s}x${s} xc:none -draw "circle $((s/2)),$((s/2)) $((s/2)),0" \) -compose DstIn -composite $RES/mipmap-$d/ic_launcher_round.png
  # camada da frente do ícone adaptativo (área segura central)
  convert -size ${fg}x${fg} xc:none \( "$SRC" -filter point -resize ${fgin}x${fgin} \) -gravity center -composite $RES/mipmap-$d/ic_launcher_fg.png
done
for n in ic_launcher ic_launcher_round; do
cat > $RES/mipmap-anydpi-v26/$n.xml <<XML
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/brand_icon_bg" />
    <foreground android:drawable="@mipmap/ic_launcher_fg" />
</adaptive-icon>
XML
done
mkdir -p $RES/values
cat > $RES/values/brand_icon.xml <<XML
<?xml version="1.0" encoding="utf-8"?>
<resources><color name="brand_icon_bg">$BG</color></resources>
XML
convert -size 512x512 xc:"$BG" \( "$SRC" -filter point -resize 410x410 \) -gravity center -composite docs/icon-512.png
echo "Ícone oficial aplicado a partir de $SRC"
