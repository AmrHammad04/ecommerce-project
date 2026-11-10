package com.perfumestore.service;

import com.perfumestore.dao.CategoryRepository;
import com.perfumestore.dao.ProductRepository;
import com.perfumestore.dto.request.CategoryRequest;
import com.perfumestore.dto.response.CategoryResponse;
import com.perfumestore.dto.response.PageResponse;
import com.perfumestore.entity.Category;
import com.perfumestore.exception.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final DtoMapper dtoMapper;

    public CategoryServiceImpl(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            DtoMapper dtoMapper) {

        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.dtoMapper = dtoMapper;
    }

    @Override
    public PageResponse<CategoryResponse> list(int page, int size) {
        Pageable pageable = PageSupport.request(
                page,
                size,
                "name",
                "asc",
                Set.of("name")
        );

        Page<CategoryResponse> result = categoryRepository
                .findAll(pageable)
                .map(dtoMapper::category);

        return PageResponse.from(result);
    }

    @Override
    public CategoryResponse get(Long id) {
        Category category = requireCategory(id);

        return dtoMapper.category(category);
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.getName().trim();

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict(
                    "Category name already exists."
            );
        }

        Category category = new Category();
        category.setName(name);
        category.setDescription(request.getDescription());

        categoryRepository.saveAndFlush(category);

        return dtoMapper.category(category);
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = requireCategory(id);
        String name = request.getName().trim();

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict(
                    "Category name already exists."
            );
        }

        category.setName(name);
        category.setDescription(request.getDescription());

        return dtoMapper.category(category);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = requireCategory(id);

        if (productRepository.existsByCategories_Id(id)) {
            throw ApiException.conflict(
                    "Reassign products before deleting their category, "
                    + "including inactive products."
            );
        }

        categoryRepository.delete(category);
    }

    private Category requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(
                        "Category not found: " + id
                ));
    }
}