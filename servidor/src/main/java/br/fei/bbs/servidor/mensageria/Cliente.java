package br.fei.bbs.servidor.mensageria;

import br.fei.bbs.proto.Envelope;
import com.google.protobuf.InvalidProtocolBufferException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.HashMap;
import java.util.Map;

@Component
public class Cliente {

    private static final int TEMPO_LIMITE_MS = 2000;

    @Autowired
    private Envelopes envelopes;

    private final ZContext contexto = new ZContext();
    private final Map<String, ZMQ.Socket> sockets = new HashMap<>();

    public Envelope requisitar(String endereco, Envelope.Builder pedido) {
        ZMQ.Socket socket = sockets.computeIfAbsent(endereco, this::conectar);
        Envelope envio = envelopes.finalizar(pedido);
        socket.send(envio.toByteArray(), 0);
        System.out.println("[enviado para " + endereco + "]\n" + envio);
        byte[] dados = socket.recv(0);
        if (dados == null) {
            System.out.println("[sem resposta de " + endereco + "]");
            contexto.destroySocket(sockets.remove(endereco));
            return null;
        }
        try {
            Envelope resposta = envelopes.ler(dados);
            System.out.println("[recebido de " + endereco + "]\n" + resposta);
            return resposta;
        } catch (InvalidProtocolBufferException e) {
            return null;
        }
    }

    private ZMQ.Socket conectar(String endereco) {
        ZMQ.Socket socket = contexto.createSocket(SocketType.REQ);
        socket.setReceiveTimeOut(TEMPO_LIMITE_MS);
        socket.setLinger(0);
        socket.connect(endereco);
        return socket;
    }

    @PreDestroy
    void encerrar() {
        contexto.close();
    }
}
