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

import fr.paris.lutece.maven.utils.plugindat.PluginDataService;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.DebugResolutionListener;
import org.apache.maven.artifact.resolver.ResolutionListener;
import org.apache.maven.artifact.resolver.ResolutionNode;
import org.apache.maven.artifact.resolver.WarningResolutionListener;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

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
     * The name of the servlet api artifact id.
     */
    protected static final String SERVLET_API = "servlet-api";
    private static final String TMP_DIR = "/WEB-INF/tmp/";
    private static final String TMP_FILE_ID_LAST_PROJECT = "idLastProject";
    private static final String TMP_FILE_MULTI_PROJECT_LOCAL_CONF_DIR = "multiProjectLocalConfDir";
    private static MavenProject lastProject;
    private static File multiProjectlocalConfDirectory;

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
        if ( ! LUTECE_CORE_PACKAGING.equals( project.getPackaging(  ) ) &&
                 ! LUTECE_PLUGIN_PACKAGING.equals( project.getPackaging(  ) ) &&
                 ! LUTECE_SITE_PACKAGING.equals( project.getPackaging(  ) ) &&
                 ! POM_PACKAGING.equals( project.getPackaging(  ) ) )
        {
            throw new MojoExecutionException( "This goal can be invoked only on a " + LUTECE_CORE_PACKAGING + " or " +
                                              LUTECE_PLUGIN_PACKAGING + " or " + LUTECE_SITE_PACKAGING + " project." );
        }

        if ( POM_PACKAGING.equals( project.getPackaging(  ) ) && project.isExecutionRoot(  ) )
        {
            // Execution for lutece-parent-pom

            // getLog().warn( "Only calling goal on modules" );
            lastProject = reactorProjects.get( reactorProjects.size(  ) - 1 );
            multiProjectlocalConfDirectory = localConfDirectory;

            getLog(  ).info( "------------------------------------------------------------------------" );
            getLog(  ).info( "Building Lutece Multi Project" );
            getLog(  ).info( "   explode local configuration, copy dependencies" );
            getLog(  ).info( "------------------------------------------------------------------------" );

            // generate plugins.dat
            getLog(  ).info( "Generate plugins.dat file" );
            PluginDataService.generatePluginsDataFile( testWebappDirectory.getAbsolutePath(  ) );

            explodeMultiProjectUserConfigurationFiles( getRootProjectBuildDirectory(  ),
                                                       multiProjectlocalConfDirectory );
        } else
        {
            // If the following condition returns "true", then it is a multi-project
            if ( ( reactorProjects.size(  ) > 1 ) && ! project.isExecutionRoot(  ) )
            {
                testWebappDirectory = getRootProjectBuildDirectory(  );

                // create  directory WEB-INF/lib in lutece-multi-project
                File webinfLib = new File( testWebappDirectory, "WEB-INF/lib" );

                if ( ! webinfLib.exists(  ) )
                {
                    webinfLib.mkdirs(  );
                }

                // Resolve dependencies in multi-project (multiProjectArtifacts)
                copyThirdPartyJars( testWebappDirectory );

                // copy dependencies in directory WEB-INF/lib
                Set<Artifact> artifactsToCopy = new LinkedHashSet<Artifact>(  );
                Set<Artifact> artifactsToDelete = new LinkedHashSet<Artifact>(  );
                Set<Artifact> artifactsReturn = doDependencyResolution(  );
                buildListArtifacts( artifactsReturn, artifactsToCopy, artifactsToDelete );

                getLog(  ).info( "Removing older dependencies from lutece-multi-project target directory" );
                removeArtifacts( webinfLib, artifactsToDelete );
                getLog(  ).info( "Copy dependencies into lutece-multi-project target directory" );
                copyDependencies( webinfLib, artifactsToCopy );
            }

            explodeWebapp( testWebappDirectory );
            explodeConfigurationFiles( testWebappDirectory );
        }
    }

    /**
     * Execute m2 mojo.
     *
     * @throws MojoExecutionException the mojo execution exception
     * @throws MojoFailureException the mojo failure exception
     * @deprecated use {@link #execute()} instead for Maven 3
     */
    @Deprecated
    public void executeM2Mojo(  )
                       throws MojoExecutionException, MojoFailureException
    {
        if ( ! LUTECE_CORE_PACKAGING.equals( project.getPackaging(  ) ) &&
                 ! LUTECE_PLUGIN_PACKAGING.equals( project.getPackaging(  ) ) &&
                 ! LUTECE_SITE_PACKAGING.equals( project.getPackaging(  ) ) &&
                 ! POM_PACKAGING.equals( project.getPackaging(  ) ) )
        {
            throw new MojoExecutionException( "This goal can be invoked only on a " + LUTECE_CORE_PACKAGING + " or " +
                                              LUTECE_PLUGIN_PACKAGING + " or " + LUTECE_SITE_PACKAGING + " project." );
        }

        if ( POM_PACKAGING.equals( project.getPackaging(  ) ) && project.isExecutionRoot(  ) )
        {
            getLog(  ).warn( "Only calling goal on modules" );

            lastProject = reactorProjects.get( reactorProjects.size(  ) - 1 );
            multiProjectlocalConfDirectory = localConfDirectory;

            // Create files in order to "save" the lastProjectId and the multiProjectLocalConfDir for the other modules
            try
            {
                FileUtils.createFile( getRootProjectBuildDirectory(  ) + TMP_DIR,
                                      TMP_FILE_ID_LAST_PROJECT,
                                      lastProject.getId(  ) );
                FileUtils.createFile( getRootProjectBuildDirectory(  ) + TMP_DIR,
                                      TMP_FILE_MULTI_PROJECT_LOCAL_CONF_DIR,
                                      multiProjectlocalConfDirectory.getAbsolutePath(  ) );
            } catch ( IOException e )
            {
                getLog(  ).error( e );
            }
        } else
        {
            if ( ( reactorProjects.size(  ) > 1 ) && ! project.isExecutionRoot(  ) )
            {
                testWebappDirectory = getRootProjectBuildDirectory(  );
            }

            explodeWebapp( testWebappDirectory );
            explodeConfigurationFiles( testWebappDirectory );

            // Get the IdLastProject and the multiProjectLocalConfDirectory
            String strIdLastProject =
                FileUtils.readLastLine( getRootProjectBuildDirectory(  ) + TMP_DIR + TMP_FILE_ID_LAST_PROJECT );
            String multiProjectLocalConfDirectory =
                FileUtils.readLastLine( getRootProjectBuildDirectory(  ) + TMP_DIR +
                                        TMP_FILE_MULTI_PROJECT_LOCAL_CONF_DIR );
            multiProjectlocalConfDirectory = new File( multiProjectLocalConfDirectory );

            if ( project.getId(  ).equals( strIdLastProject ) )
            {
                getLog(  ).info( "------------------------------------------------------------------------" );
                getLog(  ).info( "Building Lutece Multi Project" );
                getLog(  ).info( "   explode local configuration, copy dependencies" );
                getLog(  ).info( "------------------------------------------------------------------------" );

                // generate plugins.dat
                getLog(  ).info( "Generate plugins.dat file" );
                PluginDataService.generatePluginsDataFile( testWebappDirectory.getAbsolutePath(  ) );

                explodeMultiProjectUserConfigurationFiles( getRootProjectBuildDirectory(  ),
                                                           multiProjectlocalConfDirectory );

                // create  directory WEB-INF/lib in lutece-multi-project
                File webinfLib = new File( testWebappDirectory, "WEB-INF/lib" );
                webinfLib.mkdirs(  );

                // copy dependencies in directory WEB-INF/lib
                getLog(  ).info( "Copy dependencies into lutece-multi-project target directory" );
                copyDependencies( webinfLib,
                                  doDependencyResolution(  ) );

                // Remove the tmp files
                try
                {
                    FileUtils.deleteFile( getRootProjectBuildDirectory(  ) + TMP_DIR, TMP_FILE_ID_LAST_PROJECT );
                    FileUtils.deleteFile( getRootProjectBuildDirectory(  ) + TMP_DIR,
                                          TMP_FILE_MULTI_PROJECT_LOCAL_CONF_DIR );
                } catch ( IOException e )
                {
                    getLog(  ).error( e );
                }
            }
        }
    }

    /**
     * Removes the artifacts.
     *
     * @param outputDirectory the output directory
     * @param listArtifactsToDelete the list artifacts to delete
     * @throws MojoExecutionException the mojo execution exception
     */
    public void removeArtifacts( File outputDirectory, Set<Artifact> listArtifactsToDelete )
                         throws MojoExecutionException
    {
        for ( Artifact artifactToDelete : listArtifactsToDelete )
        {
            File jarFile = artifactToDelete.getFile(  );

            try
            {
                FileUtils.deleteFile( outputDirectory.getAbsolutePath(  ) + File.separator,
                                      jarFile.getName(  ) );
            } catch ( IOException e )
            {
                throw new MojoExecutionException( "Error while removing " + outputDirectory.getAbsolutePath(  ) +
                                                  jarFile.getName(  ), e );
            }
        }
    }

    /**
     * Copy the jar dependencies list iinto the output directory.
     *
     * @param outputDirectory the output directory
     * @param listArtifactsToCopy the list of artifact dependency
     * @throws MojoExecutionException the mojo exection exection
     */
    public void copyDependencies( File outputDirectory, Set<Artifact> listArtifactsToCopy )
                          throws MojoExecutionException
    {
        for ( Artifact d : listArtifactsToCopy )
        {
            File jarFile = d.getFile(  );
            File newFile = new File( outputDirectory,
                                     jarFile.getName(  ) );

            try
            {
                FileUtils.copyFileIfModified( jarFile, newFile );
            } catch ( IOException e )
            {
                throw new MojoExecutionException( "Error while copying " + jarFile.getAbsolutePath(  ) + " to " +
                                                  newFile.getAbsolutePath(  ), e );
            }
        }
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

    /**
     * Use to filter duplicate dependencies in multi project
     *
     * @return a list of artifacts whith no duplicate entry
     */
    private Set<Artifact> doDependencyResolution(  )
    {
        Set<Artifact> artifactsReturn = new HashSet<Artifact>(  );

        // Collector Filter jar artifacts in scope 'compile' or 'runtime'
        ArtifactFilter thirdPartyFilter =
            new ArtifactFilter(  )
            {
                @Override
                public boolean include( Artifact artifact )
                {
                    return ( ! LUTECE_CORE_TYPE.equals( artifact.getArtifactId(  ) ) &&
                           ! SERVLET_API.equals( artifact.getArtifactId(  ) ) &&
                           ! Artifact.SCOPE_PROVIDED.equals( artifact.getScope(  ) ) &&
                           ! Artifact.SCOPE_TEST.equals( artifact.getScope(  ) ) );
                }
            };

        // Collector listener config
        List<ResolutionListener> listeners = new ArrayList<ResolutionListener>(  );

        if ( logger.isDebugEnabled(  ) )
        {
            listeners.add( new DebugResolutionListener( logger ) );
        }

        listeners.add( new WarningResolutionListener( logger ) );

        /*---------------- Resolution-------------*/
        // resolve conflict version artifacts with collector
        ArtifactResolutionResult artifactResolutionResult = null;

        try
        {
            artifactResolutionResult =
                artifactCollector.collect( multiProjectArtifacts,
                                           project.getArtifact(  ),
                                           localRepository,
                                           remoteRepositories,
                                           metadataSource,
                                           thirdPartyFilter,
                                           listeners );
        } catch ( ArtifactResolutionException e )
        {
            e.printStackTrace(  );
        }

        // keep track of added reactor projects in order to avoid duplicates
        Set<String> emittedReactorProjectId = new HashSet<String>(  );

        for ( ResolutionNode node : artifactResolutionResult.getArtifactResolutionNodes(  ) )
        {
            Artifact art = node.getArtifact(  );

            try
            {
                resolver.resolve( art,
                                  node.getRemoteRepositories(  ),
                                  localRepository );
            } catch ( ArtifactNotFoundException e )
            {
                e.printStackTrace(  );
            } catch ( ArtifactResolutionException e )
            {
                e.printStackTrace(  );
            }

            if ( emittedReactorProjectId.add( art.getGroupId(  ) + '-' + art.getArtifactId(  ) ) )
            {
                artifactsReturn.add( art );
            }
        }

        return artifactsReturn;
    }

    /**
     * Builds the list artifacts.
     *
     * @param artifactsReturn the artifacts return
     * @param artifactsToCopy the artifacts to copy
     * @param artifactsToDelete the artifacts to delete
     * @throws MojoExecutionException the mojo execution exception
     */
    private void buildListArtifacts( Set<Artifact> artifactsReturn, Set<Artifact> artifactsToCopy,
                                     Set<Artifact> artifactsToDelete )
                             throws MojoExecutionException
    {
        getLog(  ).info( "Building lists of artifacts to copy and to remove" );

        for ( Artifact artifact : artifactsReturn )
        {
            boolean bIsInMultiProject = false;

            for ( Artifact multiprojetArtifact : multiProjectArtifactsCopied )
            {
                // Check artifact ID
                if ( artifact.getArtifactId(  ).equals( multiprojetArtifact.getArtifactId(  ) ) &&
                         artifact.getGroupId(  ).equals( multiprojetArtifact.getGroupId(  ) ) )
                {
                    //Workaround MAVENPLUGIN-33 - NullPointerException when doing multi-project with excluded dependencies
                    //Some excluded artifacts are in the list but with a null VersionRange, which triggers an NPE in getSelectedVersion()..
                    //Skip them because we want to exclude them anyway.
                    if ( artifact.getVersionRange( ) != null ) {
                        // Compare version
                        try
                        {
                            if ( artifact.getSelectedVersion(  ).compareTo( multiprojetArtifact.getSelectedVersion(  ) ) > 0 )
                            {
                                artifactsToDelete.add( multiprojetArtifact );
                            } else
                            {
                                bIsInMultiProject = true;
                            }
                        } catch ( OverConstrainedVersionException e )
                        {
                            throw new MojoExecutionException( "Error while removing comparing versions", e );
                        }
                    }

                    break;
                }
            }

            if ( ! bIsInMultiProject )
            {
                artifactsToCopy.add( artifact );
            }
        }

        multiProjectArtifactsCopied.removeAll( artifactsToDelete );
        multiProjectArtifactsCopied.addAll( artifactsToCopy );
    }
}
