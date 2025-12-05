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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
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
import org.w3c.dom.Document;

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
    * The outdatedCheckPath
    */
    @Parameter(
            defaultValue = "${WEB-INF/lib/}")
    private String outdatedCheckPath;

    /**
     * The list of webResources we want to transfer.
     */
    @Parameter
    private Resource[] webResources;
  

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
                // Copy project-specific webapp components
                if ( ! isUpdate )
                {
                    // First deployment : copy all files, in case
                    // project-specific files are meant to overwrite files from
                    // the core or the plugins.
                    FileUtils.copyDirectoryStructure( webappSourceDirectory, targetDir );

                    if ( FileUtils.getNbFileCopy(  ) == 0 )
                    {
                        getLog(  ).info( "Nothing to copy - all webapp files are up to date" );
                    } else
                    {
                        // we can't know how many file has been copied anymore
                        // due to plexus fileUtils
                        getLog(  ).info( "Copying webapp files" );
                    }

                    FileUtils.setNbFileCopy( 0 );
                } else
                {
                    // This time only overwrite newer files, since we are sure
                    // that all files common with the core have either been
                    // overwritten at webapp creation, or are older
                    FileUtils.copyDirectoryStructureIfModified( webappSourceDirectory, targetDir );

                    if ( FileUtils.getNbFileModified(  ) == 0 )
                    {
                        getLog(  ).info( "Nothing to update - all webapp files are up to date" );
                    } else
                    {
                        getLog(  ).info( "Copying " + FileUtils.getNbFileModified(  ) + " webapp files" );
                    }

                    FileUtils.setNbFileModified( 0 );
                }
            }

            // Copy SQL files
            if ( ! isInplace && sqlDirectory.exists(  ) )
            {
                getLog(  ).debug( "Copying SQL files from " + sqlDirectory.getAbsolutePath(  ) );

                File sqlTargetDir = new File( targetDir, WEB_INF_SQL_PATH );

                if ( ! isUpdate )
                {
                    FileUtils.copyDirectoryStructure( sqlDirectory, sqlTargetDir );

                    if ( FileUtils.getNbFileCopy(  ) == 0 )
                    {
                        getLog(  ).info( "Nothing to copy - all sql files are up to date" );
                    } else
                    {
                        getLog(  ).info( "Copying " + FileUtils.getNbFileCopy(  ) + " sql files" );
                    }

                    FileUtils.setNbFileCopy( 0 );
                } else
                {
                    // This time only overwrite newer files, since we are sure
                    // that all files common with the core have either been
                    // overwritten at webapp creation, or are older
                    FileUtils.copyDirectoryStructureIfModified( sqlDirectory, sqlTargetDir );

                    if ( FileUtils.getNbFileModified(  ) == 0 )
                    {
                        getLog(  ).info( "Nothing to update - all sql files are up to date" );
                    } else
                    {
                        getLog(  ).info( "Copying " + FileUtils.getNbFileModified(  ) + " sql files" );
                    }

                    FileUtils.setNbFileModified( 0 );
                }
            }

            //Copy Site User files
            if ( ! isInplace && siteDirectory.exists(  ) )
            {
                getLog(  ).debug( "Copying Site User files from " + siteDirectory.getAbsolutePath(  ) );

                File siteUserTargetDir = new File( targetDir, WEB_INF_DOC_XML_PATH );

                if ( ! isUpdate )
                {
                    FileUtils.copyDirectoryStructure( siteDirectory, siteUserTargetDir );

                    if ( FileUtils.getNbFileCopy(  ) == 0 )
                    {
                        getLog(  ).info( "Nothing to copy - all site user files are up to date" );
                    } else
                    {
                        getLog(  ).info( "Copying " + FileUtils.getNbFileCopy(  ) + " site user files" );
                    }

                    FileUtils.setNbFileCopy( 0 );
                } else
                {
                    // This time only overwrite newer files, since we are sure
                    // that all files common with the core have either been
                    // overwritten at webapp creation, or are older
                    FileUtils.copyDirectoryStructureIfModified( siteDirectory, siteUserTargetDir );

                    if ( FileUtils.getNbFileModified(  ) == 0 )
                    {
                        getLog(  ).info( "Nothing to update - all site user files are up to date" );
                    } else
                    {
                        getLog(  ).info( "Copying " + FileUtils.getNbFileModified(  ) + " site user files" );
                    }

                    FileUtils.setNbFileModified( 0 );
                }
            }

            // Copy compiled classes
            if ( classesDirectory.exists(  ) )
            {
                File classesDir = new File( targetDir, "WEB-INF/classes" );
                classesDir.mkdirs(  );
                FileUtils.copyDirectoryStructureIfModified( classesDirectory, classesDir );

                if ( FileUtils.getNbFileModified(  ) == 0 )
                {
                    getLog(  ).info( "Nothing to update - all classe and resource files are up to date" );
                } else
                {
                    getLog(  ).info( "Copying " + FileUtils.getNbFileModified(  ) + " classe and resource files" );
                }

                FileUtils.setNbFileModified( 0 );
            }
        } catch ( IOException e )
        {
            // Use the same catch block for all IOExceptions, presumably the
            // exception's message will be clear enough.
            throw new MojoExecutionException( "Error while copying resources", e );
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
        } else if ( ( cores == null ) || cores.isEmpty(  ) || ( cores.size(  ) > 1 ) )
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

        // Filter jar artifacts in scope 'compile' or 'runtime'
        ArtifactFilter thirdPartyFilter =
            new ArtifactFilter(  )
            {
                @Override
                public boolean include( Artifact artifact )
                {
                    return ( "jar".equals( artifact.getType(  ) ) &&
                           (
                               Artifact.SCOPE_RUNTIME.equals( artifact.getScope(  ) ) ||
                               Artifact.SCOPE_COMPILE.equals( artifact.getScope(  ) )
                            ) );
                }
            };

        // if multi project
        if ( ( reactorProjects.size(  ) > 1 ) && ! project.isExecutionRoot(  ) )
        {
            //add dependencies of modules for filter duplicate entry
            multiProjectArtifacts.addAll( filterArtifacts( thirdPartyFilter ) );
        } else
        { // no in multi project

            Set thirdPartyJARs = filterArtifacts( thirdPartyFilter );

            for ( Iterator iterJARs = thirdPartyJARs.iterator(  ); iterJARs.hasNext(  ); )
            {
                Artifact artifact = (Artifact) iterJARs.next(  );
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
        MavenProject mp = null;

        for ( Object o : reactorProjects )
        {
            mp = (MavenProject) o;

            if ( mp.isExecutionRoot(  ) )
            {
                break;
            }
        }

        return new File( mp.getBuild(  ).getDirectory(  ) + File.separatorChar + LUTECE_DIRECTORY );
    }

    /**
     * Used for multiproject, return the output directory ( /target ) of root project.
     *
     * @return File corresponding to the root project output directory
     */
    protected File getRootProjectBuildDirectoryTarget(  )
    {
        MavenProject mp = null;

        for ( Object o : reactorProjects )
        {
            mp = (MavenProject) o;

            if ( mp.isExecutionRoot(  ) )
            {
                break;
            }
        }

        return new File( mp.getBuild(  ).getDirectory(  ) );
    }

    protected void copyBuildConfig( File targetDir ) throws MojoExecutionException
    {
        Set<Artifact> artifactSet = filterArtifacts( a -> ARTIFACT_BUILD_CONFIG.contentEquals( a.getArtifactId( ) ) );

        if ( artifactSet == null || artifactSet.isEmpty(  ) || artifactSet.size(  ) > 1 )
        {
            throw new MojoExecutionException( "Project \"" + project.getName(  ) +
                                              "\" must have exactly one dependency named " + ARTIFACT_BUILD_CONFIG );
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
            File lq_sqlSourceDir = new File(explodedDirectory, WEB_INF_SQL_PATH);
            File lq_sqlTargetDir = new File(explodedDirectory, WEB_INF_CLASSES_SQL_PATH);
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
                    f -> (f.getName().equals("build.properties") && needRuntimeBuildProperties)
                            || (f.getName().toLowerCase().endsWith(LiquiBaseSqlMojo.SQL_EXT) && f.length() > 0 &&    LiquiBaseSqlMojo.isTaggedWithLiquibase(f, listLiquibaseFileErrors)),
                    linefilter);



            if (!listLiquibaseFileErrors.isEmpty())
            {
                getLog().warn("The following SQL files are not tagged for Liquibase and have not been copied to " + WEB_INF_CLASSES_SQL_PATH + " :");
                for (String filePath : listLiquibaseFileErrors)
                {
                    getLog().info(" - " + filePath);
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
     * generate microprofile-config.properties file indicating if liquibase can run or not
     * @param listLiquibaseFileErrors
     * @param explodedDirectory
     * @throws MojoExecutionException
     */ 
  private void generateLiquibaseState(List<String> listLiquibaseFileErrors, File explodedDirectory) throws MojoExecutionException
    {
        
        
        try
        {
            File lq_sqlTargetDir = new File(explodedDirectory, WEB_INF_CLASSES_SQL_PATH);
            File liquibasePropertiesFile = new File(lq_sqlTargetDir, LiquiBaseSqlMojo.MICROPROFILE_CONFIG_PROPERTIES_FILE);
            if (liquibasePropertiesFile.exists())
            {
                liquibasePropertiesFile.delete();
            }
               getLog().info("Generating " + LiquiBaseSqlMojo.MICROPROFILE_CONFIG_PROPERTIES_FILE + " file");
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
             
              Files.write(liquibasePropertiesFile.toPath(), sb.toString().getBytes());    
        }
        
        catch (Exception e)
        {
            throw new MojoExecutionException("Error while generating " + LiquiBaseSqlMojo.MICROPROFILE_CONFIG_PROPERTIES_FILE + " file", e);
        }
    }


    
}
