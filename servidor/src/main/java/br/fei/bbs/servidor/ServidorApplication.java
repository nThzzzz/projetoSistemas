package br.fei.bbs.servidor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Locale;

@SpringBootApplication
public class ServidorApplication {

    public static void main(String[] args) {
        Locale.setDefault(Locale.of("pt", "BR"));
        SpringApplication.run(ServidorApplication.class, args);
    }
}
