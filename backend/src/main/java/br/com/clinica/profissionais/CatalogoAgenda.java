package br.com.clinica.profissionais;

import br.com.clinica.compartilhado.domain.Dinheiro;
import br.com.clinica.compartilhado.domain.Periodo;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * API do módulo profissionais para a agenda: o que cada profissional atende, quando trabalha e
 * quando está ausente. Sempre da clínica corrente.
 */
public interface CatalogoAgenda {

    Optional<ServicoAgendavel> servico(UUID id);

    Optional<ProfissionalAgendavel> profissional(UUID id);

    List<ProfissionalAgendavel> profissionaisAtivos();

    List<FaixaSemanal> expediente(UUID profissional);

    List<AusenciaAgenda> ausencias(UUID profissional, Periodo janela);

    /** @param valorAntecipado sinal ou valor integral exigido antes da consulta; zero se não exige */
    record ServicoAgendavel(UUID id, String nome, Duration duracao, Dinheiro preco, Dinheiro valorAntecipado,
                            boolean ativo) { }

    /** @param usuarioSub conta vinculada (para "minha agenda") */
    record ProfissionalAgendavel(UUID id, String nome, Optional<String> usuarioSub, Set<UUID> servicos,
                                 boolean ativo) {
        public boolean atende(UUID servico) {
            return ativo && servicos.contains(servico);
        }
    }

    record FaixaSemanal(DayOfWeek dia, LocalTime inicio, LocalTime fim) { }

    record AusenciaAgenda(Periodo periodo, String tipo) { }
}
