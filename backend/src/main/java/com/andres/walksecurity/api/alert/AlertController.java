package com.andres.walksecurity.api.alert;

import com.andres.walksecurity.api.common.CurrentUser;
import com.andres.walksecurity.api.contact.TrustedContactRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    /** Las coordenadas son opcionales: un SOS sin GPS también debe quedar registrado. */
    public record AlertRequest(
        @NotNull(message = "El tipo de alerta es obligatorio.") Alert.Type type,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        Float accuracyMeters,
        @Size(max = 500) String message
    ) {}

    public record AlertResponse(
        long id, Alert.Type type, Alert.Status status,
        Double latitude, Double longitude, Instant createdAt
    ) {
        static AlertResponse from(Alert a) {
            return new AlertResponse(a.getId(), a.getType(), a.getStatus(),
                a.getLatitude(), a.getLongitude(), a.getCreatedAt());
        }
    }

    private final AlertRepository alerts;
    private final TrustedContactRepository contacts;
    private final AlertNotifier notifier;

    public AlertController(AlertRepository alerts, TrustedContactRepository contacts, AlertNotifier notifier) {
        this.alerts = alerts;
        this.contacts = contacts;
        this.notifier = notifier;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AlertResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AlertRequest request) {
        long userId = CurrentUser.id(jwt);
        Alert alert = alerts.save(new Alert(userId, request.type(),
            request.latitude(), request.longitude(), request.accuracyMeters(), request.message()));
        notifier.notify(alert, contacts.findByUserIdOrderByCreatedAtAsc(userId));
        return AlertResponse.from(alert);
    }

    @GetMapping
    public List<AlertResponse> history(@AuthenticationPrincipal Jwt jwt) {
        return alerts.findTop50ByUserIdOrderByCreatedAtDesc(CurrentUser.id(jwt)).stream()
            .map(AlertResponse::from)
            .toList();
    }
}
