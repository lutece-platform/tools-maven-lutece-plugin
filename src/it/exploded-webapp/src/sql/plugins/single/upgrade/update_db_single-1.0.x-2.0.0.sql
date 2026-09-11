-- liquibase formatted sql
-- changeset single:update_db_single-1.0.x-2.0.0.sql
-- preconditions onFail:MARK_RAN onError:WARN
ALTER TABLE single_item ADD COLUMN label VARCHAR(50);
