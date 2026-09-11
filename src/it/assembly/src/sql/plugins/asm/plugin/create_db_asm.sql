-- liquibase formatted sql
-- changeset asm:create_db_asm.sql
-- preconditions onFail:MARK_RAN onError:WARN
CREATE TABLE asm_item (id_item INT NOT NULL);
