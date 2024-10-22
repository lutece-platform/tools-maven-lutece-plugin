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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Goal which cleans the build.
 * Especially for the multi-modules projects.
 *
 * <P>
 * This attempts to clean a project's working directory of the files that were
 * generated at build-time. By default, it discovers and deletes the directories
 * configured in <code>project.build.directory</code>,
 * <code>project.build.outputDirectory</code>,
 * <code>project.build.testOutputDirectory</code>, and
 * <code>project.reporting.outputDirectory</code>.
 * </P>
 *
 * <P>
 * Files outside the default may also be included in the deletion by configuring
 * the <code>filesets</code> tag.
 * </P>
 *
 *
 */
@Mojo( name = "clean" )
public class CleanMojo
    extends AbstractLuteceWebappMojo
{
    /**
     * This is where compiled test classes go.
     */
    @Parameter(defaultValue = "${project.build.directory}/test-classes", required = true, readonly = true)
    private File testOutputDirectory;

    /**
     * This is where the site plugin generates its pages.
     */
    @Parameter(defaultValue = "${project.build.directory}/site", required = true, readonly = true)
    private File reportDirectory;

    /**
     * Sets whether the plugin runs in verbose mode.
     */
    @Parameter(property = "clean.verbose", defaultValue = "false")
    private boolean verbose;

    /**
     * The list of fileSets to delete, in addition to the default directories.
     */
    @Parameter
    private List<FileSet> filesets;

    /**
     * Sets whether the plugin should follow Symbolic Links to delete files.
     */
    @Parameter(property = "clean.followSymLinks", defaultValue = "false")
    private boolean followSymLinks;

    /**
     * Finds and retrieves included and excluded files, and handles their
     * deletion
     */
    private FileSetManager fileSetManager;

    /**
     * Deletes file-sets in the following project build directory order:
     * (source) directory, output directory, test directory, report directory,
     * and then the additional file-sets.
     *
     * @see org.apache.maven.plugin.Mojo#execute()
     * @throws MojoExecutionException
     *             When
     */
    public void execute(  )
                 throws MojoExecutionException
    {
        // for remove the global target before the modules target
        if ( ( reactorProjects.size(  ) > 1 ) && ( reactorProjects.indexOf( project ) == 0 ) )
        {
            //Delete the global project target
            fileSetManager = new FileSetManager( );
            removeDirectory( getRootProjectBuildDirectoryTarget(  ) );
            removeAdditionalFilesets(  );
        }
        if ( ! POM_PACKAGING.equals( project.getPackaging(  ) ) ) // For not delete the global target after the modules target
        {
            //delete the current project target
            fileSetManager = new FileSetManager( );
            removeDirectory( classesDirectory );
            removeDirectory( testOutputDirectory );
            removeDirectory( reportDirectory );
            removeDirectory( outputDirectory );
            removeAdditionalFilesets(  );
        }
    }

    /**
     * Deletes additional file-sets specified by the <code>filesets</code>
     * tag.
     *
     * @throws MojoExecutionException
     *             When a directory failed to get deleted.
     */
    private void removeAdditionalFilesets(  )
                                   throws MojoExecutionException
    {
        if ( ( filesets != null ) && ! filesets.isEmpty(  ) )
        {
            for ( Iterator<FileSet> it = filesets.iterator(  ); it.hasNext(  ); )
            {
                FileSet fileset = (FileSet) it.next(  );

                try
                {
                    getLog(  ).info( "Deleting " + fileset );

                    fileSetManager.delete( fileset );
                } catch ( IOException e )
                {
                    throw new MojoExecutionException( "Failed to delete directory: " + fileset.getDirectory(  ) +
                                                      ". Reason: " + e.getMessage(  ), e );
                }
            }
        }
    }

    /**
     * Deletes a directory and its contents.
     *
     * @param dir
     *            The base directory of the included and excluded files.
     * @throws MojoExecutionException
     *             When a directory failed to get deleted.
     */
    private void removeDirectory( File dir )
                          throws MojoExecutionException
    {
        if ( dir != null )
        {
            if ( ! dir.exists(  ) )
            {
                return;
            }

            if ( ! dir.isDirectory(  ) )
            {
                throw new MojoExecutionException( dir + " is not a directory." );
            }

            FileSet fs = new FileSet(  );
            fs.setDirectory( dir.getPath(  ) );
            fs.addInclude( "**/**" );
            fs.setFollowSymlinks( followSymLinks );

            try
            {
                getLog(  ).info( "Deleting directory " + dir.getAbsolutePath(  ) );
                fileSetManager.delete( fs );
            } catch ( IOException e )
            {
                throw new MojoExecutionException( "Failed to delete directory: " + dir + ". Reason: " +
                                                  e.getMessage(  ), e );
            } catch ( IllegalStateException e )
            {
                // TODO: IOException from plexus-utils should be acceptable here
                throw new MojoExecutionException( "Failed to delete directory: " + dir + ". Reason: " +
                                                  e.getMessage(  ), e );
            }
        }
    }

    /**
     * Sets the project build test output directory.
     *
     * @param newTestOutputDirectory
     *            The project build test output directory to set.
     */
    protected void setTestOutputDirectory( File newTestOutputDirectory )
    {
        this.testOutputDirectory = newTestOutputDirectory;
    }

    /**
     * Sets the project build report directory.
     *
     * @param newReportDirectory
     *            The project build report directory to set.
     */
    protected void setReportDirectory( File newReportDirectory )
    {
        this.reportDirectory = newReportDirectory;
    }

    /**
     * Adds a file-set to the list of file-sets to clean.
     *
     * @param fileset
     *            the fileset
     */
    public void addFileset( FileSet fileset )
    {
        if ( filesets == null )
        {
            filesets = new LinkedList<FileSet>(  );
        }

        filesets.add( fileset );
    }
}
