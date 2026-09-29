package com.andres.walksecurity.api.alert;

import com.andres.walksecurity.api.contact.TrustedContact;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlertNotifier.class);

    @Override
    public void notify(Alert alert, List<TrustedContact> contacts) {
        log.warn("Alerta {} #{} del usuario {} en ({}, {}) -> {} contacto(s)",
            alert.getType(), alert.getId(), alert.getUserId(),
            alert.getLatitude(), alert.getLongitude(), contacts.size());
    }
}
