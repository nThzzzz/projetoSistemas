package br.fei.bbs.servidor.modelo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record Publicacao(@NotBlank @Size(max = 32) @Pattern(regexp = "[a-zA-Z0-9_-]*") String canal,
                         @NotBlank String autor,
                         @NotBlank @Size(max = 280) String mensagem,
                         String data,
                         long timestamp) implements Registro {

    public Publicacao(String canal, String mensagem, String autor, long timestamp) {
        this(canal, autor, mensagem, Registro.formatar(timestamp), timestamp);
    }
}
