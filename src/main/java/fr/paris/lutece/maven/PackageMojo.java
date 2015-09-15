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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProjectHelper;

import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.zip.ZipArchiver;

import java.io.File;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Packages Lutece core or plugin projects.<br/> This mojo produces two
 * artifacts :
 * <ul>
 * <li>a JAR file containing the java classes.</li>
 * <li>a ZIP attachement containing the webapp's files.</li>
 * </ul>
 * Please note that this mojo should be executed on a fresh copy of the sources.
 * If you run it on a Lutece core where <code>lutece:exploded</code> has been
 * invoked, you may end up with unwanted files or directories in the ZIP
 * attachement.
 *
 * @goal package
 * @requiresDependencyResolution compile
 *
 */
@Mojo( name = "package" )
public class PackageMojo
    extends AbstractLuteceMojo
{
    //The path to the classes directory
    private static final String WEB_INF_CLASSES_PATH = "WEB-INF/classes/";

    /**
     * The name of the generated artifact.
     *
     * @parameter expression="${project.build.finalName}"
     * @required
     */
    private String artifactName;

    /**
     * The source directory for webapp components.
     *
     * @parameter expression="${basedir}/webapp"
     * @required
     */
    private File webappSourceDirectory;

    /**
     * Whether creating the archives should be forced.
     *
     * @parameter expression="${jar.forceCreation}" default-value="false"
     */
    private boolean forceCreation;

    /**
     * The Jar archiver.
     *
     * @component role="org.codehaus.plexus.archiver.Archiver" roleHint="jar"
     * @required
     */
    private JarArchiver jarArchiver;

    /**
     * The Zip archiver.
     *
     * @component role="org.codehaus.plexus.archiver.Archiver" roleHint="zip"
     * @required
     */
    private ZipArchiver zipArchiver;

    /**
     * The maven archive configuration to use.
     *
     * See <a
     * href="http://maven.apache.org/ref/current/maven-archiver/apidocs/org/apache/maven/archiver/MavenArchiveConfiguration.html">the
     * Javadocs for MavenArchiveConfiguration</a>.
     *
     * @parameter
     */
    private MavenArchiveConfiguration archiveCfg = new MavenArchiveConfiguration(  );

    /**
     * Project-helper instance, used to make addition of resources simpler.
     *
     * @component
     */
    private MavenProjectHelper projectHelper;

    /**
     * Executes the mojo on the current project.
     *
     * @throws MojoExecutionException
     *             if an error occured while building the artifact.
     */
    @Override
    public void execute(  )
                 throws MojoExecutionException, MojoFailureException
    {
        getLog(  ).info( "Packaging " + project.getArtifact(  ).getType(  ) + " artifact..." );

        // The two archives to be produced
        File classesJar = getArchiveFile( outputDirectory, artifactName, null, "jar" );
        File webappZip = getArchiveFile( outputDirectory, artifactName, WEBAPP_CLASSIFIER, "zip" );

        // Create a maven archiver to build these JARs
        MavenArchiver archiver = new MavenArchiver(  );

        try
        {
            // Package the classes JAR
            archiver.setArchiver( jarArchiver );
            archiver.setOutputFile( classesJar );
            archiveCfg.setForced( forceCreation );

            if ( ! classesDirectory.exists(  ) )
            {
                getLog(  ).warn( "Could not find classes directory " + classesDirectory.getAbsolutePath(  ) );
            } else
            {
                archiver.getArchiver(  )
                        .addDirectory( classesDirectory, PACKAGE_CLASSES_INCLUDES, PACKAGE_CLASSES_EXCLUDES );
            }

            archiver.createArchive( null, project, archiveCfg );

            // Package the webapp ZIP
            zipArchiver.setCompress( true );
            zipArchiver.setForced( forceCreation );
            zipArchiver.setDestFile( webappZip );

            if ( ! webappSourceDirectory.exists(  ) )
            {
                getLog(  ).warn( "Could not find webapp directory " + webappSourceDirectory.getAbsolutePath(  ) );
                getLog(  ).warn( "The zip file could not be created." );
            } else
            {
                zipArchiver.addDirectory( webappSourceDirectory, PACKAGE_WEBAPP_INCLUDES, PACKAGE_WEBAPP_EXCLUDES );

                if ( sqlDirectory.exists(  ) )
                {
                    zipArchiver.addDirectory( sqlDirectory, WEB_INF_SQL_PATH, PACKAGE_WEBAPP_INCLUDES,
                                              PACKAGE_WEBAPP_EXCLUDES );
                }

                if ( siteDirectory.exists(  ) )
                {
                    zipArchiver.addDirectory( siteDirectory, WEB_INF_DOC_XML_PATH, PACKAGE_WEBAPP_SITE_INCLUDES,
                                              PACKAGE_WEBAPP_SITE_EXCLUDES );
                }

                if ( defaultConfDirectory.exists(  ) )
                {
                    zipArchiver.addDirectory( defaultConfDirectory, PACKAGE_WEBAPP_INCLUDES, PACKAGE_WEBAPP_EXCLUDES );
                }

                if ( classesDirectory.exists(  ) )
                {
                    zipArchiver.addDirectory( classesDirectory, WEB_INF_CLASSES_PATH, PACKAGE_WEBAPP_RESOURCES_INCLUDE,
                                              PACKAGE_WEBAPP_RESOURCES_EXCLUDES );
                }

                zipArchiver.createArchive(  );
            }
        } catch ( Exception e )
        {
            throw new MojoExecutionException( "Error assembling JAR", e );
        }

        // Set the classes JAR as the main artifact
        project.getArtifact(  ).setFile( classesJar );

        // Set the webapp ZIP as an attachement
        projectHelper.attachArtifact( project, "zip", WEBAPP_CLASSIFIER, webappZip );
    }

    /**
     * Builds the name of the destination JAR file.
     */
    private static File getArchiveFile( File basedir, String finalName, String classifier, String extension )
    {
        if ( classifier == null )
        {
            classifier = "";
        } else if ( ( classifier.trim(  ).length(  ) > 0 ) && ! classifier.startsWith( "-" ) )
        {
            classifier = "-" + classifier;
        }

        return new File( basedir, finalName + classifier + "." + extension );
    }
}
