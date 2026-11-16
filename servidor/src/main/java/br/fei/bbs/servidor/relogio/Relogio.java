package br.fei.bbs.servidor.relogio;

import br.fei.bbs.servidor.config.ServidorProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Relogio {

    @Autowired
    private ServidorProperties propriedades;

    private long lamport;
    private long deslocamento;

    @PostConstruct
    void iniciar() {
        deslocamento = propriedades.getDesvio();
    }

    public synchronized long incrementar() {
        return ++lamport;
    }

    public synchronized void atualizar(long recebido) {
        lamport = Math.max(lamport, recebido);
    }

    public synchronized long agora() {
        return System.currentTimeMillis() + deslocamento;
    }

    public synchronized void definir(long hora) {
        deslocamento = hora - System.currentTimeMillis();
    }
}
