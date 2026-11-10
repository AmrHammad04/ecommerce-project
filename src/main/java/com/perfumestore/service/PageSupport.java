package com.perfumestore.service;

import com.perfumestore.exception.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.*;

public final class PageSupport {

    private PageSupport() {
    }

    public static Pageable request(
            int page,
            int size,
            String sort,
            String direction,
            Set<String> allowedSortFields) {

        if (page < 0) {
            throw ApiException.badRequest(
                    "Page number must be zero or greater."
            );
        }

        if (size < 1 || size > 100) {
            throw ApiException.badRequest(
                    "Page size must be between 1 and 100."
            );
        }

        if (sort == null || !allowedSortFields.contains(sort)) {
            throw ApiException.badRequest(
                    "Unsupported sort field."
            );
        }

        if (!"asc".equalsIgnoreCase(direction)
                && !"desc".equalsIgnoreCase(direction)) {

            throw ApiException.badRequest(
                    "Sort direction must be asc or desc."
            );
        }

        Sort ordering = Sort.by(
                Sort.Direction.fromString(direction),
                sort
        );

        if (!"id".equals(sort)) {
            ordering = ordering.and(
                    Sort.by(Sort.Direction.ASC, "id")
            );
        }

        return PageRequest.of(page, size, ordering);
    }
}