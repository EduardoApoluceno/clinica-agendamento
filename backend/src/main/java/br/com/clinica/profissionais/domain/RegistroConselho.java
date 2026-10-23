package br.com.clinica.profissionais.domain;

import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import java.util.Locale;
import java.util.Set;

/** Registro no conselho de classe (ex.: CRM 12345/RO). */
public record RegistroConselho(String conselho, String numero, String uf) {

    static final Set<String> CONSELHOS = Set.of("CRM", "CRO", "CRP", "CREFITO", "CRN", "COREN", "CRFA", "CRBM");
    private static final Set<String> UFS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT",
            "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    public RegistroConselho {
        conselho = conselho == null ? "" : conselho.strip().toUpperCase(Locale.ROOT);
        numero = numero == null ? "" : numero.replaceAll("\\D", "");
        uf = uf == null ? "" : uf.strip().toUpperCase(Locale.ROOT);
        if (!CONSELHOS.contains(conselho)) {
            throw new DadoInvalidoException("conselho-invalido", "Conselho profissional não reconhecido.");
        }
        if (!numero.matches("\\d{1,10}")) {
            throw new DadoInvalidoException("numero-conselho-invalido", "Número de registro inválido.");
        }
        if (!UFS.contains(uf)) {
            throw new DadoInvalidoException("uf-invalida", "UF do conselho inválida.");
        }
    }

    @Override
    public String toString() {
        return conselho + " " + numero + "/" + uf;
    }
}
