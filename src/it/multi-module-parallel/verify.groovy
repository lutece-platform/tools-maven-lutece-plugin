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


/** The version each module asks for, read from its pom so a dependency bump cannot make
 *  this test assert a stale number. */
def askedFor = { String module, String artifact ->
    def matcher = new File( basedir, "${module}/pom.xml" ).text =~
            /${artifact}<\/artifactId>\s*<version>([^<]+)</
    assert matcher.find() : "${artifact} is not declared by ${module}"
    matcher[0][1]
}
def highestOf = { List<String> versions ->
    versions.sort( false ) { String a, String b ->
        List<Integer> left = a.tokenize( '.' ).collect { it as int }
        List<Integer> right = b.tokenize( '.' ).collect { it as int }
        int size = Math.max( left.size(), right.size() )
        for ( int i = 0; i < size; i++ ) {
            int x = i < left.size() ? left[i] : 0
            int y = i < right.size() ? right[i] : 0
            if ( x != y ) { return x <=> y }
        }
        return 0
    }.last()
}

// The two modules ask for commons-lang3 in different versions : one jar must remain, and it
// must be the highest of the two, whichever they happen to be.
List<String> jars = new File( shared, 'WEB-INF/lib' ).listFiles().collect { it.name }.sort()
List<String> lang3 = jars.findAll { it.startsWith( 'commons-lang3-' ) }
String expected = highestOf( [ askedFor( 'modA', 'commons-lang3' ), askedFor( 'modB', 'commons-lang3' ) ] )

assert lang3.size() == 1 : "the version conflict was not arbitrated : ${lang3}"
assert lang3[0] == "commons-lang3-${expected}.jar" :
        "the highest version must win, expected ${expected}, got ${lang3[0]}"

assert jars.any { it.startsWith( 'commons-io-' ) } : "modA's jar is missing : ${jars}"
assert !jars.any { it.startsWith( 'build-config-' ) } : "a provided jar was shipped : ${jars}"

println "multi-module-parallel OK : ${jars.size()} jars, ${lang3[0]}, plugins.dat complete"
return true
