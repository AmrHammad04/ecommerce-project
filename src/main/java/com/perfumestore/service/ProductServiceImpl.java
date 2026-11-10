package com.perfumestore.service;

import com.perfumestore.dao.CategoryRepository;
import com.perfumestore.dao.ProductRepository;
import com.perfumestore.dto.request.ProductRequest;
import com.perfumestore.dto.response.PageResponse;
import com.perfumestore.dto.response.ProductResponse;
import com.perfumestore.entity.Category;
import com.perfumestore.entity.Product;
import com.perfumestore.exception.ApiException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final DtoMapper dtoMapper;

    public ProductServiceImpl(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            DtoMapper dtoMapper) {

        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.dtoMapper = dtoMapper;
    }

    @Override
    public PageResponse<ProductResponse> list(
            String search,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock,
            int page,
            int size,
            String sort,
            String direction,
            boolean includeInactive) {

        validateFilters(categoryId, minPrice, maxPrice);

        Pageable pageable = PageSupport.request(page,size,sort,direction,
                Set.of("id", "name", "brand", "price", "stockQuantity")
        );

        Specification<Product> specification = (root, query, builder) -> {
            List<Predicate> conditions = new ArrayList<>();

            if (!includeInactive) {
                conditions.add(builder.isTrue(root.get("active")));
            }

            if (search != null && !search.isBlank()) {
                String escaped = search.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_");

                String pattern = "%" + escaped + "%";

                conditions.add(
                        builder.or(
                                builder.like(
                                        builder.lower(root.get("name")),
                                        pattern,
                                        '!'
                                ),
                                builder.like(
                                        builder.lower(root.get("brand")),
                                        pattern,
                                        '!'
                                )
                        )
                );
            }

            if (categoryId != null) {
                conditions.add(
                        builder.equal(
                                root.join("categories").get("id"),
                                categoryId
                        )
                );
            }

            if (minPrice != null) {
                conditions.add(
                        builder.greaterThanOrEqualTo(
                                root.get("price"),
                                minPrice
                        )
                );
            }

            if (maxPrice != null) {
                conditions.add(
                        builder.lessThanOrEqualTo(
                                root.get("price"),
                                maxPrice
                        )
                );
            }

            if (inStock != null) {
                if (inStock) {
                    conditions.add(
                            builder.greaterThan(
                                    root.get("stockQuantity"),
                                    0
                            )
                    );
                } else {
                    conditions.add(
                            builder.equal(
                                    root.get("stockQuantity"),
                                    0
                            )
                    );
                }
            }

            return builder.and(
                    conditions.toArray(new Predicate[0])
            );
        };

        Page<ProductResponse> result = productRepository
                .findAll(specification, pageable)
                .map(dtoMapper::product);

        return PageResponse.from(result);
    }

    @Override
    public ProductResponse get(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(
                        "Product not found: " + id
                ));

        if (!product.isActive()) {
            throw ApiException.notFound(
                    "Product not found: " + id
            );
        }

        return dtoMapper.product(product);
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();

        applyRequest(product, request);

        productRepository.saveAndFlush(product);

        return dtoMapper.product(product);
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = requireLockedProduct(id);

        applyRequest(product, request);

        return dtoMapper.product(product);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Product product = requireLockedProduct(id);

        product.setActive(false);
    }

    private Product requireLockedProduct(Long id) {
        return productRepository.findLockedById(id)
                .orElseThrow(() -> ApiException.notFound(
                        "Product not found: " + id
                ));
    }

    private void applyRequest(Product product, ProductRequest request) {
        List<Category> categories = categoryRepository.findAllById(
                request.getCategoryIds()
        );

        if (categories.size() != request.getCategoryIds().size()) {
            throw ApiException.notFound(
                    "One or more categories do not exist."
            );
        }

        product.setName(request.getName().trim());
        product.setBrand(request.getBrand().trim());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setSizeMl(request.getSizeMl());
        product.setConcentration(request.getConcentration());

        product.getCategories().clear();
        product.getCategories().addAll(categories);
    }

    private void validateFilters(
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice) {

        if (categoryId != null && categoryId <= 0) {
            throw ApiException.badRequest(
                    "Category ID must be positive."
            );
        }

        if (minPrice != null && minPrice.signum() < 0) {
            throw ApiException.badRequest(
                    "Minimum price cannot be negative."
            );
        }

        if (maxPrice != null && maxPrice.signum() < 0) {
            throw ApiException.badRequest(
                    "Maximum price cannot be negative."
            );
        }

        if (minPrice != null
                && maxPrice != null
                && minPrice.compareTo(maxPrice) > 0) {

            throw ApiException.badRequest(
                    "Minimum price must not exceed maximum price."
            );
        }
    }
}