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

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.IOUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
    extends org.codehaus.plexus.util.FileUtils
{
    // The name of subversion's administrative directories
    private static final String SVN_DIRECTORY = ".svn";

    //regexp for filter xdoc
    protected static final String REGEXP_SITE_XDOC_XML = "(.)*\\\\xdoc\\\\[^\\\\]*\\.(.)*";
    protected static final String REGEXP_SITE_RESOURCES_XML = "(.)*\\\\resources\\\\images\\\\[^\\\\]*\\.(.)*";
    protected static final String REGEXP_SITE_TECH = "(.)*\\\\tech(\\\\.)*";
    protected static final String REGEXP_SITE_TECH_DIRECTORY = "(.)*\\\\tech";
    protected static final String REGEXP_SITE_XML = "(.)*site\\\\site(.)*.xml";
    private static int nNbFileModified;
    private static int nNbFileCopy;

    /**
     * Gets the nb file modified.
     *
     * @return the nb file modified
     */
    public static int getNbFileModified(  )
    {
        return nNbFileModified;
    }

    /**
     * Sets the nb file modified.
     *
     * @param nbFileMdofied the new nb file modified
     */
    public static void setNbFileModified( int nbFileMdofied )
    {
        nNbFileModified = nbFileMdofied;
    }

    /**
     * Gets the nb file copy.
     *
     * @return the nb file copy
     */
    public static int getNbFileCopy(  )
    {
        return nNbFileCopy;
    }

    /**
     * Sets the nb file copy.
     *
     * @param nbFileCopy the new nb file copy
     */
    public static void setNbFileCopy( int nbFileCopy )
    {
        nNbFileCopy = nbFileCopy;
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
    public static void copyDirectoryStructureIfModified( File sourceDirectory, File destinationDirectory )
                                                 throws IOException
    {
        if ( ! sourceDirectory.exists(  ) )
        {
            throw new IOException( "Source directory doesn't exists (" + sourceDirectory.getAbsolutePath(  ) + ")." );
        }

        File[] files = sourceDirectory.listFiles(  );

        String sourcePath = sourceDirectory.getAbsolutePath(  );

        for ( int i = 0; i < files.length; i++ )
        {
            File file = files[i];

            String dest = file.getAbsolutePath(  );

            dest = dest.substring( sourcePath.length(  ) + 1 );

            File destination = new File( destinationDirectory, dest );

            if ( file.isFile(  ) )
            {
                destination = destination.getParentFile(  );

                if ( ! file.getAbsolutePath(  ).matches( REGEXP_SITE_XML ) &&
                         ! file.getAbsolutePath(  ).matches( REGEXP_SITE_TECH ) &&
                         ! file.getAbsolutePath(  ).matches( REGEXP_SITE_XDOC_XML ) &&
                         ! file.getAbsolutePath(  ).matches( REGEXP_SITE_RESOURCES_XML ) )
                {
                    copyFileToDirectoryIfModified( file, destination );
                }
            } else if ( file.isDirectory(  ) )
            {
                // Exclude SVN administrative directories
                if ( ! SVN_DIRECTORY.equals( file.getName(  ) ) &&
                         ! file.getAbsolutePath(  ).matches( REGEXP_SITE_TECH_DIRECTORY ) )
                {
                    if ( ! destination.exists(  ) && ! destination.mkdirs(  ) )
                    {
                        throw new IOException( "Could not create destination directory '" +
                                               destination.getAbsolutePath(  ) + "'." );
                    }

                    copyDirectoryStructureIfModified( file, destination );
                }
            } else
            {
                throw new IOException( "Unknown file type: " + file.getAbsolutePath(  ) );
            }
        }
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
    public static void copyDirectoryStructure( File sourceDirectory, File destinationDirectory )
                                       throws IOException
    {
        if ( ! sourceDirectory.exists(  ) )
        {
            throw new IOException( "Source directory doesn't exists (" + sourceDirectory.getAbsolutePath(  ) + ")." );
        }

        File[] files = sourceDirectory.listFiles(  );

        String sourcePath = sourceDirectory.getAbsolutePath(  );

        for ( int i = 0; i < files.length; i++ )
        {
            File file = files[i];

            String dest = file.getAbsolutePath(  );

            dest = dest.substring( sourcePath.length(  ) + 1 );

            File destination = new File( destinationDirectory, dest );

            if ( file.isFile(  ) )
            {
                destination = destination.getParentFile(  );

                if ( ! file.getAbsolutePath(  ).matches( REGEXP_SITE_XML ) &&
                         ! file.getAbsolutePath(  ).matches( REGEXP_SITE_TECH ) &&
                         ! file.getAbsolutePath(  ).matches( REGEXP_SITE_XDOC_XML ) &&
                         ! file.getAbsolutePath(  ).matches( REGEXP_SITE_RESOURCES_XML ) )
                {
                    FileUtils.copyFileToDirectory( file, destination );
                    nNbFileCopy++;
                }
            } else if ( file.isDirectory(  ) )
            {
                // Exclude SVN administrative directories
                if ( ! SVN_DIRECTORY.equals( file.getName(  ) ) &&
                         ! file.getAbsolutePath(  ).matches( REGEXP_SITE_TECH_DIRECTORY ) )
                {
                    if ( ! destination.exists(  ) && ! destination.mkdirs(  ) )
                    {
                        throw new IOException( "Could not create destination directory '" +
                                               destination.getAbsolutePath(  ) + "'." );
                    }

                    copyDirectoryStructure( file, destination );
                }
            } else
            {
                throw new IOException( "Unknown file type: " + file.getAbsolutePath(  ) );
            }
        }
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
    public static void copyFileToDirectoryIfModified( final File source, final File destinationDirectory )
                                              throws IOException
    {
        if ( destinationDirectory.exists(  ) && ! destinationDirectory.isDirectory(  ) )
        {
            throw new IllegalArgumentException( "Destination is not a directory" );
        }

        copyFileIfModified( source,
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

        FileInputStream input = null;
        FileOutputStream output = null;

        try
        {
            input = new FileInputStream( source );
            output = new FileOutputStream( destination );
            IOUtil.copy( input, output );
        } finally
        {
            IOUtil.close( input );
            IOUtil.close( output );
        }

        if ( source.length(  ) != destination.length(  ) )
        {
            final String message = "Failed to copy full contents from " + source + " to " + destination;
            throw new IOException( message );
        }
    }

    /**
    * Create a file.
    *
    * @param strFolderPath the folder path
    * @param strFileName the file name
    * @param strFileOutPut the file output
    * @throws IOException exception if there is an error during the deletion
    */
    public static void createFile( String strFolderPath, String strFileName, String strFileOutPut )
                           throws IOException
    {
        File file = new File( strFolderPath + strFileName );

        // Delete the file if it exists
        deleteFile( strFolderPath, strFileName );

        org.apache.commons.io.FileUtils.writeStringToFile( file, strFileOutPut );
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

    /**
     * Read the last line from the given file
     * @param strFile the file absolute path (ex : /home/filetopath/file.txt)
     * @return the last line, an empty string if the file does not exists
     */
    public static String readLastLine( String strFile )
    {
        FileInputStream in = null;

        try
        {
            in = new FileInputStream( strFile );
        } catch ( FileNotFoundException e )
        {
            IOUtils.closeQuietly( in );

            return "";
        }

        String strLastLine = "";
        BufferedReader br = new BufferedReader( new InputStreamReader( in ) );
        String strTmp = null;

        try
        {
            while ( br.ready(  ) )
            {
                strTmp = br.readLine(  );
            }
        } catch ( IOException e )
        {
            e.printStackTrace(  );
        } finally
        {
            IOUtils.closeQuietly( in );
        }

        strLastLine = strTmp;

        return strLastLine;
    }

    /**
     * Write to the given file
     * @param strContent the content to write
     * @param strFile the file
     */
    public static void writeToFile( String strContent, String strFile )
    {
        FileWriter fw = null;

        try
        {
            fw = new FileWriter( strFile, false );
            fw.write( strContent );
        } catch ( IOException e )
        {
            e.printStackTrace(  );
        } finally
        {
            IOUtils.closeQuietly( fw );
        }
    }
}
