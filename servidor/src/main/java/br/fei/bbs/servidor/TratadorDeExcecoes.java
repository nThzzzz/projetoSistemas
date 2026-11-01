package br.fei.bbs.servidor;

import br.fei.bbs.proto.CriarCanalRep;
import br.fei.bbs.proto.Envelope;
import br.fei.bbs.proto.ListarCanaisRep;
import br.fei.bbs.proto.LoginRep;
import br.fei.bbs.proto.Status;
import jakarta.validation.ConstraintViolationException;

import java.util.stream.Collectors;

public final class TratadorDeExcecoes {

    private TratadorDeExcecoes() {
    }

    public static Envelope.Builder tratar(Envelope requisicao, Exception excecao, Envelope.Builder resposta) {
        Status status = status(excecao);
        String detalhe = detalhe(excecao);
        System.out.println("[erro] " + requisicao.getBodyCase() + " | " + status + " | " + detalhe);

        switch (requisicao.getBodyCase()) {
            case LOGIN_REQ -> resposta.setLoginRep(LoginRep.newBuilder()
                    .setStatus(status)
                    .setDetalhe(detalhe));
            case CRIAR_CANAL_REQ -> resposta.setCriarCanalRep(CriarCanalRep.newBuilder()
                    .setStatus(status)
                    .setDetalhe(detalhe));
            case LISTAR_CANAIS_REQ -> resposta.setListarCanaisRep(ListarCanaisRep.newBuilder()
                    .setStatus(status));
            default -> {
            }
        }
        return resposta;
    }

    private static Status status(Exception excecao) {
        return switch (excecao) {
            case ErroServidor erro -> erro.getStatus();
            case ConstraintViolationException violacao -> Status.NOME_INVALIDO;
            default -> Status.ERRO_INTERNO;
        };
    }

    private static String detalhe(Exception excecao) {
        return switch (excecao) {
            case ConstraintViolationException violacao -> violacao.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", "));
            default -> excecao.getMessage() != null ? excecao.getMessage() : excecao.getClass().getSimpleName();
        };
    }
}
