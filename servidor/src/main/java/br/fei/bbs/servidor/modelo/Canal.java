package br.fei.bbs.servidor.modelo;

import jakarta.validation.constraints.NotNull;

public record Canal(@NotNull String nome, @NotNull String criador, long timestamp) {
}
