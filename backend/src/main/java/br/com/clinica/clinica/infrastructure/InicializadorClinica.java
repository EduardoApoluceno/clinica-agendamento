package br.com.clinica.clinica.infrastructure;

import br.com.clinica.clinica.application.ClinicaRepositorio;
import br.com.clinica.clinica.domain.Clinica;
import br.com.clinica.clinica.domain.Cnpj;
import br.com.clinica.clinica.domain.ConfiguracaoAgenda;
import br.com.clinica.clinica.domain.Endereco;
import br.com.clinica.compartilhado.application.ContextoClinica;
import java.time.ZoneId;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Garante que a clínica configurada exista (primeira subida). Os dados iniciais são provisórios e
 * devem ser completados pelo ADMIN na tela de configurações. Idempotente.
 */
@Component
class InicializadorClinica implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(InicializadorClinica.class);
    private static final String CNPJ_PROVISORIO = "11222333000181";

    private final ClinicaRepositorio repositorio;
    private final ContextoClinica contexto;
    private final TransactionTemplate transacao;
    private final String nomeInicial;
    private final String fuso;

    InicializadorClinica(ClinicaRepositorio repositorio, ContextoClinica contexto, TransactionTemplate transacao,
                         @Value("${app.clinica.nome-inicial:Clínica}") String nomeInicial,
                         @Value("${app.clinica.fuso-horario}") String fuso) {
        this.repositorio = repositorio;
        this.contexto = contexto;
        this.transacao = transacao;
        this.nomeInicial = nomeInicial;
        this.fuso = fuso;
    }

    @Override
    public void run(ApplicationArguments args) {
        transacao.executeWithoutResult(status -> {
            if (repositorio.buscar(contexto.atual()).isEmpty()) {
                var endereco = new Endereco("A definir", "S/N", Optional.empty(), "Centro", "Porto Velho", "RO",
                        "76801000");
                repositorio.criar(new Clinica(contexto.atual(), nomeInicial, new Cnpj(CNPJ_PROVISORIO),
                        ZoneId.of(fuso), endereco, Optional.empty(), 0), ConfiguracaoAgenda.padrao());
                LOG.warn("Clínica {} criada com dados provisórios — complete o cadastro em Configurações", contexto.atual());
            }
        });
    }
}
