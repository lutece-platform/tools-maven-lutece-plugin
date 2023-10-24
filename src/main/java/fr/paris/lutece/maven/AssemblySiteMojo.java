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

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Builds a site's final WAR from a Lutece core artifact and a set of Lutece
 * plugin artifacts.<br/> Note that the Lutece dependencies (core and plugins)
 * will only be updated the first time the WAR is created. Subsequent calls to
 * this goal will only update the site's specific files.<br/> If you wish to
 * force webapp re-creation (for instance, if you changed the version of a
 * dependency), call the <code>clean</code> phase before this goal.
 *
 * @goal site-assembly
 * @execute phase="process-resources"
 * @requiresDependencyResolution compile+runtime
 */

@Mojo( name = "site-assembly" )

public class AssemblySiteMojo
    extends AbstractLuteceWebappMojo
{
    private static final TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone( "UTC" );
    private static final String SNAPSHOT_PATTERN = "SNAPSHOT";
    // The path to the copy of the sql directory inside the classpath
    private static final String WEB_INF_CLASSES_SQL_PATH = "WEB-INF/classes/sql/";

    /**
     * The output date format.
     *
     * @parameter default-value="yyyyMMdd.HHmmss"
     * @required
     */
    private String utcTimestampPattern;

    /**
     * The name of the generated WAR file.
     *
     * @parameter expression="${project.build.finalName}"
     * @required
     */
    private String finalName;

    /**
     * A temporary directory used to hold the exploded version of the webapp.
     *
     * @parameter expression="${project.build.directory}/${project.build.finalName}"
     * @required
     */
    private File explodedDirectory;

    /**
     * Whether creating the archive should be forced.
     *
     * @parameter expression="${jar.forceCreation}" default-value="false"
     */
    private boolean forceCreation;

    /**
     * The maven archive configuration to use.
     *
     * See <a
     * href="http://maven.apache.org/ref/current/maven-archiver/apidocs/org/apache/maven/archiver/MavenArchiveConfiguration.html">the
     * Javadocs for MavenArchiveConfiguration</a>.
     *
     * @parameter
     */
    private MavenArchiveConfiguration archive = new MavenArchiveConfiguration(  );

    /**
     * The Jar archiver.
     *
     * @component role="org.codehaus.plexus.archiver.Archiver" roleHint="jar"
     * @required
     */
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
		try
		{
			File lq_sqlSourceDir = new File(explodedDirectory, WEB_INF_SQL_PATH);
			File lq_sqlTargetDir = new File(explodedDirectory, WEB_INF_CLASSES_SQL_PATH);
			// we do not use copyDirectoryStructure since we have specific needs
			FileUtils.copyDirectoryWithFilter(lq_sqlSourceDir, lq_sqlTargetDir, f -> f.getName().toLowerCase().endsWith(".sql") && f.length() > 0 && LiquiBaseSqlMojo.isTaggedWithLiquibase(f));
		} catch (IOException e)
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
