import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [index, robots, sitemap, manifest, vercel] = await Promise.all([
  readFile('index.html', 'utf8'),
  readFile('public/robots.txt', 'utf8'),
  readFile('public/sitemap.xml', 'utf8'),
  readFile('public/manifest.webmanifest', 'utf8'),
  readFile('vercel.json', 'utf8'),
])

assert.equal(index.includes('noindex'), false, 'HTML de produção não pode bloquear indexação.')
assert.match(index, /rel="canonical" href="https:\/\/operadorzero\.com\.br\/"/)
assert.match(index, /property="og:title"/)
assert.match(index, /application\/ld\+json/)
assert.match(robots, /Sitemap: https:\/\/operadorzero\.com\.br\/sitemap\.xml/)
assert.match(sitemap, /<loc>https:\/\/operadorzero\.com\.br\/<\/loc>/)
assert.equal(sitemap.includes('/login'), false)
assert.equal(JSON.parse(manifest).start_url, '/')
assert.equal(vercel.includes('X-Robots-Tag'), false)

console.log('Metadados, robots, sitemap e manifest públicos validados.')
