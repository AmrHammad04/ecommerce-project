package com.perfumestore.service;

import com.perfumestore.dto.request.ProductRequest;
import com.perfumestore.dto.response.PageResponse;
import com.perfumestore.dto.response.ProductResponse;
import java.math.BigDecimal;

public interface ProductService {

    PageResponse<ProductResponse> list(
            String search,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock,
            int page,
            int size,
            String sort,
            String direction,
            boolean includeInactive);

    ProductResponse get(Long id);
    ProductResponse create(ProductRequest request);
    ProductResponse update(Long id, ProductRequest request);
    void delete(Long id);
}