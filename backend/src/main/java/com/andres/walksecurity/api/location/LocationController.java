package com.andres.walksecurity.api.location;

import com.andres.walksecurity.api.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Recepción de posiciones del teléfono. Se usará en la fase 2 (geofencing y zonas de riesgo). */
@RestController
@RequestMapping("/api/locations")
public class LocationController {

    public record LocationRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        Float accuracyMeters,
        Instant recordedAt
    ) {}

    private final LocationRecordRepository locations;

    public LocationController(LocationRecordRepository locations) {
        this.locations = locations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LocationRequest request) {
        Instant recordedAt = request.recordedAt() != null ? request.recordedAt() : Instant.now();
        locations.save(new LocationRecord(CurrentUser.id(jwt), request.latitude(), request.longitude(),
            request.accuracyMeters(), recordedAt));
    }
}
