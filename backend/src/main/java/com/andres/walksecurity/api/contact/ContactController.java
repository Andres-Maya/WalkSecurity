package com.andres.walksecurity.api.contact;

import com.andres.walksecurity.api.common.ApiException;
import com.andres.walksecurity.api.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    public record ContactRequest(
        @NotBlank(message = "El nombre es obligatorio.") @Size(max = 120) String name,
        @NotBlank(message = "El teléfono es obligatorio.") @Pattern(regexp = "^\\+?\\d{7,15}$", message = "Teléfono inválido.") String phone,
        @Size(max = 60) String relationship
    ) {}

    public record ContactResponse(long id, String name, String phone, String relationship) {
        static ContactResponse from(TrustedContact c) {
            return new ContactResponse(c.getId(), c.getName(), c.getPhone(), c.getRelationship());
        }
    }

    private final TrustedContactRepository contacts;
    private final int maxPerUser;

    public ContactController(
        TrustedContactRepository contacts,
        @Value("${walksecurity.contacts.max-per-user:10}") int maxPerUser
    ) {
        this.contacts = contacts;
        this.maxPerUser = maxPerUser;
    }

    @GetMapping
    public List<ContactResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return contacts.findByUserIdOrderByCreatedAtAsc(CurrentUser.id(jwt)).stream()
            .map(ContactResponse::from)
            .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ContactResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ContactRequest request) {
        long userId = CurrentUser.id(jwt);
        if (contacts.countByUserId(userId) >= maxPerUser) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                "Alcanzaste el máximo de " + maxPerUser + " contactos de confianza.");
        }
        if (contacts.existsByUserIdAndPhone(userId, request.phone())) {
            throw ApiException.conflict("Ese número ya está en tus contactos.");
        }
        String relationship = request.relationship() == null || request.relationship().isBlank()
            ? null : request.relationship().trim();
        TrustedContact saved = contacts.save(
            new TrustedContact(userId, request.name().trim(), request.phone(), relationship));
        return ContactResponse.from(saved);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        TrustedContact contact = contacts.findByIdAndUserId(id, CurrentUser.id(jwt))
            .orElseThrow(() -> ApiException.notFound("Contacto no encontrado."));
        contacts.delete(contact);
    }
}
