// Baixa o OpenAPI do backend em execução e versiona em openapi/clinica.json (entrada do Orval).
import { writeFileSync } from 'node:fs'

const url = process.env.OPENAPI_URL ?? 'http://localhost:8080/v3/api-docs'
const resposta = await fetch(url)
if (!resposta.ok) throw new Error(`Falha ao baixar ${url}: HTTP ${resposta.status}`)
const especificacao = await resposta.json()
especificacao.servers = [{ url: '/' }]
writeFileSync('openapi/clinica.json', JSON.stringify(especificacao, null, 2) + '\n')
console.log(`OpenAPI salvo (${Object.keys(especificacao.paths).length} rotas)`)
