// Built with -T 4 through .mvn/maven.config : every module explodes into target/lutece at
// the same time. Without serialization they unpack build-config over each other; without a
// rule for version conflicts the result changes from one run to the next.
File shared = new File( basedir, 'target/lutece' )
assert shared.isDirectory() : "no shared webapp in ${shared}"

String log = new File( basedir, 'build.log' ).text
assert log.contains( 'with a thread count' ) : "the build did not actually run in parallel"

// Both modules contributed, and plugins.dat was written after the last one finished, not
// after the last one declared in the reactor.
assert new File( shared, 'WEB-INF/plugins/modA.xml' ).isFile()
assert new File( shared, 'WEB-INF/plugins/modB.xml' ).isFile()
String dat = new File( shared, 'WEB-INF/plugins/plugins.dat' ).text
assert dat.contains( 'modA.installed=1' ) : "plugins.dat was written too early :\n${dat}"
assert dat.contains( 'modB.installed=1' ) : "plugins.dat was written too early :\n${dat}"

// commons-lang3 is requested in 3.14.0 by modA and 3.17.0 by modB : one jar, the highest.
List<String> jars = new File( shared, 'WEB-INF/lib' ).listFiles().collect { it.name }.sort()
List<String> lang3 = jars.findAll { it.startsWith( 'commons-lang3-' ) }
assert lang3.size() == 1 : "the version conflict was not arbitrated : ${lang3}"
assert lang3[0] == 'commons-lang3-3.17.0.jar' : "the highest version must win, got ${lang3[0]}"

assert jars.any { it.startsWith( 'commons-io-' ) } : "modA's jar is missing : ${jars}"
assert !jars.any { it.startsWith( 'build-config-' ) } : "a provided jar was shipped : ${jars}"

println "multi-module-parallel OK : ${jars.size()} jars, ${lang3[0]}, plugins.dat complete"
return true
