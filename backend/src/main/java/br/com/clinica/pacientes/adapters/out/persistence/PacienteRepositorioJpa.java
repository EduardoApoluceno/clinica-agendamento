package br.com.clinica.pacientes.adapters.out.persistence;

import br.com.clinica.compartilhado.application.Pagina;
import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.Cpf;
import br.com.clinica.compartilhado.domain.Telefone;
import br.com.clinica.compartilhado.infrastructure.criptografia.CifradorCampos;
import br.com.clinica.pacientes.application.PacientesPortas.PacienteRepositorio;
import br.com.clinica.pacientes.application.PacientesPortas.ResumoPaciente;
import br.com.clinica.pacientes.domain.Contato;
import br.com.clinica.pacientes.domain.Paciente;
import br.com.clinica.pacientes.domain.Paciente.DadosPessoais;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Adaptador JPA de pacientes: cifra/decifra CPF e contatos e mantém os HMACs de busca. */
@Component
class PacienteRepositorioJpa implements PacienteRepositorio {

    private static final String EDICAO_CONCORRENTE = "Os dados foram alterados por outra pessoa. Recarregue.";

    private final PacienteJpa jpa;
    private final CifradorCampos cifrador;

    PacienteRepositorioJpa(PacienteJpa jpa, CifradorCampos cifrador) {
        this.jpa = jpa;
        this.cifrador = cifrador;
    }

    @Override
    public Optional<Paciente> buscar(ClinicaId clinica, UUID id) {
        return jpa.findByClinicaIdAndId(clinica.valor(), id).map(this::paraDominio);
    }

    @Override
    public Optional<Paciente> buscarPorUsuario(ClinicaId clinica, String usuarioSub) {
        return jpa.findByClinicaIdAndUsuarioSub(clinica.valor(), usuarioSub).map(this::paraDominio);
    }

    @Override
    public Optional<Paciente> buscarPorCpf(ClinicaId clinica, Cpf cpf) {
        return jpa.findByClinicaIdAndCpfHmac(clinica.valor(), cifrador.hmac(cpf.digitos())).map(this::paraDominio);
    }

    @Override
    public Optional<Paciente> buscarPorContatoVerificado(ClinicaId clinica, Contato contato) {
        return jpa.porContatoVerificado(clinica.valor(), contato.tipo().name(), cifrador.hmac(contato.valor()))
                .map(this::paraDominio);
    }

    @Override
    public Pagina<ResumoPaciente> pesquisar(ClinicaId clinica, String termo, Pagina.Pedido pedido) {
        PageRequest pagina = PageRequest.of(pedido.pagina(), pedido.tamanho());
        Page<PacienteEntity> resultado = identificadorExato(termo)
                .map(hmac -> jpa.porIdentificador(clinica.valor(), hmac, pagina))
                .orElseGet(() -> jpa.porNome(clinica.valor(), termo, pagina));
        return new Pagina<>(resultado.map(this::resumo).getContent(), pedido.pagina(), pedido.tamanho(),
                resultado.getTotalElements());
    }

    @Override
    public Paciente salvar(Paciente p) {
        PacienteEntity e = jpa.findByClinicaIdAndId(p.clinicaId().valor(), p.id())
                .map(existente -> exigirVersao(existente, p.versao()))
                .orElseGet(() -> novaEntidade(p));
        DadosPessoais d = p.dados();
        e.usuarioSub = p.usuarioSub().orElse(null);
        e.nome = d.nome();
        e.nomeSocial = d.nomeSocial().orElse(null);
        e.dataNascimento = d.dataNascimento();
        e.cpfCifrado = cifrador.cifrar(d.cpf().digitos());
        e.cpfHmac = cifrador.hmac(d.cpf().digitos());
        e.sexo = d.sexo().orElse(null);
        e.canalPreferido = d.canalPreferido().name();
        sincronizarContatos(e, p);
        try {
            return paraDominio(jpa.saveAndFlush(e));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflitoException("paciente-duplicado",
                    "CPF, conta ou contato verificado já pertence a outro paciente.");
        }
    }

    /** Atualiza contatos no lugar (por tipo+HMAC): evita apagar/reinserir um contato verificado. */
    private void sincronizarContatos(PacienteEntity e, Paciente p) {
        for (Iterator<ContatoEntity> it = e.contatos.iterator(); it.hasNext();) {
            ContatoEntity existente = it.next();
            Optional<Contato> correspondente = p.contatos().stream()
                    .filter(c -> c.tipo().name().equals(existente.tipo)
                            && Arrays.equals(cifrador.hmac(c.valor()), existente.valorHmac))
                    .findFirst();
            correspondente.ifPresentOrElse(c -> {
                existente.principal = c.principal();
                existente.verificadoEm = c.verificadoEm().orElse(null);
            }, it::remove);
        }
        p.contatos().stream()
                .filter(c -> e.contatos.stream().noneMatch(ex -> ex.tipo.equals(c.tipo().name())
                        && Arrays.equals(ex.valorHmac, cifrador.hmac(c.valor()))))
                .forEach(c -> e.contatos.add(novoContato(e, c)));
    }

    private ContatoEntity novoContato(PacienteEntity paciente, Contato c) {
        ContatoEntity ce = new ContatoEntity();
        ce.id = UUID.randomUUID();
        ce.clinicaId = paciente.clinicaId;
        ce.paciente = paciente;
        ce.tipo = c.tipo().name();
        ce.valorCifrado = cifrador.cifrar(c.valor());
        ce.valorHmac = cifrador.hmac(c.valor());
        ce.principal = c.principal();
        ce.verificadoEm = c.verificadoEm().orElse(null);
        return ce;
    }

    private static PacienteEntity novaEntidade(Paciente p) {
        PacienteEntity e = new PacienteEntity();
        e.id = p.id();
        e.clinicaId = p.clinicaId().valor();
        e.origemCadastro = p.origem().name();
        return e;
    }

    private static PacienteEntity exigirVersao(PacienteEntity e, long versao) {
        if (e.version == null || e.version != versao) {
            throw new ConflitoException("edicao-concorrente", EDICAO_CONCORRENTE);
        }
        return e;
    }

    /** CPF (11 dígitos válidos), celular ou e-mail viram busca exata por HMAC. */
    private Optional<byte[]> identificadorExato(String termo) {
        String digitos = termo.replaceAll("\\D", "");
        if (termo.contains("@")) {
            return Optional.of(cifrador.hmac(termo.strip().toLowerCase(java.util.Locale.ROOT)));
        }
        if (digitos.length() == termo.replaceAll("[\\s().+-]", "").length() && digitos.length() >= 10) {
            if (digitos.length() == 11 && cpfValido(digitos)) {
                return Optional.of(cifrador.hmac(digitos));
            }
            return telefone(digitos).map(t -> cifrador.hmac(t.e164()));
        }
        return Optional.empty();
    }

    private static boolean cpfValido(String digitos) {
        try {
            new Cpf(digitos);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Optional<Telefone> telefone(String digitos) {
        try {
            return Optional.of(Telefone.de(digitos));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private Paciente paraDominio(PacienteEntity e) {
        var dados = new DadosPessoais(e.nome, Optional.ofNullable(e.nomeSocial), e.dataNascimento,
                new Cpf(cifrador.decifrar(e.cpfCifrado)), Optional.ofNullable(e.sexo),
                Paciente.Canal.valueOf(e.canalPreferido));
        var contatos = e.contatos.stream()
                .map(c -> new Contato(Contato.Tipo.valueOf(c.tipo), cifrador.decifrar(c.valorCifrado), c.principal,
                        Optional.ofNullable(c.verificadoEm)))
                .toList();
        return new Paciente(e.id, new ClinicaId(e.clinicaId), Optional.ofNullable(e.usuarioSub), dados,
                Paciente.Origem.valueOf(e.origemCadastro), contatos, e.version);
    }

    private ResumoPaciente resumo(PacienteEntity e) {
        return new ResumoPaciente(e.id, e.nome, Optional.ofNullable(e.nomeSocial), e.dataNascimento,
                new Cpf(cifrador.decifrar(e.cpfCifrado)).mascarado(), e.usuarioSub != null);
    }
}
