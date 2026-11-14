package br.fei.bbs.servidor.modelo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record Canal(@NotBlank @Size(max = 32) @Pattern(regexp = "[a-zA-Z0-9_-]*") String nome,
                    @NotBlank String criador,
                    long timestamp) {
}
