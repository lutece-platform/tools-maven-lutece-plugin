package fr.paris.lutece.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the archive naming of the assembly mojos.
 */
class AssemblyMojosTest
{
    private static Date at( int nHour, int nMinute )
    {
        Calendar calendar = Calendar.getInstance( );
        calendar.clear( );
        calendar.set( 2026, Calendar.MARCH, 4, nHour, nMinute, 0 );

        return calendar.getTime( );
    }

    @Test
    @DisplayName( "the archive timestamp uses the 24-hour clock" )
    void archiveTimestampUsesTwentyFourHourClock( )
    {
        // "hh" is the 12-hour clock : it made 01:30 and 13:30 collide on the same file name
        SimpleDateFormat twelveHour = new SimpleDateFormat( "yyMMdd-hhmm" );
        assertEquals( twelveHour.format( at( 1, 30 ) ), twelveHour.format( at( 13, 30 ) ),
                "the old pattern did collide - this is the bug being fixed" );

        SimpleDateFormat actual = new SimpleDateFormat( AbstractLuteceMojo.ARCHIVE_TIMESTAMP_PATTERN );
        assertNotEquals( actual.format( at( 1, 30 ) ), actual.format( at( 13, 30 ) ),
                "morning and afternoon assemblies must not share a file name" );
        assertEquals( "260304-1330", actual.format( at( 13, 30 ) ) );
    }

    @Test
    @DisplayName( "the archive timestamp ignores the build machine time zone" )
    void archiveTimestampIgnoresTheMachineTimeZone( )
    {
        TimeZone previous = TimeZone.getDefault( );
        try
        {
            // Epoch is 01:00 in Paris : CET, one hour ahead of UTC, in January 1970.
            TimeZone.setDefault( TimeZone.getTimeZone( "Pacific/Kiritimati" ) ); // UTC+14
            assertEquals( "19700101.010000", AbstractLuteceMojo.formatTimestamp( "yyyyMMdd.HHmmss",
                    new Date( 0L ), AbstractLuteceMojo.ARCHIVE_TIMESTAMP_TIME_ZONE ) );

            TimeZone.setDefault( TimeZone.getTimeZone( "Pacific/Midway" ) ); // UTC-11
            assertEquals( "19700101.010000", AbstractLuteceMojo.formatTimestamp( "yyyyMMdd.HHmmss",
                    new Date( 0L ), AbstractLuteceMojo.ARCHIVE_TIMESTAMP_TIME_ZONE ) );
        }
        finally
        {
            TimeZone.setDefault( previous );
        }
    }

    @Test
    @DisplayName( "the archive timestamp reads in French local time, including summer time" )
    void archiveTimestampReadsInFrenchLocalTime( )
    {
        assertEquals( "Europe/Paris", AbstractLuteceMojo.ARCHIVE_TIMESTAMP_TIME_ZONE );

        // 2026-07-01T12:00:00Z : Paris is on CEST, two hours ahead.
        Date summer = Date.from( java.time.Instant.parse( "2026-07-01T12:00:00Z" ) );
        assertEquals( "20260701.140000", AbstractLuteceMojo.formatTimestamp( "yyyyMMdd.HHmmss",
                summer, AbstractLuteceMojo.ARCHIVE_TIMESTAMP_TIME_ZONE ) );

        // 2026-01-15T12:00:00Z : Paris is back on CET, one hour ahead.
        Date winter = Date.from( java.time.Instant.parse( "2026-01-15T12:00:00Z" ) );
        assertEquals( "20260115.130000", AbstractLuteceMojo.formatTimestamp( "yyyyMMdd.HHmmss",
                winter, AbstractLuteceMojo.ARCHIVE_TIMESTAMP_TIME_ZONE ) );
    }

    @Test
    @DisplayName( "AssemblyMojo falls back to the build directory when no assembly directory is set" )
    void assemblyMojoOutputDirectoryFallsBackToBuildDirectory( )
    {
        AssemblyMojo mojo = new AssemblyMojo( );
        mojo.project = new MavenProject( );
        mojo.project.setArtifactId( "myplugin" );
        mojo.outputDirectory = new File( File.separator + "build" + File.separator + "target" );

        assertEquals( new File( mojo.outputDirectory, "myplugin" ), mojo.getOutputDirectory( ) );

        mojo.assemblyOutputDirectory = new File( File.separator + "elsewhere" );
        assertEquals( new File( mojo.assemblyOutputDirectory, "myplugin" ), mojo.getOutputDirectory( ),
                "an explicit assembly directory must win" );
    }

    @Test
    @DisplayName( "UpdaterMojo falls back to the build directory when no updater directory is set" )
    void updaterMojoOutputDirectoryFallsBackToBuildDirectory( )
    {
        UpdaterMojo mojo = new UpdaterMojo( );
        mojo.project = new MavenProject( );
        mojo.project.setArtifactId( "myplugin" );
        mojo.outputDirectory = new File( File.separator + "build" + File.separator + "target" );

        assertEquals( new File( mojo.outputDirectory, "myplugin" ), mojo.getOutputDirectory( ) );

        mojo.updOutputDirectory = new File( File.separator + "elsewhere" );
        assertEquals( new File( mojo.updOutputDirectory, "myplugin" ), mojo.getOutputDirectory( ),
                "an explicit updater directory must win" );
    }

    @Test
    @DisplayName( "a failed transitive resolution keeps the direct dependencies instead of throwing" )
    void assemblyMojoSurvivesFailedTransitiveResolution( )
    {
        AssemblyMojo mojo = new AssemblyMojo( );
        Set<File> jars = new HashSet<>( );
        jars.add( new File( "direct.jar" ) );

        // null is what the mojo holds when resolveTransitively threw and was logged
        mojo.addTransitiveJars( null, jars );

        assertEquals( 1, jars.size( ), "the direct dependencies must be kept" );
    }
}
