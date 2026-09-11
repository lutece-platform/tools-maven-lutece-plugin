import java.util.zip.ZipFile

// The goal writes under target/<artifactId>/
File outputDir = new File( basedir, 'target/assembly' )
assert outputDir.isDirectory() : "nothing was assembled in ${outputDir}"

List<File> zips = outputDir.listFiles().findAll { it.name.endsWith( '.zip' ) }
File binZip = zips.find { it.name.contains( '-bin-' ) }
File srcZip = zips.find { it.name.contains( '-src-' ) }
assert binZip != null : "no bin zip among ${zips.collect { it.name }}"
assert srcZip != null : "no src zip among ${zips.collect { it.name }}"

List<String> binEntries = new ZipFile( binZip ).withCloseable { it.entries().collect { e -> e.name } }

// The plugin's own jar and its webapp.
assert binEntries.any { it ==~ /WEB-INF\/lib\/assembly-1\.0\.jar/ } : "the plugin jar is missing : ${binEntries}"
assert binEntries.contains( 'WEB-INF/plugins/asm.xml' ) : "the webapp is missing from the bin zip"
assert binEntries.contains( 'WEB-INF/sql/plugins/asm/plugin/create_db_asm.sql' ) : "the SQL is missing"

// Dependency resolution : the direct one and both transitive levels must be shipped.
List<String> jars = binEntries.findAll { it.startsWith( 'WEB-INF/lib/' ) && it.endsWith( '.jar' ) }
                              .collect { it.substring( 'WEB-INF/lib/'.length() ) }
[ 'commons-configuration2', 'commons-text', 'commons-lang3' ].each { name ->
    assert jars.any { it.startsWith( name + '-' ) } : "${name} is missing from the bin zip : ${jars}"
}

// Provided and test artifacts must stay out, junit by name too.
assert !jars.any { it.startsWith( 'build-config-' ) } : "a provided jar was shipped : ${jars}"
assert !jars.any { it.startsWith( 'junit-' ) } : "junit was shipped : ${jars}"
assert !jars.any { it.startsWith( 'hamcrest' ) } : "a test-only jar was shipped : ${jars}"

// A Lutece dependency is deployed on its own, and so is everything it drags in. Shipping the
// core's own libraries inside a plugin zip would put two copies of each on the classpath.
assert !jars.any { it.startsWith( 'plugin-liquibase-' ) } : "a lutece-plugin jar was shipped : ${jars}"
assert !jars.any { it.startsWith( 'lutece-core-' ) } : "the core was shipped : ${jars}"
[ 'liquibase-core', 'library-sql-utils', 'liquibase-slf4j' ].each { name ->
    assert !jars.any { it.startsWith( name + '-' ) } :
            "${name} comes from a lutece dependency and must not be shipped : ${jars}"
}

// The src zip carries the sources, not the build output.
List<String> srcEntries = new ZipFile( srcZip ).withCloseable { it.entries().collect { e -> e.name } }
assert srcEntries.any { it.endsWith( 'Sample.java' ) } : "the java sources are missing from the src zip"

println "assembly OK : ${jars.size()} jars shipped -> ${jars.sort()}"
return true
