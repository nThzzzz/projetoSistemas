package br.fei.bbs.servidor.repositorio;

import br.fei.bbs.servidor.config.ServidorProperties;
import br.fei.bbs.servidor.modelo.Canal;
import br.fei.bbs.servidor.modelo.Login;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@Repository
@Validated
public class Armazenamento {

    @Autowired
    private Gson gson;

    @Autowired
    private ServidorProperties propriedades;

    private Path logins;
    private Path canais;

    @PostConstruct
    void iniciar() throws IOException {
        Path diretorio = Path.of(propriedades.getDados());
        Files.createDirectories(diretorio);
        logins = diretorio.resolve("logins.jsonl");
        canais = diretorio.resolve("canais.jsonl");
    }

    public synchronized void salvarLogin(@Valid Login login) throws IOException {
        anexar(logins, login);
    }

    public synchronized void salvarCanal(@Valid Canal canal) throws IOException {
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
