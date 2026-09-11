import java.util.zip.ZipFile

File target = new File( basedir, 'target' )
List<File> wars = target.listFiles().findAll { it.name.endsWith( '.war' ) }
assert wars.size() == 1 : "expected exactly one war in target, found ${wars.collect { it.name }}"

File war = wars[0]

// The project version is a SNAPSHOT : the marker must have been replaced by a timestamp,
// formatted with the default utcTimestampPattern, yyyyMMdd.HHmmss.
assert !war.name.contains( 'SNAPSHOT' ) : "the SNAPSHOT marker was not substituted : ${war.name}"
assert war.name ==~ /site-assembly-1\.0-\d{8}\.\d{6}\.war/ : "unexpected war name : ${war.name}"

// The timestamp is UTC, not the build machine's time zone. Allow a generous window so the
// test does not depend on how long the build took.
def matcher = war.name =~ /(\d{8}\.\d{6})/
Date stamped = new java.text.SimpleDateFormat( 'yyyyMMdd.HHmmss' ).with {
    it.timeZone = TimeZone.getTimeZone( 'UTC' )
    it.parse( matcher[0][1] )
}
long driftMinutes = Math.abs( System.currentTimeMillis() - stamped.time ) / 60000
assert driftMinutes < 60 : "the timestamp is not UTC : ${war.name} is ${driftMinutes} min away from now"

List<String> entries = new ZipFile( war ).withCloseable { zip -> zip.entries().collect { it.name } }

assert entries.any { it == 'WEB-INF/plugins/mysite.xml' } : "the site webapp is missing from the war"
assert entries.any { it == 'WEB-INF/sql/plugins/mysite/plugin/create_db_mysite.sql' }
assert entries.any { it == 'WEB-INF/classes/sql/plugins/mysite/plugin/create_db_mysite.sql' } :
        "the SQL is missing from the Liquibase classpath"

println "site-assembly OK : ${war.name}, ${entries.size()} entries, timestamp is UTC"
return true
