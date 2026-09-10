package fr.paris.lutece.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import fr.paris.lutece.utils.sql.SqlPathInfo;

/**
 * Tests for {@link LiquiBaseSqlMojo}.
 */
class LiquiBaseSqlMojoTest
{
    private static final String WINDOWS_PATH = "sql\\plugins\\myplugin\\plugin\\create_db_myplugin.sql";
    private static final String UNIX_PATH = "sql/plugins/myplugin/plugin/create_db_myplugin.sql";

    @Test
    @DisplayName( "SqlPathInfo does not understand Windows separators : they must be normalized" )
    void windowsSeparatorsMustBeNormalized( )
    {
        // This is the whole point of the normalization : without it, nothing is recognized
        // on Windows and no SQL file is ever copied to WEB-INF/classes/sql.
        assertNull( SqlPathInfo.parse( WINDOWS_PATH ), "a raw Windows path is not recognized by SqlPathInfo" );
        assertNotNull( SqlPathInfo.parse( LiquiBaseSqlMojo.normalizeSeparators( WINDOWS_PATH ) ),
                "the normalized path must be recognized" );
    }

    @Test
    @DisplayName( "normalizeSeparators leaves a unix path untouched" )
    void normalizeSeparatorsIsIdentityOnUnixPaths( )
    {
        assertEquals( UNIX_PATH, LiquiBaseSqlMojo.normalizeSeparators( UNIX_PATH ) );
        assertEquals( UNIX_PATH, LiquiBaseSqlMojo.normalizeSeparators( WINDOWS_PATH ) );
    }

    @Test
    @DisplayName( "getAbsoluteSqlFilePath yields the sql/... path expected by SqlPathInfo" )
    void getAbsoluteSqlFilePathYieldsSqlPrefixedPath( )
    {
        String strBasePath = File.separator + "build" + File.separator + "WEB-INF" + File.separator + "sql";
        File candidate = new File( strBasePath + File.separator + "plugins" + File.separator + "myplugin"
                + File.separator + "plugin" + File.separator + "create_db_myplugin.sql" );

        assertEquals( UNIX_PATH, LiquiBaseSqlMojo.getAbsoluteSqlFilePath( candidate, strBasePath ) );
        assertTrue( LiquiBaseSqlMojo.isFileManagedByLiquibase( candidate, strBasePath ) );
    }

    @Test
    @DisplayName( "a Windows-style path is recognized too" )
    void windowsStylePathIsRecognized( )
    {
        // Reproduces what File.getPath() returns on Windows. Without normalization inside
        // getAbsoluteSqlFilePath, nothing is recognized there and WEB-INF/classes/sql stays empty.
        String strBasePath = "C:\\build\\WEB-INF\\sql";
        File candidate = new File( strBasePath + "\\plugins\\myplugin\\plugin\\create_db_myplugin.sql" );

        assertEquals( UNIX_PATH, LiquiBaseSqlMojo.getAbsoluteSqlFilePath( candidate, strBasePath ),
                "the Windows path must be normalized to the sql/... form" );
        assertTrue( LiquiBaseSqlMojo.isFileManagedByLiquibase( candidate, strBasePath ),
                "a Windows path must be managed by Liquibase like a unix one" );
    }

    @Test
    @DisplayName( "isInUpgradeDirectory only flags files where a versioned script is expected" )
    void isInUpgradeDirectoryOnlyFlagsUpgradeLocations( )
    {
        String strBasePath = File.separator + "build" + File.separator + "WEB-INF" + File.separator + "sql";

        assertTrue( LiquiBaseSqlMojo.isInUpgradeDirectory(
                new File( strBasePath + "/upgrade/update_db_lutece_core-7.1.x-8.0.0.sql" ), strBasePath ),
                "a file under upgrade/ must be flagged" );
        assertTrue( LiquiBaseSqlMojo.isInUpgradeDirectory(
                new File( strBasePath + "/plugins/myplugin/upgrades/whatever.sql" ), strBasePath ),
                "upgrades/ is a valid variant" );
        assertFalse( LiquiBaseSqlMojo.isInUpgradeDirectory(
                new File( strBasePath + "/init_db/hsqldb.sql" ), strBasePath ),
                "init_db files are legitimately outside Liquibase" );
        assertFalse( LiquiBaseSqlMojo.isInUpgradeDirectory(
                new File( strBasePath + "/plugins/myplugin/plugin/data_myplugin.sql" ), strBasePath ),
                "a plugin file outside upgrade/ must not be flagged" );
    }
}
