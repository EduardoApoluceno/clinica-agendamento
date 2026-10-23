import { defineConfig } from 'orval'

// Gera hooks TanStack Query + schemas Zod a partir do OpenAPI do backend (perfil demo).
// Uso: com o compose de pé, `npm run api:gerar`.
export default defineConfig({
  clinica: {
    input: { target: process.env.OPENAPI_URL ?? 'http://localhost:8080/v3/api-docs' },
    output: {
      mode: 'tags-split',
      target: 'src/api/gerado',
      client: 'react-query',
      override: { mutator: { path: 'src/lib/orval-mutator.ts', name: 'chamarApi' } },
    },
  },
  clinicaZod: {
    input: { target: process.env.OPENAPI_URL ?? 'http://localhost:8080/v3/api-docs' },
    output: { mode: 'tags-split', target: 'src/api/gerado', client: 'zod', fileExtension: '.zod.ts' },
  },
})
