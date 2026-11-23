#!/usr/bin/env python3
"""Gera os textos do app iOS a partir dos strings.xml do Android (fonte única dos 12 idiomas).

Uso: python3 ios/scripts/gen_strings.py
Saída: ios/GuiaDoVale/Resources/<idioma>.lproj/Localizable.strings e InfoPlist.strings
"""
import os
import re
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
OUT = os.path.join(ROOT, "ios", "GuiaDoVale", "Resources")

# pasta do Android -> idioma no iOS
LANGS = {"values": "en", "values-pt": "pt", "values-es": "es", "values-fr": "fr", "values-de": "de",
         "values-it": "it", "values-hu": "hu", "values-ru": "ru", "values-tr": "tr", "values-ja": "ja",
         "values-ko": "ko", "values-zh": "zh-Hans"}


def android_text(el):
    """Texto do <string> como o Android entende (sem as barras de escape do XML do Android)."""
    s = "".join(el.itertext())
    if len(s) >= 2 and s[0] == '"' and s[-1] == '"':
        s = s[1:-1]
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            n = s[i + 1]
            out.append({"n": "\n", "t": "\t", "'": "'", '"': '"', "\\": "\\", "@": "@", "?": "?"}.get(n, n))
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def to_ios_format(s):
    # %1$s -> %1$@ ; %s -> %@  (números e decimais ficam iguais)
    s = re.sub(r"%(\d+\$)?s", lambda m: "%" + (m.group(1) or "") + "@", s)
    return s


def esc(s):
    return s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n").replace("\t", "\\t")


def main():
    for folder, lang in LANGS.items():
        path = os.path.join(RES, folder, "strings.xml")
        if not os.path.exists(path):
            continue
        tree = ET.parse(path)
        pairs = []
        for el in tree.getroot().findall("string"):
            if el.get("translatable") == "false" and folder != "values":
                continue
            pairs.append((el.get("name"), to_ios_format(android_text(el))))
        d = os.path.join(OUT, lang + ".lproj")
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, "Localizable.strings"), "w", encoding="utf-8") as f:
            f.write("/* Gerado por ios/scripts/gen_strings.py a partir do Android. Não editar à mão. */\n")
            for k, v in pairs:
                f.write('"%s" = "%s";\n' % (esc(k), esc(v)))
        name = dict(pairs).get("app_name")
        with open(os.path.join(d, "InfoPlist.strings"), "w", encoding="utf-8") as f:
            if name:
                f.write('"CFBundleDisplayName" = "%s";\n"CFBundleName" = "%s";\n' % (esc(name), esc(name)))
        print(lang, len(pairs))


if __name__ == "__main__":
    main()
