package br.fei.bbs.servidor.modelo;

import jakarta.validation.constraints.NotNull;

public record Login(@NotNull String usuario, long timestamp) {
}
