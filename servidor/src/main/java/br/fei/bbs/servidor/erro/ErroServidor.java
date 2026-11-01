package br.fei.bbs.servidor.erro;

import br.fei.bbs.proto.Status;
import lombok.Getter;

@Getter
public class ErroServidor extends RuntimeException {

    private final Status status;

    public ErroServidor(Status status, String mensagem) {
        super(mensagem);
        this.status = status;
    }
}
