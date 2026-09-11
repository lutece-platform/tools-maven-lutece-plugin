import java.util.zip.ZipFile

File war = new File( basedir, 'target/war-1.0.war' )
assert war.isFile() : "the war was not produced in ${war}"

List<String> entries = new ZipFile( war ).withCloseable { zip ->
    zip.entries().collect { it.name }
}

def has = { String path -> entries.any { it == path } }
def hasMatching = { String regex -> entries.any { it ==~ regex } }

// The webapp resources.
assert has( 'css/war.css' ) : "the webapp files are missing from the war"
assert has( 'WEB-INF/plugins/warplug.xml' ) : "the plugin descriptor is missing from the war"

// The default configuration, copied by explodeConfigurationFiles.
assert has( 'WEB-INF/conf/config.properties' ) : "the default configuration is missing from the war"

// SQL, both as sources and on the Liquibase classpath.
assert has( 'WEB-INF/sql/plugins/warplug/plugin/create_db_warplug.sql' )
assert has( 'WEB-INF/classes/sql/plugins/warplug/plugin/create_db_warplug.sql' ) :
        "the SQL is missing from the Liquibase classpath"
assert has( 'WEB-INF/classes/META-INF/microprofile-config.properties' )

// Unlike exploded-lite, the war does ship the third-party jars ...
assert hasMatching( /WEB-INF\/lib\/commons-io-.*\.jar/ ) : "the third-party jar is missing from the war"
// ... but not the provided ones, whose content is unpacked instead.
assert !hasMatching( /WEB-INF\/lib\/build-config-.*\.jar/ ) : "a provided jar was shipped in the war"
assert has( 'WEB-INF/sql/build.xml' ) : "the ant scripts are missing from the war"

// The archive carries a manifest, so it was built through MavenArchiver.
assert has( 'META-INF/MANIFEST.MF' ) : "no manifest in the war"

// NOTE : the goal never calls projectHelper.attachArtifact, so this war is not installed
// nor deployed by the install/deploy phases. Not asserted here, but worth fixing.

println "war OK : ${entries.size()} entries, webapp + conf + sql + jars"
return true
