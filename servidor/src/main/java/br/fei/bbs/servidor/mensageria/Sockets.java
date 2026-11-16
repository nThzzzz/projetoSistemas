package br.fei.bbs.servidor.mensageria;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public final class Sockets {

    private static final int HANDSHAKE_MS = 2000;

    private Sockets() {
    }

    public static ZMQ.Socket criar(ZContext contexto, SocketType tipo) {
        ZMQ.Socket socket = contexto.createSocket(tipo);
        socket.setHandshakeIvl(HANDSHAKE_MS);
        return socket;
    }
}
