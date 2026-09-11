-- liquibase formatted sql
-- changeset warplug:create_db_warplug.sql
-- preconditions onFail:MARK_RAN onError:WARN
CREATE TABLE war_item (id_item INT NOT NULL);
