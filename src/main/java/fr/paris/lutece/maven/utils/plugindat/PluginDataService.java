/*
 * Copyright (c) 2002-2008, Mairie de Paris
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
package fr.paris.lutece.maven.utils.plugindat;

import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PluginDataService
 */
public class PluginDataService
{
    private static final String PATH_PLUGINS_DIRECTORY = "/WEB-INF/plugins";
    private static final String PATH_PLUGINS_DAT_FILE = "/WEB-INF/plugins/plugins.dat";

    /**
     * Gets a File object for the plugins.dat file
     * @param strWebappPath The Webapp path
     * @return a File object for the plugins.dat file
     */
    public static File getPluginsDatFile( String strWebappPath )
    {
        return new File( strWebappPath + PATH_PLUGINS_DAT_FILE );
    }

    /**
     * The list of plugins data
     * @param strWebappPath The Webapp path
     * @return The list of plugins data
     */
    public static List<PluginData> getPluginsList( String strWebappPath )
    {
        File filePluginsDirectory = new File( strWebappPath + PATH_PLUGINS_DIRECTORY );
        FilenameFilter filter = new PluginFileFilter(  );
        File[] files = filePluginsDirectory.listFiles( filter );
        List<PluginData> list = new ArrayList<>(  );

        if( files != null )
        {
            for (File file : files)
            {
                PluginDataParser.parse(file, list);
            }
        }

        return list;
    }

    /**
     * Writes the plugins data into a given file
     * @param file The file to write into
     * @param list The list of plugins data
     */
    public static void writeFile( File file, List<PluginData> list )
    {

        File parentDirectory = file.getParentFile();

        if (null != parentDirectory)
        {
            parentDirectory.mkdirs();
        }

        FileWriter fw = null;
        try
        {
            fw = new FileWriter( file );
            fw.write( "# plugins.dat file\r\n" );
            fw.write( "# Version generated by the Lutece's Maven Plugin according to the current pom.xml file.\r\n" );
            fw.write( "# This file stores the state of each plugin available in the webapp \r\n" );
            fw.write( "# and the database pool it may be using.\r\n\r\n" );

            for ( PluginData plugin : list )
            {
                fw.write( plugin.getName(  ) + ".installed=1\r\n" );

                if ( plugin.isDbRequired(  ) )
                {
                    fw.write( plugin.getName(  ) + ".pool=portal\r\n" );
                }
            }
        } catch ( IOException ex )
        {
            Logger.getLogger( PluginDataService.class.getName(  ) ).log( Level.SEVERE, null, ex );
        } finally
        {
            try
            {
                fw.close(  );
            } catch ( IOException ex )
            {
                Logger.getLogger( PluginDataService.class.getName(  ) ).log( Level.SEVERE, null, ex );
            }
        }
    }

    /**
     * Writes the plugins data into an given output stream
     * @param list The list of plugins data
     * @param out The output stream
     */
    public static void outPut( List<PluginData> list, PrintStream out )
    {
        for ( PluginData plugin : list )
        {
            out.println( plugin.getName(  ) + ".installed=1" );

            if ( plugin.isDbRequired(  ) )
            {
                out.println( plugin.getName(  ) + ".pool=portal" );
            }
        }
    }

    /**
     * Generate plugins.dat file
     * @param strWebappPath The webapp path
     */
    public static void generatePluginsDataFile( String strWebappPath )
    {
        List<PluginData> list = PluginDataService.getPluginsList( strWebappPath );
        // Write the plugin.dat file
        PluginDataService.writeFile( getPluginsDatFile( strWebappPath ), list );
    }
}


/**
 * PluginFileFilter Inner Class
 */
class PluginFileFilter
    implements FilenameFilter
{
    @Override
    public boolean accept( File dir, String strFilename )
    {
        if ( strFilename.endsWith( ".xml" ) )
        {
            return true;
        }

        return false;
    }
}
