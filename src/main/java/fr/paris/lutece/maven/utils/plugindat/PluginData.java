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


/**
 * PluginData
 */
public class PluginData
{
    // Variables declarations
    private String _strName;
    private boolean _bInstalled;
    private boolean _bDbRequired;
    private String _strPool;

    /**
     * Returns the Name
     * @return The Name
     */
    public String getName(  )
    {
        return _strName;
    }

    /**
     * Sets the Name
     * @param strName The Name
     */
    public void setName( String strName )
    {
        _strName = strName;
    }

    /**
     * Returns the IsInstalled
     * @return The IsInstalled
     */
    public boolean isInstalled(  )
    {
        return _bInstalled;
    }

    /**
     * Sets the IsInstalled
     * @param bInstalled Is installed
     */
    public void setInstalled( boolean bInstalled )
    {
        _bInstalled = bInstalled;
    }

    /**
     * Returns the IsDbRequired
     * @return The IsDbRequired
     */
    public boolean isDbRequired(  )
    {
        return _bDbRequired;
    }

    /**
     * Sets the IsDbRequired
     * @param bDbRequired The IsDbRequired
     */
    public void setDbRequired( boolean bDbRequired )
    {
        _bDbRequired = bDbRequired;
    }

    /**
     * Returns the Pool
     * @return The Pool
     */
    public String getPool(  )
    {
        return _strPool;
    }

    /**
     * Sets the Pool
     * @param strPool The Pool
     */
    public void setPool( String strPool )
    {
        _strPool = strPool;
    }
}
