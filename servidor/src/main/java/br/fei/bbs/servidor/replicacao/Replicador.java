package br.fei.bbs.servidor.replicacao;

import br.fei.bbs.proto.Envelope;
import br.fei.bbs.proto.HistoricoReq;
import br.fei.bbs.proto.InfoServidor;
import br.fei.bbs.proto.Replicacao;
import br.fei.bbs.proto.TipoRegistro;
import br.fei.bbs.servidor.config.ServidorProperties;
import br.fei.bbs.servidor.coordenacao.Referencia;
import br.fei.bbs.servidor.mensageria.Cliente;
import br.fei.bbs.servidor.mensageria.Envelopes;
import br.fei.bbs.servidor.mensageria.Publicador;
import br.fei.bbs.servidor.modelo.Canal;
import br.fei.bbs.servidor.modelo.Login;
import br.fei.bbs.servidor.modelo.Publicacao;
import br.fei.bbs.servidor.repositorio.Armazenamento;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Component
public class Replicador implements Runnable {

    private static final String TOPICO = "replicacao";

    @Autowired
    private ServidorProperties propriedades;

    @Autowired
    private Envelopes envelopes;

    @Autowired
    private Publicador publicador;

    @Autowired
    private Armazenamento armazenamento;

    @Autowired
    private Referencia referencia;

    @Autowired
    private Cliente cliente;

    public void iniciar() {
        Thread thread = new Thread(this, "replicacao");
        thread.setDaemon(true);
        thread.start();
    }

    public void replicar(Login login) {
        enviar(paraReplicacao(login));
    }

    public void replicar(Canal canal) {
        enviar(paraReplicacao(canal));
    }

    public void replicar(Publicacao publicacao) {
        enviar(paraReplicacao(publicacao));
    }

    public void sincronizar() throws IOException {
        for (InfoServidor servidor : referencia.listar()) {
            if (servidor.getNome().equals(propriedades.getNome())) {
                continue;
            }
            Envelope resposta = cliente.requisitar(propriedades.endereco(servidor.getNome()),
                    Envelope.newBuilder().setHistoricoReq(HistoricoReq.getDefaultInstance()));
            if (resposta != null) {
                int novos = aplicar(resposta.getHistoricoRep().getRegistrosList());
                System.out.println("[replicacao] " + novos + " registros novos recebidos de " + servidor.getNome());
                return;
            }
        }
        System.out.println("[replicacao] nenhum servidor disponivel para enviar o historico");
    }

    public List<Replicacao> historico() throws IOException {
        List<Replicacao> registros = new ArrayList<>();
        armazenamento.carregarLogins().forEach(login -> registros.add(paraReplicacao(login).build()));
        armazenamento.carregarCanais().forEach(canal -> registros.add(paraReplicacao(canal).build()));
        armazenamento.carregarPublicacoes().forEach(publicacao -> registros.add(paraReplicacao(publicacao).build()));
        return registros;
    }

    private int aplicar(List<Replicacao> registros) throws IOException {
        return armazenamento.mesclarLogins(filtrar(registros, TipoRegistro.LOGIN,
                        r -> new Login(r.getUsuario(), r.getTimestamp())))
                + armazenamento.mesclarCanais(filtrar(registros, TipoRegistro.CANAL,
                        r -> new Canal(r.getCanal(), r.getUsuario(), r.getTimestamp())))
                + armazenamento.mesclarPublicacoes(filtrar(registros, TipoRegistro.PUBLICACAO,
                        r -> new Publicacao(r.getCanal(), r.getMensagem(), r.getUsuario(), r.getTimestamp())));
    }

    private <T> List<T> filtrar(List<Replicacao> registros, TipoRegistro tipo, Function<Replicacao, T> conversor) {
        return registros.stream().filter(r -> r.getTipo() == tipo).map(conversor).toList();
    }

    private Replicacao.Builder paraReplicacao(Login login) {
        return Replicacao.newBuilder()
                .setTipo(TipoRegistro.LOGIN)
                .setUsuario(login.usuario())
                .setTimestamp(login.timestamp());
    }

    private Replicacao.Builder paraReplicacao(Canal canal) {
        return Replicacao.newBuilder()
                .setTipo(TipoRegistro.CANAL)
                .setCanal(canal.nome())
                .setUsuario(canal.criador())
                .setTimestamp(canal.timestamp());
    }

    private Replicacao.Builder paraReplicacao(Publicacao publicacao) {
        return Replicacao.newBuilder()
                .setTipo(TipoRegistro.PUBLICACAO)
                .setCanal(publicacao.canal())
                .setMensagem(publicacao.mensagem())
                .setUsuario(publicacao.autor())
                .setTimestamp(publicacao.timestamp());
    }

    private void enviar(Replicacao.Builder replicacao) {
        publicador.publicar(TOPICO, envelopes.finalizar(Envelope.newBuilder().setReplicacao(replicacao)));
    }

    @Override
    public void run() {
        try (ZContext contexto = new ZContext()) {
            ZMQ.Socket sub = contexto.createSocket(SocketType.SUB);
            sub.connect(propriedades.getProxySub());
            sub.subscribe(TOPICO.getBytes());

            while (!Thread.currentThread().isInterrupted()) {
                sub.recv(0);
                try {
                    Envelope envelope = envelopes.ler(sub.recv(0));
                    if (!envelope.getHeader().getRemetente().equals(propriedades.getNome())) {
                        System.out.println("[recebido em " + TOPICO + "]\n" + envelope);
                        aplicar(List.of(envelope.getReplicacao()));
                    }
                } catch (Exception e) {
                    System.out.println("[erro] replicacao: " + e.getMessage());
                }
            }
        }
    }
}
