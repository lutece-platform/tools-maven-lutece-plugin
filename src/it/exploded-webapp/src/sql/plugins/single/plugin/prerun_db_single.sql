-- liquibase formatted sql
-- changeset single:prerun_db_single.sql
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM core_datastore WHERE entity_key = 'core.plugins.status.formersingle.installed'
UPDATE core_datastore SET entity_key = REPLACE(entity_key, 'core.plugins.status.formersingle.', 'core.plugins.status.single.') WHERE entity_key LIKE 'core.plugins.status.formersingle.%';
