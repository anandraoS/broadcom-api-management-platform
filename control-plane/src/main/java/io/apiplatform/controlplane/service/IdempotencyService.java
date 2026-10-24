package io.apiplatform.controlplane.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apiplatform.controlplane.domain.IdempotencyRecord;
import io.apiplatform.controlplane.repo.IdempotencyRecordRepository;
import io.apiplatform.controlplane.web.ApiException;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotency-Key support for create operations. The same key + same request returns the stored response;
 * the same key with a different request is a 422 (client bug). The record is written in the SAME transaction as
 * the business change, so it exists if and only if the change committed.
 */
@Service
public class IdempotencyService {
    private final IdempotencyRecordRepository repo;
    private final ObjectMapper mapper;

    public IdempotencyService(IdempotencyRecordRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Transactional
    public <T> ResponseEntity<?> execute(String key, Object request, Class<T> responseType, Supplier<T> action) {
        if (key == null || key.isBlank()) {
            T result = action.get();
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        }
        if (!key.matches("[A-Za-z0-9._-]{8,100}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_idempotency_key", "Idempotency-Key must be 8-100 URL-safe characters");
        }
        String hash = ConsumerService.sha256Hex(write(request));
        Optional<IdempotencyRecord> existing = repo.findById(key);
        if (existing.isPresent()) {
            IdempotencyRecord r = existing.get();
            if (!r.getRequestHash().equals(hash)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "idempotency_key_reuse",
                        "Idempotency-Key was already used with a different request");
            }
            try {
                return ResponseEntity.status(r.getResponseStatus()).header("Idempotent-Replay", "true")
                        .body(mapper.readValue(r.getResponseBody(), responseType));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        T result = action.get();
        repo.save(new IdempotencyRecord(key, hash, HttpStatus.CREATED.value(), write(result)));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    private String write(Object o) {
        try { return mapper.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
