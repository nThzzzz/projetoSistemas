package br.fei.bbs.servidor.repositorio;

import br.fei.bbs.servidor.config.ServidorProperties;
import br.fei.bbs.servidor.modelo.Canal;
import br.fei.bbs.servidor.modelo.Login;
import br.fei.bbs.servidor.modelo.Publicacao;
import br.fei.bbs.servidor.modelo.Registro;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
@Validated
public class Armazenamento {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    @Autowired
    private ServidorProperties propriedades;

    private Path logins;
    private Path canais;
    private Path publicacoes;

    @PostConstruct
    void iniciar() throws IOException {
        Path diretorio = Path.of(propriedades.getDados());
        Files.createDirectories(diretorio);
        logins = diretorio.resolve("logins.json");
        canais = diretorio.resolve("canais.json");
        publicacoes = diretorio.resolve("publicacoes.json");
    }

    public synchronized void salvarLogin(@Valid Login login) throws IOException {
        mesclar(logins, Login.class, List.of(login));
    }

    public synchronized void salvarCanal(@Valid Canal canal) throws IOException {
        mesclar(canais, Canal.class, List.of(canal));
    }

    public synchronized void salvarPublicacao(@Valid Publicacao publicacao) throws IOException {
        mesclar(publicacoes, Publicacao.class, List.of(publicacao));
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

    private <T extends Registro> int mesclar(Path arquivo, Class<T> tipo, List<T> registros) throws IOException {
        List<T> itens = ler(arquivo, tipo);
        Set<T> existentes = new HashSet<>(itens);
        int novos = 0;
        for (T registro : registros) {
            if (existentes.add(registro)) {
                itens.add(registro);
                novos++;
            }
        }
        if (novos > 0) {
            itens.sort(Comparator.comparingLong(Registro::timestamp).thenComparing(Object::toString));
            gravar(arquivo, itens);
        }
        return novos;
    }

    private void gravar(Path arquivo, List<?> itens) throws IOException {
        Path temporario = arquivo.resolveSibling(arquivo.getFileName() + ".tmp");
        Files.writeString(temporario, gson.toJson(itens) + "\n", StandardCharsets.UTF_8);
        Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private <T> List<T> ler(Path arquivo, Class<T> tipo) throws IOException {
        if (!Files.exists(arquivo)) {
            return new ArrayList<>();
        }
        List<T> itens = gson.fromJson(Files.readString(arquivo, StandardCharsets.UTF_8),
                TypeToken.getParameterized(List.class, tipo).getType());
        return itens == null ? new ArrayList<>() : new ArrayList<>(itens);
    }
}
