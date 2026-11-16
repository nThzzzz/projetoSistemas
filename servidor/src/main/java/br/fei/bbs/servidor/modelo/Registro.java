package br.fei.bbs.servidor.modelo;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public interface Registro {

    DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss.SSS")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    long timestamp();

    static String formatar(long timestamp) {
        return FORMATO.format(Instant.ofEpochMilli(timestamp));
    }
}
