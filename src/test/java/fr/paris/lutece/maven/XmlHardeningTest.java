package fr.paris.lutece.maven;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import fr.paris.lutece.maven.utils.plugindat.PluginData;
import fr.paris.lutece.maven.utils.plugindat.PluginDataService;

/**
 * The XML parsers must not resolve external entities : a plugin descriptor must never make
 * the build read an arbitrary file, nor reach out to the network.
 */
class XmlHardeningTest
{
    private static final String SECRET = "a-secret-the-build-must-not-leak";

    @TempDir
    Path tempDir;

    private Path writeSecret( ) throws IOException
    {
        Path secret = tempDir.resolve( "secret.txt" );
        Files.write( secret, SECRET.getBytes( StandardCharsets.UTF_8 ) );

        return secret;
    }

    /**
     * Writes a descriptor whose plugin name is an external entity pointing at a local file.
     */
    private File descriptorLeaking( Path secret ) throws IOException
    {
        String strXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE plug-in [ <!ENTITY xxe SYSTEM \"" + secret.toUri( ) + "\"> ]>\n"
                + "<plug-in><name>&xxe;</name><version>1.0.0</version></plug-in>\n";
        Path descriptor = tempDir.resolve( "webapp/WEB-INF/plugins/evil.xml" );
        Files.createDirectories( descriptor.getParent( ) );
        Files.write( descriptor, strXml.getBytes( StandardCharsets.UTF_8 ) );

        return descriptor.toFile( );
    }

    @Test
    @DisplayName( "the SAX parser does not resolve an external entity" )
    void saxParserDoesNotResolveExternalEntities( ) throws IOException
    {
        descriptorLeaking( writeSecret( ) );

        List<PluginData> plugins = PluginDataService.getPluginsList( tempDir.resolve( "webapp" ).toString( ) );

        assertEquals( 1, plugins.size( ) );
        assertFalse( String.valueOf( plugins.get( 0 ).getName( ) ).contains( SECRET ),
                "the SAX parser leaked the file contents into the plugin name" );
    }

    @Test
    @DisplayName( "the DOM parser does not resolve an external entity" )
    void domParserDoesNotResolveExternalEntities( ) throws Exception
    {
        File descriptor = descriptorLeaking( writeSecret( ) );

        Document doc = LiquiBaseSqlMojo.newSafeDocumentBuilder( ).parse( descriptor );
        String strName = doc.getElementsByTagName( "name" ).item( 0 ).getTextContent( );

        assertFalse( strName.contains( SECRET ), "the DOM parser leaked the file contents into the plugin name" );
    }

    @Test
    @DisplayName( "a descriptor declaring a DOCTYPE is still accepted, and plugins.dat is UTF-8" )
    void plainDescriptorsAreStillParsed( ) throws Exception
    {
        Path descriptor = tempDir.resolve( "webapp/WEB-INF/plugins/ok.xml" );
        Files.createDirectories( descriptor.getParent( ) );
        Files.write( descriptor, ( "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE plug-in SYSTEM \"plugin_2_2.dtd\">\n"
                + "<plug-in><name>ok</name><version>1.0.0</version>"
                + "<db-pool-required>1</db-pool-required></plug-in>\n" ).getBytes( StandardCharsets.UTF_8 ) );

        List<PluginData> plugins = assertDoesNotThrow(
                ( ) -> PluginDataService.getPluginsList( tempDir.resolve( "webapp" ).toString( ) ),
                "a descriptor declaring a DOCTYPE must still be accepted" );

        assertEquals( 1, plugins.size( ) );
        assertEquals( "ok", plugins.get( 0 ).getName( ) );

        File datFile = tempDir.resolve( "webapp/WEB-INF/plugins/plugins.dat" ).toFile( );
        PluginDataService.writeFile( datFile, new ArrayList<>( plugins ) );

        String dat = new String( Files.readAllBytes( datFile.toPath( ) ), StandardCharsets.UTF_8 );
        assertTrue( dat.contains( "ok.installed=1" ) );
        assertTrue( dat.contains( "ok.pool=portal" ) );
    }

    @Test
    @DisplayName( "an unreadable descriptor fails the build instead of vanishing" )
    void anUnreadableDescriptorFailsTheBuild( ) throws IOException
    {
        Path descriptor = tempDir.resolve( "webapp/WEB-INF/plugins/broken.xml" );
        Files.createDirectories( descriptor.getParent( ) );
        Files.write( descriptor, "<plug-in><name>broken".getBytes( StandardCharsets.UTF_8 ) );

        // It used to be logged to java.util.logging, invisible in the Maven output, and the
        // plugin simply disappeared from plugins.dat.
        assertThrows( IOException.class,
                ( ) -> PluginDataService.getPluginsList( tempDir.resolve( "webapp" ).toString( ) ) );
    }
}
