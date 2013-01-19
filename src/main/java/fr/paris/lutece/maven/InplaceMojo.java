package fr.paris.lutece.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Builds a test webapp for a Lutece core project, directly in the
 * <code>webapp</code> directory.
 *
 * @goal inplace
 * @execute phase="process-classes"
 * @requiresDependencyResolution compile
 */
public class InplaceMojo
    extends AbstractLuteceWebappMojo
{
    /**
     * Executes the mojo on the current project.
     *
     * @throws MojoExecutionException
     *             if an error occured while exploding the webapp.
     */
    public void execute(  )
                 throws MojoExecutionException, MojoFailureException
    {
        if ( ! LUTECE_CORE_PACKAGING.equals( project.getPackaging(  ) ) )
        {
            throw new MojoExecutionException( "This goal can be invoked only on a " + LUTECE_CORE_PACKAGING +
                                              " project." );
        }

        explodeWebapp( webappSourceDirectory );
    }
}
