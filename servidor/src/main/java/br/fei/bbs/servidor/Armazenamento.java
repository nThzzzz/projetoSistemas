package br.fei.bbs.servidor;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class Armazenamento {
    public record Login(String usuario, long timestamp) {
    }
    public record Canal(String nome, String criador, long timestamp) {
    }

    private final Gson gson = new Gson();
    private final Path logins;
    private final Path canais;

    public Armazenamento(Path diretorio) throws IOException {
        Files.createDirectories(diretorio);
        logins = diretorio.resolve("logins.jsonl");
        canais = diretorio.resolve("canais.jsonl");
    }

    public synchronized void salvarLogin(Login login) throws IOException {
        anexar(logins, login);
    }

    public synchronized void salvarCanal(Canal canal) throws IOException {
        anexar(canais, canal);
    }

    public List<Login> carregarLogins() throws IOException {
        return ler(logins, Login.class);
    }

    public List<Canal> carregarCanais() throws IOException {
        return ler(canais, Canal.class);
    }

    private void anexar(Path arquivo, Object registro) throws IOException {
        Files.writeString(arquivo, gson.toJson(registro) + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private <T> List<T> ler(Path arquivo, Class<T> tipo) throws IOException {
        List<T> itens = new ArrayList<>();
        if (Files.exists(arquivo)) {
            for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                if (!linha.isBlank()) {
                    itens.add(gson.fromJson(linha, tipo));
                }
            }
        }
        return itens;
    }
}
