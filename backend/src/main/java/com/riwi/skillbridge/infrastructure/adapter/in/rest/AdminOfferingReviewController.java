package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.ReviewOfferingsUseCase;
import com.riwi.skillbridge.domain.model.OfferingReviewStatus;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.AdminOfferingResponse;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.AdminOfferingReviewRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** HU-21: revisión de mentorías. Solo ADMIN (regla /api/admin/** de SecurityConfiguration). */
@RestController
@RequestMapping("/api/admin/offerings")
public class AdminOfferingReviewController {
    private final ReviewOfferingsUseCase review;

    public AdminOfferingReviewController(ReviewOfferingsUseCase review) {
        this.review = review;
    }

    @GetMapping("/pending")
    public List<AdminOfferingResponse> listPending() {
        return review.listPending().stream().map(AdminOfferingResponse::from).toList();
    }

    @PatchMapping("/{id}/review")
    public AdminOfferingResponse decide(@PathVariable UUID id, @Valid @RequestBody AdminOfferingReviewRequest request) {
        if (request.decision() == OfferingReviewStatus.APPROVED) {
            return AdminOfferingResponse.from(review.approve(id));
        }
        if (request.decision() == OfferingReviewStatus.REJECTED) {
            return AdminOfferingResponse.from(review.reject(id, request.reason()));
        }
        // Volver a "pendiente" no es una decisión del administrador: lo hace el proveedor al corregirla
        throw new IllegalArgumentException("La decisión debe ser APPROVED o REJECTED");
    }
}
