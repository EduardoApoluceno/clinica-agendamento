import { defineConfig } from 'orval'

// Gera hooks TanStack Query + schemas Zod a partir do OpenAPI versionado em openapi/clinica.json.
// Para atualizar: com o backend de pé, `npm run api:baixar && npm run api:gerar`.
const entrada = { target: './openapi/clinica.json' }

export default defineConfig({
  clinica: {
    input: entrada,
    output: {
      mode: 'tags-split',
      target: 'src/api/gerado',
      schemas: 'src/api/gerado/modelos',
      client: 'react-query',
      httpClient: 'fetch',
      clean: true,
      override: {
        mutator: { path: 'src/lib/orval-mutator.ts', name: 'chamarApi' },
        fetch: { includeHttpResponseReturnType: false },
        query: { signal: true },
      },
    },
  },
  clinicaZod: {
    input: entrada,
    output: {
      mode: 'tags-split',
      target: 'src/api/gerado/zod',
      client: 'zod',
      fileExtension: '.zod.ts',
    },
  },
})
