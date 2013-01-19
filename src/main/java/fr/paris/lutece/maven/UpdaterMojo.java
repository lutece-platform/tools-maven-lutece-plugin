package fr.paris.lutece.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.DirectoryScanner;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Assembly zips for Lutece core or plugin project.<br/> If you wish to force
 * webapp re-creation (for instance, if you changed the version of a
 * dependency), call the <code>clean</code> phase before this goal.
 *
 * @goal updater
 * @execute phase="process-classes"
 * @requiresDependencyResolution compile
 */
public class UpdaterMojo
    extends AbstractLuteceWebappMojo
{
    private static final String MINIMAL_VERSION = "0.0.0";
    private static final String WEB_INF_LIB_PATH = "webapp/WEB-INF/lib/";
    private static final String SQL_FOLDER_PATH = "sql/";
    protected static final String EXCLUDE_PATTERN_SRC = "**/src/**";
    protected static final String EXCLUDE_PATTERN_SETTINGS = "**/.settings/**";

    // The path to the classes directory
    private static final String WEB_INF_CLASSES_PATH = "webapp/WEB-INF/classes/";

    /**
     * The directory containing the default configuration files.
     *
     * @parameter expression="${basedir}"
     * @required
     */
    protected File baseDirectory;
    private static final String JUNIT = "junit";
    private static final String SERVELT_API = "servlet-api";
    protected static final String[] ASSEMBLY_WEBAPP_EXCLUDES_UPDATER =
        new String[]
        {
            EXCLUDE_PATTERN_CLASSES, EXCLUDE_PATTERN_LIB, EXCLUDE_PATTERN_SVN, EXCLUDE_PATTERN_TARGET,
            EXCLUDE_PATTERN_SRC, EXCLUDE_PATTERN_SETTINGS, "*.*"
        };
    protected static final String INCLUDE_PATTERN_RESOURCES = "**/fr/paris/lutece/**/resources/**";
    private static final String[] RELEASE_INCLUDES =
        new String[] { "**/sql/**", "**/core/**/init*", "**/plugin/**/create*" };
    private static final String[] RELEASE_EXCLUDES = new String[] { "**/.svn/**", "**/upgrades/**" };

    /**
     * The project's output directory.
     *
     * @parameter expression="${updOutputDirectory}"
     */
    protected File updOutputDirectory;

    /**
     * To look up Archiver/UnArchiver implementations
     *
     * @component
     */
    private ArchiverManager archiverManager;

    /**
     * Executes the mojo on the current project.
     *
     * @throws MojoExecutionException if an error occurred while building the artifact.
     */
    public void execute(  )
                 throws MojoExecutionException, MojoFailureException
    {
        if ( ! LUTECE_CORE_PACKAGING.equals( project.getPackaging(  ) ) &&
                 ! LUTECE_PLUGIN_PACKAGING.equals( project.getPackaging(  ) ) )
        {
            throw new MojoExecutionException( "This goal can be invoked only on a " + LUTECE_CORE_PACKAGING + " or " +
                                              LUTECE_PLUGIN_PACKAGING + " project." );
        }

        String strVersion = getVersion(  );

        // Build the release package
        String[] releaseSqlFiles = getSqlFiles( RELEASE_INCLUDES, RELEASE_EXCLUDES );
        assemblyBinaries( releaseSqlFiles, "release-" + strVersion );

        // Build upgrades packages corresponding to sql upgrades
        String[] includes = new String[] { "**/upgrades/**/*" + strVersion + ".sql" };
        String[] upgradeSqlFiles = getSqlFiles( includes, null );

        for ( int i = 0; i < upgradeSqlFiles.length; i++ )
        {
            String strVersionFrom = getVersionFrom( upgradeSqlFiles[i] );

            if ( strVersionFrom != null )
            {
                String[] files = new String[] { upgradeSqlFiles[i] };
                assemblyBinaries( files, strVersionFrom + "-" + strVersion );
            }
        }

        // Build simple upgrade package with no sql upgrade
        assemblyBinaries( new String[] {  },
                          "upgrade-" + strVersion );
    }

    private File getOutputDirectory(  )
    {
        String strPath = "";

        if ( ( updOutputDirectory == null ) || "".equals( updOutputDirectory ) )
        {
            strPath = outputDirectory.getAbsolutePath(  );
        } else
        {
            strPath = updOutputDirectory.getAbsolutePath(  );
        }

        strPath += ( File.separatorChar + project.getArtifactId(  ) );

        return new File( strPath );
    }

    private String[] getSqlFiles( String[] includes, String[] excludes )
    {
        DirectoryScanner scanner = new DirectoryScanner(  );
        scanner.setBasedir( sqlDirectory.getParentFile(  ) );

        scanner.setIncludes( includes );

        if ( excludes != null )
        {
            scanner.setExcludes( excludes );
        }

        scanner.scan(  );

        return scanner.getIncludedFiles(  );
    }

    private String getVersion(  )
    {
        String strVersion = project.getArtifact(  ).getVersion(  );

        if ( strVersion.endsWith( "-SNAPSHOT" ) )
        {
            strVersion =
                strVersion.substring( 0,
                                      strVersion.indexOf( "-SNAPSHOT" ) );
            getLog(  ).warn( "**** updater packages should not be assembly from SNAPSHOT ****" );
        }

        return strVersion;
    }

    private String getVersionFrom( String strFilename )
    {
        if ( ( strFilename.indexOf( "-" ) == -1 ) || ( strFilename.lastIndexOf( "-" ) == -1 ) )
        {
            // there is an error in the file name
            getLog(  ).warn( "Error in fileName : " + strFilename );

            return null;
        }

        return strFilename.substring( strFilename.indexOf( "-" ) + 1,
                                      strFilename.lastIndexOf( "-" ) );
    }

    /**
     * Create a zip with binaries files.
     *
     * @throws MojoExecutionException
     *             if an error occurred while building the artifact.
     */
    private synchronized void assemblyBinaries( String[] filenames, String strZipVersion )
                                        throws MojoExecutionException
    {
        try
        {
            // Get the project type
            String projectType = project.getArtifact(  ).getType(  );

            Archiver archiver = archiverManager.getArchiver( "jar" );

            //MavenArchiver archiver = new MavenArchiver();

            // Create the jar file, containing compiled classes
            File jarFile = getArchiveFile( null,
                                           false,
                                           "jar",
                                           project.getArtifact(  ).getVersion(  ) );

            //archiver.setArchiver(jarArchiver);
            archiver.setDestFile( jarFile );

            if ( ! classesDirectory.exists(  ) )
            {
                getLog(  ).warn( "Could not find classes directory " + classesDirectory.getAbsolutePath(  ) );
            } else
            {
                archiver.addDirectory( classesDirectory, PACKAGE_CLASSES_INCLUDES, PACKAGE_CLASSES_EXCLUDES );
            }

            archiver.createArchive(  );

            archiver = archiverManager.getArchiver( "zip" );

            // Create the final zip file
            File webappZip = null;
            webappZip = getArchiveFile( ( LUTECE_CORE_TYPE.equals( projectType ) ? "war" : "upd" ), false, "zip",
                                        strZipVersion );

            archiver.setDestFile( webappZip );

            /*zipBinArchiver.reset();
            zipBinArchiver.setCompress(true);
            zipBinArchiver.setForced(false);
            zipBinArchiver.setDestFile(webappZip);*/
            if ( LUTECE_PLUGIN_TYPE.equals( projectType ) )
            {
                /**
                 * Add the sql directory
                 */
                for ( int i = 0; i < filenames.length; i++ )
                {
                    File f = new File( sqlDirectory.getParentFile(  ),
                                       filenames[i] );
                    archiver.addFile( f, SQL_FOLDER_PATH + f.getName(  ) );
                }

                // Add the resource files
                /*
                * if (resourcesDirectory.exists()) {
                * zipBinArchiver.addDirectory(resourcesDirectory,
                * ASSEMBLY_WEBAPP_INCLUDES, ASSEMBLY_WEBAPP_EXCLUDES); }
                */

                // Add the webapp files
                if ( webappSourceDirectory.exists(  ) )
                {
                    archiver.addDirectory( webappSourceDirectory.getParentFile(  ),
                                           ASSEMBLY_WEBAPP_INCLUDES,
                                           ASSEMBLY_WEBAPP_EXCLUDES_UPDATER );
                }

                // add the Classes resources directories to the archive
                if ( classesDirectory.exists(  ) )
                {
                    archiver.addDirectory( classesDirectory, WEB_INF_CLASSES_PATH, PACKAGE_WEBAPP_RESOURCES_INCLUDE,
                                           PACKAGE_WEBAPP_RESOURCES_EXCLUDES );
                }

                // Add the site user directories to WEB-INF/doc/xml/ folder
                if ( siteDirectory.exists(  ) )
                {
                    archiver.addDirectory( siteDirectory, WEB_INF_DOC_XML_PATH, ASSEMBLY_WEBAPP_SITE_INCLUDES,
                                           ASSEMBLY_WEBAPP_SITE_EXCLUDES );
                }

                // Add jar
                archiver.addFile( jarFile, WEB_INF_LIB_PATH + jarFile.getName(  ) );

                // Add the dependency libraries
                for ( File f : getDependentJars(  ) )
                {
                    if ( ( null != f ) && f.exists(  ) )
                    {
                        archiver.addFile( f, WEB_INF_LIB_PATH + f.getName(  ) );
                    }
                }
            }

            // Finaly build the zip file.
            archiver.createArchive(  );

            // Delete temp files
            if ( jarFile.exists(  ) )
            {
                jarFile.delete(  );
            }
        } catch ( Exception e )
        {
            throw new MojoExecutionException( "Error assembling ZIP", e );
        }
    }

    /**
     * Builds the name of the destination ZIP file with a timestamp if
     * necessary.
     *
     * @param classifier
     *            The type of target appears in the name (bin or src)
     * @param timestamp
     *            Tell if the file name must contain timestamp.
     * @param extension
     *            The extension of tager file.
     * @return new File build with params.
     */
    private File getArchiveFile( String classifier, boolean timestamp, String extension, String strZipVersion )
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat( "yyMMdd-hhmm" );
        dateFormat.format( new Date(  ) ).toString(  );

        return new File( getOutputDirectory(  ),
                         project.getArtifactId(  ) + ( ( null != classifier ) ? ( "-" + classifier ) : "" ) + "-" +
                         strZipVersion + ( 
                                             timestamp ? ( "-" + dateFormat.format( new Date(  ) ).toString(  ) ) : ""
                                          ) + "." + extension );
    }

    /**
     * Get the collection of non lutece-core and non lutece-plugin jars.
     *
     * @return Collection of jar
     */
    @SuppressWarnings( "unchecked" )
    private Collection<File> getDependentJars(  )
    {
        HashSet<File> result = new HashSet<File>(  );

        // Direct dependency artifacts of project
        Set<Artifact> resultArtifact = new HashSet<Artifact>(  );

        for ( Object o : project.getDependencyArtifacts(  ) )
        {
            Artifact a = null;

            try
            {
                a = (Artifact) o;
            } catch ( ClassCastException e )
            {
                e.printStackTrace(  );

                continue;
            }

            if ( ! LUTECE_CORE_TYPE.equals( a.getType(  ) ) &&
                     ! LUTECE_PLUGIN_TYPE.equals( a.getType(  ) ) &&
                     ! Artifact.SCOPE_PROVIDED.equals( a.getScope(  ) ) &&
                     ! Artifact.SCOPE_TEST.equals( a.getScope(  ) ) )
            {
                resultArtifact.add( a );
                result.add( a.getFile(  ) );
            }
        }

        // add the transitive dependency
        ArtifactResolutionResult artifactResolutionResult = null;

        try
        {
            artifactResolutionResult =
                resolver.resolveTransitively( resultArtifact,
                                              project.getArtifact(  ),
                                              remoteRepositories,
                                              localRepository,
                                              metadataSource );
        } catch ( ArtifactResolutionException e )
        {
            e.printStackTrace(  );
        } catch ( ArtifactNotFoundException e )
        {
            e.printStackTrace(  );
        }

        for ( Object o : artifactResolutionResult.getArtifacts(  ) )
        {
            Artifact a = null;

            try
            {
                a = (Artifact) o;
            } catch ( ClassCastException e )
            {
                e.printStackTrace(  );

                continue;
            }

            if ( ! Artifact.SCOPE_PROVIDED.equals( a.getScope(  ) ) &&
                     ! Artifact.SCOPE_TEST.equals( a.getScope(  ) ) // for transitively dependencies artifact are not a good
                                                                        // scope ( junit and servlet-api )
                      &&
                     ! JUNIT.equals( a.getArtifactId(  ) ) &&
                     ! SERVELT_API.equals( a.getArtifactId(  ) ) )
            {
                result.add( a.getFile(  ) );
            }
        }

        return result;
    }
}
