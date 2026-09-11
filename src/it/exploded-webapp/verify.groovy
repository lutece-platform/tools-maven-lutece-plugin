File webapp = new File( basedir, 'target/exploded-webapp-1.0' )
assert webapp.isDirectory() : "the webapp was not exploded in ${webapp}"

// WEB-INF/sql holds every SQL file of the plugin ...
File sqlDir = new File( webapp, 'WEB-INF/sql/plugins/single' )
assert new File( sqlDir, 'plugin/create_db_single.sql' ).isFile()
assert new File( sqlDir, 'upgrade/update_db_single-1.0.x-2.0.0.sql' ).isFile()

// ... but only those Liquibase can name are copied to the classpath it reads at run time.
File classesSql = new File( webapp, 'WEB-INF/classes/sql/plugins/single' )
assert new File( classesSql, 'plugin/create_db_single.sql' ).isFile() : "the create script must be shipped"
assert !new File( classesSql, 'upgrade/update_db_single-1.0.x-2.0.0.sql' ).exists() :
        "a script named after no convention must not be shipped"
assert !new File( classesSql, 'plugin/data_single.sql' ).exists() :
        "a file outside Liquibase's naming scheme must not be shipped"

String log = new File( basedir, 'build.log' ).text

// The misnamed upgrade script must be named in a warning : it sits where a versioned script
// is expected, so it is a naming fault, not a deliberate exclusion.
assert log.contains( 'update_db_single-1.0.x-2.0.0.sql' ) :
        "the misnamed upgrade script was skipped silently"
assert log.contains( 'do not follow the Lutece SQL naming convention' ) :
        "the warning is missing from the build log"

// data_single.sql is outside upgrade/ : excluding it is normal, it must not be reported.
assert !log.contains( 'data_single.sql' ) : "a legitimate exclusion was reported as a fault"

// Those exclusions must not flip the Liquibase readiness flag.
File mpConfig = new File( webapp, 'WEB-INF/classes/META-INF/microprofile-config.properties' )
assert mpConfig.isFile() : "microprofile-config.properties was not generated"
assert mpConfig.text.contains( 'liquibase.readyToRun=true' ) :
        "readyToRun must stay true :\n${mpConfig.text}"

println "exploded-webapp OK : create script shipped, misnamed upgrade reported, readyToRun untouched"
return true
