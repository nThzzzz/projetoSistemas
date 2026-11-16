package br.fei.bbs.servidor.modelo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Publicacao(@NotBlank String canal,
                         @NotBlank @Size(max = 280) String mensagem,
                         @NotBlank String autor,
                         long timestamp) {
}
