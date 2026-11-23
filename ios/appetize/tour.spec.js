// Abre o app no modo "tour" (ele passa sozinho pelas telas, 8 s em cada) e guarda um print a cada 4 s.
const { test } = require('@appetize/playwright')
const fs = require('fs')
const path = require('path')

const buildId = process.env.APPETIZE_BUILD_ID
const versions = (process.env.IOS_VERSIONS || '17,18,26,27').split(',').map((v) => v.trim()).filter(Boolean)
const out = path.join(__dirname, '..', '..', 'appetize-shots')
fs.mkdirSync(out, { recursive: true })

for (const v of versions) {
  test.describe(`iOS ${v}`, () => {
    test.use({
      config: {
        buildId,
        device: 'iphone12',
        osVersion: v,
        launchArgs: ['-lang', 'pt', '-screen', 'tour'],
      },
    })

    test(`tour iOS ${v}`, async ({ session }) => {
      await session.waitForTimeout(3000)
      // 8 telas x 8 s = 64 s; dois prints por tela para não perder nenhuma
      for (let i = 0; i < 17; i++) {
        const shot = await session.screenshot('base64')
        fs.writeFileSync(path.join(out, `ios${v}-${String(i).padStart(2, '0')}.png`), Buffer.from(shot.data, 'base64'))
        await session.waitForTimeout(4000)
      }
    })
  })
}
