# Courier Tracker — JFLAP + JDBC + MySQL

An independent Java 17 console rebuild in this repository. JFLAP models the delivery automaton; Java loads that exact `.jff` file to enforce status changes; JDBC prepared statements persist data in MySQL. Run commands from `jdbc-jflap/`. The existing web application remains available separately.

## Start

Install JDK 17+, Maven 3.6.3+, MySQL 8.4 (or Docker), and JFLAP to view/simulate the model.

```bash
cd jdbc-jflap
export DB_PASSWORD='choose-a-unique-database-password'
export MYSQL_ROOT_PASSWORD='choose-a-different-root-password'
docker compose up -d --wait
export DB_USER=courier
export DB_URL='jdbc:mysql://localhost:3306/courier_jflap?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&sslMode=DISABLED'
mvn compile exec:java -Dexec.args='--init'
mvn exec:java -Dexec.args='--bootstrap'
mvn exec:java
```

The URL above is for local development. For a remote server configure verified TLS. Without Docker, create a separate `courier_jflap` database and grant a dedicated account access to it, then set DB_URL, DB_USER and DB_PASSWORD. Do not point this application at the Spring Boot database: the schemas and password formats differ. `--init` creates tables; `--bootstrap` prompts for the first administrator and refuses once a user exists. Passwords need at least 12 characters; no default login is shipped.

For hidden password entry use a real console after building:

```bash
mvn package dependency:copy-dependencies
java -cp 'target/classes:target/dependency/*' com.courier.jdbc.Main
```

On Windows use `;` instead of `:` in the classpath and set environment variables with your shell's syntax. Maven or IDE consoles may echo password input; the application tells you when this occurs.

## Demonstration

1. Sign in as the bootstrapped admin. Add two agents and list their numeric IDs.
2. Register a parcel with sender/recipient names, addresses and cities, positive weight, service and expected date. Copy its generated tracking ID.
3. Assign an agent. Updates before assignment are rejected except admin cancellation.
4. Log out and sign in as that agent. List your parcels and use the update menu to enter `p`, `t`, `o`, then `d`.
5. Public tracking displays cities, service, expected date and status history, with no names, addresses, notes or actor details.
6. The admin sees all parcels; each agent sees and changes only their assigned parcels. Search and status filters are paginated at 20 rows. The dashboard groups counts by status. The inbox supports marking notifications read.

## JFLAP model and meaning

Open `models/parcel-workflow.jff` in JFLAP. Select **Input → Multiple Run**, and enter the sample strings below. Each input character represents a delivery event, not an entire status name.

| Symbol | Event / target status |
|---|---|
| p | Picked up |
| t | In transit |
| o | Out for delivery (including retry) |
| d | Delivered |
| f | Delivery failed |
| r | Returned |
| c | Cancelled |

Initial state: REGISTERED. Final states: DELIVERED, RETURNED, CANCELLED. A final state means a completed lifecycle, including cancellation or return; it does not always mean successful delivery. Valid unfinished journeys are rejected as *complete strings* by JFLAP, while Java allows them as intermediate progress.

| Input | Result | Explanation |
|---|---|---|
| ptod | Accept | Delivered normally |
| ptofod | Accept | Failed delivery, retry, delivered |
| c | Accept | Cancelled before pickup |
| pr | Accept | Returned after pickup |
| ptofr | Accept | Returned after failed delivery |
| pto | Reject | Valid prefix, still out for delivery |
| d | Reject | Pickup/transit skipped |
| ptodd | Reject | Event after terminal state |
| pc | Reject | Cancellation after pickup |

Assignment and access permissions are application rules, not symbols in this DFA. Assignment appends an event with the current status without consuming a DFA character. The DFA is deterministic and partial: missing transitions reject. JFLAP simulation was not run in this environment; open the file to inspect it visually.

## Implementation

| File | Responsibility |
|---|---|
| Main.java | Console navigation, login session, input and password cleanup |
| Store.java | JDBC persistence, permissions, row locking and transactions |
| Workflow.java | Parses the actual JFLAP model, rejects nondeterminism and validates events |
| Passwords.java | Salted PBKDF2-HMAC-SHA256 password hashing and constant-time comparison |
| schema.sql | Users, parcels, immutable application history and notifications with foreign keys |
| parcel-workflow.jff | Editable JFLAP finite automaton used at runtime |
| Checks.java | Model, password and isolated database scenario checks |

Parcel registration, assignment and updates use transactions. Updates lock the parcel with `SELECT ... FOR UPDATE`, then validate its current state, agent ownership and event before committing status, history and notification together. Errors roll back the transaction. Prepared statements bind user input. The schema uses InnoDB, unique tracking/email keys and indexes. XML external entities and DOCTYPE are disabled.

This is a console project, not a replacement React UI. Its dashboard provides status counts; SMTP/email delivery, phone/email contact fields, rich web dialogs and overdue metrics from the original are not implemented. Database credentials are trusted application credentials; direct SQL access can bypass application permissions. History has no edit/delete operation in the application.

## Verification

```bash
mvn test-compile dependency:copy-dependencies
java -cp 'target/test-classes:target/classes:target/dependency/*' com.courier.jdbc.Checks
# Destructive only to the test database: use an EMPTY dedicated test schema.
java -cp 'target/test-classes:target/classes:target/dependency/*' com.courier.jdbc.Checks --database
```

The database check needs an empty schema and tests bootstrap, login, agent isolation, assignment requirements, admin-only cancellation, failure/retry, terminal-state protection, tracking and inbox queries. A second run needs a new empty test database because first-admin bootstrap is intentionally single-use. GitHub Actions supplies a fresh MySQL 8.4 service and runs these checks. Local execution requires the tools listed above.
