/*
 * Copyright (c) 2002-2015, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.maven;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.TimeZone;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.LogEnabled;
import org.eclipse.aether.SessionData;
import org.codehaus.plexus.logging.Logger;

/**
 * Abstracts functionnality common to all Lutece mojos.
 */
public abstract class AbstractLuteceMojo
    extends AbstractMojo
    implements LogEnabled
{
    /**
     * The name of the lutece plugin artifact type.
     */
    protected static final String LUTECE_PLUGIN_TYPE = "lutece-plugin";

    /**
     * The name of the lutece core artifact type.
     */
    protected static final String LUTECE_CORE_TYPE = "lutece-core";

    /**
     * The name of the lutece site artifact type.
     */
    protected static final String LUTECE_SITE_TYPE = "lutece-site";

    /**
     * The name of the lutece core packaging.
     */
    protected static final String LUTECE_CORE_PACKAGING = "lutece-core";

    /**
     * The name of the lutece plugin packaging.
     */
    protected static final String LUTECE_PLUGIN_PACKAGING = "lutece-plugin";

    /**
     * The name of the lutece site packaging.
     */
    protected static final String LUTECE_SITE_PACKAGING = "lutece-site";

    /**
     * The name of the pom packaging.
     */
    protected static final String POM_PACKAGING = "pom";

    /**
     * The classifier used for the webapp artifact.
     *
     * @see PackageMojo
     */
    protected static final String WEBAPP_CLASSIFIER = "webapp";

    /**
     * The name of the lutece directory.
     */
    protected static final String LUTECE_DIRECTORY = "lutece";

    protected static final String ARTIFACT_BUILD_CONFIG = "build-config";

    /**
     * Artifacts never shipped in WEB-INF/lib : transitive dependencies do not always carry a
     * usable scope, so they are excluded by name as well.
     */
    protected static final String JUNIT = "junit";
    protected static final String SERVLET_API = "servlet-api";

    /**
     * Constants used to create inclusion or exclusion rules, for the zip, jar
     * or war building.
     */
    protected static final String INCLUDE_PATTERN_ALL = "**/**";
    protected static final String INCLUDE_PATTERN_SQL = "**/sql/**";
    protected static final String INCLUDE_PATTERN_SRC = "**/src/**";
    protected static final String INCLUDE_PATTERN_WEBAPP = "**/webapp/**";
    protected static final String INCLUDE_PATTERN_RESOURCES = "**/resources/**";
    protected static final String INCLUDE_PATTERN_SITE_USER = "**/user/**";
    protected static final String EXCLUDE_PATTERN_CLASSES = "**/WEB-INF/classes/**";
    protected static final String EXCLUDE_PATTERN_LIB = "WEB-INF/lib/**";
    protected static final String EXCLUDE_PATTERN_SVN = "**/.svn/**";
    protected static final String EXCLUDE_PATTERN_ASSEMBLY = "**/assembly/**";
    protected static final String EXCLUDE_PATTERN_TARGET = "**/target/**";
    protected static final String EXCLUDE_PATTERN_WEBAPP = "**/webapp/**";
    protected static final String EXCLUDE_PATTERN_JAVA = "**/java/**";
    protected static final String EXCLUDE_PATTERN_SITE = "**/site/**";
    protected static final String EXCLUDE_PATTERN_TEST = "**/test/**";
    protected static final String EXCLUDE_PATTERN_PACKAGE_HTML = "**/package.html";
    protected static final String EXCLUDE_PATTERN_RESOURCES = "**/fr/paris/lutece/**/resources/**";
    protected static final String EXCLUDE_PATTERN_BUSINESS = "**/fr/paris/lutece/**/business/**";
    protected static final String EXCLUDE_PATTERN_WEB = "**/fr/paris/lutece/**/web/**";
    protected static final String EXCLUDE_PATTERN_SERVICE = "**/fr/paris/lutece/**/service/**";
    protected static final String EXCLUDE_PATTERN_UTILS = "**/fr/paris/lutece/**/utils/**";
    protected static final String EXCLUDE_PATTERN_UTIL = "**/fr/paris/lutece/**/util/**";
    protected static final String EXCLUDE_PATTERN_WEBINF_TEST = "**/WEB-INF/test/**";

    // The files to include from the classes directory
    protected static final String[] PACKAGE_CLASSES_INCLUDES = new String[] { INCLUDE_PATTERN_ALL };

    // The classes to exclude from the classes directory
    protected static final String[] PACKAGE_CLASSES_EXCLUDES =
        new String[] { EXCLUDE_PATTERN_PACKAGE_HTML, EXCLUDE_PATTERN_RESOURCES };

    /**
     * The files to include in webapp archives.
     */
    protected static final String[] PACKAGE_WEBAPP_INCLUDES = new String[] { INCLUDE_PATTERN_ALL };
    protected static final String[] PACKAGE_WEBAPP_SITE_INCLUDES = new String[] { INCLUDE_PATTERN_SITE_USER };

    /**
     * The files to exclude from webapp archives.
     */
    protected static final String[] PACKAGE_WEBAPP_EXCLUDES =
        new String[] { EXCLUDE_PATTERN_CLASSES, EXCLUDE_PATTERN_LIB, EXCLUDE_PATTERN_SVN };
    protected static final String[] PACKAGE_WEBAPP_SITE_EXCLUDES = new String[] { EXCLUDE_PATTERN_SVN };

    /**
     * The files to includes from webapp archives.
     */
    protected static final String[] PACKAGE_WEBAPP_RESOURCES_INCLUDE = new String[] { INCLUDE_PATTERN_RESOURCES };

    /**
     * The files to exclude from webapp archives.
     */
    protected static final String[] PACKAGE_WEBAPP_RESOURCES_EXCLUDES =
        new String[]
        {
            EXCLUDE_PATTERN_BUSINESS, EXCLUDE_PATTERN_WEB, EXCLUDE_PATTERN_SERVICE, EXCLUDE_PATTERN_UTILS,
            EXCLUDE_PATTERN_UTIL, EXCLUDE_PATTERN_WEBINF_TEST, EXCLUDE_PATTERN_PACKAGE_HTML, EXCLUDE_PATTERN_SVN
        };

    /**
     * The files to include in zip archives.
     */
    protected static final String[] ASSEMBLY_WEBAPP_INCLUDES = new String[] { INCLUDE_PATTERN_ALL };
    protected static final String[] ASSEMBLY_WEBAPP_SITE_INCLUDES = new String[] { INCLUDE_PATTERN_SITE_USER };

    /**
     * The files to exclude from zip archives.
     */
    protected static final String[] ASSEMBLY_WEBAPP_EXCLUDES =
        new String[] { EXCLUDE_PATTERN_CLASSES, EXCLUDE_PATTERN_LIB, EXCLUDE_PATTERN_SVN };
    protected static final String[] ASSEMBLY_WEBAPP_SITE_EXCLUDES = new String[] { EXCLUDE_PATTERN_SVN };

    /**
     * Constante use for output path directory.
     */

    // The path to the sql directory
    protected static final String WEB_INF_SQL_PATH = "WEB-INF/sql/";

    protected static final String BUILD_CONFIG_PATH = "build-config/";
    protected static final String ANT_PATH = "ant/";

    protected static final String WEB_INF_CLASSES_SQL_PATH = "WEB-INF/classes/sql/";
    protected static final String META_INF_DIRECTORY = "WEB-INF/classes/META-INF/";
    protected static final String WEB_INF_DB_PROPERTIES_PATH = "WEB-INF/conf/db.properties";
    protected static final String WEB_INF_BUILD_PROPERTIES_PATH = "WEB-INF/sql/build.properties";
    protected static final String BUILD_PROPERTIES_FILE = "build.properties";

    /**
     * Timestamp appended to assembly file names. "HH" is the 24-hour clock : with "hh", an
     * assembly built at 13:30 and one built at 01:30 produced the very same file name.
     */
    protected static final String ARCHIVE_TIMESTAMP_PATTERN = "yyMMdd-HHmm";

    /**
     * Time zone the archive timestamps are formatted in. Pinning it keeps the file names
     * readable for the French teams that operate these assemblies, and keeps them stable
     * whatever the time zone of the machine that happens to run the build.
     */
    protected static final String ARCHIVE_TIMESTAMP_TIME_ZONE = "Europe/Paris";
    protected static final String DATABASE_VENDOR_NONE = "none";
    protected static final String DATABASE_VENDOR_AUTO = "auto";
    protected static final Collection<String> DATABASE_VENDORS = Arrays.asList("hsqldb", "mysql", "oracle", "postgresql");



    //the path to the site directory
    protected static final String WEB_INF_DOC_XML_PATH = "doc/xml/";

    /**
     * The maven project.
     *
     */
    @Parameter(
            property = "project",
            readonly = true,
            required = true )
    protected MavenProject project;
    /**
     * The maven session
     */
    @Parameter(defaultValue = "${session}", readonly = true)
	 protected MavenSession session;

    /**
     * The directory containing the Java classes.
     *
     */
    @Parameter(
    		property = "project.build.outputDirectory",
            required = true )
    protected File classesDirectory;

    /**
     * The directory containing the default configuration files.
     *
     */
    @Parameter(
    		property = "defaultConfDirectory",
    	    defaultValue = "${basedir}/src/conf/default"
             )
    protected File defaultConfDirectory;

    /**
     * The source directory for webapp components.
     */
    @Parameter(
    		property = "webappSourceDirectory",
    	    defaultValue="${basedir}/webapp",
            required = true )
    protected File webappSourceDirectory;

    /**
     * The directory containing the database sql script.
     *
     */
    @Parameter(
            property = "sqlDirectory",
            defaultValue = "${basedir}/src/sql"
    		)
    protected File sqlDirectory;

    /**
     * The directory containing the default user documentation.
     *
     */
    @Parameter(
    		property = "siteDirectory",
    		defaultValue = "${basedir}/src/site"
    		)
    protected File siteDirectory;

    /**
     * The project's output directory
     *
     */
    @Parameter(
    		property = "project.build.directory",
            required = true )
    protected File outputDirectory;

    /**
     * The projects in the reactor for aggregation report.
     *
     */
    @Parameter(
    		property = "reactorProjects",
            readonly = true)
    protected List<MavenProject> reactorProjects;

    /**
    * Artifact collector, needed to resolve dependencies.
    *
    * @component
    */
    @Component
    protected ArtifactCollector artifactCollector;

    /**
     * Keys under which the multi-project artifact sets are shared between the modules of a
     * reactor build.
     */
    private static final String MULTI_PROJECT_ARTIFACTS_KEY =
        AbstractLuteceMojo.class.getName(  ) + ".multiProjectArtifacts";
    private static final String MULTI_PROJECT_ARTIFACTS_COPIED_KEY =
        AbstractLuteceMojo.class.getName(  ) + ".multiProjectArtifactsCopied";

    /** Key of the counter of modules that have exploded into the shared webapp. */
    private static final String EXPLODED_MODULES_KEY =
        AbstractLuteceMojo.class.getName(  ) + ".explodedModules";

    /** Key prefix of the locks guarding the directories the modules share. */
    private static final String SHARED_DIRECTORY_LOCK_KEY =
        AbstractLuteceMojo.class.getName(  ) + ".sharedDirectoryLock:";

    /**
    * Plexus logger needed for debugging manual artifact resolution.
    */
    protected Logger logger;

    /**
     * @see org.codehaus.plexus.logging.LogEnabled#enableLogging(org.codehaus.plexus.logging.Logger)
     */
    @Override
    public void enableLogging( Logger logger )
    {
        this.logger = logger;
    }

    protected void validatePackaging( String... allowedPackagings )
                              throws MojoExecutionException
    {
        String packaging = project.getPackaging(  );
        for ( String allowed : allowedPackagings )
        {
            if ( allowed.equals( packaging ) )
            {
                return;
            }
        }
        throw new MojoExecutionException( "This goal can be invoked only on a " +
                                          String.join( " or ", allowedPackagings ) + " project." );
    }

    /**
     * The set of artifacts required by the multi project, including transitive dependencies.
     * Every module of the reactor contributes to it.
     *
     * @return the shared set, never null
     */
    protected Set<Artifact> getMultiProjectArtifacts(  )
    {
        return getSharedArtifacts( session.getRepositorySession(  ).getData(  ), MULTI_PROJECT_ARTIFACTS_KEY );
    }

    /**
     * The multi project artifacts already copied to the shared WEB-INF/lib.
     *
     * @return the shared set, never null
     */
    protected Set<Artifact> getMultiProjectArtifactsCopied(  )
    {
        return getSharedArtifacts( session.getRepositorySession(  ).getData(  ), MULTI_PROJECT_ARTIFACTS_COPIED_KEY );
    }

    /**
     * Returns a set shared by every module of the current build.
     *
     * These sets used to be static fields. The session data lives exactly as long as one build,
     * so the state no longer leaks from one build to the next in a reused JVM (daemon, IDE), and
     * a synchronized set makes it safe for the modules a parallel build (-T) runs concurrently.
     *
     * @param data
     *            the current session data
     * @param strKey
     *            the key the set is stored under
     * @return the shared set, created on first access
     */
    @SuppressWarnings( "unchecked" )
    static Set<Artifact> getSharedArtifacts( SessionData data, String strKey )
    {
        return (Set<Artifact>) data.computeIfAbsent( strKey,
                (  ) -> Collections.synchronizedSet( new LinkedHashSet<Artifact>(  ) ) );
    }

    /**
     * Keeps a single version of each artifact : the highest one.
     *
     * Every module of a reactor resolves its own dependencies first, honouring the
     * dependencyManagement it inherits, so the versions reaching this point already are the
     * ones the poms impose. What is left to arbitrate is a genuine divergence between two
     * modules, and the shared webapp can hold only one jar per library. Picking the highest
     * version is a rule; relying on the order the modules happen to run in is not one, and it
     * made a parallel build ship a different jar from one run to the next.
     *
     * @param artifactsToReduce
     *            the artifacts gathered from every module
     * @return one artifact per groupId:artifactId, in a stable order
     */
    static Set<Artifact> keepHighestVersions( Collection<Artifact> artifactsToReduce )
    {
        Map<String, Artifact> highestByKey = new TreeMap<>(  );

        for ( Artifact artifact : artifactsToReduce )
        {
            String strKey = artifact.getGroupId(  ) + ":" + artifact.getArtifactId(  );
            Artifact highest = highestByKey.get( strKey );

            if ( ( highest == null ) || ( compareVersions( artifact, highest ) > 0 ) )
            {
                highestByKey.put( strKey, artifact );
            }
        }

        return new LinkedHashSet<>( highestByKey.values(  ) );
    }

    /**
     * Compares two artifacts by version.
     *
     * Uses the plain version string rather than getSelectedVersion(), which needs a version
     * range to be set and throws when it is not : excluded artifacts reach us without one.
     *
     * @param left
     *            the first artifact
     * @param right
     *            the second artifact
     * @return a negative value, zero or a positive value as left is older, equal or newer
     */
    static int compareVersions( Artifact left, Artifact right )
    {
        return new DefaultArtifactVersion( left.getVersion(  ) )
                .compareTo( new DefaultArtifactVersion( right.getVersion(  ) ) );
    }

    /**
     * Records that a module has finished exploding, and returns how many have.
     *
     * @return the number of modules done so far, this one included
     */
    protected int countExplodedModules(  )
    {
        return ( (AtomicInteger) session.getRepositorySession(  ).getData(  )
                .computeIfAbsent( EXPLODED_MODULES_KEY, AtomicInteger::new ) ).incrementAndGet(  );
    }

    /**
     * Returns the lock guarding a directory several modules of the build write into.
     *
     * A reactor assembles one shared webapp, so with -T the modules explode into the very same
     * tree at the same time and corrupt each other. The lock lives in the session data, like
     * the multi-project artifact sets, so it is the same object for every module of one build
     * and disappears with it.
     *
     * @param sharedDirectory
     *            the directory the modules share
     * @return the lock to synchronize on
     */
    protected Object getSharedDirectoryLock( File sharedDirectory )
    {
        return session.getRepositorySession(  ).getData(  )
                .computeIfAbsent( SHARED_DIRECTORY_LOCK_KEY + sharedDirectory.getAbsolutePath(  ), Object::new );
    }

    /**
     * Formats a timestamp in the given time zone, rather than in the one of the machine
     * running the build.
     *
     * @param strPattern
     *            the date pattern
     * @param date
     *            the date to format
     * @param strTimeZoneId
     *            the time zone the timestamp reads in
     * @return the formatted timestamp
     */
    static String formatTimestamp( String strPattern, Date date, String strTimeZoneId )
    {
        DateFormat formatter = new SimpleDateFormat( strPattern );
        formatter.setTimeZone( TimeZone.getTimeZone( strTimeZoneId ) );

        return formatter.format( date );
    }

    /**
     * The version of this plugin, so that the banner does not claim a hardcoded one.
     */
    @Parameter(
            defaultValue = "${plugin.version}",
            readonly = true )
    protected String pluginVersion;

    public void logBanner() {
        getLog().info(" __        __    __   ________  ________  ________  ________");
        getLog().info("   |         |     |          |         |         |         |");
        getLog().info("   |         |     |       |      |__       |         |__    ");
        getLog().info("   |         |     |       |         |      |            |   ");
        getLog().info("   |____     |___  |       |      |_____    |_____    |_____ ");
        getLog().info("        |          |       |            |         |         |");
        getLog().info("              LUTECE Maven Plugin - Version : " + pluginVersion );
    }
}
