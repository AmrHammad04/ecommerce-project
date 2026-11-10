# Perfume Store Backend

A Spring Boot REST API for a perfume store. Customers register, browse a paginated catalog, manage their own cart, place orders and write reviews. Administrators manage the catalog and inspect orders. A perfume can belong to more than one category.

The assignment is an API-only project. Open an API endpoint or use Postman; there is no storefront page mapped to `/`.

## Technology and structure

- Java 21 target; Spring Boot 4.1.2; Maven Wrapper.
- Spring Web MVC, Spring Data JPA/Hibernate, Bean Validation and Spring Security.
- MySQL for the running application; isolated H2 for `StoreIntegrationTests`.
- HTTP Basic authentication, JDBC user lookup, BCrypt password hashes and CUSTOMER/ADMIN roles.
- Spring AOP with AspectJ-style annotations for search, checkout, timing and trace messages.

| Package | Responsibility |
| --- | --- |
| `com.perfumestore.entity` | Persistent entities, `BaseEntity` and `Role` |
| `com.perfumestore.dao` | Spring Data repository interfaces |
| `com.perfumestore.dto.request` | Validated request classes |
| `com.perfumestore.dto.response` | API response classes |
| `com.perfumestore.service` | Service interfaces, implementations, DTO mapping and paging |
| `com.perfumestore.controller` | REST endpoints |
| `com.perfumestore.security` | Security configuration and password validation |
| `com.perfumestore.exception` | API exceptions and global error responses |
| `com.perfumestore.aop` | Custom aspects and `@LogStoreAction` |

Java source belongs in `src/main/java`, configuration in `src/main/resources`, and tests in `src/test/java`. `AdminInitializer.java` and its now-empty `config` package have been removed. Authentication remains configured in `security/SecurityConfig.java`; the existing administrator remains in MySQL.

## Files to add to the project

Place the supplied documentation and collection as follows:

| Supplied file | Project location |
| --- | --- |
| `README.md` | Project root |
| `REQUIREMENTS_CHECKLIST.md` | Project root |
| `PERFUME_STORE_PROJECT_GUIDE.md` | Project root; detailed learning and design reference |
| `perfume-store.postman_collection.json` | `postman/perfume-store.postman_collection.json` |
| Previously supplied `perfume-store-seed.sql` | `database/perfume-store-seed.sql` |
| Previously supplied `StoreIntegrationTests.java` | `src/test/java/com/perfumestore/StoreIntegrationTests.java` |

## Prerequisites

1. A JDK compatible with the Java 21 target. Confirm `java -version` and `JAVA_HOME` point to the intended JDK.
2. A running MySQL server, normally at `localhost:3306`.
3. A MySQL account that can use the application schema and create/update its tables during development.
4. Postman for manual API checks or the collection walkthrough.

The Maven Wrapper downloads Maven and dependencies when necessary, so a separate Maven installation is optional.

## Database and configuration

Create the schema in MySQL Workbench or the MySQL client:

```sql
CREATE DATABASE IF NOT EXISTS perfume_store
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

The relevant `src/main/resources/application.properties` settings are:

```properties
spring.application.name=perfume-store

spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/perfume_store}
spring.datasource.username=${DB_USERNAME:perfume_store}
spring.datasource.password=${DB_PASSWORD}

spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.open-in-view=false
spring.jpa.show-sql=false
```

Use ordinary lines without the trailing backslashes or Markdown escapes that can appear when copying formatted text.

| Variable | Purpose |
| --- | --- |
| `DB_URL` | Optional JDBC URL; defaults to the local `perfume_store` schema |
| `DB_USERNAME` | MySQL account name; the local walkthrough used `root` |
| `DB_PASSWORD` | Password for that MySQL account |

Set the database credentials in the PowerShell terminal that will run Maven:

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "YOUR_MYSQL_PASSWORD"
```

Replace the placeholder with the actual MySQL password. MySQL credentials and store-account credentials have different purposes. Keep real passwords out of source control.

### Administrator setup after removing the initializer

The application no longer creates an administrator during startup. The existing administrator account remains in the `users` table and can continue authenticating normally. Deleting the Java initializer does not delete database rows.

For a fresh database:

1. Create the schema and start the application once so Hibernate creates the tables.
2. Stop the application.
3. Import the complete `database/perfume-store-seed.sql` script as described under **Optional sample data**.
4. Restart and authenticate as `seed.admin@perfume.test` using `DemoPerfume123!` for a newly seeded account.

The seed creates demo data as well as an administrator. If that email already exists, its original password and role are preserved; the demo password is not a reset mechanism.

Normal registration always creates CUSTOMER accounts. There is no public administrator-registration endpoint. Keep the seed script available so another developer can establish an administrator on a fresh database.

The old `app.admin.email` and `app.admin.password` entries can be removed from application properties. `ADMIN_EMAIL` and `ADMIN_PASSWORD` no longer create accounts or change passwords. Legacy blank `app.admin.*` values in test configuration are harmless unused settings; they are not required for authentication.

## Compile, test and run

Run commands from the directory containing `pom.xml` and `mvnw.cmd`.

Compile the application:

```powershell
.\mvnw.cmd clean compile
```

Run the supplied integration test class:

```powershell
.\mvnw.cmd clean test "-Dtest=StoreIntegrationTests"
```

This command compiles production and test sources before executing the selected test class. The provided class contains 44 test methods, creates its own users/catalog data, uses an in-memory H2 database and resets that test data before each case. No running HTTP server, MySQL seed import or fixed database IDs are required for this class.

The class exercises real controllers, services, repositories, authentication filters and AOP through MockMvc. AOP advice is verified through captured terminal output. No custom `SecurityUser` principal is required.

To run all project tests and package the application:

```powershell
.\mvnw.cmd clean verify
```

Other test classes use their own configuration; ensure any additional application-context test has a valid datasource configuration too. Do not use `-DskipTests` or `-Dmaven.test.skip=true` as a final verification step.

Start only after the selected integration suite passes:

```powershell
.\mvnw.cmd clean test "-Dtest=StoreIntegrationTests"

if ($LASTEXITCODE -eq 0) {
    .\mvnw.cmd spring-boot:run
}
```

On macOS/Linux, use `./mvnw` in place of `.\mvnw.cmd`; make the wrapper executable with `chmod +x mvnw` if necessary.

Successful startup includes `Started PerfumeStoreApplication`. Keep that process running while using Postman. The normal local base URL is `http://localhost:8080`.

### Verification recorded during development

- Before removing the initializer, the independently checked `clean verify` run confirmed **45 tests, 0 failures, 0 errors, 0 skipped**: 44 in `StoreIntegrationTests` and one startup test in `PerfumeStoreApplicationTests`. After removing it, the user reported **BUILD SUCCESS** again; no new full test-count log was supplied for that last build.
- Maven created and repackaged `target/perfume-store-0.0.1-SNAPSHOT.jar` and ended with **BUILD SUCCESS**. Mockito/ByteBuddy agent warnings did not cause a failure in this run.
- The live Postman run reported **138 passing assertions, 0 failed, 0 skipped and 0 errors**, matching the supplied 89-request collection's 138 assertions.
- Manual checks against MySQL confirmed administrator/customer access rules and customer order privacy.
- Manual checkout returned 201, saved the order, reduced stock, and cleared the cart. An empty cart returned 409.
- Manual review creation, duplicate prevention and public review listing passed.

The tests cover application workflows and failure cases. H2 concurrency tests do not by themselves establish MySQL-specific locking behavior under simultaneous requests.

## Optional sample data

The dataset is optional when an administrator already exists. For a fresh database, it is the supplied way to create the first administrator after removing the initializer.

1. Start the app successfully once so Hibernate creates the tables.
2. Stop it with Ctrl+C.
3. Open `database/perfume-store-seed.sql` in MySQL Workbench and execute the entire script in one connection.
4. Restart the application.

The seed contains fictional perfumes and illustrative prices:

| Data | Seed records |
| --- | ---: |
| Categories | 20 |
| Perfumes | 120 |
| Product-category links | 480 |
| Users | 21 |
| Carts / cart items | 20 / 45 |
| Orders / order items | 40 / 120 |
| Reviews | 80 |

The seed includes six inactive perfumes and nine active perfumes with zero stock. Public browsing excludes inactive products. A normal first page contains at most 20 records.

The script inserts missing sample records, preserves existing values and avoids duplicate sample rows on an unchanged re-import. Import it without concurrent writes. If an import stops on an error before COMMIT, run ROLLBACK before correcting and retrying it.

| Demo role | Email | Password for a newly seeded account |
| --- | --- | --- |
| ADMIN | `seed.admin@perfume.test` | `DemoPerfume123!` |
| CUSTOMER | `seed.customer01@perfume.test` | `DemoPerfume123!` |
| CUSTOMER | `seed.customer20@perfume.test` | `DemoPerfume123!` |

Customer numbers run from 01 through 20. Existing accounts with these emails retain their original passwords and roles.

## Authentication and permissions

In Postman choose Authorization > Basic Auth. The username is the store account's email and the password is its original plaintext password. Authentication is checked on protected requests; there is no token-issuing login endpoint.

`JdbcUserDetailsManager` reads email/password hash/role from `users`. The role query adds `ROLE_` for Spring Security, while the database stores `CUSTOMER` or `ADMIN`. New BCrypt hashes use the `{bcrypt}` prefix expected by the delegating encoder.

| Access | Operations |
| --- | --- |
| Public | Register; list/read products and categories; list reviews |
| CUSTOMER | Read own account/cart/orders; add/update/remove cart items; checkout; write reviews |
| ADMIN | Read own account; create/update/remove products and categories; inspect the admin catalog and all orders |

Under this configuration the cart and customer-order routes require CUSTOMER specifically. Administrators inspect orders through `/api/admin/orders`.

The service layer receives the authenticated user's ID. A client-supplied user ID cannot select another customer's cart. An order lookup for a different customer returns 404.

CSRF is disabled for this course-style, locally tested HTTP Basic API configuration. Browser integration would require reviewing that choice because browsers can automatically attach credentials.

## API endpoints

All paths below are relative to the base URL.

| Method | Path | Access |
| --- | --- | --- |
| POST | `/api/auth/register` | Public |
| GET | `/api/auth/me` | Authenticated |
| GET | `/api/products` | Public |
| GET | `/api/products/{productId}` | Public |
| POST | `/api/products` | ADMIN |
| PUT | `/api/products/{productId}` | ADMIN |
| DELETE | `/api/products/{productId}` | ADMIN |
| GET | `/api/categories` | Public |
| GET | `/api/categories/{categoryId}` | Public |
| POST | `/api/categories` | ADMIN |
| PUT | `/api/categories/{categoryId}` | ADMIN |
| DELETE | `/api/categories/{categoryId}` | ADMIN |
| GET | `/api/cart` | CUSTOMER |
| POST | `/api/cart/items` | CUSTOMER |
| PUT | `/api/cart/items/{productId}` | CUSTOMER |
| DELETE | `/api/cart/items/{productId}` | CUSTOMER |
| DELETE | `/api/cart` | CUSTOMER |
| POST | `/api/orders/checkout` | CUSTOMER |
| GET | `/api/orders` | CUSTOMER, own orders |
| GET | `/api/orders/{orderId}` | CUSTOMER, own order |
| GET | `/api/products/{productId}/reviews` | Public |
| POST | `/api/products/{productId}/reviews` | CUSTOMER |
| GET | `/api/admin/products` | ADMIN, includes inactive products |
| GET | `/api/admin/orders` | ADMIN |
| GET | `/api/admin/orders/{orderId}` | ADMIN |

Cart item path IDs refer to **product IDs**, not the internal cart-item row ID.

### Product browsing

`GET /api/products` supports `search`, `categoryId`, `minPrice`, `maxPrice`, `inStock`, `page`, `size`, `sort` and `direction`.

- Search matches name or brand, ignoring case.
- `inStock=true` selects positive stock; `inStock=false` selects zero stock.
- Pages start at 0; default size is 20 and allowed sizes are 1 through 100.
- Product sort fields are `id`, `name`, `brand`, `price` and `stockQuantity`; direction is `asc` or `desc`.
- Public results contain active products. The admin product-list endpoint includes inactive products.
- Reviews and order histories also use page/size parameters. Categories are returned as a list, not a paged response.

Example:

```http
GET /api/products?search=cedar&minPrice=20&maxPrice=150&page=0&size=10&sort=price&direction=asc
```

Page responses contain `content`, `page`, `size`, `totalElements` and `totalPages`.

### Request examples

Register:

```json
{
  "name": "Sample Customer",
  "email": "sample.customer@perfume.test",
  "password": "SamplePassword123!"
}
```

Create/update a product as ADMIN. Replace category IDs with actual values returned by the category API:

```json
{
  "name": "Sample Cedar",
  "brand": "Sample House",
  "description": "A fictional woody perfume.",
  "price": 50.25,
  "stockQuantity": 10,
  "sizeMl": 100,
  "concentration": "Eau de Parfum",
  "categoryIds": [1, 2]
}
```

Add to cart, replacing the example product ID:

```json
{
  "productId": 123,
  "quantity": 2
}
```

Update a cart quantity:

```json
{
  "quantity": 3
}
```

Checkout uses `POST /api/orders/checkout` with Body > none. The server calculates prices and totals from the current catalog; customers do not submit an order price or total.

Write a review:

```json
{
  "rating": 5,
  "comment": "A pleasant fragrance with balanced notes."
}
```

## Business rules and persistence

- Each customer has one cart. Re-adding a product merges its quantity into the existing line.
- Cart quantities must be from 1 to 1000 and fit current stock. Adding to a cart does not reserve stock.
- Checkout rechecks stock and product activity. Its transaction saves the order, deducts stock and clears the cart together.
- Checkout locks the customer's cart and the involved products; product locks are acquired in a consistent ID order. Products also have a JPA `@Version` field.
- Order items store product-name and unit-price snapshots, preserving history after catalog changes.
- Removing a product marks it inactive. Removing a category is rejected while it is assigned to any product, including an inactive one.
- Categories are shared records and are not cascade-deleted through products.
- Reviews are tied to the authenticated author. Rating is 1 through 5, comment is required with a 2000-character limit, and each customer can leave one review per active product. Prior purchase is not required.
- Registration always creates CUSTOMER accounts. Passwords require at least 8 characters and at most 72 UTF-8 bytes for BCrypt.

## AOP terminal output

The custom AOP layout follows the first Library Management practice project:

| File | Behavior |
| --- | --- |
| `OrderTimingAspect` | Combined pointcuts exclude order reads; binds checkout user ID, logs returned order ID, failures and elapsed time; order 3 |
| `OrderTraceAspect` | Enters/exits checkout with `@Around` and `finally`; order 2 |
| `ProductSearchAspect` | Logs list result counts, lookup completion and lookup/delete failures |
| `LogStoreAction` | Runtime method annotation |
| `StoreActionAspect` | Logs methods marked with `@LogStoreAction` |

`OrderServiceImpl.checkout(Long userId)` must carry `@LogStoreAction` alongside its existing transaction annotation.

Example successful checkout messages:

```text
TRACE enter checkout
Checkout requested: user 22
Tracked action started: checkout
checkout returned order ID 41
checkout took 15 ms
TRACE exit checkout
```

IDs and duration vary. Failed checkout still logs its failure, elapsed time and trace exit. Security can reject a request before any service method is called.

## Postman collection walkthrough

1. Import `postman/perfume-store.postman_collection.json` into Postman.
2. Open the collection's Variables tab and configure:
   - `baseUrl`: normally `http://localhost:8080`, without a trailing slash.
   - `adminEmail` and `adminPassword`: an existing administrator. Defaults match the optional seed.
   - `customerPassword`: password for the new demo customers; the provided demo value is valid.
3. Keep the application running against the intended local development database.
4. Run the **whole collection**, starting with `00 - Initialize and administrator login`, with **one iteration** and the default request order.
5. Enable **Keep variable values** if you want to inspect the captured IDs after the run. Avoid environment variables that override collection variables with the same names.

No separate Postman environment file is required.

The collection contains **89 requests in 10 folders**. The first request creates a unique run tag. Subsequent requests register two new customers, create demo categories/products, capture their IDs, exercise cart operations, perform checkout, check privacy and purchase snapshots, simulate an unfulfillable multi-item order, create reviews and check soft deletion.

Each run writes real demo records to the running database. It updates/removes only the records it creates. The unused test category is deleted, one purchased demo product is soft-deleted, and order history/reviews/assigned categories intentionally remain. Re-running from the first request creates a new demonstration dataset; it is not a database reset.

Expected 400/401/403/404/409 responses are successful negative tests. An unexpected HTTP status stops the rest of the collection workflow in the Runner. Workflow control has no effect when sending one request manually, so follow the numbered order when using Send.

The collection checks normal and failed stock handling on the running database. Its requests are sequential; simultaneous competing checkout is covered by the automated H2 tests and still merits a separate MySQL concurrency check. Terminal AOP output is observed in the application's terminal, not read through a Postman endpoint.

The collection was checked for JSON/schema validity, script syntax and variable/body consistency before delivery. The subsequent live run against the local application reported all 138 assertions passing. Retain that run result and repeat relevant checks after application changes.

## Errors and troubleshooting

| Result | Meaning / check |
| --- | --- |
| 201 | Account, product, category, order or review created |
| 204 | Successful removal/clear with no response body |
| 400 | Invalid JSON, fields, paging or filter values |
| 401 | Missing/incorrect store credentials |
| 403 | Account has the wrong role |
| 404 | Item missing/inactive or order does not belong to this customer |
| 409 | Duplicate email/category/review, stock conflict, empty cart or assigned category |

Controller/service errors use `ApiError` fields: `timestamp`, `status`, `error`, `message`, `path` and `fieldErrors`. Security-filter 401/403 responses are produced before the controller advice and need not have that same body.

- **MySQL access denied:** check DB_USERNAME/DB_PASSWORD against the actual MySQL account, not a store account.
- **Password encoding prefix error:** an older database row may hold an unprefixed BCrypt hash. The current encoder expects `{bcrypt}` followed by the existing BCrypt hash. Never label a plaintext password as a BCrypt hash. New registrations and the demo seed already use the expected format.
- **Old administrator environment variables have no effect:** the initializer has been removed. Use the existing database account; importing the seed also preserves a matching existing account rather than resetting it.
- **Empty-cart checkout:** add an available product using the same customer credentials as the checkout request.
- **Missing old Java symbols:** save all files, use `com.perfumestore.dao`, replace obsolete test code, and run a clean build.
- **No storefront at `/`:** use an API URL such as `/api/products`.

## Hand-in checklist

The assignment asks for the complete working project source. Include `pom.xml`, the Maven Wrapper files (including `.mvn`), `src/main`, `src/test`, non-secret application configuration, and these documentation/testing assets. The SQL seed is optional sample data; the application must also work with newly registered accounts and administrator-created catalog data.

The first-admin setup now depends on the supplied seed for a fresh database. Do not re-add the removed initializer or require a `config` folder in the hand-in. `SecurityConfig.java` and `PasswordPolicy.java` remain in `security`.

Generated `target` output is rebuildable. Check that real passwords, local credential files and IDE-specific caches are not included. Keep all service interfaces and their implementations and the five AOP files together in the submitted source.

See `REQUIREMENTS_CHECKLIST.md` for the requirement-to-implementation mapping and the exact limits of the verification evidence.
