-- liquibase formatted sql
-- changeset single:create_db_single.sql
-- preconditions onFail:MARK_RAN onError:WARN
CREATE TABLE single_item (id_item INT NOT NULL);
