package br.fei.bbs.servidor;

import br.fei.bbs.proto.Envelope;
import br.fei.bbs.proto.Header;
import com.google.protobuf.InvalidProtocolBufferException;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.UUID;

public class Servidor {

    private final String nome;
    private final String broker;

    public Servidor(String nome, String broker) {
        this.nome = nome;
        this.broker = broker;
    }

    public static void main(String[] args) {
        String nome = System.getenv().getOrDefault("SERVER_NAME", "servidor");
        String broker = System.getenv().getOrDefault("BROKER_ADDR", "tcp://broker:5556");
        new Servidor(nome, broker).executar();
    }

    public void executar() {
        try (ZContext contexto = new ZContext()) {
            ZMQ.Socket socket = contexto.createSocket(SocketType.REP);
            socket.connect(broker);
            System.out.println(nome + " conectado em " + broker);

            while (!Thread.currentThread().isInterrupted()) {
                byte[] dados = socket.recv(0);
                Envelope resposta;
                try {
                    Envelope requisicao = Envelope.parseFrom(dados);
                    System.out.println("[recebido]\n" + requisicao);
                    resposta = processar(requisicao);
                } catch (InvalidProtocolBufferException e) {
                    resposta = novoEnvelope().build();
                }
                socket.send(resposta.toByteArray(), 0);
                System.out.println("[enviado]\n" + resposta);
            }
        }
    }

    private Envelope processar(Envelope requisicao) {
        try {
            return tratar(requisicao);
        } catch (Exception e) {
            return TratadorDeExcecoes.tratar(requisicao, e, novoEnvelope()).build();
        }
    }

    private Envelope tratar(Envelope requisicao) throws Exception {
        Envelope.Builder resposta = novoEnvelope();
        switch (requisicao.getBodyCase()) {
            case LOGIN_REQ -> {
                
            }
            case CRIAR_CANAL_REQ -> {
            }
            case LISTAR_CANAIS_REQ -> {
            }
            default -> {
            }
        }
        return resposta.build();
    }

    private Envelope.Builder novoEnvelope() {
        return Envelope.newBuilder().setHeader(Header.newBuilder()
                .setMsgId(UUID.randomUUID().toString())
                .setRemetente(nome)
                .setTsEnvioMs(System.currentTimeMillis()));
    }
}
