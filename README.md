# Valley Guide / Guia do Vale / Guía del Valle

*[Português abaixo](#português)*

An **unofficial, free and non-profit** reader for the [Stardew Valley Wiki](https://stardewvalleywiki.com), made for phones.
Read-only: the app never edits the wiki.

Stardew Valley was created by **Eric Barone (ConcernedApe)**. All content comes from the Stardew Valley Wiki, written by
its contributors, under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/) (except the developer's
images, art and lore, which belong to ConcernedApe). This app is not affiliated with or endorsed by ConcernedApe.
ConcernedApe's team gave permission for this app (including the game item icons in the menu) on the condition that it is
never monetized and is clearly unofficial.

- **No ads, no purchases, no accounts, no data collection.** See the [privacy policy](docs/privacy.html).
- Pages are loaded live from the wiki and shown with a link to the original page and its edit history; nothing is re-hosted.
- [Google Play](https://play.google.com/store/apps/details?id=io.github.mmmartines.guiadovale) (Android). An iOS version is in testing.

### Features
- The wiki's 12 languages: pt, en, es, fr, de, it, hu, ru, tr, ja, ko, zh
- Menu by topic, instant and full-text search (including by English name, e.g. "Parsnip" finds the Portuguese page)
- Pages adapted for phones: tables become cards, collapsible sections, filters on long lists
- Favorites, history and offline reading of pages you've opened
- Full credits: first launch, "About & credits" screen and a footer on every page

### Expansion mod wikis
Version 1.8.0 adds optional sections for expansion mod wikis hosted on wiki.gg, **only for mods whose authors agreed**:
currently **Sunberry Village**. Other mods are ready in the code but stay off unless their authors say yes
(East Scarp was removed at its authors' request). The list lives in
[`Wikis.kt`](android/app/src/main/java/com/stardew/wikipt/Wikis.kt) (Android) and
[`Sources.swift`](ios/GuiaDoVale/Core/Sources.swift) (iOS); adding or removing a mod is a one-line change.

- **Live and read-only:** pages are loaded from each mod's wiki through the standard MediaWiki API, with a small delay between requests. Nothing is copied to a server; pages a player opens are cached only on their own phone for offline reading.
- **Credit and license:** every mod page says which mod it belongs to ("Mod: Sunberry Village"), credits the mod's wiki and its contributors, shows the wiki's license (CC BY-SA 4.0) and links to the original page and its edit history. The About screen links to each mod wiki.
- **Players choose:** a "My mods" screen lets players pick the mods they play; only those appear on the home screen and in search.
- **Non-English players:** names of base-game things (items, villagers, seasons) come from the official wiki in the player's language. The rest of the text can be machine-translated **on the device** (Google ML Kit on Android, Apple Translation on iOS 18+), always marked "automatic translation" with a "see original" button. Translations stay under CC BY-SA 4.0.
- **Mod authors:** if you'd like your mod added, changed or removed, [open an issue](https://github.com/mmmartines/stardew-wiki-app/issues).

### Repository layout
- `android/` — Android app (Kotlin), published on Google Play
- `ios/` — iOS app (SwiftUI), reusing the page CSS/JS and texts from Android
- `docs/` — privacy policy (GitHub Pages)

### Contact
Questions, requests or removal requests: [open an issue](https://github.com/mmmartines/stardew-wiki-app/issues).
The app will be changed or taken down if the content owners ask.

---

## Português

Leitor **não oficial, gratuito e sem fins lucrativos** da [Stardew Valley Wiki](https://stardewvalleywiki.com) para Android.
Somente leitura: o app nunca edita a wiki.

Stardew Valley foi criado por **Eric Barone (ConcernedApe)**. Todo o conteúdo vem da Stardew Valley Wiki, escrita pelos
seus contribuidores, sob [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/) (exceto imagens, arte e
lore do desenvolvedor, que são da ConcernedApe). Este app não tem ligação nem endosso da ConcernedApe.

## Wikis dos mods (versão 1.8.0)
Seções opcionais para as wikis de mods de expansão, **só dos mods cujos autores autorizaram**: por enquanto, Sunberry Village.
Os outros estão prontos no código, mas só entram se os autores autorizarem (o East Scarp saiu a pedido da autora).
As páginas são lidas ao vivo, com crédito, licença (CC BY-SA 4.0), link para o original e tradução automática sempre identificada.

## Recursos
- 12 idiomas, os mesmos da wiki: pt, en, es, fr, de, it, hu, ru, tr, ja, ko, zh (interface completa em pt, en e es; tela de boas-vindas nos 12)
- Menu por seções, busca instantânea e no texto completo, todas as páginas e categorias
- Páginas adaptadas para celular: tabelas em cartões, seções recolhíveis, filtro em listas longas
- Favoritos, histórico e leitura offline por idioma
- Créditos completos: primeira abertura, tela "Sobre e créditos" e rodapé em cada página com link para o original e o histórico de autores
- Android 7.0 (API 24) ou mais novo; mira o Android 16 (API 36), como exige o Google Play

## Estrutura
- `android/` — app Android (Kotlin), publicado no Google Play
- `ios/` — app iOS (SwiftUI), em teste; reaproveita o CSS/JS das páginas e os textos do Android

## Builds
Cada envio para `main` compila no GitHub Actions e publica no branch `apk`:
- `GuiaDoVale-teste.apk` — versão de teste (instala ao lado da versão da loja, com o sufixo "-teste")
- `GuiaDoVale-loja.aab` — pacote assinado para o Google Play (só aparece depois de configurar a chave abaixo)

### Chave de assinatura para a loja (uma vez)
1. Gere a chave de upload no seu computador (guarde o arquivo e as senhas em local seguro; sem eles não dá para atualizar o app):
   ```
   keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Converta para texto: `base64 -w0 upload.jks > upload.txt` (no Windows: `certutil -encode upload.jks upload.txt` e apague as linhas BEGIN/END).
3. No GitHub: **Settings → Secrets and variables → Actions → New repository secret**, crie:
   `SIGNING_KEYSTORE_BASE64` (conteúdo do upload.txt), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` (`upload`), `SIGNING_KEY_PASSWORD`.
4. Os ícones de itens do jogo no menu vêm ligados (uso autorizado pela ConcernedApe em 30/10/2026, desde que o app nunca seja monetizado e seja claramente não oficial). Para desligar, crie em **Variables** `GAME_ICONS` = `false`.

No Google Play Console, ative a **Assinatura de apps do Google Play** e envie o `.aab`.

## Política de privacidade
[docs/privacy.html](docs/privacy.html) — o app não coleta nenhum dado. Para ter um link público, ative o GitHub Pages
(Settings → Pages → branch `main`, pasta `/docs`).

## Licença
Código e adaptação do conteúdo sob CC BY-NC-SA 3.0 (ver [LICENSE](LICENSE)). Fonte Pixelify Sans sob SIL OFL 1.1.
