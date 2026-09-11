package fr.paris.lutece.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.eclipse.aether.DefaultSessionData;
import org.eclipse.aether.SessionData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the multi-project artifact sets, which used to be static fields.
 */
class SharedArtifactsTest
{
    private static final String KEY_A = "a";
    private static final String KEY_B = "b";

    private static Artifact artifact( String strArtifactId )
    {
        return artifact( strArtifactId, "1.0" );
    }

    private static Artifact artifact( String strArtifactId, String strVersion )
    {
        return new DefaultArtifact( "fr.paris.lutece", strArtifactId,
                VersionRange.createFromVersion( strVersion ), Artifact.SCOPE_COMPILE, "jar", null,
                new DefaultArtifactHandler( "jar" ) );
    }

    @Test
    @DisplayName( "every module of a build shares the same set" )
    void modulesOfOneBuildShareTheSameSet( )
    {
        SessionData data = new DefaultSessionData( );

        Set<Artifact> fromFirstModule = AbstractLuteceMojo.getSharedArtifacts( data, KEY_A );
        fromFirstModule.add( artifact( "library-a" ) );

        Set<Artifact> fromSecondModule = AbstractLuteceMojo.getSharedArtifacts( data, KEY_A );

        assertSame( fromFirstModule, fromSecondModule, "the modules must share one set" );
        assertEquals( 1, fromSecondModule.size( ), "what a module contributed must be visible to the next" );
    }

    @Test
    @DisplayName( "the two sets are kept apart" )
    void theTwoSetsAreKeptApart( )
    {
        SessionData data = new DefaultSessionData( );

        AbstractLuteceMojo.getSharedArtifacts( data, KEY_A ).add( artifact( "library-a" ) );

        assertTrue( AbstractLuteceMojo.getSharedArtifacts( data, KEY_B ).isEmpty( ) );
    }

    @Test
    @DisplayName( "a new build starts from an empty set, unlike the former static fields" )
    void aNewBuildStartsEmpty( )
    {
        SessionData firstBuild = new DefaultSessionData( );
        AbstractLuteceMojo.getSharedArtifacts( firstBuild, KEY_A ).add( artifact( "library-a" ) );

        // A second build in the same JVM : a static field would still hold the first build's
        // artifacts, and they would end up in the second build's WEB-INF/lib.
        SessionData secondBuild = new DefaultSessionData( );
        Set<Artifact> set = AbstractLuteceMojo.getSharedArtifacts( secondBuild, KEY_A );

        assertNotSame( AbstractLuteceMojo.getSharedArtifacts( firstBuild, KEY_A ), set );
        assertTrue( set.isEmpty( ), "no state must leak from the previous build" );
    }

    @Test
    @DisplayName( "versions compare without needing a version range, and order the arbitration" )
    void versionsCompareWithoutAVersionRange( )
    {
        // getSelectedVersion() throws on an artifact that carries no range, which is how
        // excluded artifacts reach us. Comparing the plain versions does not.
        Artifact noRange = new DefaultArtifact( "fr.paris.lutece", "library", "2.0", Artifact.SCOPE_COMPILE,
                "jar", null, new DefaultArtifactHandler( "jar" ) );

        // deployThirdPartyJar keeps the jar whose version compares highest, so this is the
        // rule that decides what a reactor ships when two modules disagree.
        assertTrue( AbstractLuteceMojo.compareVersions( artifact( "library", "3.17.0" ),
                artifact( "library", "3.14.0" ) ) > 0, "3.17.0 must win over 3.14.0" );
        assertTrue( AbstractLuteceMojo.compareVersions( noRange, artifact( "library", "1.0" ) ) > 0 );
        assertTrue( AbstractLuteceMojo.compareVersions( artifact( "library", "1.0" ), noRange ) < 0 );
        assertEquals( 0, AbstractLuteceMojo.compareVersions( artifact( "library", "1.0" ),
                artifact( "library", "1.0" ) ) );
    }

    @Test
    @DisplayName( "concurrent modules do not lose contributions" )
    void concurrentModulesDoNotLoseContributions( ) throws InterruptedException
    {
        final int nModules = 8;
        final int nArtifactsPerModule = 50;
        SessionData data = new DefaultSessionData( );

        List<Artifact> artifacts = IntStream.range( 0, nModules * nArtifactsPerModule )
                .mapToObj( i -> artifact( "library-" + i ) ).collect( Collectors.toList( ) );

        ExecutorService pool = Executors.newFixedThreadPool( nModules );
        CountDownLatch start = new CountDownLatch( 1 );

        for ( int nModule = 0; nModule < nModules; nModule++ )
        {
            final int nOffset = nModule * nArtifactsPerModule;
            pool.submit( ( ) -> {
                start.await( );
                Set<Artifact> shared = AbstractLuteceMojo.getSharedArtifacts( data, KEY_A );
                for ( int i = 0; i < nArtifactsPerModule; i++ )
                {
                    shared.add( artifacts.get( nOffset + i ) );
                }
                return null;
            } );
        }

        start.countDown( );
        pool.shutdown( );
        assertTrue( pool.awaitTermination( 30, TimeUnit.SECONDS ), "the modules should have finished" );

        // A plain HashSet loses entries here, which means jars missing from WEB-INF/lib.
        assertEquals( nModules * nArtifactsPerModule, AbstractLuteceMojo.getSharedArtifacts( data, KEY_A ).size( ) );
    }
}
