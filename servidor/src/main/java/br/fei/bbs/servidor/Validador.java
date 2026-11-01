package br.fei.bbs.servidor;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Validador {

    private static final Logger LOG_HIBERNATE = Logger.getLogger("org.hibernate.validator");

    static {
        LOG_HIBERNATE.setLevel(Level.WARNING);
    }

    private static final Validator VALIDATOR = Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
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
