package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ListOfferingsUseCase {
    List<Offering> listActive();
    Page<Offering> listVisible(Pageable pageable);
    Offering getVisibleById(UUID id);
}
