package com.perfumestore;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Full-context API tests for the HTTP Basic version of the perfume store.
 * Uses real services, repositories, security filters and AOP, with H2 only.
 * No outer test transaction: checkout must really commit or roll back.
 * No dependency on the MySQL seed file or fixed database IDs.
 * Run: .\mvnw.cmd clean test "-Dtest=StoreIntegrationTests"
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:perfume_store_integration_tests;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=false",
        "spring.liquibase.enabled=false",
        "app.admin.email=",
        "app.admin.password=",
        "logging.level.com.perfumestore.aop=INFO"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StoreIntegrationTests {

    private static final String ADMIN = "admin@integration.test";
    private static final String ALICE = "alice@integration.test";
    private static final String BOB = "bob@integration.test";
    private static final String PASSWORD = "IntegrationPass123!";
    private static final long MISSING_ID = Long.MAX_VALUE;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final JsonMapper json = JsonMapper.builder().build();

    private long aliceId;
    private long woodyId;
    private long unisexId;
    private long cedarId;
    private long citrusId;

    @BeforeEach
    void prepareIsolatedDatabase(TestInfo testInfo) throws Exception {

        // Refuse cleanup if configuration ever points outside this H2 database.
        assertNotNull(jdbc.getDataSource());
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getURL()
                    .startsWith("jdbc:h2:mem:perfume_store_integration_tests"));
        }

        System.out.println("\n[TEST] " + testInfo.getDisplayName());

        for (String table : List.of("reviews", "order_items", "orders",
                "cart_items", "carts", "product_categories", "products",
                "categories", "users")) {
            jdbc.update("DELETE FROM " + table);
        }

        jdbc.update("INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, ?)",
                "Test Administrator", ADMIN, passwordEncoder.encode(PASSWORD), "ADMIN");

        aliceId = id(register("Alice", ALICE));
        register("Bob", BOB);

        woodyId = createCategory("Woody");
        unisexId = createCategory("Unisex");
        cedarId = createProduct("Atlas Cedar", "Atlas House", "50.25", 10,
                List.of(woodyId, unisexId));
        citrusId = createProduct("Citrus Dawn", "Dawn House", "30.00", 5,
                List.of(unisexId));
    }

    @Test
    @DisplayName("Catalog and reviews can be read without signing in")
    void publicCatalogEndpointsAreAccessible() throws Exception {
        getAs("/api/products", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        getAs("/api/products/" + cedarId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", hasSize(2)));
        getAs("/api/categories", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        getAs("/api/categories/" + woodyId, null).andExpect(status().isOk());
        getAs(reviewsUrl(cedarId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Real JDBC authentication returns the correct roles without passwords")
    void realBasicAuthenticationWorks() throws Exception {
        getAs("/api/auth/me", ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
        getAs("/api/auth/me", ALICE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) aliceId))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        assertTrue(passwordEncoder.matches(PASSWORD, jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, ALICE)));
    }

    @Test
    @DisplayName("Missing, wrong and unknown credentials return 401")
    void invalidAuthenticationIsRejected() throws Exception {
        getAs("/api/auth/me", null).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").with(httpBasic(ALICE, "WrongPassword123!")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").with(httpBasic("missing@integration.test", PASSWORD)))
                .andExpect(status().isUnauthorized());
        getAs("/api/cart", null).andExpect(status().isUnauthorized());
        getAs("/api/orders", null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Registration creates a customer, normalized email, hash and empty cart")
    void registrationCreatesCustomerAndCart() throws Exception {
        String email = "NEW.CUSTOMER@integration.test";
        MvcResult result = jsonRequest(post("/api/auth/register"), null, Map.of(
                "name", "  New Customer  ", "email", email, "password", PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Customer"))
                .andExpect(jsonPath("$.email").value("new.customer@integration.test"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?",
                String.class, id(result));
        assertNotEquals(PASSWORD, hash);
        assertTrue(hash.startsWith("{bcrypt}"));
        assertTrue(passwordEncoder.matches(PASSWORD, hash));
        getAs("/api/cart", "new.customer@integration.test")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalAmount").value(0));
    }

    @Test
    @DisplayName("Duplicate email registration is case insensitive")
    void duplicateEmailIsRejected() throws Exception {
        long before = count("users");
        jsonRequest(post("/api/auth/register"), null, Map.of(
                "name", "Duplicate", "email", ALICE.toUpperCase(java.util.Locale.ROOT),
                "password", PASSWORD)).andExpect(status().isConflict());
        assertEquals(before, count("users"));
    }

    @Test
    @DisplayName("Registration rejects invalid fields and BCrypt byte overflow")
    void registrationValidationWorks() throws Exception {
        List<Map<String, Object>> invalid = new ArrayList<>();
        invalid.add(Map.of("name", " ", "email", "new@integration.test", "password", PASSWORD));
        invalid.add(Map.of("name", "New", "email", "invalid-email", "password", PASSWORD));
        invalid.add(Map.of("name", "New", "email", "new@integration.test", "password", "short"));
        invalid.add(Map.of("name", "New", "email", "new@integration.test", "password", "x".repeat(73)));
        invalid.add(Map.of("name", "New", "email", "new@integration.test", "password", "ع".repeat(40)));
        long before = count("users");
        for (Map<String, Object> request : invalid) {
            jsonRequest(post("/api/auth/register"), null, request)
                    .andExpect(status().isBadRequest());
        }
        assertEquals(before, count("users"));
    }

    @Test
    @DisplayName("Customer and anonymous users cannot modify the administrator catalog")
    void catalogPermissionsAreEnforced() throws Exception {
        List<MockHttpServletRequestBuilder> requests = List.of(
                post("/api/products"), put("/api/products/" + cedarId),
                delete("/api/products/" + cedarId), post("/api/categories"),
                put("/api/categories/" + woodyId), delete("/api/categories/" + woodyId));
        for (MockHttpServletRequestBuilder request : requests) {
            jsonRequest(request, ALICE, Map.of()).andExpect(status().isForbidden());
        }
        jsonRequest(post("/api/products"), null, Map.of())
                .andExpect(status().isUnauthorized());
        getAs("/api/admin/products", ALICE).andExpect(status().isForbidden());
        getAs("/api/admin/orders", ALICE).andExpect(status().isForbidden());
        getAs("/api/admin/orders", null).andExpect(status().isUnauthorized());
        getAs("/api/admin/orders", ADMIN).andExpect(status().isOk());
        getAs("/api/cart", ADMIN).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Administrator can create and update categories")
    void categoryCreateAndUpdateWork() throws Exception {
        long categoryId = createCategory("Fresh");
        jsonRequest(put("/api/categories/" + categoryId), ADMIN,
                Map.of("name", "Citrus", "description", "Fresh citrus fragrances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Citrus"));
        getAs("/api/categories/" + categoryId, null)
                .andExpect(jsonPath("$.description").value("Fresh citrus fragrances"));
    }

    @Test
    @DisplayName("Category duplicate names and invalid names are rejected")
    void categoryValidationAndDuplicatesAreHandled() throws Exception {
        jsonRequest(post("/api/categories"), ADMIN, Map.of("name", "woody"))
                .andExpect(status().isConflict());
        jsonRequest(put("/api/categories/" + woodyId), ADMIN, Map.of("name", "Unisex"))
                .andExpect(status().isConflict());
        jsonRequest(post("/api/categories"), ADMIN, Map.of("name", " "))
                .andExpect(status().isBadRequest());
        jsonRequest(post("/api/categories"), ADMIN, Map.of("name", "x".repeat(81)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Assigned categories are protected; unused categories can be deleted")
    void categoryDeletionRulesWork() throws Exception {
        request(delete("/api/categories/" + woodyId), ADMIN)
                .andExpect(status().isConflict());
        long unused = createCategory("Unused");
        request(delete("/api/categories/" + unused), ADMIN)
                .andExpect(status().isNoContent());
        getAs("/api/categories/" + unused, null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Product update replaces fields and category assignments")
    void productUpdateWorks() throws Exception {
        jsonRequest(put("/api/products/" + cedarId), ADMIN,
                productData("Updated Cedar", "Atlas House", "75.50", 12, List.of(unisexId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Cedar"))
                .andExpect(jsonPath("$.price").value(75.5))
                .andExpect(jsonPath("$.stockQuantity").value(12))
                .andExpect(jsonPath("$.categories", hasSize(1)))
                .andExpect(jsonPath("$.categories[0].id").value((int) unisexId));
    }

    @Test
    @DisplayName("Products require valid prices, quantities, bottle sizes and categories")
    void productValidationWorks() throws Exception {
        List<Map<String, Object>> invalid = new ArrayList<>();
        invalid.add(productData("Invalid", "Test", "0.00", 1, List.of(woodyId)));
        invalid.add(productData("Invalid", "Test", "10.00", -1, List.of(woodyId)));
        invalid.add(productData("Invalid", "Test", "10.00", 1, List.of()));
        Map<String, Object> invalidSize = new java.util.LinkedHashMap<>(
                productData("Invalid", "Test", "10.00", 1, List.of(woodyId)));
        invalidSize.put("sizeMl", 0);
        invalid.add(invalidSize);
        for (Map<String, Object> data : invalid) {
            jsonRequest(post("/api/products"), ADMIN, data).andExpect(status().isBadRequest());
        }
        jsonRequest(post("/api/products"), ADMIN,
                productData("Missing category", "Test", "10.00", 1, List.of(MISSING_ID)))
                .andExpect(status().isNotFound());
        assertEquals(2, count("products"));
    }

    @Test
    @DisplayName("Search matches name and brand, and treats wildcard characters literally")
    void productSearchWorks() throws Exception {
        getAs("/api/products?search=ATLAS", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value((int) cedarId));
        getAs("/api/products?search=Dawn", null)
                .andExpect(jsonPath("$.content[0].id").value((int) citrusId));
        request(get("/api/products").param("search", "%"), null)
                .andExpect(jsonPath("$.totalElements").value(0));
        request(get("/api/products").param("search", "_"), null)
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Category and price filters work without duplicate products")
    void catalogFiltersWork() throws Exception {
        getAs("/api/products?categoryId=" + woodyId, null)
                .andExpect(jsonPath("$.totalElements").value(1));
        getAs("/api/products?categoryId=" + unisexId, null)
                .andExpect(jsonPath("$.totalElements").value(2));
        getAs("/api/products?minPrice=50.25&maxPrice=50.25", null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value((int) cedarId));
        getAs("/api/products?categoryId=" + MISSING_ID, null)
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("In-stock filters distinguish available and sold-out perfumes")
    void stockFiltersWork() throws Exception {
        long empty = createProduct("Sold Out", "Test House", "20.00", 0, List.of(woodyId));
        getAs("/api/products?inStock=true", null)
                .andExpect(jsonPath("$.totalElements").value(2));
        getAs("/api/products?inStock=false", null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value((int) empty));
    }

    @Test
    @DisplayName("Pagination and price sorting return the expected pages")
    void paginationAndSortingWork() throws Exception {
        getAs("/api/products?page=0&size=1&sort=price&direction=asc", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value((int) citrusId))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
        getAs("/api/products?page=1&size=1&sort=price&direction=asc", null)
                .andExpect(jsonPath("$.content[0].id").value((int) cedarId));
        getAs("/api/products?sort=price&direction=desc", null)
                .andExpect(jsonPath("$.content[0].id").value((int) cedarId));
        getAs("/api/categories?page=0&size=1", null)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("Invalid catalog query parameters return 400")
    void invalidQueryParametersAreRejected() throws Exception {
        for (String query : List.of("page=-1", "size=0", "size=101", "sort=passwordHash",
                "direction=sideways", "minPrice=-1", "minPrice=100&maxPrice=10",
                "categoryId=0", "categoryId=abc")) {
            getAs("/api/products?" + query, null).andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("Soft deletion hides products publicly but keeps them in the admin catalog")
    void softDeletionAndAdminCatalogWork() throws Exception {
        request(delete("/api/products/" + cedarId), ADMIN).andExpect(status().isNoContent());
        getAs("/api/products/" + cedarId, null).andExpect(status().isNotFound());
        getAs("/api/products?includeInactive=true", null)
                .andExpect(jsonPath("$.totalElements").value(1));
        getAs("/api/admin/products", ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        assertEquals(2, count("products"));
    }

    @Test
    @DisplayName("Adding the same product merges quantities without reserving stock")
    void cartAddsAndMergesItems() throws Exception {
        add(ALICE, cedarId, 2).andExpect(status().isOk());
        add(ALICE, cedarId, 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.items[0].lineTotal").value(251.25))
                .andExpect(jsonPath("$.totalAmount").value(251.25));
        assertEquals(10, stock(cedarId));
    }

    @Test
    @DisplayName("Cart quantity update uses product ID and recalculates totals")
    void cartQuantityUpdateWorks() throws Exception {
        add(ALICE, cedarId, 2).andExpect(status().isOk());
        updateQuantity(ALICE, cedarId, 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.totalAmount").value(150.75));
    }

    @Test
    @DisplayName("Cart item removal and clearing work")
    void cartRemoveAndClearWork() throws Exception {
        add(ALICE, cedarId, 1).andExpect(status().isOk());
        add(ALICE, citrusId, 1).andExpect(status().isOk());
        request(delete("/api/cart/items/" + cedarId), ALICE)
                .andExpect(status().isNoContent());
        getAs("/api/cart", ALICE).andExpect(jsonPath("$.items", hasSize(1)));
        request(delete("/api/cart"), ALICE).andExpect(status().isNoContent());
        getAs("/api/cart", ALICE)
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalAmount").value(0));
    }

    @Test
    @DisplayName("Invalid cart quantities and missing fields return 400")
    void cartRequestValidationWorks() throws Exception {
        for (int quantity : List.of(0, -1, 1001)) {
            add(ALICE, cedarId, quantity).andExpect(status().isBadRequest());
        }
        jsonRequest(post("/api/cart/items"), ALICE, Map.of("quantity", 1))
                .andExpect(status().isBadRequest());
        jsonRequest(post("/api/cart/items"), ALICE, Map.of("productId", cedarId))
                .andExpect(status().isBadRequest());
        add(ALICE, 0, 1).andExpect(status().isBadRequest());
        add(ALICE, cedarId, 1).andExpect(status().isOk());
        updateQuantity(ALICE, cedarId, 0).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Stock conflicts preserve the previous cart quantity")
    void cartRejectsExcessStockWithoutChangingItems() throws Exception {
        add(ALICE, cedarId, 2).andExpect(status().isOk());
        add(ALICE, cedarId, 9).andExpect(status().isConflict());
        updateQuantity(ALICE, cedarId, 11).andExpect(status().isConflict());
        getAs("/api/cart", ALICE).andExpect(jsonPath("$.items[0].quantity").value(2));
        assertEquals(10, stock(cedarId));
    }

    @Test
    @DisplayName("Missing, inactive and sold-out products cannot be added")
    void cartRejectsUnavailableProducts() throws Exception {
        add(ALICE, MISSING_ID, 1).andExpect(status().isNotFound());
        request(delete("/api/products/" + cedarId), ADMIN).andExpect(status().isNoContent());
        add(ALICE, cedarId, 1).andExpect(status().isNotFound());
        long soldOut = createProduct("Sold Out", "Test", "20.00", 0, List.of(woodyId));
        add(ALICE, soldOut, 1).andExpect(status().isConflict());
        updateQuantity(ALICE, citrusId, 1).andExpect(status().isNotFound());
        request(delete("/api/cart/items/" + citrusId), ALICE)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A customer's cart cannot be read or changed through another account")
    void cartOwnershipIsEnforced() throws Exception {
        add(ALICE, cedarId, 2).andExpect(status().isOk());
        getAs("/api/cart?userId=" + aliceId, BOB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        updateQuantity(BOB, cedarId, 3).andExpect(status().isNotFound());
        request(delete("/api/cart/items/" + cedarId), BOB).andExpect(status().isNotFound());
        getAs("/api/cart", ALICE).andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    @DisplayName("Checkout saves snapshots, deducts stock and clears the cart")
    void checkoutCompletesThePurchase() throws Exception {
        add(ALICE, cedarId, 2).andExpect(status().isOk());
        add(ALICE, citrusId, 2).andExpect(status().isOk());
        MvcResult result = checkout(ALICE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value((int) aliceId))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalAmount").value(160.5))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();
        assertEquals(8, stock(cedarId));
        assertEquals(3, stock(citrusId));
        getAs("/api/cart", ALICE).andExpect(jsonPath("$.items", hasSize(0)));
        getAs("/api/orders/" + id(result), ALICE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(160.5));
        getAs("/api/orders", ALICE).andExpect(jsonPath("$.totalElements").value(1));
        assertOrderTotalsMatch();
    }

    @Test
    @DisplayName("An empty cart cannot create an order")
    void emptyCheckoutIsRejected() throws Exception {
        checkout(ALICE).andExpect(status().isConflict());
        assertEquals(0, count("orders"));
        assertEquals(10, stock(cedarId));
    }

    @Test
    @DisplayName("Checkout rechecks stock changed after an item was added")
    void checkoutRechecksCurrentStock() throws Exception {
        add(ALICE, cedarId, 3).andExpect(status().isOk());
        jdbc.update("UPDATE products SET stock_quantity = 2 WHERE id = ?", cedarId);
        checkout(ALICE).andExpect(status().isConflict());
        assertEquals(2, stock(cedarId));
        assertEquals(0, count("orders"));
        getAs("/api/cart", ALICE).andExpect(jsonPath("$.items[0].quantity").value(3));
    }

    @Test
    @DisplayName("A product deactivated after adding to cart blocks checkout")
    void checkoutRejectsNewlyInactiveProducts() throws Exception {
        add(ALICE, cedarId, 1).andExpect(status().isOk());
        request(delete("/api/products/" + cedarId), ADMIN).andExpect(status().isNoContent());
        getAs("/api/cart", ALICE)
                .andExpect(jsonPath("$.items[0].available").value(false));
        checkout(ALICE).andExpect(status().isConflict());
        assertEquals(0, count("orders"));
        assertEquals(10, stock(cedarId));
    }

    @Test
    @DisplayName("One unavailable line prevents every part of a multi-item checkout")
    void failedMultiItemCheckoutHasNoPartialChanges() throws Exception {
        add(ALICE, cedarId, 2).andExpect(status().isOk());
        add(ALICE, citrusId, 2).andExpect(status().isOk());
        jdbc.update("UPDATE products SET stock_quantity = 1 WHERE id = ?", citrusId);
        checkout(ALICE).andExpect(status().isConflict());
        assertEquals(10, stock(cedarId));
        assertEquals(1, stock(citrusId));
        assertEquals(0, count("orders"));
        assertEquals(0, count("order_items"));
        getAs("/api/cart", ALICE).andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    @DisplayName("A database failure during order save rolls back stock and the order")
    void checkoutRollsBackAfterPersistenceFailure() throws Exception {
        add(ALICE, cedarId, 2).andExpect(status().isOk());
        // Intentionally fail the order-item INSERT, after business validation.
        // This constraint exists only in this isolated test database.
        jdbc.execute("ALTER TABLE order_items ADD CONSTRAINT test_reject_order_items CHECK (quantity < 0)");
        try {
            checkout(ALICE).andExpect(status().isConflict());
            assertEquals(10, stock(cedarId));
            assertEquals(0, count("orders"));
            assertEquals(0, count("order_items"));
            getAs("/api/cart", ALICE).andExpect(jsonPath("$.items[0].quantity").value(2));
        } finally {
            jdbc.execute("ALTER TABLE order_items DROP CONSTRAINT test_reject_order_items");
        }
    }

    @Test
    @DisplayName("Repeating checkout cannot charge the same cleared cart twice")
    void repeatedCheckoutCreatesOnlyOneOrder() throws Exception {
        add(ALICE, cedarId, 2).andExpect(status().isOk());
        checkout(ALICE).andExpect(status().isCreated());
        checkout(ALICE).andExpect(status().isConflict());
        assertEquals(1, count("orders"));
        assertEquals(8, stock(cedarId));
    }

    @Test
    @Timeout(40)
    @DisplayName("Two customers competing for the last bottle cannot oversell")
    void competingCustomersCannotOversell() throws Exception {
        jdbc.update("UPDATE products SET stock_quantity = 1 WHERE id = ?", cedarId);
        add(ALICE, cedarId, 1).andExpect(status().isOk());
        add(BOB, cedarId, 1).andExpect(status().isOk());
        List<Integer> statuses = simultaneousCheckout(ALICE, BOB);
        assertEquals(List.of(201, 409), statuses.stream().sorted().toList());
        assertEquals(0, stock(cedarId));
        assertEquals(1, count("orders"));
        assertEquals(1, count("order_items"));
        assertEquals(1, count("cart_items"));
    }

    @Test
    @Timeout(40)
    @DisplayName("Concurrent checkout of the same customer cart creates one order")
    void sameCartCannotBeCheckedOutConcurrentlyTwice() throws Exception {
        add(ALICE, cedarId, 2).andExpect(status().isOk());
        List<Integer> statuses = simultaneousCheckout(ALICE, ALICE);
        assertEquals(List.of(201, 409), statuses.stream().sorted().toList());
        assertEquals(1, count("orders"));
        assertEquals(8, stock(cedarId));
        assertEquals(0, count("cart_items"));
    }

    @Test
    @DisplayName("Orders are private to their owner; administrators can read all orders")
    void orderOwnershipAndAdminAccessWork() throws Exception {
        add(ALICE, cedarId, 1).andExpect(status().isOk());
        long orderId = id(checkout(ALICE).andExpect(status().isCreated()).andReturn());
        getAs("/api/orders/" + orderId, ALICE).andExpect(status().isOk());
        getAs("/api/orders/" + orderId, BOB).andExpect(status().isNotFound());
        getAs("/api/orders", BOB).andExpect(jsonPath("$.totalElements").value(0));
        getAs("/api/admin/orders/" + orderId, ADMIN).andExpect(status().isOk());
        getAs("/api/admin/orders", ADMIN).andExpect(jsonPath("$.totalElements").value(1));
        getAs("/api/admin/orders/" + orderId, BOB).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Changing or deleting a catalog product preserves purchase snapshots")
    void purchaseSnapshotsSurviveCatalogChanges() throws Exception {
        add(ALICE, cedarId, 2).andExpect(status().isOk());
        long orderId = id(checkout(ALICE).andExpect(status().isCreated()).andReturn());
        jsonRequest(put("/api/products/" + cedarId), ADMIN,
                productData("Renamed Perfume", "Atlas House", "99.99", 8, List.of(woodyId)))
                .andExpect(status().isOk());
        request(delete("/api/products/" + cedarId), ADMIN).andExpect(status().isNoContent());
        getAs("/api/orders/" + orderId, ALICE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("Atlas Cedar"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(50.25))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.totalAmount").value(100.5));
    }

    @Test
    @DisplayName("Reviews record the authenticated author and are publicly readable")
    void reviewCreationAndAuthorIdentityWork() throws Exception {
        review(ALICE, cedarId, 4, "  A balanced woody scent.  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value((int) aliceId))
                .andExpect(jsonPath("$.authorName").value("Alice"))
                .andExpect(jsonPath("$.productId").value((int) cedarId))
                .andExpect(jsonPath("$.comment").value("A balanced woody scent."))
                .andExpect(jsonPath("$.authorEmail").doesNotExist());
        getAs(reviewsUrl(cedarId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("One review per customer/product is enforced while other customers can review")
    void reviewUniquenessAndPaginationWork() throws Exception {
        review(ALICE, cedarId, 5, "Excellent scent").andExpect(status().isCreated());
        review(ALICE, cedarId, 3, "Second review").andExpect(status().isConflict());
        review(BOB, cedarId, 4, "Pleasant scent").andExpect(status().isCreated());
        getAs(reviewsUrl(cedarId) + "?page=0&size=1", null)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("Review rating, comment, product and role validation are enforced")
    void reviewValidationAndPermissionsWork() throws Exception {
        review(ALICE, cedarId, 0, "Invalid rating").andExpect(status().isBadRequest());
        review(ALICE, cedarId, 6, "Invalid rating").andExpect(status().isBadRequest());
        review(ALICE, cedarId, 4, " ").andExpect(status().isBadRequest());
        review(ALICE, cedarId, 4, "x".repeat(2001)).andExpect(status().isBadRequest());
        review(ALICE, MISSING_ID, 4, "Missing product").andExpect(status().isNotFound());
        review(null, cedarId, 4, "Anonymous").andExpect(status().isUnauthorized());
        review(ADMIN, cedarId, 4, "Admin").andExpect(status().isForbidden());
        request(delete("/api/products/" + cedarId), ADMIN).andExpect(status().isNoContent());
        review(ALICE, cedarId, 4, "Inactive product").andExpect(status().isNotFound());
        assertEquals(0, count("reviews"));
    }

    @Test
    @DisplayName("Errors use the API error format and malformed JSON returns 400")
    void apiErrorsAreStructured() throws Exception {
        getAs("/api/products/" + MISSING_ID, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/products/" + MISSING_ID))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content("{ broken json"))
                .andExpect(status().isBadRequest());
        jsonRequest(post("/api/auth/register"), null,
                Map.of("name", "", "email", "bad", "password", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("Product AOP logs searches and lookup failure/finally advice")
    void productAopMessagesAppear(CapturedOutput output) throws Exception {
        int before = output.getAll().length();
        getAs("/api/products?search=Atlas", null).andExpect(status().isOk());
        getAs("/api/products/" + MISSING_ID, null).andExpect(status().isNotFound());
        String logs = output.getAll().substring(before);
        assertTrue(logs.contains("Calling service method: list"), logs);
        assertTrue(logs.contains("Product search returned 1 product(s)"), logs);
        assertTrue(logs.contains("get failed:"), logs);
        assertTrue(logs.contains("get finished"), logs);
    }

    @Test
    @DisplayName("Checkout AOP logs the annotation, result, duration and ordered trace")
    void successfulCheckoutAopMessagesAppear(CapturedOutput output) throws Exception {
        add(ALICE, cedarId, 1).andExpect(status().isOk());
        int before = output.getAll().length();
        long orderId = id(checkout(ALICE).andExpect(status().isCreated()).andReturn());
        String logs = output.getAll().substring(before);
        assertTrue(logs.contains("TRACE enter checkout"), logs);
        assertTrue(logs.contains("Checkout requested: user " + aliceId), logs);
        assertTrue(logs.contains("Tracked action started: checkout"),
                "Add @LogStoreAction to OrderServiceImpl.checkout.\n" + logs);
        assertTrue(logs.contains("checkout returned order ID " + orderId), logs);
        assertTrue(logs.contains("checkout took"), logs);
        assertTrue(logs.contains("TRACE exit checkout"), logs);
        assertTrue(logs.indexOf("TRACE enter checkout") < logs.indexOf("Checkout requested:"), logs);
        assertTrue(logs.indexOf("checkout took") < logs.indexOf("TRACE exit checkout"), logs);
        assertFalse(logs.contains(PASSWORD), logs);
        assertFalse(logs.contains(ALICE), logs);
    }

    @Test
    @DisplayName("Failed checkout AOP still logs timing and exit without a success message")
    void failedCheckoutAopMessagesAppear(CapturedOutput output) throws Exception {
        int before = output.getAll().length();
        checkout(ALICE).andExpect(status().isConflict());
        String logs = output.getAll().substring(before);
        assertTrue(logs.contains("checkout failed:"), logs);
        assertTrue(logs.contains("checkout took"), logs);
        assertTrue(logs.contains("TRACE exit checkout"), logs);
        assertFalse(logs.contains("checkout returned order ID"), logs);
    }

    @Test
    @DisplayName("Read-only order methods are excluded from checkout timing and tracing")
    void orderReadMethodsAreExcludedFromActionAdvice(CapturedOutput output) throws Exception {
        add(ALICE, cedarId, 1).andExpect(status().isOk());
        long orderId = id(checkout(ALICE).andExpect(status().isCreated()).andReturn());
        int before = output.getAll().length();
        getAs("/api/orders", ALICE).andExpect(status().isOk());
        getAs("/api/orders/" + orderId, ALICE).andExpect(status().isOk());
        getAs("/api/admin/orders", ADMIN).andExpect(status().isOk());
        getAs("/api/admin/orders/" + orderId, ADMIN).andExpect(status().isOk());
        String logs = output.getAll().substring(before);
        assertFalse(logs.contains(" took "), logs);
        assertFalse(logs.contains("TRACE enter"), logs);
        assertFalse(logs.contains("Tracked action started"), logs);
    }

    // Helpers keep each test focused on an observable application behavior.

    private ResultActions request(MockHttpServletRequestBuilder builder, String email) throws Exception {
        if (email != null) {
            builder.with(httpBasic(email, PASSWORD));
        }
        return mvc.perform(builder);
    }

    private ResultActions getAs(String path, String email) throws Exception {
        return request(get(path), email);
    }

    private ResultActions jsonRequest(MockHttpServletRequestBuilder builder,
            String email, Map<String, ?> body) throws Exception {
        return request(builder.contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), email);
    }

    private MvcResult register(String name, String email) throws Exception {
        return jsonRequest(post("/api/auth/register"), null,
                Map.of("name", name, "email", email, "password", PASSWORD))
                .andExpect(status().isCreated()).andReturn();
    }

    private long createCategory(String name) throws Exception {
        return id(jsonRequest(post("/api/categories"), ADMIN,
                Map.of("name", name, "description", name + " sample category"))
                .andExpect(status().isCreated()).andReturn());
    }

    private long createProduct(String name, String brand, String price,
            int stock, List<Long> categories) throws Exception {
        return id(jsonRequest(post("/api/products"), ADMIN,
                productData(name, brand, price, stock, categories))
                .andExpect(status().isCreated()).andReturn());
    }

    private Map<String, Object> productData(String name, String brand,
            String price, int stock, List<Long> categories) {
        return Map.of("name", name, "brand", brand, "description", "Integration test perfume",
                "price", new BigDecimal(price), "stockQuantity", stock,
                "sizeMl", 100, "concentration", "Eau de Parfum", "categoryIds", categories);
    }

    private ResultActions add(String email, long productId, int quantity) throws Exception {
        return jsonRequest(post("/api/cart/items"), email,
                Map.of("productId", productId, "quantity", quantity));
    }

    private ResultActions updateQuantity(String email, long productId, int quantity) throws Exception {
        return jsonRequest(put("/api/cart/items/" + productId), email,
                Map.of("quantity", quantity));
    }

    private ResultActions checkout(String email) throws Exception {
        return request(post("/api/orders/checkout"), email);
    }

    private String reviewsUrl(long productId) {
        return "/api/products/" + productId + "/reviews";
    }

    private ResultActions review(String email, long productId, int rating, String comment) throws Exception {
        return jsonRequest(post(reviewsUrl(productId)), email,
                Map.of("rating", rating, "comment", comment));
    }

    private long id(MvcResult result) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return value.longValue();
    }

    private long count(String table) {
        // Table names are constants supplied only by this test class.
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private int stock(long productId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?",
                Integer.class, productId);
    }

    private void assertOrderTotalsMatch() {
        long mismatches = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT o.id FROM orders o
                    JOIN order_items i ON i.order_id = o.id
                    GROUP BY o.id, o.total_amount
                    HAVING ABS(o.total_amount - SUM(i.unit_price * i.quantity)) > 0.001
                ) mismatches
                """, Long.class);
        assertEquals(0, mismatches);
    }

    private List<Integer> simultaneousCheckout(String firstEmail, String secondEmail) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> first = checkoutTask(firstEmail, ready, start);
            Callable<Integer> second = checkoutTask(secondEmail, ready, start);
            Future<Integer> firstResult = executor.submit(first);
            Future<Integer> secondResult = executor.submit(second);
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Checkout workers did not become ready");
            start.countDown();
            return List.of(firstResult.get(15, TimeUnit.SECONDS),
                    secondResult.get(15, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Checkout workers did not stop");
        }
    }

    private Callable<Integer> checkoutTask(String email, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Checkout start signal timed out");
            }
            return checkout(email).andReturn().getResponse().getStatus();
        };
    }
}
