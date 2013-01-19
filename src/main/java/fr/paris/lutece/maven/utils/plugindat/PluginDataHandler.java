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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * PluginDataHandler
 */
public class PluginDataHandler
    extends DefaultHandler
{
    private PluginData _plugin = new PluginData(  );
    private StringBuffer _buffer;

    /**
     * {@inheritDoc}
     */
    @Override
    public void startElement( String uri, String localName, String qName, Attributes attributes )
                      throws SAXException
    {
        _buffer = new StringBuffer(  );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endElement( String uri, String localName, String qName )
                    throws SAXException
    {
        if ( qName.equals( "name" ) )
        {
            _plugin.setName( _buffer.toString(  ) );
        } else if ( qName.equals( "db-pool-required" ) )
        {
            String strValue = _buffer.toString(  );
            _plugin.setDbRequired( strValue.equals( "1" ) );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void characters( char[] ch, int start, int length )
                    throws SAXException
    {
        String lecture = new String( ch, start, length );

        if ( _buffer != null )
        {
            _buffer.append( lecture );
        }
    }

    /**
     * Returns the plugin data
     * @return the plugin data
     */
    public PluginData getPlugin(  )
    {
        return _plugin;
    }
}
