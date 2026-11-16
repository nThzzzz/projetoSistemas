package br.fei.bbs.servidor.coordenacao;

import br.fei.bbs.proto.Coordenador;
import br.fei.bbs.proto.EleicaoRep;
import br.fei.bbs.proto.EleicaoReq;
import br.fei.bbs.proto.HistoricoRep;
import br.fei.bbs.proto.Envelope;
import br.fei.bbs.proto.InfoServidor;
import br.fei.bbs.proto.RelogioRep;
import br.fei.bbs.proto.RelogioReq;
import br.fei.bbs.proto.Status;
import br.fei.bbs.servidor.config.ServidorProperties;
import br.fei.bbs.servidor.mensageria.Cliente;
import br.fei.bbs.servidor.mensageria.Envelopes;
import br.fei.bbs.servidor.mensageria.Publicador;
import br.fei.bbs.servidor.mensageria.Sockets;
import br.fei.bbs.servidor.relogio.Relogio;
import br.fei.bbs.servidor.replicacao.Replicador;
import com.google.protobuf.InvalidProtocolBufferException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class Coordenacao implements Runnable {

    private static final String TOPICO = "servers";

    @Autowired
    private ServidorProperties propriedades;

    @Autowired
    private Referencia referencia;

    @Autowired
    private Cliente cliente;

    @Autowired
    private Envelopes envelopes;

    @Autowired
    private Publicador publicador;

    @Autowired
    private Relogio relogio;

    @Autowired
    private Replicador replicador;

    private volatile String coordenador = "";
    private volatile boolean eleicaoPendente;
    private final Map<String, Long> diferencas = new HashMap<>();

    public void iniciar() {
        Thread thread = new Thread(this, "coordenacao");
        thread.setDaemon(true);
        thread.start();
    }

    public void verificar() {
        if (eleicaoPendente) {
            eleicao();
        }
    }

    public void sincronizar() {
        if (eleicaoPendente || coordenador.isEmpty()) {
            eleicao();
            return;
        }
        if (coordenador.equals(propriedades.getNome())) {
            anunciar();
            return;
        }
        Envelope resposta = cliente.requisitar(propriedades.endereco(coordenador),
                Envelope.newBuilder().setRelogioReq(RelogioReq.getDefaultInstance()));
        if (resposta == null) {
            eleicao();
            return;
        }
        long hora = resposta.getRelogioRep().getHoraMs();
        System.out.println("[relogio] ajuste de " + (hora - relogio.agora()) + " ms pelo coordenador " + coordenador);
        relogio.definir(hora);
    }

    private void eleicao() {
        eleicaoPendente = false;
        System.out.println("[eleicao] iniciada por " + propriedades.getNome());
        boolean respondeu = false;
        for (InfoServidor servidor : referencia.listar()) {
            if (servidor.getRank() > referencia.getRank()) {
                respondeu |= cliente.requisitar(propriedades.endereco(servidor.getNome()),
                        Envelope.newBuilder().setEleicaoReq(EleicaoReq.getDefaultInstance())) != null;
            }
        }
        if (!respondeu) {
            coordenador = propriedades.getNome();
            anunciar();
        }
    }

    private void anunciar() {
        publicador.publicar(TOPICO, envelopes.finalizar(Envelope.newBuilder()
                .setCoordenador(Coordenador.newBuilder()
                        .setNome(propriedades.getNome())
                        .setRank(referencia.getRank()))));
    }

    @Override
    public void run() {
        try (ZContext contexto = new ZContext()) {
            ZMQ.Socket rep = Sockets.criar(contexto, SocketType.REP);
            rep.bind("tcp://*:" + propriedades.getPorta());
            ZMQ.Socket sub = Sockets.criar(contexto, SocketType.SUB);
            sub.connect(propriedades.getProxySub());
            sub.subscribe(TOPICO.getBytes());
            ZMQ.Poller poller = contexto.createPoller(2);
            poller.register(rep, ZMQ.Poller.POLLIN);
            poller.register(sub, ZMQ.Poller.POLLIN);

            while (!Thread.currentThread().isInterrupted()) {
                poller.poll();
                if (poller.pollin(0)) {
                    atender(rep);
                }
                if (poller.pollin(1)) {
                    receberAnuncio(sub);
                }
            }
        }
    }

    private void atender(ZMQ.Socket rep) {
        Envelope.Builder resposta = Envelope.newBuilder();
        try {
            Envelope pedido = envelopes.ler(rep.recv(0));
            System.out.println("[recebido de servidor]\n" + pedido);
            switch (pedido.getBodyCase()) {
                case RELOGIO_REQ -> resposta.setRelogioRep(RelogioRep.newBuilder()
                        .setHoraMs(berkeley(pedido.getHeader().getRemetente(), pedido.getHeader().getTsEnvioMs())));
                case HISTORICO_REQ -> resposta.setHistoricoRep(HistoricoRep.newBuilder()
                        .addAllRegistros(replicador.historico()));
                case ELEICAO_REQ -> {
                    resposta.setEleicaoRep(EleicaoRep.newBuilder().setStatus(Status.OK));
                    eleicaoPendente = true;
                }
                default -> {
                }
            }
        } catch (Exception e) {
            System.out.println("[erro] mensagem de servidor: " + e.getMessage());
        }
        Envelope envio = envelopes.finalizar(resposta);
        rep.send(envio.toByteArray(), 0);
        System.out.println("[enviado para servidor]\n" + envio);
    }

    private void receberAnuncio(ZMQ.Socket sub) {
        sub.recv(0);
        try {
            Envelope envelope = envelopes.ler(sub.recv(0));
            System.out.println("[recebido em " + TOPICO + "]\n" + envelope);
            Coordenador anunciado = envelope.getCoordenador();
            if (anunciado.getRank() < referencia.getRank()) {
                eleicaoPendente = true;
            } else {
                coordenador = anunciado.getNome();
                System.out.println("[coordenador] " + coordenador);
            }
        } catch (InvalidProtocolBufferException e) {
            System.out.println("[erro] anuncio invalido");
        }
    }

    private long berkeley(String servidor, long hora) {
        Set<String> ativos = referencia.getServidores().stream()
                .map(InfoServidor::getNome)
                .collect(Collectors.toSet());
        diferencas.keySet().retainAll(ativos);
        diferencas.put(servidor, hora - relogio.agora());
        long media = diferencas.values().stream().mapToLong(Long::longValue).sum() / (diferencas.size() + 1);
        relogio.ajustar(media);
        diferencas.replaceAll((nome, diferenca) -> diferenca - media);
        diferencas.put(servidor, 0L);
        System.out.println("[berkeley] media de " + media + " ms entre " + (diferencas.size() + 1) + " relogios");
        return relogio.agora();
    }
}
