package br.fei.bbs.servidor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "servidor")
public class ServidorProperties {

    private String nome;
    private String broker;
    private String dados;
}
