# Database design

Flyway migration: `backend/src/main/resources/db/migration/V1__initial_schema.sql`.

```mermaid
erDiagram
    APP_USERS ||--o{ PARCELS : creates
    APP_USERS o|--o{ PARCELS : delivers
    PARCELS ||--o{ PARCEL_EVENTS : records
    APP_USERS ||--o{ NOTIFICATIONS : receives
    PARCELS ||--o{ NOTIFICATIONS : triggers
    APP_USERS {
      bigint id PK
      string email UK
      string password_hash
      string role
      boolean active
    }
    PARCELS {
      bigint id PK
      string tracking_id UK
      bigint created_by_id FK
      bigint agent_id FK
      string status
      decimal weight_kg
      date expected_delivery
      bigint version
    }
    PARCEL_EVENTS {
      bigint id PK
      bigint parcel_id FK
      string status
      string location
      string actor_name
      timestamp occurred_at
    }
    NOTIFICATIONS {
      bigint id PK
      bigint user_id FK
      bigint parcel_id FK
      string email_status
      int attempts
      boolean is_read
    }
```

Assignments/status transitions lock the parcel row and commit parcel changes, history, and notifications together. An optimistic version guards against stale entity writes. Events are append-only through the API; there are no edit/delete endpoints for events. The role/status fields use Java enums persisted as strings. The same migration supports MySQL 8.4 and H2 in MySQL compatibility mode.

Foreign keys preserve parent relationships. Unique indexes protect tracking IDs and user emails. Additional indexes support status/agent filtering, ordered parcel events, and each user's notification feed. Internal timestamps are stored and interpreted as UTC. Tracking IDs use random UUIDs (demo IDs are deliberately recognizable).
