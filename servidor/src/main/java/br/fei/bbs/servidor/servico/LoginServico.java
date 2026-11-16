package br.fei.bbs.servidor.servico;

import br.fei.bbs.servidor.modelo.Login;
import br.fei.bbs.servidor.replicacao.Replicador;
import br.fei.bbs.servidor.repositorio.Armazenamento;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class LoginServico {

    @Autowired
    private Armazenamento armazenamento;

    @Autowired
    private Replicador replicador;

    public void entrar(String usuario) throws IOException {
        Login login = new Login(usuario, System.currentTimeMillis());
        armazenamento.salvarLogin(login);
        replicador.replicar(login);
    }
}
