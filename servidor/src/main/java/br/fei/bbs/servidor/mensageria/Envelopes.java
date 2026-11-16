package br.fei.bbs.servidor.mensageria;

import br.fei.bbs.proto.Envelope;
import br.fei.bbs.proto.Header;
import br.fei.bbs.servidor.config.ServidorProperties;
import br.fei.bbs.servidor.relogio.Relogio;
import com.google.protobuf.InvalidProtocolBufferException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class Envelopes {

    @Autowired
    private ServidorProperties propriedades;

    @Autowired
    private Relogio relogio;

    public Envelope ler(byte[] dados) throws InvalidProtocolBufferException {
        Envelope envelope = Envelope.parseFrom(dados);
        relogio.atualizar(envelope.getHeader().getLamport());
        return envelope;
    }

    public Envelope finalizar(Envelope.Builder envelope) {
        return envelope.setHeader(Header.newBuilder()
                        .setMsgId(UUID.randomUUID().toString())
                        .setRemetente(propriedades.getNome())
                        .setTsEnvioMs(relogio.agora())
                        .setLamport(relogio.incrementar()))
                .build();
    }
}
