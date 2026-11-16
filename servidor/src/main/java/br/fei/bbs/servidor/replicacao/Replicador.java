package br.fei.bbs.servidor.replicacao;

import br.fei.bbs.proto.Envelope;
import br.fei.bbs.proto.Replicacao;
import br.fei.bbs.proto.TipoRegistro;
import br.fei.bbs.servidor.config.ServidorProperties;
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

    public void iniciar() {
        Thread thread = new Thread(this, "replicacao");
        thread.setDaemon(true);
        thread.start();
    }

    public void replicar(Login login) {
        enviar(Replicacao.newBuilder()
                .setTipo(TipoRegistro.LOGIN)
                .setUsuario(login.usuario())
                .setTimestamp(login.timestamp()));
    }

    public void replicar(Canal canal) {
        enviar(Replicacao.newBuilder()
                .setTipo(TipoRegistro.CANAL)
                .setCanal(canal.nome())
                .setUsuario(canal.criador())
                .setTimestamp(canal.timestamp()));
    }

    public void replicar(Publicacao publicacao) {
        enviar(Replicacao.newBuilder()
                .setTipo(TipoRegistro.PUBLICACAO)
                .setCanal(publicacao.canal())
                .setMensagem(publicacao.mensagem())
                .setUsuario(publicacao.autor())
                .setTimestamp(publicacao.timestamp()));
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
                        aplicar(envelope.getReplicacao());
                    }
                } catch (Exception e) {
                    System.out.println("[erro] replicacao: " + e.getMessage());
                }
            }
        }
    }

    private void aplicar(Replicacao replicacao) throws IOException {
        switch (replicacao.getTipo()) {
            case LOGIN -> armazenamento.salvarLogin(new Login(replicacao.getUsuario(), replicacao.getTimestamp()));
            case CANAL -> armazenamento.salvarCanal(new Canal(replicacao.getCanal(), replicacao.getUsuario(),
                    replicacao.getTimestamp()));
            case PUBLICACAO -> armazenamento.salvarPublicacao(new Publicacao(replicacao.getCanal(),
                    replicacao.getMensagem(), replicacao.getUsuario(), replicacao.getTimestamp()));
            default -> {
            }
        }
    }
}
