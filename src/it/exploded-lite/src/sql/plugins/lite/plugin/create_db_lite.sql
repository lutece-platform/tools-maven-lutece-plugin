-- liquibase formatted sql
-- changeset lite:create_db_lite.sql
-- preconditions onFail:MARK_RAN onError:WARN
CREATE TABLE lite_item (id_item INT NOT NULL);
