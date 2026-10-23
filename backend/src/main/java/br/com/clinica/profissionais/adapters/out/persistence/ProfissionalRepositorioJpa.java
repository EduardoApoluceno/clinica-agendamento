package br.com.clinica.profissionais.adapters.out.persistence;

import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.profissionais.application.ProfissionaisPortas.ProfissionalRepositorio;
import br.com.clinica.profissionais.domain.Profissional;
import br.com.clinica.profissionais.domain.RegistroConselho;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ProfissionalRepositorioJpa implements ProfissionalRepositorio {

    private final ProfissionalJpa jpa;

    ProfissionalRepositorioJpa(ProfissionalJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Profissional> buscar(ClinicaId clinica, UUID id) {
        return jpa.findByClinicaIdAndId(clinica.valor(), id).map(ProfissionalRepositorioJpa::paraDominio);
    }

    @Override
    public Optional<Profissional> buscarPorUsuario(ClinicaId clinica, String usuarioSub) {
        return jpa.findByClinicaIdAndUsuarioSub(clinica.valor(), usuarioSub).map(ProfissionalRepositorioJpa::paraDominio);
    }

    @Override
    public List<Profissional> listar(ClinicaId clinica, boolean apenasAtivos) {
        var entidades = apenasAtivos ? jpa.findByClinicaIdAndAtivoTrueOrderByNome(clinica.valor())
                : jpa.findByClinicaIdOrderByNome(clinica.valor());
        return entidades.stream().map(ProfissionalRepositorioJpa::paraDominio).toList();
    }

    @Override
    public Profissional salvar(Profissional p) {
        ProfissionalEntity e = jpa.findByClinicaIdAndId(p.clinicaId().valor(), p.id())
                .map(existente -> Gravacao.exigirVersao(existente, existente.version, p.versao()))
                .orElseGet(ProfissionalEntity::new);
        e.id = p.id();
        e.clinicaId = p.clinicaId().valor();
        e.usuarioSub = p.usuarioSub().orElse(null);
        e.nome = p.nome();
        e.conselho = p.registro().conselho();
        e.numeroConselho = p.registro().numero();
        e.ufConselho = p.registro().uf();
        e.ativo = p.ativo();
        e.especialidades = new HashSet<>(p.especialidades());
        e.servicos = new HashSet<>(p.servicos());
        return paraDominio(Gravacao.gravar(() -> jpa.saveAndFlush(e),
                "Já existe profissional com este registro no conselho ou com esta conta vinculada."));
    }

    static Profissional paraDominio(ProfissionalEntity e) {
        return new Profissional(e.id, new ClinicaId(e.clinicaId), Optional.ofNullable(e.usuarioSub), e.nome,
                new RegistroConselho(e.conselho, e.numeroConselho, e.ufConselho), Set.copyOf(e.especialidades),
                Set.copyOf(e.servicos), e.ativo, e.version);
    }
}
