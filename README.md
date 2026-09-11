# lutece-maven-plugin

The Maven plugin behind the `lutece-core`, `lutece-plugin` and `lutece-site` packagings. It
assembles a runnable Lutece webapp out of a set of Lutece artifacts, and packages a project
into the artifacts the others consume.

Projects never declare it: they inherit it from `lutece-global-pom`, which registers it as a
build extension and pins its version through `${maven-lutece-plugin.version}`.

## Goals

| Goal | Packagings | What it does |
|---|---|---|
| `package` | any | The two artifacts a Lutece project publishes: a jar of classes, and a `-webapp.zip` attachment holding its webapp, SQL and default configuration. Bound to the `package` phase by the packagings. |
| `exploded` | core, plugin, site, pom | Explodes a test webapp into `target/lutece`. In a reactor, every module explodes into the root project's one. |
| `exploded-webapp` | core, plugin, site | Same, into `target/${finalName}`, without the reactor handling. |
| `exploded-lite` | core, plugin, site, pom | The webapp structure without the jars and without the compiled classes. |
| `war` | core, plugin, site, pom | Explodes, then archives the result into a war. |
| `site-assembly` | site | The war of a site, with the SNAPSHOT marker replaced by a timestamp. |
| `assembly` | core, plugin | The `-bin` and `-src` zips of a release. |
| `updater` | core, plugin | The upgrade packages, one per SQL upgrade script found. |
| `inplace` | core | Explodes into the source `webapp` directory itself. |
| `liquibase-sql` | any | Prepends the Liquibase changeset headers to the SQL sources, in place. |
| `clean` | any | Removes the build directories. In a reactor it also removes the root project's `target`, which holds the shared webapp. |

## What ends up in the webapp

`WEB-INF/lib` receives the third-party jars of the project — plain jars in scope `compile` or
`runtime`. The jars of Lutece dependencies come from their own `-webapp.zip`, unpacked
separately, so they are not copied twice.

The same code runs whether the project stands alone or belongs to a reactor: the artifacts
Maven resolved are walked and deployed, and nothing is resolved a second time. A reactor
differs only in that every module writes into the same `WEB-INF/lib`. A library two modules
ask for in different versions is therefore arbitrated, and **the highest version wins** —
within a module, `dependencyManagement` has already decided, as usual.

Note this is not Maven's own rule for the compile classpath, which is *nearest definition
wins*. The two can disagree when modules of a reactor diverge.

### Parallel builds

`mvn -T` works. The modules of a reactor all write into one shared tree, so that part of the
work is serialized on a lock; the rest of the build stays parallel. A parallel build produces
the same tree as a sequential one, and `src/it/multi-module-parallel` checks exactly that.

## SQL and Liquibase

SQL files travel in `WEB-INF/sql`, and those Liquibase runs are also copied to
`WEB-INF/classes/sql`, where it looks for them on the classpath. Which ones are copied is
decided by `SqlPathInfo` (from `library-sql-utils`), on the file path:

```
sql/plugins/<plugin>[/modules/<module>]/(core|plugin)/(init|create)*.sql
sql/plugins/<plugin>[/modules/<module>]/upgrade(s)/(update|upgrade)*-<from>-<to>.sql
```

A file that matches nothing is not copied — the runtime changelog filter discards it too, so
this is not a packaging loss. It *is* reported, as a warning naming the file, when it sits
under `upgrade/`, where a versioned script is expected and a name like `7.1.x` is a mistake.

`WEB-INF/classes/META-INF/microprofile-config.properties` is generated alongside, telling the
runtime whether Liquibase may run: it is set to false when a file **is** managed by Liquibase
but carries no changeset header.

`-DtargetDatabaseVendor=mysql|postgresql|oracle|hsqldb|auto` rewrites the SQL for one vendor
on the way. Without it the files are copied byte for byte.

## The build-config dependency

`copyBuildConfig` unpacks `build-config/ant/` into `WEB-INF/sql`: the ant scripts that create
and upgrade the database. The goal fails without that dependency, on purpose — the webapp
would ship without its scripts, and that would only show at deployment.

It is never declared by a project: `lutece-global-pom` declares it, in scope `provided` so
that only its content is unpacked and the jar itself stays out of `WEB-INF/lib`. The
dependency is also what carries the version, which lets build-config evolve without a release
of this plugin.

## Parameters other tools rely on

`liberty-maven-plugin` reads four of them by name, and hardcodes two of their defaults, to map
the webapp live in dev mode. **Renaming them, or changing those defaults, breaks dev mode.**

| Parameter | Default |
|---|---|
| `webappSourceDirectory` | `${basedir}/webapp` |
| `webappDirectory` | `${project.build.directory}/${project.build.finalName}` |
| `localConfDirectory` | `${user.home}/lutece/conf/${project.artifactId}` |
| `defaultConfDirectory` | `${basedir}/src/conf/default` |

It also watches the configuration of the `exploded` goal, and redeploys when it changes.

## Building and testing

```sh
mvn verify              # unit tests
mvn verify -Prun-its    # plus the integration tests, needs network access
```

The integration tests build real projects with the freshly installed plugin and check what
comes out — the reactor, the parallel build, and each packaging goal. See
[src/it/README.md](src/it/README.md).

## Known limitation

The plugin still uses `ArtifactResolver`, `ArtifactMetadataSource` and
`MavenProject.getDependencyArtifacts()`, from `maven-compat`. Maven 4 drops them, so it will
not build there as it stands. The two places left are `getDependentJars`, which needs the
transitive closure of the non-Lutece dependencies only, and the webapp attachment lookup in
`addToExplodedWebapp`. Both are covered by integration tests — `assembly` pins down which jars
a bin zip ships, which is the trap: reading `project.getArtifacts()` instead took a real
plugin from 10 shipped jars to 50.
