CREATE TABLE app_users (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(100) NOT NULL,
 email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(20) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE parcels (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 tracking_id VARCHAR(40) NOT NULL UNIQUE,
 sender_name VARCHAR(100) NOT NULL,
 sender_email VARCHAR(254) NOT NULL,
 sender_phone VARCHAR(20) NOT NULL,
 sender_address VARCHAR(300) NOT NULL,
 sender_city VARCHAR(100) NOT NULL,
 recipient_name VARCHAR(100) NOT NULL,
 recipient_email VARCHAR(254) NOT NULL,
 recipient_phone VARCHAR(20) NOT NULL,
 recipient_address VARCHAR(300) NOT NULL,
 recipient_city VARCHAR(100) NOT NULL,
 description VARCHAR(300) NOT NULL,
 weight_kg DECIMAL(10,2) NOT NULL,
 service_type VARCHAR(20) NOT NULL,
 status VARCHAR(30) NOT NULL,
 expected_delivery DATE NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 created_by_id BIGINT NOT NULL,
 agent_id BIGINT,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_parcel_creator FOREIGN KEY(created_by_id) REFERENCES app_users(id),
 CONSTRAINT fk_parcel_agent FOREIGN KEY(agent_id) REFERENCES app_users(id)
);
CREATE INDEX ix_parcel_status ON parcels(status);
CREATE INDEX ix_parcel_agent ON parcels(agent_id);
CREATE TABLE parcel_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 parcel_id BIGINT NOT NULL,
 status VARCHAR(30) NOT NULL,
 location VARCHAR(100) NOT NULL,
 note VARCHAR(500) NOT NULL,
 actor_name VARCHAR(100) NOT NULL,
 occurred_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_event_parcel FOREIGN KEY(parcel_id) REFERENCES parcels(id)
);
CREATE INDEX ix_event_parcel ON parcel_events(parcel_id, occurred_at);
CREATE TABLE notifications (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 parcel_id BIGINT NOT NULL,
 title VARCHAR(200) NOT NULL,
 message VARCHAR(700) NOT NULL,
 recipient_email VARCHAR(254),
 email_status VARCHAR(30) NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 is_read BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_notification_user FOREIGN KEY(user_id) REFERENCES app_users(id),
 CONSTRAINT fk_notification_parcel FOREIGN KEY(parcel_id) REFERENCES parcels(id)
);
CREATE INDEX ix_notification_user ON notifications(user_id, created_at);
