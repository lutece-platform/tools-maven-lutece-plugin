# Integration tests

Each directory here is a small Maven project built by `maven-invoker-plugin` with the plugin
freshly installed, then checked by its `verify.groovy`. They cover what a unit test cannot
reach: the multi-module reactor, and the goals end to end.

    mvn verify -Prun-its

They are behind the `run-its` profile because they need network access: the projects resolve
`build-config` from the Lutece repository. A plain `mvn verify` does not run them.

| Project | What it pins down |
|---|---|
| `liquibase-sql-reactor` | `liquibase-sql` on a module of a reactor, where the working directory is the root and not the module |
| `multi-module` | one shared webapp per reactor: merged descriptors, pooled jars, arbitrated version conflict |
| `exploded-webapp` | which SQL files reach `WEB-INF/classes/sql`, and the report on a misnamed upgrade script |

`verify.groovy` receives `basedir` (the cloned project) and can read `build.log` to assert on
the build output.
