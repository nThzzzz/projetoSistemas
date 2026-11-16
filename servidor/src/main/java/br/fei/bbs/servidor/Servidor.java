package br.fei.bbs.servidor;

import br.fei.bbs.proto.CriarCanalRep;
import br.fei.bbs.proto.Envelope;
import br.fei.bbs.proto.ListarCanaisRep;
import br.fei.bbs.proto.LoginRep;
import br.fei.bbs.proto.Publicacao;
import br.fei.bbs.proto.PublicarRep;
import br.fei.bbs.proto.PublicarReq;
import br.fei.bbs.proto.Status;
import br.fei.bbs.servidor.config.ServidorProperties;
import br.fei.bbs.servidor.erro.TratadorDeExcecoes;
import br.fei.bbs.servidor.mensageria.Envelopes;
import br.fei.bbs.servidor.mensageria.Publicador;
import br.fei.bbs.servidor.servico.CanalServico;
import br.fei.bbs.servidor.servico.LoginServico;
import br.fei.bbs.servidor.servico.PublicacaoServico;
import com.google.protobuf.InvalidProtocolBufferException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

@Component
public class Servidor implements CommandLineRunner {

    @Autowired
    private ServidorProperties propriedades;

    @Autowired
    private LoginServico loginServico;

    @Autowired
    private CanalServico canalServico;

    @Autowired
    private PublicacaoServico publicacaoServico;

    @Autowired
    private Publicador publicador;

    @Autowired
    private Envelopes envelopes;

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
                Envelope.Builder resposta;
                try {
                    Envelope requisicao = envelopes.ler(dados);
                    System.out.println("[recebido]\n" + requisicao);
                    resposta = processar(requisicao);
                } catch (InvalidProtocolBufferException e) {
                    resposta = Envelope.newBuilder();
                }
                Envelope envio = envelopes.finalizar(resposta);
                socket.send(envio.toByteArray(), 0);
                System.out.println("[enviado]\n" + envio);
            }
        }
    }

    private Envelope.Builder processar(Envelope requisicao) {
        try {
            return tratar(requisicao);
        } catch (Exception e) {
            return tratadorDeExcecoes.tratar(requisicao, e, Envelope.newBuilder());
        }
    }

    private Envelope.Builder tratar(Envelope requisicao) throws Exception {
        Envelope.Builder resposta = Envelope.newBuilder();
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
            case PUBLICAR_REQ -> {
                PublicarReq pedido = requisicao.getPublicarReq();
                var registro = publicacaoServico.registrar(pedido.getCanal(), pedido.getMensagem(),
                        requisicao.getHeader().getRemetente());
                publicador.publicar(registro.canal(), envelopes.finalizar(Envelope.newBuilder()
                        .setPublicacao(Publicacao.newBuilder()
                                .setCanal(registro.canal())
                                .setMensagem(registro.mensagem())
                                .setAutor(registro.autor()))));
                resposta.setPublicarRep(PublicarRep.newBuilder().setStatus(Status.OK));
            }
            default -> {
            }
        }
        return resposta;
    }
}
