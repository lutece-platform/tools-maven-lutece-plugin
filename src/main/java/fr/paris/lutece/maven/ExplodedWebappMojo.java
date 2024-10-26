/*
 * Copyright (c) 2002-2024, Mairie de Paris
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
package fr.paris.lutece.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Explodes a  webapp for a Lutece plugin or site project.<br/> Note that
 * the Lutece dependencies (core and plugins) will only be updated the first
 * time the exploded webapp is created. Subsequent calls to this goal will only
 * update the project's specific files :
 * <ul>
 *	 <li>for a plugin project : the plugin-specific webapp elements and classes.</li>
 * 	 <li>for a site project : the site-specific webapp elements.</li>
 * </ul>
 * If you wish to force webapp re-creation (for instance, if you changed the
 * version of a dependency), call the <code>clean</code> phase before this
 * goal.
 *
 * This goal supports the following configuration parameters:
 * </p>
 *
 * <ul>
 *   <li><code>outputDirectory</code>: The directory where the generated WAR file will be placed.</li>
 *   <li><code>webResources</code>: Resources to be included in the WAR.</li>
 *   <li><code>includeDependencies</code>: Whether to include project dependencies in the WAR file.</li>
 *   <li><code>localConfDirectory</code>: The directory containing the local, user-specific configuration files.</li>
 *   <li><code>defaultConfDirectory</code>: The directory containing the default configuration files.</li>
 *   <li><code>sqlDirectory</code>: The directory containing the database sql script.</li>
 *   <li><code>siteDirectory</code>: The directory containing the default user documentation</li>
 *
 * </ul>
 *
 */

@Mojo( name = "exploded-webapp" , defaultPhase=LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ExplodedWebappMojo extends AbstractLuteceWebappMojo
{
    /**
     * Executes the mojo on the current project.
     *
     * @throws MojoExecutionException
     *             if the project is not supported.
     * @throws MojoFailureException
     *             if an error occurred while exploding the webapp.
     */
    @Override
    public void execute(  )
                 throws MojoExecutionException, MojoFailureException
    {
    	 logBanner();
        if ( ! LUTECE_CORE_PACKAGING.equals( project.getPackaging(  ) ) &&
                 ! LUTECE_PLUGIN_PACKAGING.equals( project.getPackaging(  ) ) &&
                 ! LUTECE_SITE_PACKAGING.equals( project.getPackaging(  ) )
                   )
        {
            throw new MojoExecutionException( "This goal can be invoked only on a " + LUTECE_CORE_PACKAGING + " or " +
                                              LUTECE_PLUGIN_PACKAGING + " or " + LUTECE_SITE_PACKAGING + " project." );
        }

        explodeWebapp( webappDirectory );
        explodeConfigurationFiles( webappDirectory );

    }
}
