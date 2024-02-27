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
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.TimeZone;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.util.StringUtils;

/**
 * Builds a site's final WAR from a Lutece core artifact and a set of Lutece
 * plugin artifacts.<br/> Note that the Lutece dependencies (core and plugins)
 * will only be updated the first time the WAR is created. Subsequent calls to
 * this goal will only update the site's specific files.<br/> If you wish to
 * force webapp re-creation (for instance, if you changed the version of a
 * dependency), call the <code>clean</code> phase before this goal.
 *
 */

@Mojo( name = "site-assembly" ,
requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
		)
@Execute ( goal = "site-assembly",
		phase=LifecyclePhase.PROCESS_CLASSES )
public class AssemblySiteMojo
    extends AbstractLuteceWebappMojo
{
    private static final TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone( "UTC" );
    private static final String SNAPSHOT_PATTERN = "SNAPSHOT";
    // The path to the copy of the sql directory inside the classpath
    private static final String WEB_INF_CLASSES_SQL_PATH = "WEB-INF/classes/sql/";
    private static final String WEB_INF_DB_PROPERTIES_PATH = "WEB-INF/conf/db.properties";
    private static final String WEB_INF_BUILD_PROPERTIES_PATH = "WEB-INF/sql/build.properties";

    /**
     * The output date format.
     *
     */
    @Parameter(
            defaultValue = "yyyyMMdd.HHmmss", required = true )
    private String utcTimestampPattern;

    /**
     * The name of the generated WAR file.
     *
     */
    @Parameter(
            property = "project.build.finalName", required = true )
    private String finalName;

    /**
     * A temporary directory used to hold the exploded version of the webapp.
     *
     */
    @Parameter(
            defaultValue = "${project.build.directory}/${project.build.finalName}", required = true )
    private File explodedDirectory;

    /**
     * Whether creating the archive should be forced.
     *
     */
    @Parameter(
    		property = "jar.forceCreation",
            defaultValue = "yyyyMMdd.HHmmss" )
    private boolean forceCreation;
    
    /**
     * When used, the name of the database vendor.
     * 
     * Authorized value are:
     * <ul>
     * <li>hsqldb
     * <li>mysql
     * <li>oracle
     * <li>postgresql
     * <li>none : does not process anything (default)
     * <li>auto : will try to determine behaviour from contents of db.properties
     * </ul>
     */
    @Parameter(property = "targetDatabaseVendor", defaultValue = DATABASE_VENDOR_NONE)
    private String targetDatabaseVendor;
    private static final String DATABASE_VENDOR_NONE = "none";
    private static final String DATABASE_VENDOR_AUTO = "auto";
    private static final Collection<String> DATABASE_VENDORS = Arrays.asList("hsqldb", "mysql", "oracle", "postgresql");

    /**
     * The maven archive configuration to use.
     *
     * See <a
     * href="http://maven.apache.org/ref/current/maven-archiver/apidocs/org/apache/maven/archiver/MavenArchiveConfiguration.html">the
     * Javadocs for MavenArchiveConfiguration</a>.
     *
     */
    @Parameter
    private MavenArchiveConfiguration archive = new MavenArchiveConfiguration(  );

    /**
     * The Jar archiver.
     *
     */
    @Component ( role = org.codehaus.plexus.archiver.Archiver.class, hint = "jar" )
    private JarArchiver jarArchiver;

    /**
     * Executes the mojo on the current project.
     *
     * @throws MojoExecutionException
     *             if an error occurred while building the artifact.
     */
    public void execute(  )
                 throws MojoExecutionException, MojoFailureException
    {
        if ( ! LUTECE_SITE_PACKAGING.equals( project.getPackaging(  ) ) )
        {
            throw new MojoExecutionException( "This goal can be invoked only on a " + LUTECE_SITE_PACKAGING +
                                              " project." );
        } else
        {
            getLog(  ).info( "Assembly-site " + project.getArtifact(  ).getType(  ) + " artifact..." );
            assemblySite(  );
        }
    }

    private void assemblySite(  )
                       throws MojoExecutionException
    {
        // Explode the webapp in the temporary directory
        explodeWebapp( explodedDirectory );
        explodeConfigurationFiles( explodedDirectory );
        
		// duplicate SQL files in target WAR classpath for liquibase
        if (targetDatabaseVendor != null && !DATABASE_VENDOR_NONE.equals(targetDatabaseVendor))
            try
            {
                File lq_sqlSourceDir = new File(explodedDirectory, WEB_INF_SQL_PATH);
                File lq_sqlTargetDir = new File(explodedDirectory, WEB_INF_CLASSES_SQL_PATH);
                // we allow explicit override of build.properties location with this system property
                File dbProperties = new File(explodedDirectory, WEB_INF_DB_PROPERTIES_PATH);
                File buildProperties = new File(explodedDirectory, WEB_INF_BUILD_PROPERTIES_PATH);
                String buildPropertiesOverride = System.getProperty("liquibase.override.build.properties");
                if (buildPropertiesOverride != null)
                    buildProperties = new File(buildPropertiesOverride);
                String dbVendor = null;
                if (DATABASE_VENDOR_AUTO.equals(targetDatabaseVendor))
                    dbVendor = SqlRegexpHelper.findDbName(dbProperties);
                else if (DATABASE_VENDORS.contains(targetDatabaseVendor))
                    dbVendor = targetDatabaseVendor;
                else
                    throw new IllegalArgumentException("Unknown targetDatabaseVendor : '" + targetDatabaseVendor + "'");
                SqlRegexpHelper sqlHelper = new SqlRegexpHelper(buildProperties, dbVendor);
                getLog(  ).info( "Processing SQL files with target " + dbVendor );
                // we do not use copyDirectoryStructure since we have specific needs
                FileUtils.copyDirectoryWithFilter(lq_sqlSourceDir, lq_sqlTargetDir,
                        f -> f.getName().toLowerCase().endsWith(LiquiBaseSqlMojo.SQL_EXT) && f.length() > 0 && LiquiBaseSqlMojo.isTaggedWithLiquibase(f),
                        sqlHelper::filter);
            } catch (Exception e)
            {
                // Use the same catch block for all IOExceptions, presumably the
                // exception's message will be clear enough.
                throw new MojoExecutionException("Error while copying resources", e);
            }

        // put the timestamp in the assembly name
        if ( ArtifactUtils.isSnapshot( project.getVersion(  ) ) )
        {
            DateFormat utcDateFormatter = new SimpleDateFormat( utcTimestampPattern );
            String newVersion = utcDateFormatter.format( new Date(  ) );
            finalName = StringUtils.replace( finalName, SNAPSHOT_PATTERN, newVersion );
        }

        // Make a war from the exploded directory
        File warFile = new File( outputDirectory, finalName + ".war" );

        MavenArchiver archiver = new MavenArchiver(  );
        archiver.setArchiver( jarArchiver );
        archiver.setOutputFile( warFile );
        archive.setForced( forceCreation );

        try
        {
            if ( explodedDirectory.exists(  ) )
            {
                archiver.getArchiver(  )
                        .addDirectory( explodedDirectory, PACKAGE_WEBAPP_INCLUDES, PACKAGE_WEBAPP_RESOURCES_EXCLUDES );
            }

            archiver.createArchive( project, archive );
        } catch ( Exception e )
        {
            throw new MojoExecutionException( "Error assembling WAR", e );
        }
    }
}
