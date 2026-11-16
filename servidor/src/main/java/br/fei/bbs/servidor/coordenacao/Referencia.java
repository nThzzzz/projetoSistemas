package br.fei.bbs.servidor.coordenacao;

import br.fei.bbs.proto.Envelope;
import br.fei.bbs.proto.HeartbeatReq;
import br.fei.bbs.proto.InfoServidor;
import br.fei.bbs.proto.ListarServidoresReq;
import br.fei.bbs.proto.RankReq;
import br.fei.bbs.servidor.config.ServidorProperties;
import br.fei.bbs.servidor.mensageria.Cliente;
import br.fei.bbs.servidor.relogio.Relogio;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Referencia {

    @Autowired
    private ServidorProperties propriedades;

    @Autowired
    private Cliente cliente;

    @Autowired
    private Relogio relogio;

    @Getter
    private volatile int rank;

    @Getter
    private volatile List<InfoServidor> servidores = List.of();

    public void registrar() {
        while (rank == 0) {
            Envelope resposta = cliente.requisitar(propriedades.getReferencia(),
                    Envelope.newBuilder().setRankReq(RankReq.getDefaultInstance()));
            if (resposta != null) {
                rank = resposta.getRankRep().getRank();
            }
        }
        System.out.println("[rank] " + propriedades.getNome() + " tem rank " + rank);
    }

    public void heartbeat() {
        Envelope resposta = cliente.requisitar(propriedades.getReferencia(),
                Envelope.newBuilder().setHeartbeatReq(HeartbeatReq.getDefaultInstance()));
        if (resposta != null) {
            long hora = resposta.getHeartbeatRep().getHoraMs();
            System.out.println("[relogio] ajuste de " + (hora - relogio.agora()) + " ms");
            relogio.definir(hora);
        }
    }

    public List<InfoServidor> listar() {
        Envelope resposta = cliente.requisitar(propriedades.getReferencia(),
                Envelope.newBuilder().setListarServidoresReq(ListarServidoresReq.getDefaultInstance()));
        if (resposta != null) {
            servidores = resposta.getListarServidoresRep().getServidoresList();
        }
        return servidores;
    }
}
