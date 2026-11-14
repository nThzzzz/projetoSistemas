package br.fei.bbs.servidor.modelo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Login(@NotBlank @Size(max = 32) String usuario, long timestamp) {
}
