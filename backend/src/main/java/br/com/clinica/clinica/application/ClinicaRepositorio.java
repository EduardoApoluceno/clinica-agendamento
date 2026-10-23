package br.com.clinica.clinica.application;

import br.com.clinica.clinica.domain.Clinica;
import br.com.clinica.clinica.domain.ConfiguracaoAgenda;
import br.com.clinica.compartilhado.domain.ClinicaId;
import java.util.Optional;

/** Porta de saída: persistência da clínica. */
public interface ClinicaRepositorio {

    Optional<Clinica> buscar(ClinicaId id);

    void criar(Clinica clinica, ConfiguracaoAgenda configuracao);

    /** Atualiza se a versão bater; caso contrário lança ConflitoException. */
    Clinica atualizar(Clinica clinica);

    ConfiguracaoComVersao configuracao(ClinicaId id);

    ConfiguracaoComVersao atualizarConfiguracao(ClinicaId id, ConfiguracaoAgenda configuracao, long versao);

    record ConfiguracaoComVersao(ConfiguracaoAgenda configuracao, long versao) { }
}
