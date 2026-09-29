package com.andres.walksecurity.api.contact;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustedContactRepository extends JpaRepository<TrustedContact, Long> {

    List<TrustedContact> findByUserIdOrderByCreatedAtAsc(Long userId);

    Optional<TrustedContact> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndPhone(Long userId, String phone);

    long countByUserId(Long userId);
}
