package br.fei.bbs.servidor.mensageria;

import br.fei.bbs.proto.Envelope;
import br.fei.bbs.servidor.config.ServidorProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

@Component
public class Publicador {

    @Autowired
    private ServidorProperties propriedades;

    private ZContext contexto;
    private ZMQ.Socket socket;

    @PostConstruct
    void iniciar() {
        contexto = new ZContext();
        socket = Sockets.criar(contexto, SocketType.PUB);
        socket.connect(propriedades.getProxy());
    }

    public void publicar(String canal, Envelope envelope) {
        socket.sendMore(canal);
        socket.send(envelope.toByteArray(), 0);
        System.out.println("[publicado em " + canal + "]\n" + envelope);
    }

    @PreDestroy
    void encerrar() {
        contexto.close();
    }
}
