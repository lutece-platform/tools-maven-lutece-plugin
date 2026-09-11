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
assert jars.any { it.startsWith( 'build-config-' ) } : "build-config is missing : ${jars}"

// commons-lang3 was requested in 3.14.0 by modA and 3.17.0 by modB. Exactly one must remain,
// otherwise the webapp ships two versions of the same library on its classpath.
List<String> lang3 = jars.findAll { it.startsWith( 'commons-lang3-' ) }
assert lang3.size() == 1 : "the version conflict was not arbitrated : ${lang3}"

println "multi-module OK : ${jars.size()} pooled jars, commons-lang3 arbitrated to ${lang3[0]}"
return true
