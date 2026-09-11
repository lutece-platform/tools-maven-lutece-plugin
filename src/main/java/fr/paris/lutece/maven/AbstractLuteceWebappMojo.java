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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.TypeArtifactFilter;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import fr.paris.lutece.utils.sql.SqlRegexpHelper;
import java.util.function.Function;

/**
 * Abstracts functionnality common to mojos that create a Lutece webapp from
 * Lutece artifacts.
 */
public abstract class AbstractLuteceWebappMojo
    extends AbstractLuteceMojo
{

    /**
     * The directory containing the local, user-specific configuration files.
     */
	@Parameter(
			property="localConfDirectory",
            defaultValue = "${user.home}/lutece/conf/${project.artifactId}")
    protected File localConfDirectory;

    /**
     * The set of artifacts required by this project, including transitive
     * dependencies.
     */
    @Parameter(
            defaultValue = "${project.artifacts}",
            required = true,
            readonly = true)
    protected Set<Artifact> artifacts;
    /**
     * The Repository System Session
     */
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected RepositorySystemSession repoSession;
    /**
     * The remote Project Repositories
     */
    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    protected List<RemoteRepository> remoteProjectRepositories;

    /**
     * The local repository.
     */
    @Parameter(
            property = "localRepository")
    protected org.apache.maven.artifact.repository.ArtifactRepository localRepository;

    /**
     * The remote repositories.
     */
    @Parameter(
    		property = "project.remoteArtifactRepositories")
    protected java.util.List<ArtifactRepository> remoteRepositories;

    /**
     * The directory where to explode the test webapp.
     */
    @Parameter(
    		property = "testWebappDirectory",
    		defaultValue = "${project.build.directory}/lutece" )
    protected File testWebappDirectory;

    /**
     * The directory where to explode the  webapp.
     */
    @Parameter(
    		property = "webappDirectory",
            defaultValue = "${project.build.directory}/${project.build.finalName}")
    protected File webappDirectory;
    
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
    protected String targetDatabaseVendor;


    /**
     * The artifact factory.
     */
    @Inject
    protected org.apache.maven.artifact.factory.ArtifactFactory artifactFactory;

    /**
     * The artifact resolver.
     */
    @Inject
    protected org.apache.maven.artifact.resolver.ArtifactResolver resolver;
    /**
     * The unarchiver.
     */
    @Inject
    protected ZipUnArchiver unArchiver;

    @Inject
    protected ArtifactHandlerManager artifactHandlerManager;

    @Inject
    protected RepositorySystem repoSystem;

    /**
     * The artifact metadata source.
     */
    @Inject
    protected ArtifactMetadataSource metadataSource;

    /** Third-party jars a webapp ships : plain jars in scope compile or runtime. */
    private static final ArtifactFilter THIRD_PARTY_FILTER = artifact ->
            "jar".equals( artifact.getType(  ) ) &&
            ( Artifact.SCOPE_RUNTIME.equals( artifact.getScope(  ) ) ||
              Artifact.SCOPE_COMPILE.equals( artifact.getScope(  ) ) );

    /**
     * Creates an exploded webapp structure from the current project.
     *
     * @param targetDir
     *            the destination directory.
     * @throws MojoExecutionException
     *             if an error occurs while exploding the webapp.
     */
    protected void explodeWebapp( File targetDir )
                          throws MojoExecutionException
    {
        try
        {
            // Indicates whether we perform in-place deployment (ie the webapp
            // is assembled in the webapp source directory)
            boolean isInplace = targetDir.equals( webappSourceDirectory );

            // Indicates whether the exploded webapp already exists
            boolean isUpdate = targetDir.exists(  );

            getLog(  ).info( ( isUpdate ? "Updating" : "Exploding" ) + " webapp in " + targetDir + "..." );

            // Create the directory if necessary
            targetDir.mkdirs(  );

            if ( ! isInplace && ! isUpdate )
            {
                // Explode the lutece-core artifact
                explodeCore( targetDir );

                // Explode all lutece-plugin artifacts
                explodePlugins( targetDir );

                // Explode all lutece-site artifacts
                explodeSites( targetDir );
            }

            // Copy third-party JARs
            copyThirdPartyJars( targetDir );

            // Copy Build Config
            copyBuildConfig( targetDir );

            if ( ! isInplace && webappSourceDirectory.exists(  ) )
            {
                // On first deployment copy every file, in case project-specific files are meant
                // to overwrite files from the core or the plugins. Afterwards only overwrite newer
                // files : common files have either been overwritten at creation, or are older.
                logCopied( copyDirectory( webappSourceDirectory, targetDir, isUpdate ), "webapp files" );
            }

            // Copy SQL files
            if ( ! isInplace && sqlDirectory.exists(  ) )
            {
                getLog(  ).debug( "Copying SQL files from " + sqlDirectory.getAbsolutePath(  ) );

                File sqlTargetDir = new File( targetDir, WEB_INF_SQL_PATH );

                logCopied( copyDirectory( sqlDirectory, sqlTargetDir, isUpdate ), "sql files" );
            }

            //Copy Site User files
            if ( ! isInplace && siteDirectory.exists(  ) )
            {
                getLog(  ).debug( "Copying Site User files from " + siteDirectory.getAbsolutePath(  ) );

                File siteUserTargetDir = new File( targetDir, WEB_INF_DOC_XML_PATH );

                logCopied( copyDirectory( siteDirectory, siteUserTargetDir, isUpdate ), "site user files" );
            }

            // Copy compiled classes
            if ( classesDirectory.exists(  ) )
            {
                File classesDir = new File( targetDir, "WEB-INF/classes" );
                classesDir.mkdirs(  );
                logCopied( FileUtils.copyDirectoryStructureIfModified( classesDirectory, classesDir ),
                        "class and resource files" );
            }
        } catch ( IOException e )
        {
            // Use the same catch block for all IOExceptions, presumably the
            // exception's message will be clear enough.
            throw new MojoExecutionException( "Error while copying resources", e );
        }
    }

    /**
     * Copies a directory structure, either wholesale or only the files newer than their target.
     *
     * @param sourceDirectory
     *            the source directory
     * @param targetDirectory
     *            the destination directory
     * @param isUpdate
     *            true to copy only the modified files
     * @return the number of files copied
     * @throws IOException
     *             if an I/O exception occurs
     */
    protected int copyDirectory( File sourceDirectory, File targetDirectory, boolean isUpdate )
                        throws IOException
    {
        return isUpdate ? FileUtils.copyDirectoryStructureIfModified( sourceDirectory, targetDirectory )
                        : FileUtils.copyDirectoryStructure( sourceDirectory, targetDirectory );
    }

    /**
     * Logs how many files a copy actually wrote.
     *
     * @param nCopied
     *            the number of files copied
     * @param strWhat
     *            what was copied, for the message
     */
    protected void logCopied( int nCopied, String strWhat )
    {
        if ( nCopied == 0 )
        {
            getLog(  ).info( "Nothing to update - all " + strWhat + " are up to date" );
        } else
        {
            getLog(  ).info( "Copying " + nCopied + " " + strWhat );
        }
    }

    protected void explodeConfigurationFiles( File targetDir )
                                      throws MojoExecutionException
    {
        try
        {
            // Copy default configuration files
            if ( defaultConfDirectory.exists(  ) )
            {
                getLog(  ).debug( "Copying default configuration from " + defaultConfDirectory.getAbsolutePath(  ) );
                FileUtils.copyDirectoryStructure( defaultConfDirectory, targetDir );
            } else
            {
                getLog(  )
                    .debug( "Default configuration directory " + defaultConfDirectory.getAbsolutePath(  ) +
                           " does not exist" );
            }

            // Copy user-specific configuration files
            getLog(  ).info( "Local configuration directory is " + localConfDirectory.getAbsolutePath(  ) );

            if ( localConfDirectory.exists(  ) )
            {
                getLog(  ).debug( "Copying local configuration" );
                FileUtils.copyDirectoryStructure( localConfDirectory, targetDir );
            } else
            {
                getLog(  )
                    .debug( "Local configuration directory " + localConfDirectory.getAbsolutePath(  ) +
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
     * Add the lutece-core dependency's files to an exploded webapp directory.
     *
     * @param webappDir
     *            the webapp directory.
     *
     * @throws MojoExecutionException
     *             if there is no lutece-core dependency, or more than one, or
     *             if an error occurs while resolving the artifact.
     */
    protected void explodeCore( File webappDir )
                      throws MojoExecutionException
    {
        // Get all the lutece-core artifacts from the project
        Set cores = filterArtifacts( new TypeArtifactFilter( LUTECE_CORE_TYPE ) );

        // There must be exactly one
        if ( LUTECE_CORE_TYPE.equals( project.getArtifactId(  ) ) )
        {
            return;
        }
        else if (( cores == null ) || cores.isEmpty(  ) )
        {
        	// Case where the project is built without lutece-core
        	return;
        }
        else if (  cores.size(  ) > 1  )
        { 	
            throw new MojoExecutionException( "Project \"" + project.getName(  ) +
                                              "\" must have exactly one dependency of type " + LUTECE_CORE_TYPE );
        }

        // Now we know the Set has exactly one element
        Artifact coreArtifact = (Artifact) cores.iterator(  ).next(  );

        addToExplodedWebapp( coreArtifact, webappDir );
    }

    /**
     * Add the lutece-plugin dependencies' files to an exploded webapp
     * directory.
     *
     * @param webappDir
     *            the webapp directory.
     *
     * @throws MojoExecutionException
     *             if an error occurs while resolving the artifacts.
     */
    protected void explodePlugins( File webappDir )
                         throws MojoExecutionException
    {
        // Get all the lutece-plugin artifacts from the project
        Set plugins = filterArtifacts( new TypeArtifactFilter( LUTECE_PLUGIN_TYPE ) );

        // Explode each artifact file
        for ( Iterator iterArtifacts = plugins.iterator(  ); iterArtifacts.hasNext(  ); )
        {
            Artifact pluginArtifact = (Artifact) iterArtifacts.next(  );
            addToExplodedWebapp( pluginArtifact, webappDir );
        }
    }

    /**
     * Add the lutece-site dependencies' files to an exploded webapp
     * directory.
     *
     * @param webappDir
     *            the webapp directory.
     *
     * @throws MojoExecutionException
     *             if an error occurs while resolving the artifacts.
     */
    protected void explodeSites( File webappDir )
                         throws MojoExecutionException
    {
        // Get all the lutece-site artifacts from the project
        Set sites = filterArtifacts( new TypeArtifactFilter( LUTECE_SITE_TYPE ) );

        // Explode each artifact file
        for ( Iterator iterArtifacts = sites.iterator(  ); iterArtifacts.hasNext(  ); )
        {
            Artifact siteArtifact = (Artifact) iterArtifacts.next(  );
            addToExplodedWebapp( siteArtifact, webappDir );
        }
    }

    /**
     * Copy third-party JARs to an exploded webapp directory.
     *
     * @param webappDir
     *            the webapp directory
     *
     * @throws MojoExecutionException
     *             if an error occurs while copying the files.
     */
    protected void copyThirdPartyJars( File webappDir )
                             throws MojoExecutionException
    {
        File webinfLib = new File( webappDir, "WEB-INF/lib" );
        webinfLib.mkdirs(  );

        // One code path whatever the build topology, the way liberty-maven-plugin does it :
        // walk the artifacts Maven resolved for this project and deploy them. It used to
        // branch on "am I in a reactor ?" and, there, gather the artifacts into a shared set
        // to resolve them all over again later - a second resolution answering a question
        // Maven had already answered per module.
        //
        // A reactor only differs in that every module writes into the same WEB-INF/lib. A
        // library two modules ask in different versions still has to be arbitrated, and the
        // registry below is what makes that happen; on a single project nothing ever collides
        // and the loop simply copies.
        for ( Artifact artifact : filterArtifacts( THIRD_PARTY_FILTER ) )
        {
            deployThirdPartyJar( artifact, webinfLib );
        }
    }

    /**
     * Deploys a third-party jar to WEB-INF/lib, keeping a single version of each library.
     *
     * @param artifact
     *            the artifact to deploy
     * @param webinfLib
     *            the WEB-INF/lib directory
     * @throws MojoExecutionException
     *             if the jar cannot be copied or the superseded one removed
     */
    private void deployThirdPartyJar( Artifact artifact, File webinfLib )
                               throws MojoExecutionException
    {
        Set<Artifact> deployed = getDeployedJars(  );

        synchronized ( deployed )
        {
            Artifact previous = findSameLibrary( deployed, artifact );

            if ( previous != null )
            {
                if ( compareVersions( artifact, previous ) <= 0 )
                {
                    // The same library is already there in that version or a newer one.
                    return;
                }

                deleteJar( previous, webinfLib );
                deployed.remove( previous );
            }

            copyJar( artifact, webinfLib );
            deployed.add( artifact );
        }
    }

    /**
     * Finds an already deployed artifact with the same groupId and artifactId.
     *
     * @param deployed
     *            the artifacts deployed so far
     * @param artifact
     *            the artifact being deployed
     * @return the one already deployed, or null
     */
    private static Artifact findSameLibrary( Collection<Artifact> deployed, Artifact artifact )
    {
        for ( Artifact candidate : deployed )
        {
            if ( candidate.getGroupId(  ).equals( artifact.getGroupId(  ) ) &&
                     candidate.getArtifactId(  ).equals( artifact.getArtifactId(  ) ) )
            {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Copies an artifact's jar to WEB-INF/lib.
     *
     * @param artifact
     *            the artifact to copy
     * @param webinfLib
     *            the WEB-INF/lib directory
     * @throws MojoExecutionException
     *             if the copy fails
     */
    private void copyJar( Artifact artifact, File webinfLib )
                  throws MojoExecutionException
    {
        File jarFile = artifact.getFile(  );
        File newFile = new File( webinfLib,
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

    /**
     * Removes a jar superseded by a newer version of the same library.
     *
     * @param artifact
     *            the superseded artifact
     * @param webinfLib
     *            the WEB-INF/lib directory
     * @throws MojoExecutionException
     *             if the jar cannot be removed
     */
    private void deleteJar( Artifact artifact, File webinfLib )
                    throws MojoExecutionException
    {
        File jarFile = new File( webinfLib, artifact.getFile(  ).getName(  ) );

        if ( jarFile.exists(  ) && ! jarFile.delete(  ) )
        {
            throw new MojoExecutionException( "Error while removing " + jarFile.getAbsolutePath(  ) );
        }
    }

    /**
     * Adds a Lutece artifact to an exploded webapp.
     *
     * @param luteceArtifact
     *            the Lutece artifact.
     * @param webappDir
     *            the exploded webapp's base directory.
     *
     * @throws MojoExecutionException
     *             if an error occurs while unpacking.
     */
    protected void addToExplodedWebapp( Artifact luteceArtifact, File webappDir )
                                throws MojoExecutionException
    {
        // Copy the artifact's main JAR to WEB-INF/lib
        File repoJar = luteceArtifact.getFile(  );

        File webinfLib = new File( webappDir, "WEB-INF/lib" );
        webinfLib.mkdirs(  );

        File webinfJar = new File( webinfLib,
                                   repoJar.getName(  ) );

        try
        {
            FileUtils.copyFileIfModified( repoJar, webinfJar );
        } catch ( IOException e )
        {
            throw new MojoExecutionException( "Error while copying " + repoJar.getAbsolutePath(  ) + " to " +
                                              webinfJar.getAbsolutePath(  ), e );
        }

        // Every Lutece artifact has an attached webapp artifact
        Artifact webappArtifact =
            artifactFactory.createArtifactWithClassifier( luteceArtifact.getGroupId(  ),
                                                          luteceArtifact.getArtifactId(  ),
                                                          luteceArtifact.getVersion(  ),
                                                          "zip",
                                                          WEBAPP_CLASSIFIER );

        // Resolve the webapp artifact
        try
        {
            resolver.resolve( webappArtifact, remoteRepositories, localRepository );
        } catch ( Exception e )
        {
            throw new MojoExecutionException( "Error while resolving artifact " + webappArtifact, e );
        }

        // Unzip the webapp artifact to the webapp directory
        try
        {
            unArchiver.setSourceFile( webappArtifact.getFile(  ) );
            unArchiver.setDestDirectory( webappDir );
            unArchiver.extract(  );
        } catch ( Exception e )
        {
            throw new MojoExecutionException( "Error while unpacking file " +
                                              webappArtifact.getFile(  ).getAbsolutePath(  ), e );
        }
    }

    /**
     * Gets the jars this project depends on. Lutece artifacts are excluded : they are deployed
     * by {@link #addToExplodedWebapp(Artifact, File)}. Provided and test artifacts are excluded
     * too, as are junit and the servlet API, whose transitive scope is not reliable.
     *
     * @return the jar files, never null
     */
    protected Collection<File> getDependentJars(  )
    {
        Set<File> result = new HashSet<>(  );
        Set<Artifact> directArtifacts = new HashSet<>(  );

        // getDependencyArtifacts() is deprecated and may return null
        Set<Artifact> dependencyArtifacts = project.getDependencyArtifacts(  );

        if ( dependencyArtifacts != null )
        {
            for ( Artifact artifact : dependencyArtifacts )
            {
                if ( ! LUTECE_CORE_TYPE.equals( artifact.getType(  ) ) &&
                         ! LUTECE_PLUGIN_TYPE.equals( artifact.getType(  ) ) &&
                         ! Artifact.SCOPE_PROVIDED.equals( artifact.getScope(  ) ) &&
                         ! Artifact.SCOPE_TEST.equals( artifact.getScope(  ) ) )
                {
                    directArtifacts.add( artifact );
                    result.add( artifact.getFile(  ) );
                }
            }
        }

        ArtifactResolutionResult artifactResolutionResult = null;

        try
        {
            artifactResolutionResult =
                resolver.resolveTransitively( directArtifacts,
                                              project.getArtifact(  ),
                                              remoteRepositories,
                                              localRepository,
                                              metadataSource );
        } catch ( ArtifactResolutionException | ArtifactNotFoundException e )
        {
            getLog(  ).error( e );
        }

        addTransitiveJars( artifactResolutionResult, result );

        return result;
    }

    /**
     * Adds the resolved transitive artifacts to the collected jars.
     *
     * @param artifactResolutionResult
     *            the resolution result, null when the resolution failed and was logged
     * @param result
     *            the jars collected so far
     */
    void addTransitiveJars( ArtifactResolutionResult artifactResolutionResult, Set<File> result )
    {
        if ( artifactResolutionResult == null )
        {
            // The resolution failed and has already been logged : keep the direct dependencies
            // rather than failing with a NullPointerException.
            return;
        }

        for ( Artifact artifact : artifactResolutionResult.getArtifacts(  ) )
        {
            if ( ! Artifact.SCOPE_PROVIDED.equals( artifact.getScope(  ) ) &&
                     ! Artifact.SCOPE_TEST.equals( artifact.getScope(  ) ) &&
                     ! JUNIT.equals( artifact.getArtifactId(  ) ) &&
                     ! SERVLET_API.equals( artifact.getArtifactId(  ) ) )
            {
                result.add( artifact.getFile(  ) );
            }
        }
    }

    /**
     * Filters the project's set of artifacts with an ArtifactFilter.
     *
     * @param type
     *            the type to retain.
     * @return a new Set containing the filtered elements.
     */
    protected Set<Artifact> filterArtifacts( ArtifactFilter filter )
    {
        Set<Artifact> result = new HashSet<>(  );

        for ( Artifact artifact : artifacts )
        {
            if ( filter.include( artifact ) )
            {
                result.add( artifact );
            }
        }

        return result;
    }

    /**
     * Used for multiproject, return the output directory ( /target/lutece/ ) of root project.
     *
     * @return File corresponding to the root project output directory
     */
    protected File getRootProjectBuildDirectory(  )
    {
        return new File( getRootProjectBuildDirectoryTarget(  ), LUTECE_DIRECTORY );
    }

    /**
     * Used for multiproject, return the output directory ( /target ) of root project.
     *
     * @return File corresponding to the root project output directory
     */
    protected File getRootProjectBuildDirectoryTarget(  )
    {
        return new File( getRootProject(  ).getBuild(  ).getDirectory(  ) );
    }

    /**
     * Returns the project the reactor was started from.
     *
     * The previous version walked the reactor and kept whatever project came last when no
     * execution root was found, and dereferenced null on an empty reactor.
     *
     * @return the execution root, or the current project when the reactor declares none
     */
    private MavenProject getRootProject(  )
    {
        if ( reactorProjects != null )
        {
            for ( MavenProject reactorProject : reactorProjects )
            {
                if ( reactorProject.isExecutionRoot(  ) )
                {
                    return reactorProject;
                }
            }
        }

        return project;
    }

    protected void copyBuildConfig( File targetDir ) throws MojoExecutionException
    {
        Set<Artifact> artifactSet = filterArtifacts( a -> ARTIFACT_BUILD_CONFIG.contentEquals( a.getArtifactId( ) ) );

        if ( artifactSet.isEmpty(  ) )
        {
            // The dependency is what carries the version of the scripts to deploy, so the
            // project decides it and build-config can evolve without touching this plugin.
            // It is inherited, never written by hand, hence the hint.
            throw new MojoExecutionException( "Project \"" + project.getName(  ) +
                    "\" has no \"" + ARTIFACT_BUILD_CONFIG + "\" dependency, so the SQL build scripts" +
                    " cannot be deployed to " + WEB_INF_SQL_PATH + "." +
                    " That dependency comes from lutece-global-pom : inherit from it, or declare" +
                    " fr.paris.lutece.tools:" + ARTIFACT_BUILD_CONFIG + " with scope provided." );
        }

        if ( artifactSet.size(  ) > 1 )
        {
            throw new MojoExecutionException( "Project \"" + project.getName(  ) +
                    "\" depends on several \"" + ARTIFACT_BUILD_CONFIG + "\" artifacts, so the version of the" +
                    " SQL build scripts to deploy is ambiguous : " + artifactSet );
        }

        Artifact buildConfig = artifactSet.iterator( ).next( );

        Path sqlDir = Paths.get( targetDir.getAbsolutePath( ), WEB_INF_SQL_PATH );
        sqlDir.toFile( ).mkdirs(  );

        unArchiver.setSourceFile( buildConfig.getFile(  ) );
        unArchiver.setDestDirectory( sqlDir.toFile( ) );
        unArchiver.extract( BUILD_CONFIG_PATH + ANT_PATH, sqlDir.toFile( ) );

        Path buildConfigDir = sqlDir.resolve( BUILD_CONFIG_PATH );
        Path antDir = buildConfigDir.resolve( ANT_PATH );
        try ( Stream<Path> stream = Files.walk( antDir ) )
        {
            stream.forEach( source -> {
                if ( !antDir.toFile( ).equals( source.toFile( ) ) )
                {
                    try
                    {
                        Path dest = sqlDir.resolve( antDir.relativize( source ) );
                        if ( !source.toFile( ).isDirectory( ) )
                        {
                            Files.copy( source,  dest, StandardCopyOption.REPLACE_EXISTING );
                        }
                        else if ( !dest.toFile( ).exists( ) )
                        {
                            dest.toFile( ).mkdirs(  );
                        }
                    }
                    catch ( IOException e )
                    {
                        getLog( ).warn( "Error while copying file " + source.toFile( ).getAbsolutePath( ), e );
                    }
                }
            } );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error while copying build config ", e );
        }

        try {
            org.codehaus.plexus.util.FileUtils.deleteDirectory( buildConfigDir.toFile( ) );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error while cleaning build config ", e );
        }
    }


   
    /**
     * explode sql files in WEB-INF/classes/sql directory
     * @param explodedDirectory 
     * @param targetDatabaseVendor
     * @throws MojoExecutionException
     */
    protected void explodeSqlFiles(File explodedDirectory, String targetDatabaseVendor) throws MojoExecutionException
    {
        // duplicate SQL files in target WAR classpath for liquibase
        try
        {

            List<String> listLiquibaseFileErrors = new ArrayList<>();
            List<String> listUnrecognizedUpgradeFiles = new ArrayList<>();
            final File lq_sqlSourceDir = new File(explodedDirectory, WEB_INF_SQL_PATH);
            final File lq_sqlTargetDir = new File(explodedDirectory, WEB_INF_CLASSES_SQL_PATH);

            if (!lq_sqlSourceDir.isDirectory())
            {
                getLog().info("No " + WEB_INF_SQL_PATH + " directory : no SQL file to process");

                return;
            }
            // we allow explicit override of build.properties location with this system property
            File dbProperties = new File(explodedDirectory, WEB_INF_DB_PROPERTIES_PATH);
            File buildProperties = new File(explodedDirectory, WEB_INF_BUILD_PROPERTIES_PATH);
            String buildPropertiesOverride = System.getProperty("liquibase.override.build.properties");
            if (buildPropertiesOverride != null)
                buildProperties = new File(buildPropertiesOverride);
            Function<String, String> linefilter = null;
            if (targetDatabaseVendor != null && !DATABASE_VENDOR_NONE.equals(targetDatabaseVendor))
            {
                String dbVendor = null;
                if (DATABASE_VENDOR_AUTO.equals(targetDatabaseVendor))
                    dbVendor = SqlRegexpHelper.findDbName(dbProperties);
                else if (DATABASE_VENDORS.contains(targetDatabaseVendor))
                    dbVendor = targetDatabaseVendor;
                else
                    throw new IllegalArgumentException("Unknown targetDatabaseVendor : '" + targetDatabaseVendor + "'");
                SqlRegexpHelper sqlHelper = new SqlRegexpHelper(buildProperties, dbVendor);
                linefilter = sqlHelper::filter;
                getLog().info("Processing SQL files with target " + dbVendor);
            } 
            getLog().info("Copying SQL files into " + WEB_INF_CLASSES_SQL_PATH);
            boolean needRuntimeBuildProperties = linefilter == null;// no filter here => we have to do it at run-time
            // we do not use copyDirectoryStructure since we have specific needs
            FileUtils.copyDirectoryWithFilter(lq_sqlSourceDir, lq_sqlTargetDir,
                    f -> acceptSqlFile(f, lq_sqlSourceDir, needRuntimeBuildProperties, listLiquibaseFileErrors,
                            listUnrecognizedUpgradeFiles),
                    linefilter);



            if (!listLiquibaseFileErrors.isEmpty())
            {
                getLog().error("The following SQL files are not tagged for Liquibase and have not been copied to " + WEB_INF_CLASSES_SQL_PATH + " :");
                for (String filePath : listLiquibaseFileErrors)
                {
                    getLog().error(" - " + filePath);
                }
            }

            if (!listUnrecognizedUpgradeFiles.isEmpty())
            {
                getLog().warn("The following upgrade scripts do not follow the Lutece SQL naming convention."
                        + " They have not been copied to " + WEB_INF_CLASSES_SQL_PATH
                        + " and will NOT be run by Liquibase :");
                for (String filePath : listUnrecognizedUpgradeFiles)
                {
                    getLog().warn(" - " + filePath);
                }
            }

            generateLiquibaseState(listLiquibaseFileErrors, explodedDirectory);

            

        } catch (Exception e)
        {
            // Use the same catch block for all IOExceptions, presumably the
            // exception's message will be clear enough.
            throw new MojoExecutionException("Error while copying resources", e);
        }


    }


    /**
     * Tells whether a file found in WEB-INF/sql must be copied to WEB-INF/classes/sql, where
     * Liquibase looks for it at runtime.
     *
     * @param file
     *            the candidate file
     * @param sqlSourceDir
     *            the WEB-INF/sql directory
     * @param needRuntimeBuildProperties
     *            true when build.properties must be shipped for run-time SQL processing
     * @param listLiquibaseFileErrors
     *            collects the files that are managed by Liquibase but not tagged
     * @param listUnrecognizedUpgradeFiles
     *            collects the upgrade scripts that Liquibase will not recognize
     * @return true if the file must be copied
     */
    private boolean acceptSqlFile(File file, File sqlSourceDir, boolean needRuntimeBuildProperties,
            List<String> listLiquibaseFileErrors, List<String> listUnrecognizedUpgradeFiles)
    {
        if (BUILD_PROPERTIES_FILE.equals(file.getName()))
        {
            return needRuntimeBuildProperties;
        }

        if (!file.getName().toLowerCase().endsWith(LiquiBaseSqlMojo.SQL_EXT) || file.length() == 0)
        {
            return false;
        }

        String strBasePath = sqlSourceDir.getAbsolutePath();

        if (!LiquiBaseSqlMojo.isFileManagedByLiquibase(file, strBasePath))
        {
            // The runtime changelog filter discards such a file too, so this is not a packaging
            // loss. It is only a fault when the file sits where an upgrade script is expected.
            if (LiquiBaseSqlMojo.isInUpgradeDirectory(file, strBasePath))
            {
                listUnrecognizedUpgradeFiles.add(LiquiBaseSqlMojo.getAbsoluteSqlFilePath(file, strBasePath));
            }

            return false;
        }

        return LiquiBaseSqlMojo.isTaggedWithLiquibase(file, listLiquibaseFileErrors, strBasePath);
    }

   /**
     * generate microprofile-config.properties file indicating if liquibase can run or not
     * @param listLiquibaseFileErrors
     * @param explodedDirectory
     * @throws MojoExecutionException
     */ 
  private void generateLiquibaseState(List<String> listLiquibaseFileErrors, File explodedDirectory) throws MojoExecutionException
    {
        
        
        try
        {
          
            File lq_propertiesFileDir = new File(explodedDirectory, META_INF_DIRECTORY);
            if(!lq_propertiesFileDir.exists()) 
            {
                lq_propertiesFileDir.mkdirs();
            }
            File liquibasePropertiesFile = new File(lq_propertiesFileDir, LiquiBaseSqlMojo.MICROPROFILE_CONFIG_PROPERTIES_FILE);
            if (liquibasePropertiesFile.exists())
            {
                liquibasePropertiesFile.delete();
            }
               getLog().info("Generating file " + liquibasePropertiesFile.getAbsolutePath());
                StringBuilder sb = new StringBuilder();
                sb.append("# Generated file - do not edit\n");
                sb.append("liquibase.readyToRun="+listLiquibaseFileErrors.isEmpty()+"\n");
                sb.append("# Lists SQL files not managed by Liquibase\n");
                sb.append("liquibase.fileErrors=");
                if (!listLiquibaseFileErrors.isEmpty())
                {
                    listLiquibaseFileErrors.forEach(x-> sb.append(x).append(","));
                    //remove last ,
                    if (sb.charAt(sb.length() - 1) == ',')
                    {
                        sb.deleteCharAt(sb.length() - 1);
                    }
                }
              sb.append("\n"); 
             
              Files.write(liquibasePropertiesFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));    
        }
        
        catch (Exception e)
        {
            throw new MojoExecutionException("Error while generating " + LiquiBaseSqlMojo.MICROPROFILE_CONFIG_PROPERTIES_FILE + " file", e);
        }
    }


    
}
