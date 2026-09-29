package com.andres.walksecurity.api.alert;

import com.andres.walksecurity.api.contact.TrustedContact;
import java.util.List;

/**
 * Canal de notificación del lado servidor hacia los contactos de confianza.
 * Fase 1: el teléfono envía los SMS directamente y aquí solo se registra.
 * Más adelante se puede implementar con FCM (push), Twilio (SMS) o WhatsApp Business.
 */
public interface AlertNotifier {

    void notify(Alert alert, List<TrustedContact> contacts);
}
