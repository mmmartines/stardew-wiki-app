# Guia do Vale – iOS

App iOS nativo (SwiftUI, iOS 16+ (iPhone 8 e mais novos)) com as mesmas telas e textos do app Android.

- **Compartilhado com o Android:** formatação das páginas (`article.js`/`article.css`), títulos do menu
  (`titles.json`), fontes e os textos dos 12 idiomas (`strings.xml`, convertidos por `scripts/gen_strings.py`).
- **Projeto:** gerado pelo XcodeGen a partir de `project.yml` (`cd ios && xcodegen generate`).
- **Build:** o workflow `Gerar iOS` compila num Mac do GitHub, roda no simulador e publica prints no branch `ios-shots`.
