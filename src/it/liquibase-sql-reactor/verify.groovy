// The goal rewrites the module's SQL sources in place. It used to resolve ./src/sql against
// the working directory, which in a reactor is the root : it failed on every module.
File sqlRoot = new File( basedir, 'plug/src/sql/plugins/plug' )

File create = new File( sqlRoot, 'plugin/create_db_plug.sql' )
File update = new File( sqlRoot, 'upgrade/update_db_plug-1.0.0-1.0.1.sql' )

assert create.isFile() && update.isFile() : "the SQL sources are gone"

[ create, update ].each { File sql ->
    List<String> lines = sql.readLines()
    assert lines[0] == '-- liquibase formatted sql' : "${sql.name} was not tagged :\n${sql.text}"
    assert lines[1] == "-- changeset plug:${sql.name}" : "wrong changeset line in ${sql.name} : ${lines[1]}"
    assert lines[2].startsWith( '-- preconditions' ) : "missing preconditions in ${sql.name}"
}

// A pre-execution script is written by hand, already tagged : the goal leaves it as is and does not
// report it as misnamed, plugin-liquibase runs it.
File prerun = new File( sqlRoot, 'plugin/prerun_db_plug.sql' )
assert prerun.readLines()[1] == '-- changeset plug:prerun_db_plug.sql' : "the pre-execution script was rewritten :\n${prerun.text}"
assert prerun.text.count( 'liquibase formatted sql' ) == 1 : "the pre-execution script was tagged twice :\n${prerun.text}"
String log = new File( basedir, 'build.log' ).text
assert !log.contains( 'prerun_db_plug.sql does not follow' ) : "the pre-execution script was reported as misnamed"

// Nothing must have been written at the reactor root.
assert !new File( basedir, 'src' ).exists() : "the goal wrote outside the module"

println "liquibase-sql-reactor OK : both SQL files tagged from a reactor build"
return true
