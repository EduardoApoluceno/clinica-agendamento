package br.com.clinica.profissionais.domain;

import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import br.com.clinica.compartilhado.domain.Dinheiro;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Serviço oferecido (consulta, retorno, procedimento) com duração, preço e política de pagamento. */
public record TipoServico(UUID id, ClinicaId clinicaId, String nome, Optional<String> descricao,
                          Optional<String> instrucoesPreparo, Duration duracao, Dinheiro preco,
                          PoliticaPagamento politicaPagamento, boolean ativo, long versao) {

    private static final Duration DURACAO_MINIMA = Duration.ofMinutes(5);
    private static final Duration DURACAO_MAXIMA = Duration.ofHours(8);
    private static final int PASSO_MINUTOS = 5;

    public TipoServico {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clinicaId, "clinicaId");
        Objects.requireNonNull(preco, "preco");
        Objects.requireNonNull(politicaPagamento, "politicaPagamento");
        if (nome == null || nome.isBlank()) {
            throw new DadoInvalidoException("nome-invalido", "Informe o nome do serviço.");
        }
        nome = nome.strip();
        descricao = vazioSeEmBranco(descricao);
        instrucoesPreparo = vazioSeEmBranco(instrucoesPreparo);
        if (duracao == null || duracao.compareTo(DURACAO_MINIMA) < 0 || duracao.compareTo(DURACAO_MAXIMA) > 0
                || duracao.toMinutes() % PASSO_MINUTOS != 0) {
            throw new DadoInvalidoException("duracao-invalida", "A duração deve ser de 5 min a 8 h, em passos de 5 min.");
        }
        politicaPagamento.valorSinal().filter(s -> s.maiorQue(preco)).ifPresent(s -> {
            throw new DadoInvalidoException("sinal-maior-que-preco", "O sinal não pode ser maior que o preço.");
        });
    }

    public static TipoServico novo(ClinicaId clinica, DadosServico d) {
        return new TipoServico(UUID.randomUUID(), clinica, d.nome(), d.descricao(), d.instrucoesPreparo(), d.duracao(),
                d.preco(), d.politicaPagamento(), true, 0);
    }

    public TipoServico atualizado(DadosServico d, boolean novoAtivo) {
        return new TipoServico(id, clinicaId, d.nome(), d.descricao(), d.instrucoesPreparo(), d.duracao(), d.preco(),
                d.politicaPagamento(), novoAtivo, versao);
    }

    private static Optional<String> vazioSeEmBranco(Optional<String> texto) {
        return texto == null ? Optional.empty() : texto.map(String::strip).filter(t -> !t.isEmpty());
    }

    /** Dados editáveis do serviço. */
    public record DadosServico(String nome, Optional<String> descricao, Optional<String> instrucoesPreparo,
                               Duration duracao, Dinheiro preco, PoliticaPagamento politicaPagamento) { }
}
