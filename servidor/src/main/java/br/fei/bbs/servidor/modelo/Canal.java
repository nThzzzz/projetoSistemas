package br.fei.bbs.servidor.modelo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record Canal(@NotBlank @Size(max = 32) @Pattern(regexp = "[a-zA-Z0-9_-]*") String nome,
                    @NotBlank String criador,
                    String data,
                    long timestamp) implements Registro {

    public Canal(String nome, String criador, long timestamp) {
        this(nome, criador, Registro.formatar(timestamp), timestamp);
    }
}
