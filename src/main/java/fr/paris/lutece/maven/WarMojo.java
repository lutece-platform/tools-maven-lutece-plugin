/*
 * Copyright (c) 2002-2024, Mairie de Paris
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

import javax.inject.Inject;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.archiver.jar.JarArchiver;


/**
 * Mojo for building a WAR (Web Application Archive) file for lutece applications.
 *
 * This goal compiles the project's source code, packages it along with its resources,
 * and creates a WAR file suitable for deployment on a web server.
 *
 * <p>
 * This goal supports the following configuration parameters:
 * </p>
 *
 * <ul>
 *   <li><code>outputDirectory</code>: The directory where the generated WAR file will be placed.</li>
 *   <li><code>finalName</code>: The final name of the WAR file, without extension.</li>
 *   <li><code>webResources</code>: Resources to be included in the WAR.</li>
 *   <li><code>includeDependencies</code>: Whether to include project dependencies in the WAR file.</li>
 *   <li><code>localConfDirectory</code>: The directory containing the local, user-specific configuration files.</li>
 *   <li><code>defaultConfDirectory</code>: The directory containing the default configuration files.</li>
 *   <li><code>sqlDirectory</code>: The directory containing the database sql script.</li>
 *   <li><code>siteDirectory</code>: The directory containing the default user documentation</li>
 *
 * </ul>
 **/

@Mojo( name = "war" , defaultPhase=LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class WarMojo extends AbstractLuteceWebappMojo
{
    /**
     * The name of the generated WAR file.
     *
     */
    @Parameter(
            property = "project.build.finalName", required = true )
    private String finalName;

    /**
     * Whether creating the archive should be forced.
     *
     */
    @Parameter(
    		property = "jar.forceCreation",
            defaultValue = "yyyyMMdd.HHmmss" )
    private boolean forceCreation;

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
    @Inject
    private JarArchiver jarArchiver;

    /**
     * Executes the mojo on the current project.
     *
     * @throws MojoExecutionException
     *             if an error occurred while building the artifact.
     */
    @Override
	public void execute(  )
                 throws MojoExecutionException, MojoFailureException
    {

        	if ( ! LUTECE_SITE_PACKAGING.equals( project.getPackaging(  ) )&&
        			! LUTECE_CORE_PACKAGING.equals( project.getPackaging(  ) ) &&
                    ! LUTECE_PLUGIN_PACKAGING.equals( project.getPackaging(  ) ) &&
                    ! LUTECE_SITE_PACKAGING.equals( project.getPackaging(  ) ) &&
                    ! POM_PACKAGING.equals( project.getPackaging(  ) ) )
           {
        		throw new MojoExecutionException( "This goal can be invoked only on a " + LUTECE_CORE_PACKAGING + " or " +
                        LUTECE_PLUGIN_PACKAGING + " or " + LUTECE_SITE_PACKAGING +" or "+ LUTECE_SITE_PACKAGING + "project." );

        } else
        {
            getLog(  ).info( "Assembly " + project.getArtifact(  ).getType(  ) + " artifact..." );
            assemblyProject(  );
        }
    }

    private void assemblyProject(  )
                       throws MojoExecutionException
    {
        // Explode the webapp in the temporary directory
        explodeWebapp( webappDirectory );
        explodeConfigurationFiles( webappDirectory );
        explodeSqlFiles(webappDirectory, targetDatabaseVendor);
        
        // Make a war from the exploded directory
        File warFile = new File( outputDirectory, finalName + ".war" );

        MavenArchiver archiver = new MavenArchiver(  );
        archiver.setArchiver( jarArchiver );
        archiver.setOutputFile( warFile );
        archive.setForced( forceCreation );

        try
        {
            if ( webappDirectory.exists(  ) )
            {
                archiver.getArchiver(  )
                        .addDirectory( webappDirectory);
            }
            archiver.createArchive( session, project, archive );
        } catch ( Exception e )
        {
            throw new MojoExecutionException( "Error assembling WAR", e );
        }
    }
}
