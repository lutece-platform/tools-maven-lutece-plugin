-- liquibase formatted sql
-- changeset single:data_single.sql
-- preconditions onFail:MARK_RAN onError:WARN
INSERT INTO single_item (id_item) VALUES (1);
