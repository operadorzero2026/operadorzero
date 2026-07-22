import { readdir, readFile } from 'node:fs/promises'
import { join } from 'node:path'

const forbidden = [
  /teste@operadorzero\.local/i,
  /OperadorZero@2026/i,
  /DEMO_PASSWORD/,
  /GOOGLE_CLIENT_SECRET/,
  /MERCADO_PAGO_ACCESS_TOKEN/,
  /MERCADO_PAGO_WEBHOOK_SECRET/,
  /S3_SECRET_KEY/,
  /DATABASE_PASSWORD/,
]

async function filesIn(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  return (await Promise.all(entries.map(async entry => {
    const path = join(directory, entry.name)
    return entry.isDirectory() ? filesIn(path) : [path]
  }))).flat()
}

const files = await filesIn('dist')
for (const file of files.filter(path => /\.(?:html|js|css|json|map)$/.test(path))) {
  const content = await readFile(file, 'utf8')
  const match = forbidden.find(pattern => pattern.test(content))
  if (match) {
    throw new Error(`Conteudo proibido encontrado no bundle: ${file} (${match})`)
  }
}

console.log(`Bundle validado: ${files.length} arquivos sem identificadores sensiveis.`)
