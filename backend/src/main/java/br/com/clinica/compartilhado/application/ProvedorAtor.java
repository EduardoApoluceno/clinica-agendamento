package br.com.clinica.compartilhado.application;

/** Porta: obtém o ator da requisição corrente (implementada pelo módulo identidade-acesso). */
public interface ProvedorAtor {

    Ator atual();
}
