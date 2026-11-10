package com.perfumestore.service;

import com.perfumestore.dto.request.CategoryRequest;
import com.perfumestore.dto.response.CategoryResponse;
import com.perfumestore.dto.response.PageResponse;

public interface CategoryService {

    PageResponse<CategoryResponse> list(int page, int size);
    CategoryResponse get(Long id);
    CategoryResponse create(CategoryRequest request);
    CategoryResponse update(Long id, CategoryRequest request);
    void delete(Long id);
}