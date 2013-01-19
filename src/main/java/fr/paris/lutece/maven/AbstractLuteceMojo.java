/*
 * Copyright (c) 2002-2013, Mairie de Paris
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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.project.MavenProject;

import org.codehaus.plexus.logging.LogEnabled;
import org.codehaus.plexus.logging.Logger;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Abstracts functionnality common to all Lutece mojos.
 */
public abstract class AbstractLuteceMojo
    extends AbstractMojo
    implements LogEnabled
{
    /**
     * The name of the lutece plugin artifact type.
     */
    protected static final String LUTECE_PLUGIN_TYPE = "lutece-plugin";

    /**
     * The name of the lutece core artifact type.
     */
    protected static final String LUTECE_CORE_TYPE = "lutece-core";

    /**
     * The name of the lutece core packaging.
     */
    protected static final String LUTECE_CORE_PACKAGING = "lutece-core";

    /**
     * The name of the lutece plugin packaging.
     */
    protected static final String LUTECE_PLUGIN_PACKAGING = "lutece-plugin";

    /**
     * The name of the lutece site packaging.
     */
    protected static final String LUTECE_SITE_PACKAGING = "lutece-site";

    /**
     * The name of the pom packaging.
     */
    protected static final String POM_PACKAGING = "pom";

    /**
     * The classifier used for the webapp artifact.
     *
     * @see PackageMojo
     */
    protected static final String WEBAPP_CLASSIFIER = "webapp";

    /**
     * The name of the lutece directory.
     */
    protected static final String LUTECE_DIRECTORY = "lutece";

    /**
     * Constants used to create inclusion or exclusion rules, for the zip, jar
     * or war building.
     */
    protected static final String INCLUDE_PATTERN_ALL = "**/**";
    protected static final String INCLUDE_PATTERN_SQL = "**/sql/**";
    protected static final String INCLUDE_PATTERN_SRC = "**/src/**";
    protected static final String INCLUDE_PATTERN_WEBAPP = "**/webapp/**";
    protected static final String INCLUDE_PATTERN_RESOURCES = "**/resources/**";
    protected static final String INCLUDE_PATTERN_SITE_USER = "**/user/**";
    protected static final String EXCLUDE_PATTERN_CLASSES = "**/WEB-INF/classes/**";
    protected static final String EXCLUDE_PATTERN_LIB = "WEB-INF/lib/**";
    protected static final String EXCLUDE_PATTERN_SVN = "**/.svn/**";
    protected static final String EXCLUDE_PATTERN_ASSEMBLY = "**/assembly/**";
    protected static final String EXCLUDE_PATTERN_TARGET = "**/target/**";
    protected static final String EXCLUDE_PATTERN_WEBAPP = "**/webapp/**";
    protected static final String EXCLUDE_PATTERN_JAVA = "**/java/**";
    protected static final String EXCLUDE_PATTERN_SITE = "**/site/**";
    protected static final String EXCLUDE_PATTERN_TEST = "**/test/**";
    protected static final String EXCLUDE_PATTERN_PACKAGE_HTML = "**/package.html";
    protected static final String EXCLUDE_PATTERN_RESOURCES = "**/fr/paris/lutece/**/resources/**";
    protected static final String EXCLUDE_PATTERN_BUSINESS = "**/fr/paris/lutece/**/business/**";
    protected static final String EXCLUDE_PATTERN_WEB = "**/fr/paris/lutece/**/web/**";
    protected static final String EXCLUDE_PATTERN_SERVICE = "**/fr/paris/lutece/**/service/**";
    protected static final String EXCLUDE_PATTERN_UTILS = "**/fr/paris/lutece/**/utils/**";
    protected static final String EXCLUDE_PATTERN_UTIL = "**/fr/paris/lutece/**/util/**";
    protected static final String EXCLUDE_PATTERN_WEBINF_TEST = "**/WEB-INF/test/**";

    // The files to include from the classes directory
    protected static final String[] PACKAGE_CLASSES_INCLUDES = new String[] { INCLUDE_PATTERN_ALL };

    // The classes to exclude from the classes directory
    protected static final String[] PACKAGE_CLASSES_EXCLUDES =
        new String[] { EXCLUDE_PATTERN_PACKAGE_HTML, EXCLUDE_PATTERN_RESOURCES };

    /**
     * The files to include in webapp archives.
     */
    protected static final String[] PACKAGE_WEBAPP_INCLUDES = new String[] { INCLUDE_PATTERN_ALL };
    protected static final String[] PACKAGE_WEBAPP_SITE_INCLUDES = new String[] { INCLUDE_PATTERN_SITE_USER };

    /**
     * The files to exclude from webapp archives.
     */
    protected static final String[] PACKAGE_WEBAPP_EXCLUDES =
        new String[] { EXCLUDE_PATTERN_CLASSES, EXCLUDE_PATTERN_LIB, EXCLUDE_PATTERN_SVN };
    protected static final String[] PACKAGE_WEBAPP_SITE_EXCLUDES = new String[] { EXCLUDE_PATTERN_SVN };

    /**
     * The files to includes from webapp archives.
     */
    protected static final String[] PACKAGE_WEBAPP_RESOURCES_INCLUDE = new String[] { INCLUDE_PATTERN_RESOURCES };

    /**
     * The files to exclude from webapp archives.
     */
    protected static final String[] PACKAGE_WEBAPP_RESOURCES_EXCLUDES =
        new String[]
        {
            EXCLUDE_PATTERN_BUSINESS, EXCLUDE_PATTERN_WEB, EXCLUDE_PATTERN_SERVICE, EXCLUDE_PATTERN_UTILS,
            EXCLUDE_PATTERN_UTIL, EXCLUDE_PATTERN_WEBINF_TEST, EXCLUDE_PATTERN_PACKAGE_HTML, EXCLUDE_PATTERN_SVN
        };

    /**
     * The files to include in zip archives.
     */
    protected static final String[] ASSEMBLY_WEBAPP_INCLUDES = new String[] { INCLUDE_PATTERN_ALL };
    protected static final String[] ASSEMBLY_WEBAPP_SITE_INCLUDES = new String[] { INCLUDE_PATTERN_SITE_USER };

    /**
     * The files to exclude from zip archives.
     */
    protected static final String[] ASSEMBLY_WEBAPP_EXCLUDES =
        new String[] { EXCLUDE_PATTERN_CLASSES, EXCLUDE_PATTERN_LIB, EXCLUDE_PATTERN_SVN };
    protected static final String[] ASSEMBLY_WEBAPP_SITE_EXCLUDES = new String[] { EXCLUDE_PATTERN_SVN };

    /**
     * Constante use for output path directory.
     */

    // The path to the sql directory
    protected static final String WEB_INF_SQL_PATH = "WEB-INF/sql/";

    //the path to the site directory
    protected static final String WEB_INF_DOC_XML_PATH = "doc/xml/";

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The directory containing the Java classes.
     *
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     */
    protected File classesDirectory;

    /**
     * The directory containing the default configuration files.
     *
     * @parameter expression="${basedir}/src/conf/default"
     * @required
     */
    protected File defaultConfDirectory;

    /**
     * The source directory for webapp components.
     *
     * @parameter expression="${basedir}/webapp"
     * @required
     */
    protected File webappSourceDirectory;

    /**
     * The directory containing the database sql script.
     *
     * @parameter expression="${basedir}/src/sql"
     */
    protected File sqlDirectory;

    /**
     * The directory containing the default user documentation.
     *
     * @parameter expression="${basedir}/src/site"
     */
    protected File siteDirectory;

    /**
     * The project's output directory
     *
     * @parameter expression="${project.build.directory}"
     * @required
     */
    protected File outputDirectory;

    /**
     * The projects in the reactor for aggregation report.
     *
     * @parameter expression="${reactorProjects}"
     * @readonly
     */
    protected List<MavenProject> reactorProjects;

    /**
    * Artifact collector, needed to resolve dependencies.
    *
    * @component
    */
    protected ArtifactCollector artifactCollector;

    /**
     * The set of artifacts required by the Multi Project, including transitive dependencies.
     *
     */
    protected static Set<Artifact> multiProjectArtifacts = new HashSet<Artifact>(  );

    /** The multi project artifacts. */
    protected static Set<Artifact> multiProjectArtifactsCopied = new HashSet<Artifact>(  );

    /**
    * Plexus logger needed for debugging manual artifact resolution.
    */
    protected Logger logger;

    /**
     * @see org.codehaus.plexus.logging.LogEnabled#enableLogging(org.codehaus.plexus.logging.Logger)
     */
    @Override
    public void enableLogging( Logger logger )
    {
        this.logger = logger;
    }
}
