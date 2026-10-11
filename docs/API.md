# API reference

Base path `/api`. Request and response bodies are JSON. Management routes require an authenticated session. All POST/PATCH requests (including login and logout) require the CSRF header returned by `/auth/csrf`. Preserve session cookies.

| Method | Endpoint                 | Access               | Purpose                                       |
| ------ | ------------------------ | -------------------- | --------------------------------------------- |
| GET    | /health                  | Public               | API liveness                                  |
| GET    | /auth/csrf               | Public               | Returns token and headerName                  |
| POST   | /auth/login              | Public + CSRF        | email and password; creates session           |
| GET    | /auth/me                 | Signed in            | Safe current user profile                     |
| POST   | /auth/logout             | Signed in + CSRF     | Invalidates session; 204                      |
| GET    | /tracking/{trackingId}   | Public               | Shipment cities, state, ETA, safe history     |
| GET    | /dashboard               | Admin/agent          | Counts and recent visible parcels             |
| GET    | /parcels                 | Admin/agent          | search, status, page (0-based), size (1–100)  |
| POST   | /parcels                 | Admin                | Register parcel; 201                          |
| GET    | /parcels/{id}            | Admin/assigned agent | Parcel details and full history               |
| PATCH  | /parcels/{id}/assignment | Admin                | agentId                                       |
| PATCH  | /parcels/{id}/status     | Admin/assigned agent | status, location, note                        |
| GET    | /agents                  | Admin                | Active delivery agents                        |
| POST   | /agents                  | Admin                | name, email, password (12–72 characters); 201 |
| GET    | /notifications           | Signed in            | Own latest 100 notifications                  |
| PATCH  | /notifications/{id}/read | Owner                | Marks read; 204                               |

## Parcel registration body

```json
{
  "senderName": "Sender Name",
  "senderEmail": "sender@example.com",
  "senderPhone": "+91 9000000000",
  "senderAddress": "1 Market Street",
  "senderCity": "Bengaluru",
  "recipientName": "Recipient Name",
  "recipientEmail": "recipient@example.com",
  "recipientPhone": "+91 9111111111",
  "recipientAddress": "2 Lake Street",
  "recipientCity": "Mumbai",
  "description": "Books",
  "weightKg": 1.5,
  "serviceType": "STANDARD",
  "expectedDelivery": "2030-01-15"
}
```

All fields required. Weight must be 0.01–1000.00 kg, at most two decimal places. Service is STANDARD or EXPRESS. Expected delivery cannot be in the past (UTC date). The server generates tracking ID, status, creator, and timestamps.

Public tracking omits full contacts and internal notes. Parcel detail includes `parcel.nextStatuses`, which lists allowed next steps. An assigned agent is required before progressing a delivery. Only an admin can cancel a registered parcel.

Errors use `message`; validation errors also return a `fields` object. HTTP statuses: 400 invalid input, 401 unauthenticated/invalid login, 403 role/CSRF failure, 404 unavailable or inaccessible parcel, 409 conflict/invalid transition.

Notifications include `emailStatus`: NOT_CONFIGURED, IN_APP_ONLY, PENDING, SENT, FAILED. Read state is independent of email delivery.
