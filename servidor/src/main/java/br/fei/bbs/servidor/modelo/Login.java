package br.fei.bbs.servidor.modelo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Login(@NotBlank @Size(max = 32) String usuario, String data, long timestamp) implements Registro {

    public Login(String usuario, long timestamp) {
        this(usuario, Registro.formatar(timestamp), timestamp);
    }
}
