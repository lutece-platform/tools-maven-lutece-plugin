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

import org.codehaus.plexus.util.IOUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Utility class to manipulate files.<br>
 * Note : these methods were copied from plexus-utils 1.2 because :
 * <ul>
 * <li>Maven currently uses plexus-utils version 1.1, which doesn't contain
 * <code>copyDirectoryStructureIfModified</code>;</li>
 * <li>Plexus methods do not provide any exclusion method, and we need to
 * exlude .svn directories.</li>
 * </ul>
 */
public class FileUtils
{
    // The name of subversion's administrative directories
    private static final String SVN_DIRECTORY = ".svn";

    // Regexps filtering out the site sources. They are matched against paths normalized with
    // "/" separators : written with "\\" they only ever matched on Windows, so the archives
    // produced on Linux and on Windows did not have the same content.
    protected static final String REGEXP_SITE_XDOC_XML = "(.)*/xdoc/[^/]*\\.(.)*";
    protected static final String REGEXP_SITE_RESOURCES_XML = "(.)*/resources/images/[^/]*\\.(.)*";
    protected static final String REGEXP_SITE_TECH = "(.)*/tech(/.)*";
    protected static final String REGEXP_SITE_TECH_DIRECTORY = "(.)*/tech";
    protected static final String REGEXP_SITE_XML = "(.)*site/site(.)*.xml";
    /**
     * Tells whether a file is a site source that must not be copied as-is : those are processed
     * by the site plugin.
     *
     * @param file
     *            the candidate file
     * @return true if the file must be skipped
     */
    static boolean isExcludedSiteFile( File file )
    {
        return isExcludedSiteFile( file.getAbsolutePath(  ) );
    }

    /**
     * Tells whether an absolute path denotes a site source. The path is normalized to "/"
     * separators first, so that the result is the same on Windows and on unix systems.
     *
     * @param strAbsolutePath
     *            the candidate absolute path
     * @return true if the file must be skipped
     */
    static boolean isExcludedSiteFile( String strAbsolutePath )
    {
        String strPath = strAbsolutePath.replace( '\\', '/' );

        return strPath.matches( REGEXP_SITE_XML ) || strPath.matches( REGEXP_SITE_TECH ) ||
               strPath.matches( REGEXP_SITE_XDOC_XML ) || strPath.matches( REGEXP_SITE_RESOURCES_XML );
    }

    /**
     * Tells whether a directory must not be copied : subversion administrative directories and
     * the site "tech" directory.
     *
     * @param directory
     *            the candidate directory
     * @return true if the directory must be skipped
     */
    static boolean isExcludedSiteDirectory( File directory )
    {
        return SVN_DIRECTORY.equals( directory.getName(  ) ) ||
               isExcludedSiteDirectory( directory.getAbsolutePath(  ) );
    }

    /**
     * Tells whether an absolute path denotes the site "tech" directory, whatever the platform
     * separator.
     *
     * @param strAbsolutePath
     *            the candidate absolute path
     * @return true if the directory must be skipped
     */
    static boolean isExcludedSiteDirectory( String strAbsolutePath )
    {
        return strAbsolutePath.replace( '\\', '/' ).matches( REGEXP_SITE_TECH_DIRECTORY );
    }

    /**
     * Copies an entire directory structure but only source files with timestamp
     * later than the destinations'.
     *
     * Note:
     * <ul>
     * <li>It will include empty directories.
     * <li>The <code>sourceDirectory</code> must exists.
     * </ul>
     *
     * @param sourceDirectory
     *            the source directory.
     * @param destinationDirectory
     *            the destination directory.
     * @throws IOException
     *             if an I/O exception occurs.
     */
    public static int copyDirectoryStructureIfModified( File sourceDirectory, File destinationDirectory )
                                                 throws IOException
    {
        if ( ! sourceDirectory.exists(  ) )
        {
            throw new IOException( "Source directory doesn't exists (" + sourceDirectory.getAbsolutePath(  ) + ")." );
        }

        File[] files = sourceDirectory.listFiles(  );

        if ( files == null )
        {
            throw new IOException( "Could not list directory (" + sourceDirectory.getAbsolutePath(  ) + ")." );
        }

        int nCopied = 0;
        String sourcePath = sourceDirectory.getAbsolutePath(  );

        for (File file : files) {
            String dest = file.getAbsolutePath(  );

            dest = dest.substring( sourcePath.length(  ) + 1 );

            File destination = new File( destinationDirectory, dest );

            if ( file.isFile(  ) )
            {
                destination = destination.getParentFile(  );

                if ( ! isExcludedSiteFile( file ) )
                {
                    if ( copyFileToDirectoryIfModified( file, destination ) )
                    {
                        nCopied++;
                    }
                }
            } else if ( file.isDirectory(  ) )
            {
                // Exclude SVN administrative directories and site sources
                if ( ! isExcludedSiteDirectory( file ) )
                {
                    if ( ! destination.exists(  ) && ! destination.mkdirs(  ) )
                    {
                        throw new IOException( "Could not create destination directory '" +
                                               destination.getAbsolutePath(  ) + "'." );
                    }

                    nCopied += copyDirectoryStructureIfModified( file, destination );
                }
            } else
            {
                throw new IOException( "Unknown file type: " + file.getAbsolutePath(  ) );
            }
        }

        return nCopied;
    }

    /**
     * Copies an entire directory structure.
     *
     * Note:
     * <ul>
     * <li>It will include empty directories.
     * <li>The <code>sourceDirectory</code> must exists.
     * </ul>
     *
     * @param sourceDirectory
     *            the source directory.
     * @param destinationDirectory
     *            the destination directory.
     * @throws IOException
     *             if an I/O exception occurs.
     */
    public static int copyDirectoryStructure( File sourceDirectory, File destinationDirectory )
                                                 throws IOException
    {
        if ( ! sourceDirectory.exists(  ) )
        {
            throw new IOException( "Source directory doesn't exists (" + sourceDirectory.getAbsolutePath(  ) + ")." );
        }

        File[] files = sourceDirectory.listFiles(  );

        if ( files == null )
        {
            throw new IOException( "Could not list directory (" + sourceDirectory.getAbsolutePath(  ) + ")." );
        }

        int nCopied = 0;
        String sourcePath = sourceDirectory.getAbsolutePath(  );

        for (File file : files) {
            String dest = file.getAbsolutePath(  );

            dest = dest.substring( sourcePath.length(  ) + 1 );

            File destination = new File( destinationDirectory, dest );

            if ( file.isFile(  ) )
            {
                destination = destination.getParentFile(  );

                if ( ! isExcludedSiteFile( file ) )
                {
                    org.codehaus.plexus.util.FileUtils.copyFileToDirectory( file, destination );
                    nCopied++;
                }
            } else if ( file.isDirectory(  ) )
            {
                // Exclude SVN administrative directories and site sources
                if ( ! isExcludedSiteDirectory( file ) )
                {
                    if ( ! destination.exists(  ) && ! destination.mkdirs(  ) )
                    {
                        throw new IOException( "Could not create destination directory '" +
                                               destination.getAbsolutePath(  ) + "'." );
                    }

                    nCopied += copyDirectoryStructure( file, destination );
                }
            } else
            {
                throw new IOException( "Unknown file type: " + file.getAbsolutePath(  ) );
            }
        }

        return nCopied;
    }

    /**
     * Copies an entire directory structure, with the possibility to filter files and lines
     *
     * Note:
     * <ul>
     * <li>It will NOT include empty directories.
     * <li>The <code>sourceDirectory</code> must exists.
     * </ul>
     *
     * @param sourceDirectory
     *            the source directory.
     * @param destinationDirectory
     *            the destination directory.
     * @param fileFilter
     *            the file filter (true copies the file, false ignores it).
     * @param linefilter
     *            the line filter (takes a source line and outputs a possibly modified line).
     * @throws IOException
     *             if an I/O exception occurs.
     */
    public static void copyDirectoryWithFilter( File sourceDirectory, File destinationDirectory, Function<File, Boolean> fileFilter, Function<String, String> linefilter)
            throws IOException
    {
    	Path source = sourceDirectory.toPath();
    	Path target = destinationDirectory.toPath();
        Files.walkFileTree(source, new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
            {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                if (fileFilter.apply(file.toFile()))
                {
                    // parent directory is only created if needed
                    Files.createDirectories(target.resolve(source.relativize(file.getParent()).toString()));
                    copyFileWithLineFilter(file, target.resolve(source.relativize(file).toString()), linefilter);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Copies a file from source to destination, applying a filter for each line
     * 
     * @param sourceFile      the source file
     * @param destinationFile the destination file (always written to)
     * @throws IOException if anything goes wrong
     */
    public static void copyFileWithLineFilter(Path sourceFile, Path destinationFile, Function<String, String> linefilter) throws IOException
    {
        if (linefilter == null)
            Files.copy(sourceFile, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        else
            try (Stream<String> lines = Files.lines(sourceFile); BufferedWriter writer = Files.newBufferedWriter(destinationFile))
            {
                for (String line : (Iterable<String>) lines::iterator)// avoid a clumsy loop with try/catch
                    writer.append(linefilter.apply(line)).append('\n');
            }
    }

    /**
     * Copy file from source to destination only if source is newer than the
     * target file. If <code>destinationDirectory</code> does not exist, it
     * (and any parent directories) will be created. If a file
     * <code>source</code> in <code>destinationDirectory</code> exists, it
     * will be overwritten.
     *
     * @param source
     *            An existing <code>File</code> to copy.
     * @param destinationDirectory
     *            A directory to copy <code>source</code> into.
     *
     * @throws java.io.FileNotFoundException
     *             if <code>source</code> isn't a normal file.
     * @throws IllegalArgumentException
     *             if <code>destinationDirectory</code> isn't a directory.
     * @throws IOException
     *             if <code>source</code> does not exist, the file in
     *             <code>destinationDirectory</code> cannot be written to, or
     *             an IO error occurs during copying.
     */
    public static boolean copyFileToDirectoryIfModified( final File source, final File destinationDirectory )
                                              throws IOException
    {
        if ( destinationDirectory.exists(  ) && ! destinationDirectory.isDirectory(  ) )
        {
            throw new IllegalArgumentException( "Destination is not a directory" );
        }

        return copyFileIfModified( source,
                                   new File( destinationDirectory,
                                             source.getName(  ) ) );
    }

    /**
     * Copy file from source to destination only if source timestamp is later
     * than the destination timestamp. The directories up to
     * <code>destination</code> will be created if they don't already exist.
     * <code>destination</code> will be overwritten if it already exists.
     *
     * @param source
     *            An existing non-directory <code>File</code> to copy bytes
     *            from.
     * @param destination
     *            A non-directory <code>File</code> to write bytes to
     *            (possibly overwriting).
     *
     * @throws IOException
     *             if <code>source</code> does not exist,
     *             <code>destination</code> cannot be written to, or an IO
     *             error occurs during copying.
     *
     * @throws java.io.FileNotFoundException
     *             if <code>destination</code> is a directory (use
     *             {@link #copyFileToDirectory}).
     */
    public static boolean copyFileIfModified( final File source, final File destination )
                                      throws IOException
    {
        if ( destination.lastModified(  ) < source.lastModified(  ) )
        {
            copyFile( source, destination );

            return true;
        }

        return false;
    }

    /**
     * Copy file from source to destination. The directories up to
     * <code>destination</code> will be created if they don't already exist.
     * <code>destination</code> will be overwritten if it already exists.
     *
     * @param source
     *            An existing non-directory <code>File</code> to copy bytes
     *            from.
     * @param destination
     *            A non-directory <code>File</code> to write bytes to
     *            (possibly overwriting).
     *
     * @throws IOException
     *             if <code>source</code> does not exist,
     *             <code>destination</code> cannot be written to, or an IO
     *             error occurs during copying.
     *
     * @throws java.io.FileNotFoundException
     *             if <code>destination</code> is a directory (use
     *             {@link #copyFileToDirectory}).
     */
    public static void copyFile( final File source, final File destination )
                         throws IOException
    {
        // check source exists
        if ( ! source.exists(  ) )
        {
            final String message = "File " + source + " does not exist";
            throw new IOException( message );
        }

        // does destinations directory exist ?
        if ( ( destination.getParentFile(  ) != null ) && ! destination.getParentFile(  ).exists(  ) )
        {
            destination.getParentFile(  ).mkdirs(  );
        }

        // make sure we can write to destination
        if ( destination.exists(  ) && ! destination.canWrite(  ) )
        {
            final String message = "Unable to open file " + destination + " for writing.";
            throw new IOException( message );
        }

        try ( FileInputStream input = new FileInputStream( source );
              FileOutputStream output = new FileOutputStream( destination ) )
        {
            IOUtil.copy( input, output );
        }

        if ( source.length(  ) != destination.length(  ) )
        {
            final String message = "Failed to copy full contents from " + source + " to " + destination;
            throw new IOException( message );
        }
    }

    /**
     * Delete a file
     * @param strFolderPath the folder path
     * @param strFileName the file name
     * @throws IOException exception if there is an error during the deletion
     */
    public static void deleteFile( String strFolderPath, String strFileName )
                           throws IOException
    {
        File file = new File( strFolderPath + strFileName );

        if ( file.exists(  ) )
        {
            if ( ! file.delete(  ) )
            {
                throw new IOException( "ERROR when deleting the file or folder " + strFolderPath + strFileName );
            }
        }
    }
}
