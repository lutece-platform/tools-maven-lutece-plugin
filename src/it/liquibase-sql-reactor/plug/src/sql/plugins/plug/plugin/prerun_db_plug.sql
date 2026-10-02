-- liquibase formatted sql
-- changeset plug:prerun_db_plug.sql
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM core_datastore WHERE entity_key = 'core.plugins.status.formerplug.installed'
UPDATE core_datastore SET entity_key = REPLACE(entity_key, 'core.plugins.status.formerplug.', 'core.plugins.status.plug.') WHERE entity_key LIKE 'core.plugins.status.formerplug.%';
