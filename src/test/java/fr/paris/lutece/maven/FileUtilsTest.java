package fr.paris.lutece.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link FileUtils}.
 */
class FileUtilsTest
{
    @TempDir
    Path tempDir;

    @BeforeEach
    void resetCounters( )
    {
        // The counters are static : without this reset, tests leak into each other.
        FileUtils.setNbFileCopy( 0 );
        FileUtils.setNbFileModified( 0 );
    }

    private File writeFile( Path dir, String strRelativePath, String strContent ) throws IOException
    {
        Path file = dir.resolve( strRelativePath );
        Files.createDirectories( file.getParent( ) );
        Files.write( file, strContent.getBytes( StandardCharsets.UTF_8 ) );

        return file.toFile( );
    }

    @Test
    @DisplayName( "copyFileIfModified copies a missing target and reports it" )
    void copyFileIfModifiedCopiesMissingTarget( ) throws IOException
    {
        File source = writeFile( tempDir, "src/a.txt", "content" );
        File destination = tempDir.resolve( "dest/a.txt" ).toFile( );

        assertTrue( FileUtils.copyFileIfModified( source, destination ), "the file should have been copied" );
        assertTrue( destination.exists( ) );
        assertEquals( 1, FileUtils.getNbFileModified( ), "the copy should have been counted" );
    }

    @Test
    @DisplayName( "copyFileIfModified skips an up-to-date target and counts nothing" )
    void copyFileIfModifiedSkipsUpToDateTarget( ) throws IOException
    {
        File source = writeFile( tempDir, "src/a.txt", "content" );
        File destination = writeFile( tempDir, "dest/a.txt", "content" );

        source.setLastModified( 1_000_000L );
        destination.setLastModified( 2_000_000L );
        FileUtils.setNbFileModified( 0 );

        assertFalse( FileUtils.copyFileIfModified( source, destination ), "the file should have been skipped" );
        assertEquals( 0, FileUtils.getNbFileModified( ), "nothing should have been counted" );
    }

    @Test
    @DisplayName( "copyDirectoryStructureIfModified reports how many files it copied" )
    void copyDirectoryStructureIfModifiedCountsCopiedFiles( ) throws IOException
    {
        Path source = tempDir.resolve( "src" );
        writeFile( source, "a.txt", "a" );
        writeFile( source, "sub/b.txt", "b" );
        writeFile( source, "sub/c.txt", "c" );

        File destination = tempDir.resolve( "dest" ).toFile( );
        FileUtils.copyDirectoryStructureIfModified( source.toFile( ), destination );

        assertEquals( 3, FileUtils.getNbFileModified( ), "the three copied files should have been counted" );
    }

    @Test
    @DisplayName( "copyDirectoryStructure skips site sources whatever the platform separator" )
    void copyDirectoryStructureSkipsSiteSources( ) throws IOException
    {
        Path source = tempDir.resolve( "src" );
        writeFile( source, "xdoc/index.xml", "x" );
        writeFile( source, "resources/images/logo.png", "x" );
        writeFile( source, "tech/notes.txt", "x" );
        writeFile( source, "site/site.xml", "x" );
        writeFile( source, "user/guide.html", "x" );

        File destination = tempDir.resolve( "dest" ).toFile( );
        FileUtils.copyDirectoryStructure( source.toFile( ), destination );

        assertTrue( new File( destination, "user/guide.html" ).exists( ), "regular files must be copied" );
        assertFalse( new File( destination, "xdoc/index.xml" ).exists( ), "xdoc sources must be skipped" );
        assertFalse( new File( destination, "resources/images/logo.png" ).exists( ), "site images must be skipped" );
        assertFalse( new File( destination, "tech/notes.txt" ).exists( ), "the tech directory must be skipped" );
        assertFalse( new File( destination, "site/site.xml" ).exists( ), "site.xml must be skipped" );
    }

    @Test
    @DisplayName( "site sources are recognized through Windows separators too" )
    void siteSourcesAreRecognizedThroughWindowsSeparators( )
    {
        // Reproduces what File.getAbsolutePath() returns on Windows.
        assertTrue( FileUtils.isExcludedSiteFile( "C:\\proj\\src\\site\\xdoc\\index.xml" ) );
        assertTrue( FileUtils.isExcludedSiteFile( "C:\\proj\\src\\resources\\images\\logo.png" ) );
        assertTrue( FileUtils.isExcludedSiteDirectory( "C:\\proj\\src\\tech" ) );
        assertFalse( FileUtils.isExcludedSiteFile( "C:\\proj\\webapp\\js\\app.js" ) );

        // and the same paths on unix
        assertTrue( FileUtils.isExcludedSiteFile( "/proj/src/site/xdoc/index.xml" ) );
        assertTrue( FileUtils.isExcludedSiteDirectory( "/proj/src/tech" ) );
        assertFalse( FileUtils.isExcludedSiteFile( "/proj/webapp/js/app.js" ) );
    }

    @Test
    @DisplayName( "copyDirectoryStructureIfModified counts nothing when everything is up to date" )
    void copyDirectoryStructureIfModifiedCountsNothingWhenUpToDate( ) throws IOException
    {
        Path source = tempDir.resolve( "src" );
        writeFile( source, "a.txt", "a" );

        File destination = tempDir.resolve( "dest" ).toFile( );
        FileUtils.copyDirectoryStructureIfModified( source.toFile( ), destination );
        FileUtils.setNbFileModified( 0 );

        FileUtils.copyDirectoryStructureIfModified( source.toFile( ), destination );

        assertEquals( 0, FileUtils.getNbFileModified( ), "the second pass should have copied nothing" );
    }
}
