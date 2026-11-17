package com.perfumestore;

import com.perfumestore.dto.request.CartItemRequest;
import com.perfumestore.dto.request.CategoryRequest;
import com.perfumestore.dto.request.ProductRequest;
import com.perfumestore.dto.request.RegisterRequest;
import com.perfumestore.dto.response.PageResponse;
import com.perfumestore.dto.response.ProductResponse;
import com.perfumestore.exception.ApiException;
import com.perfumestore.service.AccountService;
import com.perfumestore.service.CartService;
import com.perfumestore.service.CategoryService;
import com.perfumestore.service.OrderService;
import com.perfumestore.service.ProductService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Real services and transactions; an isolated H2 database, never local MySQL. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:perfume_cache_tests;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CacheIntegrationTests {

    @Autowired private CategoryService categories;
    @Autowired private ProductService products;
    @Autowired private OrderService orders;
    @Autowired private CartService carts;
    @Autowired private AccountService accounts;
    @Autowired private CacheManager caches;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private PlatformTransactionManager transactionManager;

    private Long woody;
    private Long unisex;
    private Long cedar;
    private Long citrus;
    private Long inactive;
    private Statistics statistics;

    @BeforeEach
    void prepareDatabaseAndCaches() throws Exception {
        assertNotNull(jdbc.getDataSource());
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getURL()
                    .startsWith("jdbc:h2:mem:perfume_cache_tests"));
        }
        for (String name : caches.getCacheNames()) {
            cache(name).clear();
        }
        for (String table : List.of("reviews", "order_items", "orders", "cart_items",
                "carts", "product_categories", "products", "categories", "users")) {
            jdbc.update("DELETE FROM " + table);
        }
        woody = categories.create(new CategoryRequest("Woody", "Woody notes")).getId();
        unisex = categories.create(new CategoryRequest("Unisex", "All customers")).getId();
        cedar = products.create(product("Cedar", "50.00", 10, woody, unisex)).getId();
        citrus = products.create(product("Citrus", "30.00", 5, unisex)).getId();
        inactive = products.create(product("Secret", "90.00", 1, woody)).getId();
        products.delete(inactive);
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    void repeatedCatalogReadsAvoidDatabaseStatements() {
        categories.get(woody);
        categories.list(0, 20);
        products.get(cedar);
        list(false);
        long statementsAfterMisses = statistics.getPrepareStatementCount();
        assertTrue(statementsAfterMisses > 0);

        categories.get(woody);
        categories.list(0, 20);
        products.get(cedar);
        list(false);
        assertEquals(statementsAfterMisses, statistics.getPrepareStatementCount());
    }

    @Test
    void productListKeysSeparatePublicAndAdminResults() {
        assertEquals(3, list(true).getTotalElements());
        assertEquals(2, list(false).getTotalElements());
        assertFalse(ids(list(false)).contains(inactive));
        assertTrue(ids(list(true)).contains(inactive));
        assertEquals(2, list(false).getTotalElements());
    }

    @Test
    void listKeysIncludeFiltersPaginationAndSorting() {
        assertEquals(List.of(cedar), ids(products.list("Cedar", null, null, null,
                null, 0, 20, "name", "asc", false)));
        assertEquals(List.of(citrus), ids(products.list("Citrus", null, null, null,
                null, 0, 20, "name", "asc", false)));
        assertEquals(List.of(cedar), ids(products.list(null, woody, null, null,
                null, 0, 20, "name", "asc", false)));
        assertEquals(List.of(citrus), ids(products.list(null, null,
                new BigDecimal("20"), new BigDecimal("40"), null,
                0, 20, "price", "asc", false)));
        assertEquals(List.of(cedar), ids(products.list(null, null,
                new BigDecimal("40"), new BigDecimal("60"), null,
                0, 20, "price", "asc", false)));
        assertEquals(List.of(citrus), ids(products.list(null, null, null, null,
                null, 0, 1, "price", "asc", false)));
        assertEquals(List.of(cedar), ids(products.list(null, null, null, null,
                null, 1, 1, "price", "asc", false)));
        assertEquals(List.of(cedar), ids(products.list(null, null, null, null,
                null, 0, 1, "price", "desc", false)));
        assertEquals(2, products.list(null, null, null, null,
                null, 0, 2, "price", "asc", false).getContent().size());
        assertEquals(List.of(cedar, citrus), ids(products.list(null, null, null, null,
                true, 0, 20, "name", "asc", false)));
        assertTrue(products.list(null, null, null, null,
                false, 0, 20, "name", "asc", false).getContent().isEmpty());
        assertEquals(woody, categories.list(1, 1).getContent().getFirst().getId());
        assertEquals(unisex, categories.list(0, 1).getContent().getFirst().getId());
    }

    @Test
    void productCreationRefreshesPublicAndAdminPages() {
        list(false);
        list(true);
        Long added = products.create(product("Amber", "60.00", 3, woody)).getId();
        assertEquals(3, list(false).getTotalElements());
        assertEquals(4, list(true).getTotalElements());
        assertTrue(ids(list(false)).contains(added));
    }

    @Test
    void productUpdatesAndSoftDeletionInvalidateDetailsAndPages() {
        products.get(cedar);
        list(false);
        list(true);
        products.update(cedar, product("Updated Cedar", "75.00", 0, woody));
        assertEquals("Updated Cedar", products.get(cedar).getName());
        assertEquals(0, products.get(cedar).getStockQuantity());
        assertEquals(new BigDecimal("75.00"), products.get(cedar).getPrice());
        assertEquals("Updated Cedar", list(false).getContent().stream()
                .filter(p -> p.getId().equals(cedar)).findFirst().orElseThrow().getName());

        products.delete(cedar);
        assertThrows(ApiException.class, () -> products.get(cedar));
        assertFalse(ids(list(false)).contains(cedar));
        assertFalse(list(true).getContent().stream()
                .filter(p -> p.getId().equals(cedar)).findFirst().orElseThrow().isActive());
    }

    @Test
    void categoryRenameRefreshesEmbeddedProductDetailsAndPages() {
        categories.get(woody);
        categories.list(0, 20);
        products.get(cedar);
        list(false);
        list(true);
        categories.update(woody, new CategoryRequest("Renamed Woody", "New notes"));

        assertEquals("Renamed Woody", categories.get(woody).getName());
        assertTrue(categories.list(0, 20).getContent().stream()
                .anyMatch(c -> c.getName().equals("Renamed Woody")));
        assertRenamedCategory(products.get(cedar));
        for (boolean admin : List.of(false, true)) {
            assertRenamedCategory(list(admin).getContent().stream()
                    .filter(p -> p.getId().equals(cedar)).findFirst().orElseThrow());
        }
    }

    @Test
    void categoryCreationAndDeletionRefreshPagesAndRemoveDetails() {
        categories.list(0, 20);
        Long added = categories.create(new CategoryRequest("Temporary", "Test")).getId();
        assertEquals(3, categories.list(0, 20).getTotalElements());
        categories.get(added);
        categories.delete(added);
        assertThrows(ApiException.class, () -> categories.get(added));
        assertEquals(2, categories.list(0, 20).getTotalElements());
    }

    @Test
    void checkoutRefreshesStockAndStockFilteredPages() {
        Long buyer = accounts.register(new RegisterRequest(
                "Buyer", "buyer@cache.test", "CacheTestPassword123!")).getId();
        carts.add(buyer, new CartItemRequest(citrus, 5));
        products.get(citrus);
        list(false);
        list(true);
        assertTrue(products.list(null, null, null, null, false,
                0, 20, "name", "asc", false).getContent().isEmpty());

        orders.checkout(buyer);

        assertEquals(0, products.get(citrus).getStockQuantity());
        assertEquals(List.of(citrus), ids(products.list(null, null, null, null, false,
                0, 20, "name", "asc", false)));
        for (boolean admin : List.of(false, true)) {
            assertEquals(0, list(admin).getContent().stream()
                    .filter(p -> p.getId().equals(citrus)).findFirst().orElseThrow()
                    .getStockQuantity());
        }
    }

    @Test
    void checkoutUsesDatabaseStockEvenWhenCatalogCopyIsOlder() {
        Long buyer = accounts.register(new RegisterRequest(
                "Buyer", "buyer@cache.test", "CacheTestPassword123!")).getId();
        carts.add(buyer, new CartItemRequest(cedar, 2));
        products.get(cedar);
        // Simulate an external writer; this deliberately does not invalidate the cache.
        jdbc.update("UPDATE products SET stock_quantity = 1 WHERE id = ?", cedar);

        assertThrows(ApiException.class, () -> orders.checkout(buyer));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT stock_quantity FROM products WHERE id = ?", Integer.class, cedar));
    }

    @Test
    void successfulUpdatesEvictOnlyAfterDatabaseCommit() {
        products.get(cedar);
        list(false);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            products.update(cedar, product("Committed Cedar", "70.00", 7, woody));
            assertNotNull(cache("products").get(cedar));
        });

        assertNull(cache("products").get(cedar));
        assertEquals("Committed Cedar", products.get(cedar).getName());
        assertEquals("Committed Cedar", list(false).getContent().stream()
                .filter(p -> p.getId().equals(cedar)).findFirst().orElseThrow().getName());
    }

    @Test
    void rollbackDoesNotEvictExistingCopiesOrPublishNewEntries() {
        categories.get(woody);
        categories.list(0, 20);
        products.get(cedar);
        list(false);
        Object previousProduct = cache("products").get(cedar).get();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            products.update(cedar, product("Rolled Back", "70.00", 1, woody));
            categories.update(woody, new CategoryRequest("Rolled Back Category", "Test"));
            products.get(citrus);
            status.setRollbackOnly();
        });

        assertSame(previousProduct, cache("products").get(cedar).get());
        assertNull(cache("products").get(citrus));
        assertEquals("Cedar", products.get(cedar).getName());
        assertEquals("Woody", categories.get(woody).getName());
        assertEquals("Cedar", jdbc.queryForObject(
                "SELECT name FROM products WHERE id = ?", String.class, cedar));
    }

    @Test
    void rejectedCategoryDeletionKeepsExistingCacheEntries() {
        categories.get(woody);
        categories.list(0, 20);
        products.get(cedar);
        list(false);
        assertThrows(ApiException.class, () -> categories.delete(woody));
        assertNotNull(cache("categories").get(woody));
        assertNotNull(cache("products").get(cedar));
        assertEquals("Woody", categories.get(woody).getName());
    }

    private ProductRequest product(String name, String price, int stock, Long... categoryIds) {
        return new ProductRequest(name, "Test House", "Test perfume", new BigDecimal(price),
                stock, 100, "Eau de Parfum", Set.of(categoryIds));
    }

    private PageResponse<ProductResponse> list(boolean admin) {
        return products.list(null, null, null, null, null, 0, 20, "name", "asc", admin);
    }

    private List<Long> ids(PageResponse<ProductResponse> result) {
        return result.getContent().stream().map(products -> products.getId()).toList();
    }

    private org.springframework.cache.Cache cache(String name) {
        return java.util.Objects.requireNonNull(caches.getCache(name));
    }

    private void assertRenamedCategory(ProductResponse product) {
        assertEquals("Renamed Woody", product.getCategories().stream()
                .filter(c -> c.getId().equals(woody)).findFirst().orElseThrow().getName());
    }
}
