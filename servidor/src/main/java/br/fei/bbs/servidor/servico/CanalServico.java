package br.fei.bbs.servidor.servico;

import br.fei.bbs.proto.Status;
import br.fei.bbs.servidor.erro.ErroServidor;
import br.fei.bbs.servidor.modelo.Canal;
import br.fei.bbs.servidor.replicacao.Replicador;
import br.fei.bbs.servidor.repositorio.Armazenamento;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class CanalServico {

    @Autowired
    private Armazenamento armazenamento;

    @Autowired
    private Replicador replicador;

    public void criar(String nome, String criador) throws IOException {
        if (listar().contains(nome)) {
            throw new ErroServidor(Status.CANAL_JA_EXISTE, "canal " + nome + " já existe");
        }
        Canal canal = new Canal(nome, criador, System.currentTimeMillis());
        armazenamento.salvarCanal(canal);
        replicador.replicar(canal);
    }

    public List<String> listar() throws IOException {
        return armazenamento.carregarCanais().stream().map(Canal::nome).distinct().toList();
    }
}
