package com.izalco.transferencias.dominio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferenciaRepository extends JpaRepository<Transferencia, String> {

    Optional<Transferencia> findByIdempotencyKey(String idempotencyKey);
}
