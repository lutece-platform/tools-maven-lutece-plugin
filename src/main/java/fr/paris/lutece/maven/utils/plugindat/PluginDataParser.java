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
import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

/**
 * PluginDataParser
 */
public class PluginDataParser
{
    /**
     * Parses a plugin descriptor and appends its data to the list.
     *
     * Failures used to be logged to java.util.logging, which does not show in the Maven
     * output : a descriptor that could not be read disappeared from plugins.dat silently.
     *
     * @param file
     *            the plugin descriptor
     * @param list
     *            the list to append to
     * @throws IOException
     *             if the descriptor cannot be read or parsed
     */
    static void parse( File file, List<PluginData> list ) throws IOException
    {
        try
        {
            PluginDataHandler handler = new PluginDataHandler(  );
            newSafeParser(  ).parse( file, handler );
            list.add( handler.getPlugin(  ) );
        } catch ( ParserConfigurationException | SAXException ex )
        {
            throw new IOException( "Could not parse the plugin descriptor " + file.getAbsolutePath(  ), ex );
        }
    }

    /**
     * Builds a parser that resolves no external entity, so that a descriptor cannot make the
     * build read an arbitrary file nor reach out to the network. The DOCTYPE itself stays
     * allowed : older descriptors may declare one.
     *
     * @return a hardened SAX parser
     * @throws ParserConfigurationException
     *             if the parser rejects the configuration
     * @throws SAXException
     *             if a feature is not recognized
     */
    static SAXParser newSafeParser(  ) throws ParserConfigurationException, SAXException
    {
        SAXParserFactory factory = SAXParserFactory.newInstance(  );
        factory.setFeature( "http://xml.org/sax/features/external-general-entities", false );
        factory.setFeature( "http://xml.org/sax/features/external-parameter-entities", false );
        factory.setFeature( "http://apache.org/xml/features/nonvalidating/load-external-dtd", false );
        factory.setXIncludeAware( false );

        return factory.newSAXParser(  );
    }
}
