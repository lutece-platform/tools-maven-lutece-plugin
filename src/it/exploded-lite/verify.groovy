File webapp = new File( basedir, 'target/exploded-lite-1.0' )
assert webapp.isDirectory() : "the webapp was not exploded in ${webapp}"

// The webapp resources are laid out as usual.
assert new File( webapp, 'js/lite.js' ).isFile() : "the webapp files were not copied"
assert new File( webapp, 'WEB-INF/plugins/lite.xml' ).isFile() : "the plugin descriptor was not copied"

// SQL is deployed and duplicated on the classpath for Liquibase, like the full goal does.
assert new File( webapp, 'WEB-INF/sql/plugins/lite/plugin/create_db_lite.sql' ).isFile()
assert new File( webapp, 'WEB-INF/classes/sql/plugins/lite/plugin/create_db_lite.sql' ).isFile() :
        "the SQL was not copied to the Liquibase classpath"

// build-config is unpacked into WEB-INF/sql, as for the other explode goals.
assert new File( webapp, 'WEB-INF/sql/build.xml' ).isFile() : "build-config was not unpacked"

// The point of the "lite" variant : no jar and no compiled class.
File lib = new File( webapp, 'WEB-INF/lib' )
List<String> jars = lib.isDirectory() ? lib.listFiles().collect { it.name } : []
assert jars.isEmpty() : "exploded-lite must not ship any jar, found ${jars}"

File classes = new File( webapp, 'WEB-INF/classes' )
List<String> classFiles = []
if ( classes.isDirectory() )
{
    classes.eachFileRecurse { if ( it.name.endsWith( '.class' ) ) { classFiles << it.name } }
}
assert classFiles.isEmpty() : "exploded-lite must not ship compiled classes, found ${classFiles}"

println "exploded-lite OK : webapp and SQL deployed, no jar, no class"
return true
