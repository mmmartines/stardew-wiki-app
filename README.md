# Guia do Vale / Valley Guide / Guía del Valle

Leitor **não oficial, gratuito e sem fins lucrativos** da [Stardew Valley Wiki](https://stardewvalleywiki.com) para Android.
Somente leitura: o app nunca edita a wiki.

Stardew Valley foi criado por **Eric Barone (ConcernedApe)**. Todo o conteúdo vem da Stardew Valley Wiki, escrita pelos
seus contribuidores, sob [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/) (exceto imagens, arte e
lore do desenvolvedor, que são da ConcernedApe). Este app não tem ligação nem endosso da ConcernedApe.

## Recursos
- 12 idiomas, os mesmos da wiki: pt, en, es, fr, de, it, hu, ru, tr, ja, ko, zh (interface completa em pt, en e es; tela de boas-vindas nos 12)
- Menu por seções, busca instantânea e no texto completo, todas as páginas e categorias
- Páginas adaptadas para celular: tabelas em cartões, seções recolhíveis, filtro em listas longas
- Favoritos, histórico e leitura offline por idioma
- Créditos completos: primeira abertura, tela "Sobre e créditos" e rodapé em cada página com link para o original e o histórico de autores
- Android 7.0 (API 24) ou mais novo; mira o Android 16 (API 36), como exige o Google Play

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
