package br.com.clinica.notificacoes;

import br.com.clinica.compartilhado.domain.Telefone;

/**
 * API do módulo notificações para mensagens transacionais imediatas. Nesta fase existe só o
 * adaptador "caixa de saída" (demonstração); WhatsApp Cloud API/SMS chegam na Fase 3 por trás desta
 * mesma interface.
 */
public interface EnvioMensagens {

    void enviarCodigoVerificacao(Telefone destino, String codigo);
}
