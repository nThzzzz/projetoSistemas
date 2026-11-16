package br.fei.bbs.servidor.servico;

import br.fei.bbs.servidor.modelo.Publicacao;
import br.fei.bbs.servidor.replicacao.Replicador;
import br.fei.bbs.servidor.repositorio.Armazenamento;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class PublicacaoServico {

    @Autowired
    private Armazenamento armazenamento;

    @Autowired
    private Replicador replicador;

    public Publicacao registrar(String canal, String mensagem, String autor) throws IOException {
        Publicacao publicacao = new Publicacao(canal, mensagem, autor, System.currentTimeMillis());
        armazenamento.salvarPublicacao(publicacao);
        replicador.replicar(publicacao);
        return publicacao;
    }
}
