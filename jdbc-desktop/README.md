# Courier JDBC desktop application

Independent Java 17 + Swing + JDBC + MySQL application. No Spring Boot, React, application server, or ORM is required. The original web application remains in `backend/` and `frontend/`.

## Set up MySQL 8+

Run as a database administrator (replace the example password):

```sql
CREATE DATABASE courier_jdbc CHARACTER SET utf8mb4;
CREATE USER 'courier'@'localhost' IDENTIFIED BY 'replace-with-a-unique-password';
GRANT SELECT, INSERT, UPDATE, CREATE, REFERENCES ON courier_jdbc.* TO 'courier'@'localhost';
```

Install JDK 17+ and Maven 3.6.3+. From the repository root:

```bash
cd jdbc-desktop
export DB_URL='jdbc:mysql://localhost:3306/courier_jdbc?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
export DB_USERNAME='courier'
export DB_PASSWORD='replace-with-a-unique-password'
export ADMIN_EMAIL='admin@yourdomain.com'
export ADMIN_PASSWORD='replace-with-a-unique-admin-password'
mvn package
java -jar target/courier-jdbc-1.0.0.jar
```

PowerShell users: use `$env:DB_USERNAME='courier'` (and the same pattern for each variable). A graphical desktop is required to launch Swing. Database tables are initialized automatically; use a separate database from the original web application. Administrator credentials only bootstrap an empty user table. Existing passwords are never reset by environment variables.

## Use the application

1. Sign in with the administrator account.
2. Create delivery agents with unique emails and passwords of at least 12 characters.
3. Register a parcel with sender/recipient contact information, addresses, weight, standard/express service, and expected date. Copy the generated UUID tracking ID.
4. Select the parcel and assign an agent. Agents see only their assigned parcels.
5. Select **Update status** to progress the journey. Locations are public: enter only a city or hub. Internal notes stay private.
6. Use search, status filters, pagination, dashboard totals, history, and notifications. Select a notification and confirm to mark it read.
7. **Public tracking** is available before login. It shows only tracking ID, status, public location, and UTC event time.

Workflow: REGISTERED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED / DELIVERY_FAILED. Failed deliveries can retry or return. Picked-up/in-transit parcels can return. Only administrators can cancel registered parcels. Terminal parcels cannot progress or be reassigned.

## Implementation

- `App.java`: Swing forms, tables, dialogs, and background JDBC workers.
- `Store.java`: prepared statements, validation, database authorization, transactions, row locks, version checks, private/public queries, and persistent notifications.
- `Passwords.java`: salted PBKDF2-HMAC-SHA256, 210,000 iterations, constant-time hash comparison. Passwords are not stored in plaintext.
- `schema.sql`: users, shipments, append-only application event history, and alerts with foreign keys and unique emails/tracking IDs.
- `StoreSmokeTest.java`: executable database integration checks for login, access control, registration, assignments, workflow, rollback, privacy, SQL parameterization, notifications, and dashboard totals.

The desktop client connects directly to MySQL. Run it on trusted computers: distributing database credentials to untrusted users bypasses application-level protections. This is a classroom/local desktop architecture. Cookies and CSRF do not apply. SMTP delivery, recipient accounts, automated migrations, web/mobile UI, and the original web app's BCrypt implementation are not included in this independent module. Notifications are persisted in-app for agents on assignment and status updates. Schema initialization creates missing tables; it does not upgrade existing table definitions.

## Run integration checks

```bash
mvn test-compile dependency:copy-dependencies -DincludeScope=test
java -cp 'target/classes:target/test-classes:target/dependency/*' courier.StoreSmokeTest
```

On Windows use `;` instead of `:` in the classpath. Tests default to an isolated in-memory H2 database in MySQL compatibility mode. To test against real MySQL, create a **disposable empty** database and set `TEST_DB_URL`, `TEST_DB_USERNAME`, and `TEST_DB_PASSWORD` before running the test. Do not point tests at real parcel data. The CI workflow runs both H2 and MySQL checks. `mvn test` alone compiles this executable suite; use the explicit command above to execute it.
