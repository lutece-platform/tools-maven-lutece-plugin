package fr.paris.lutece.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
    }

    @Test
    @DisplayName( "copyFileIfModified skips an up-to-date target" )
    void copyFileIfModifiedSkipsUpToDateTarget( ) throws IOException
    {
        File source = writeFile( tempDir, "src/a.txt", "content" );
        File destination = writeFile( tempDir, "dest/a.txt", "content" );

        source.setLastModified( 1_000_000L );
        destination.setLastModified( 2_000_000L );

        assertFalse( FileUtils.copyFileIfModified( source, destination ), "the file should have been skipped" );
    }

    @Test
    @DisplayName( "copyDirectoryStructure returns how many files it copied" )
    void copyDirectoryStructureReturnsCopiedCount( ) throws IOException
    {
        Path source = tempDir.resolve( "src" );
        writeFile( source, "a.txt", "a" );
        writeFile( source, "sub/b.txt", "b" );

        assertEquals( 2, FileUtils.copyDirectoryStructure( source.toFile( ), tempDir.resolve( "dest" ).toFile( ) ) );
    }

    @Test
    @DisplayName( "copyDirectoryStructureIfModified returns how many files it copied" )
    void copyDirectoryStructureIfModifiedReturnsCopiedCount( ) throws IOException
    {
        Path source = tempDir.resolve( "src" );
        writeFile( source, "a.txt", "a" );
        writeFile( source, "sub/b.txt", "b" );
        writeFile( source, "sub/c.txt", "c" );

        File destination = tempDir.resolve( "dest" ).toFile( );

        assertEquals( 3, FileUtils.copyDirectoryStructureIfModified( source.toFile( ), destination ),
                "the three copied files should be reported" );
        assertEquals( 0, FileUtils.copyDirectoryStructureIfModified( source.toFile( ), destination ),
                "the second pass should copy nothing" );
    }

    @Test
    @DisplayName( "each copy reports its own count, independently of the others" )
    void copiesDoNotShareState( ) throws IOException
    {
        Path first = tempDir.resolve( "first" );
        writeFile( first, "a.txt", "a" );
        Path second = tempDir.resolve( "second" );
        writeFile( second, "b.txt", "b" );
        writeFile( second, "c.txt", "c" );

        // With the former static counters, the second call reported the sum of both.
        assertEquals( 1, FileUtils.copyDirectoryStructure( first.toFile( ), tempDir.resolve( "d1" ).toFile( ) ) );
        assertEquals( 2, FileUtils.copyDirectoryStructure( second.toFile( ), tempDir.resolve( "d2" ).toFile( ) ) );
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
}
