package br.fei.bbs.servidor;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Validador {

    private static final Logger LOG_HIBERNATE = Logger.getLogger("org.hibernate.validator");

    static {
        LOG_HIBERNATE.setLevel(Level.WARNING);
    }

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private static final Validator VALIDATOR = Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator(Set.of(PT_BR), PT_BR, false))
            .buildValidatorFactory()
            .getValidator();

    private Validador() {
    }

    public static <T> T validar(T objeto) {
        Set<ConstraintViolation<T>> violacoes = VALIDATOR.validate(objeto);
        if (!violacoes.isEmpty()) {
            throw new ConstraintViolationException(violacoes);
        }
        return objeto;
    }
}
