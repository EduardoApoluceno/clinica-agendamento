// Caddy compilado a partir do código-fonte com toolchain Go e dependências corrigidas
// (o binário oficial da imagem caddy:2.11.7 traz CVEs HIGH do Go 1.26.8 e do golang.org/x/net 0.59).
package main

import (
	caddycmd "github.com/caddyserver/caddy/v2/cmd"

	_ "github.com/caddyserver/caddy/v2/modules/standard"
)

func main() {
	caddycmd.Main()
}
