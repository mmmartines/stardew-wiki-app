// Uma sessão por versão de iOS, uma de cada vez (o plano grátis permite 2 simultâneas e 3 min por sessão).
const { defineConfig } = require('@playwright/test')

module.exports = defineConfig({
  testDir: '.',
  testMatch: 'tour.spec.js',
  timeout: 170_000,
  workers: 1,
  retries: 0,
  reporter: [['list']],
})
