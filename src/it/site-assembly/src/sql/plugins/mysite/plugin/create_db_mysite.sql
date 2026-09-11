-- liquibase formatted sql
-- changeset mysite:create_db_mysite.sql
-- preconditions onFail:MARK_RAN onError:WARN
CREATE TABLE site_item (id_item INT NOT NULL);
