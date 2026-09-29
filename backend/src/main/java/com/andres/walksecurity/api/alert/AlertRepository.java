package com.andres.walksecurity.api.alert;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
}
