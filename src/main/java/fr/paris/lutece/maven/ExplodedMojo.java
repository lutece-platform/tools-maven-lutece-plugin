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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import fr.paris.lutece.maven.utils.plugindat.PluginDataService;

/**
 * Explodes a test webapp for a Lutece plugin or site project.<br/> Note that
 * the Lutece dependencies (core and plugins) will only be updated the first
 * time the exploded webapp is created. Subsequent calls to this goal will only
 * update the project's specific files :
 * <ul>
 * <li>for a plugin project : the plugin-specific webapp elements and classes.</li>
 * <li>for a site project : the site-specific webapp elements.</li>
 * </ul>
 * If you wish to force webapp re-creation (for instance, if you changed the
 * version of a dependency), call the <code>clean</code> phase before this
 * goal.
 *
 */

@Mojo( name = "exploded" ,
 requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
		)
@Execute ( goal = "exploded",
		phase=LifecyclePhase.PROCESS_CLASSES )
public class ExplodedMojo
    extends AbstractLuteceWebappMojo
{

    /**
     * Executes the mojo on the current project.
     *
     * @throws MojoExecutionException
     *             if an error occured while exploding the webapp.
     */
    @Override
    public void execute(  )
                 throws MojoExecutionException, MojoFailureException
    {
    	 logBanner();
        validatePackaging( LUTECE_CORE_PACKAGING, LUTECE_PLUGIN_PACKAGING, LUTECE_SITE_PACKAGING, POM_PACKAGING );

        if ( POM_PACKAGING.equals( project.getPackaging(  ) ) && project.isExecutionRoot(  ) )
        {
            // Execution for lutece-parent-pom

            getLog(  ).info( "------------------------------------------------------------------------" );
            getLog(  ).info( "Building Lutece Multi Project" );
            getLog(  ).info( "   explode local configuration, copy dependencies" );
            getLog(  ).info( "------------------------------------------------------------------------" );

            explodeMultiProjectUserConfigurationFiles( getRootProjectBuildDirectory(  ), localConfDirectory );
        } else
        {
            // In a reactor every module explodes into the webapp of the root project.
            if ( ( reactorProjects.size(  ) > 1 ) && ! project.isExecutionRoot(  ) )
            {
                testWebappDirectory = getRootProjectBuildDirectory(  );
            }

            // One path, whether that directory is shared or not. The lock is only ever
            // contended by a reactor built with -T, where the modules would otherwise unpack
            // build-config over each other and copy into a tree another module is writing.
            synchronized ( getSharedDirectoryLock( testWebappDirectory ) )
            {
                explodeWebapp( testWebappDirectory );
                explodeConfigurationFiles( testWebappDirectory );
                explodeSqlFiles( testWebappDirectory, targetDatabaseVendor );
            }

            if ( ( reactorProjects.size(  ) > 1 ) && isLastModuleToExplode(  ) )
            {
                // Every module has now deployed its descriptor into the shared webapp, so the
                // file can list them. It used to be generated from the root POM branch, and
                // Maven builds the root first : WEB-INF/plugins was still empty and the file
                // declared no plugin at all.
                getLog(  ).info( "Generate plugins.dat file" );

                try
                {
                    PluginDataService.generatePluginsDataFile( testWebappDirectory.getAbsolutePath(  ) );
                }
                catch ( IOException e )
                {
                    throw new MojoExecutionException( "Error while generating plugins.dat", e );
                }
            }
        }
    }

    /**
     * Tells whether this module is the last one to have exploded into the shared webapp.
     *
     * Counting the modules that are done, rather than comparing with the last one the reactor
     * declares, is what makes this hold under -T : with parallel builds the last declared
     * module can well finish first, and plugins.dat was then written before the others had
     * deployed their descriptors.
     *
     * @return true when every module of the reactor has exploded
     */
    private boolean isLastModuleToExplode(  )
    {
        long nExpected = reactorProjects.stream(  )
                .filter( p -> ! ( POM_PACKAGING.equals( p.getPackaging(  ) ) && p.isExecutionRoot(  ) ) )
                .count(  );

        return countExplodedModules(  ) >= nExpected;
    }

    /**
     * Explode multi project local configuration directory
     *
     * @param targetDir the multi project target directory
     * @param confDirectory The multi project local conf directory
     * @throws MojoExecutionException the mojo exception
     */
    protected void explodeMultiProjectUserConfigurationFiles( File targetDir, File confDirectory )
                                                      throws MojoExecutionException
    {
        try
        {
            // Copy user-specific configuration files
            getLog(  ).info( "Local configuration directory is " + confDirectory.getAbsolutePath(  ) );

            if ( confDirectory.exists(  ) )
            {
                getLog(  ).debug( "Copying local configuration" );
                FileUtils.copyDirectoryStructure( confDirectory, targetDir );
            } else
            {
                getLog(  ).warn( "Local configuration directory " + confDirectory.getAbsolutePath(  ) +
                                 " does not exist" );
            }
        } catch ( IOException e )
        {
            // Use the same catch block for all IOExceptions, presumably the
            // exception's message will be clear enough.
            throw new MojoExecutionException( "Error while copying configuration resources", e );
        }
    }

}
