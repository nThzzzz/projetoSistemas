package br.fei.bbs.servidor.servico;

import br.fei.bbs.proto.Status;
import br.fei.bbs.servidor.erro.ErroServidor;
import br.fei.bbs.servidor.modelo.Publicacao;
import br.fei.bbs.servidor.repositorio.Armazenamento;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class PublicacaoServico {

    @Autowired
    private Armazenamento armazenamento;

    @Autowired
    private CanalServico canalServico;

    public Publicacao registrar(String canal, String mensagem, String autor) throws IOException {
        if (!canalServico.listar().contains(canal)) {
            throw new ErroServidor(Status.CANAL_NAO_EXISTE, "canal " + canal + " não existe");
        }
        Publicacao publicacao = new Publicacao(canal, mensagem, autor, System.currentTimeMillis());
        armazenamento.salvarPublicacao(publicacao);
        return publicacao;
    }
}
