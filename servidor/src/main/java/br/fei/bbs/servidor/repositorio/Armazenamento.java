package br.fei.bbs.servidor.repositorio;

import br.fei.bbs.servidor.config.ServidorProperties;
import br.fei.bbs.servidor.modelo.Canal;
import br.fei.bbs.servidor.modelo.Login;
import br.fei.bbs.servidor.modelo.Publicacao;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
@Validated
public class Armazenamento {

    @Autowired
    private Gson gson;

    @Autowired
    private ServidorProperties propriedades;

    private Path logins;
    private Path canais;
    private Path publicacoes;

    @PostConstruct
    void iniciar() throws IOException {
        Path diretorio = Path.of(propriedades.getDados());
        Files.createDirectories(diretorio);
        logins = diretorio.resolve("logins.jsonl");
        canais = diretorio.resolve("canais.jsonl");
        publicacoes = diretorio.resolve("publicacoes.jsonl");
    }

    public synchronized void salvarLogin(@Valid Login login) throws IOException {
        anexar(logins, login);
    }

    public synchronized void salvarCanal(@Valid Canal canal) throws IOException {
        anexar(canais, canal);
    }

    public synchronized void salvarPublicacao(@Valid Publicacao publicacao) throws IOException {
        anexar(publicacoes, publicacao);
    }

    public synchronized int mesclarLogins(List<Login> registros) throws IOException {
        return mesclar(logins, Login.class, registros);
    }

    public synchronized int mesclarCanais(List<Canal> registros) throws IOException {
        return mesclar(canais, Canal.class, registros);
    }

    public synchronized int mesclarPublicacoes(List<Publicacao> registros) throws IOException {
        return mesclar(publicacoes, Publicacao.class, registros);
    }

    public synchronized List<Login> carregarLogins() throws IOException {
        return ler(logins, Login.class);
    }

    public synchronized List<Canal> carregarCanais() throws IOException {
        return ler(canais, Canal.class);
    }

    public synchronized List<Publicacao> carregarPublicacoes() throws IOException {
        return ler(publicacoes, Publicacao.class);
    }

    private <T> int mesclar(Path arquivo, Class<T> tipo, List<T> registros) throws IOException {
        Set<T> existentes = new HashSet<>(ler(arquivo, tipo));
        int novos = 0;
        for (T registro : registros) {
            if (existentes.add(registro)) {
                anexar(arquivo, registro);
                novos++;
            }
        }
        return novos;
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
