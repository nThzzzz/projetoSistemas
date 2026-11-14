package br.fei.bbs.servidor;

import br.fei.bbs.proto.CriarCanalRep;
import br.fei.bbs.proto.Envelope;
import br.fei.bbs.proto.Header;
import br.fei.bbs.proto.ListarCanaisRep;
import br.fei.bbs.proto.LoginRep;
import br.fei.bbs.proto.Status;
import br.fei.bbs.servidor.config.ServidorProperties;
import br.fei.bbs.servidor.erro.TratadorDeExcecoes;
import br.fei.bbs.servidor.servico.CanalServico;
import br.fei.bbs.servidor.servico.LoginServico;
import com.google.protobuf.InvalidProtocolBufferException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.UUID;

@Component
public class Servidor implements CommandLineRunner {

    @Autowired
    private ServidorProperties propriedades;

    @Autowired
    private LoginServico loginServico;

    @Autowired
    private CanalServico canalServico;

    @Autowired
    private TratadorDeExcecoes tratadorDeExcecoes;

    @Override
    public void run(String... args) {
        try (ZContext contexto = new ZContext()) {
            ZMQ.Socket socket = contexto.createSocket(SocketType.REP);
            socket.connect(propriedades.getBroker());
            System.out.println(propriedades.getNome() + " conectado em " + propriedades.getBroker());

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
            return tratadorDeExcecoes.tratar(requisicao, e, novoEnvelope()).build();
        }
    }

    private Envelope tratar(Envelope requisicao) throws Exception {
        Envelope.Builder resposta = novoEnvelope();
        switch (requisicao.getBodyCase()) {
            case LOGIN_REQ -> {
                loginServico.entrar(requisicao.getHeader().getRemetente());
                resposta.setLoginRep(LoginRep.newBuilder().setStatus(Status.OK));
            }
            case CRIAR_CANAL_REQ -> {
                canalServico.criar(requisicao.getCriarCanalReq().getCanal(), requisicao.getHeader().getRemetente());
                resposta.setCriarCanalRep(CriarCanalRep.newBuilder().setStatus(Status.OK));
            }
            case LISTAR_CANAIS_REQ -> resposta.setListarCanaisRep(ListarCanaisRep.newBuilder()
                    .setStatus(Status.OK)
                    .addAllCanais(canalServico.listar()));
            default -> {
            }
        }
        return resposta.build();
    }

    private Envelope.Builder novoEnvelope() {
        return Envelope.newBuilder().setHeader(Header.newBuilder()
                .setMsgId(UUID.randomUUID().toString())
                .setRemetente(propriedades.getNome())
                .setTsEnvioMs(System.currentTimeMillis()));
    }
}
