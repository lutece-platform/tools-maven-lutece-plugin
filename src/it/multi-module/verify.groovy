// One shared webapp is assembled at the reactor root : every module explodes into it.
File shared = new File( basedir, 'target/lutece' )
assert shared.isDirectory() : "no shared webapp in ${shared}"

// Both modules contributed their own descriptor : the merge really happened.
assert new File( shared, 'WEB-INF/plugins/modA.xml' ).isFile() : "modA did not contribute its descriptor"
assert new File( shared, 'WEB-INF/plugins/modB.xml' ).isFile() : "modB did not contribute its descriptor"

// plugins.dat is generated once the last module has exploded, so it lists every plugin the
// shared webapp holds. Generating it from the root POM, which Maven builds first, used to
// produce a file declaring no plugin at all.
File pluginsDat = new File( shared, 'WEB-INF/plugins/plugins.dat' )
assert pluginsDat.isFile() : "plugins.dat was not generated"
String dat = pluginsDat.text
assert dat.contains( 'modA.installed=1' ) : "modA missing from plugins.dat :\n${dat}"
assert dat.contains( 'modB.installed=1' ) : "modB missing from plugins.dat :\n${dat}"

// modB declares db-pool-required, modA does not : the descriptors are really parsed, not
// just listed by file name.
assert dat.contains( 'modB.pool=portal' ) : "modB's pool was not declared :\n${dat}"
assert !dat.contains( 'modA.pool' ) : "modA needs no pool and must not declare one :\n${dat}"

// Third-party jars of every module are pooled in the shared WEB-INF/lib.
List<String> jars = new File( shared, 'WEB-INF/lib' ).listFiles().collect { it.name }.sort()
assert jars.any { it.startsWith( 'commons-io-' ) } : "modA's own jar is missing : ${jars}"

// build-config is provided : it carries the ant scripts that get unpacked into WEB-INF/sql,
// the jar itself has no business being shipped in the webapp.
assert !jars.any { it.startsWith( 'build-config-' ) } : "a provided jar was shipped : ${jars}"
assert new File( shared, 'WEB-INF/sql/build.xml' ).isFile() : "the ant scripts were not unpacked"


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

// The two modules ask for commons-lang3 in different versions. Exactly one must remain,
// otherwise the webapp ships two copies of the same library on its classpath, and it must be
// the highest of the two.
List<String> lang3 = jars.findAll { it.startsWith( 'commons-lang3-' ) }
String expected = highestOf( [ askedFor( 'modA', 'commons-lang3' ), askedFor( 'modB', 'commons-lang3' ) ] )

assert lang3.size() == 1 : "the version conflict was not arbitrated : ${lang3}"
assert lang3[0] == "commons-lang3-${expected}.jar" :
        "the highest version must win, expected ${expected}, got ${lang3[0]}"

println "multi-module OK : ${jars.size()} pooled jars, commons-lang3 arbitrated to ${lang3[0]}"
return true
