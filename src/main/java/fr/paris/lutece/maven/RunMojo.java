/*
 * Copyright (c) 2002-2015, Mairie de Paris
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

import fr.paris.lutece.maven.tomcat.TomcatLog;
import fr.paris.lutece.maven.utils.plugindat.PluginDataService;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Server;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.util.StandardSessionIdGenerator;
import org.apache.catalina.valves.AbstractAccessLogValve;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.jasper.servlet.JasperInitializer;
import org.apache.jasper.servlet.JspServlet;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.TypeArtifactFilter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.scan.StandardJarScanFilter;
import org.apache.tomcat.util.scan.StandardJarScanner;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import java.io.BufferedReader;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.Collections.emptySet;
import static java.util.Locale.ROOT;
import static org.apache.maven.plugins.annotations.ResolutionScope.COMPILE;

/**
 * Plugin intended to run a dev instance of lucene from a plugin.
 * It avoids to create a fake/demo/example module to test a plugin during its development.
 * <p>
 * IMPORTANT: {@code mvn package} (to run assembly mojo) is required before this plugin execution.
 * It is generally already bound in maven module lifecyle thanks its packaging.
 *
 * @goal run
 * @requiresDependencyResolution compile
 */
// dev note: @Parameter are not yet supported by the plugin version used in the pom but it is used to enable to drop
//           legacy javadoc annotations.
@Mojo(name = "run", requiresDependencyResolution = COMPILE)
public class RunMojo extends AbstractAssemblySiteMojo {
    /**
     * Webapp work directory (for JSP etc).
     *
     * @parameter expression="${lutece.run.workDir}" default-value="${project.build.directory}/run_workdir"
     */
    @Parameter(property = "lutece.run.workDir", defaultValue = "${project.build.directory}/run_workdir")
    private File workdir;

    /**
     * Should the exploded directory be used or the war.
     *
     * @parameter expression="${lutece.run.useWar}" default-value="false"
     */
    @Parameter(property = "lutece.run.useWar", defaultValue = "false")
    private boolean useWar;

    /**
     * Should HSQLDB be used instead of MySQL.
     *
     * @parameter expression="${lutece.run.useHSQLDB}" default-value="true"
     */
    @Parameter(property = "lutece.run.useHSQLDB", defaultValue = "true")
    private boolean useHSQLDB;

    /**
     * Should Tomcat JMX be disabled (faster to startup).
     *
     * @parameter expression="${lutece.run.tomcat.disableJMX}" default-value="true"
     */
    @Parameter(property = "lutece.run.tomcat.disableJMX", defaultValue = "true")
    private boolean disableJMX;

    /**
     * Tomcat port.
     *
     * @parameter expression="${lutece.run.tomcat.port}" default-value="8080"
     */
    @Parameter(property = "lutece.run.tomcat.port", defaultValue = "8080")
    private int port;

    /**
     * Context (webapp) name, default to ROOT.
     *
     * @parameter expression="${lutece.run.tomcat.context}" default-value="/"
     */
    @Parameter(property = "lutece.run.tomcat.context", defaultValue = "/")
    private String context;

    /**
     * Context (webapp) name, default to ROOT.
     *
     * @parameter expression="${lutece.run.tomcat.jarFilterExclusions}" default-value="/"
     */
    @Parameter(property = "lutece.run.tomcat.jarFilterExclusions", defaultValue = "")
    private String jarFilterExclusions;

    /**
     * Custom SQL scripts to executes (often plugins or upgrades ones).
     * Path is relative to the execution directory so ensure to use {@code ${project.build.directory}} or other maven variables.
     * It is only done for HSQLDB mode, not for MySQL one which is assumed reusing an external/existing database.
     *
     * @parameter
     */
    @Parameter
    private List<String> sqlScripts;

    /**
     * Custom core artifacts to use.
     *
     * @parameter
     */
    @Parameter
    private List<DefaultArtifact> coreArtifacts;

    /**
     * Custom plugins artifacts to use.
     *
     * @parameter
     */
    @Parameter
    private List<DefaultArtifact> pluginArtifacts;

    /**
     * Custom site artifacts to use.
     *
     * @parameter
     */
    @Parameter
    private List<DefaultArtifact> siteArtifacts;

    @Override
    protected Set<Artifact> filterArtifacts(final ArtifactFilter filter) {
        if (!TypeArtifactFilter.class.isInstance(filter)) {
            return super.filterArtifacts(filter);
        }
        try {
            final Field field = TypeArtifactFilter.class.getDeclaredField("type");
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            final String type = String.valueOf(field.get(filter));
            switch (type) {
                case LUTECE_CORE_TYPE:
                    return coreArtifacts == null || coreArtifacts.isEmpty() ?
                            super.filterArtifacts(filter) :
                            new HashSet<>(coreArtifacts);
                case LUTECE_PLUGIN_TYPE:
                    return pluginArtifacts == null || pluginArtifacts.isEmpty() ?
                            super.filterArtifacts(filter) :
                            new HashSet<>(pluginArtifacts);
                case LUTECE_SITE_TYPE:
                    return siteArtifacts == null || siteArtifacts.isEmpty() ?
                            super.filterArtifacts(filter) :
                            new HashSet<>(siteArtifacts);
                default:
                    return super.filterArtifacts(filter);

            }
        } catch (final Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    protected File prepare() throws MojoExecutionException {
        final File explodedDir = super.prepare();
        if (useHSQLDB) {
            setupHSQLDB(explodedDir);
        }
        PluginDataService.generatePluginsDataFile(explodedDir.getAbsolutePath());
        return explodedDir;
    }

    @Override
    public void execute() throws MojoExecutionException {
        run(useWar ? assemblySite() : prepare());
    }

    private void setupHSQLDB(final File explodedDir) {
        final String artifactId = "hsqldb";
        final String version = "2.3.4";
        try {
            final File out = new File(explodedDir, "WEB-INF/lib/" + artifactId + "-" + version + ".jar");
            if (!out.exists()) { // already done
                final Artifact artifact = artifactFactory.createArtifact("org.hsqldb", artifactId, version, "compile", "jar");
                resolver.resolve(artifact, remoteRepositories, localRepository);
                FileUtils.copyFile(artifact.getFile(), out);
            }

            Files.write(explodedDir.toPath().resolve("WEB-INF/conf/db.properties"), ("" +
                    "portal.poolservice=fr.paris.lutece.util.pool.service.LuteceConnectionService\n" +
                    "portal.driver=org.hsqldb.jdbc.JDBCDriver\n" +
                    "portal.url=jdbc:hsqldb:mem:lutece;mode=MySQL\n" +
                    "portal.user=SA\n" +
                    "portal.password=\n" +
                    "portal.initconns=2\n" +
                    "portal.maxconns=10\n" +
                    "portal.logintimeout=2\n" +
                    "portal.checkvalidconnectionsql=SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS\n" +
                    "").getBytes(StandardCharsets.UTF_8));
        } catch (final ArtifactResolutionException | ArtifactNotFoundException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void run(final File dir) {
        if (disableJMX) {
            Registry.disableRegistry();
        }

        TomcatLog.delegate = getLog();

        try {
            final Tomcat tomcat = new NoBaseDirTomcat();
            tomcat.setBaseDir("");
            tomcat.setPort(port);

            final StandardHost host = new StandardHost();
            host.setAutoDeploy(false);
            host.setName("localhost");
            tomcat.getEngine().addChild(host);

            try {
                tomcat.init();
            } catch (final LifecycleException e) {
                try {
                    tomcat.destroy();
                } catch (final LifecycleException ex) {
                    // no-op
                }
                throw new IllegalStateException(e);
            }
            try {
                tomcat.start();
            } catch (final LifecycleException e) {
                stop(tomcat);
                throw new IllegalStateException(e);
            }

            // bind the connector
            tomcat.getConnector();

            // deploy the app
            final StandardContext ctx = new StandardContext();
            ctx.setWorkDir(workdir.getAbsolutePath());
            ctx.setPath(context.equals("/") ? "" : context);
            ctx.setName(context.startsWith("/") ? context.substring(1) : context);
            ctx.setParentClassLoader(getClass().getClassLoader());
            ctx.setDocBase(dir.getAbsolutePath());
            ctx.setFailCtxIfServletStartFails(true);
            ctx.addLifecycleListener(new ContextConfig());
            ctx.addServletContainerInitializer(new JasperInitializer(), emptySet());
            ctx.addServletContainerInitializer((set, servletContext) -> { // replaces default web.xml
                final ServletRegistration.Dynamic def = servletContext.addServlet("default", DefaultServlet.class);
                def.setInitParameter("listings", "false");
                def.setInitParameter("debug", "0");
                def.setLoadOnStartup(1);
                def.addMapping("/");

                final ServletRegistration.Dynamic jspDef = servletContext.addServlet("jsp", new JspServlet());
                if (jspDef != null) {
                    jspDef.setInitParameter("fork", "false");
                    jspDef.setInitParameter("xpoweredBy", "false");
                    jspDef.setInitParameter("development", "true");
                    jspDef.setLoadOnStartup(3);
                    jspDef.addMapping("*.jsp");
                    jspDef.addMapping("*.jspx");
                }

            }, emptySet());
            ctx.setJarScanner(createScanFilter());
            if (useHSQLDB) {
                ctx.addServletContainerInitializer((ignored, servletContext) -> initHSQLDBDatabase(dir, servletContext), emptySet());
            }

            Tomcat.addDefaultMimeTypeMappings(ctx);

            final Pipeline pipeline = ctx.getPipeline();
            pipeline.addValve(new AbstractAccessLogValve() {
                @Override
                protected void log(final CharArrayWriter charArrayWriter) {
                    getLog().info(charArrayWriter.toString());
                }
            });
            pipeline.addValve(new ErrorReportValve());

            final StandardManager mgr = new StandardManager();
            mgr.setSessionIdGenerator(new FastSessionIdGenerator());
            ctx.setManager(mgr);

            tomcat.getHost().addChild(ctx);

            final LifecycleState state = ctx.getState();
            if (state == LifecycleState.STOPPED || state == LifecycleState.FAILED) {
                try {
                    stop(tomcat);
                } catch (final RuntimeException re) {
                    // no-op
                }
                throw new IllegalStateException("Context didn't start");
            }

            getLog().info("Hit ENTER to exit.");
            new Scanner(System.in).nextLine();
        } finally {
            TomcatLog.delegate = null;
        }
    }

    private StandardJarScanner createScanFilter() {
        final StandardJarScanFilter jarScanFilter = new StandardJarScanFilter();
        jarScanFilter.setTldSkip("tomcat.util.scan.StandardJarScanFilter.jarsToSkip=" +
                "annotations-api.jar," +
                "ant-junit*.jar," +
                "ant-launcher.jar," +
                "ant.jar," +
                "asm-*.jar," +
                "aspectj*.jar," +
                "bootstrap.jar," +
                "catalina-ant.jar," +
                "catalina-ha.jar," +
                "catalina-ssi.jar," +
                "catalina-storeconfig.jar," +
                "catalina-tribes.jar," +
                "catalina.jar," +
                "cglib-*.jar," +
                "cobertura-*.jar," +
                "commons-beanutils*.jar," +
                "commons-codec*.jar," +
                "commons-collections*.jar," +
                "commons-daemon.jar," +
                "commons-dbcp*.jar," +
                "commons-digester*.jar," +
                "commons-fileupload*.jar," +
                "commons-httpclient*.jar," +
                "commons-io*.jar," +
                "commons-lang*.jar," +
                "commons-logging*.jar," +
                "commons-math*.jar," +
                "commons-pool*.jar," +
                "derby-*.jar," +
                "dom4j-*.jar," +
                "easymock-*.jar," +
                "ecj-*.jar," +
                "el-api.jar," +
                "geronimo-spec-jaxrpc*.jar," +
                "h2*.jar," +
                "ha-api-*.jar," +
                "hamcrest-*.jar," +
                "hibernate*.jar," +
                "httpclient*.jar," +
                "icu4j-*.jar," +
                "jasper-el.jar," +
                "jasper.jar," +
                "jaspic-api.jar," +
                "jaxb-*.jar," +
                "jaxen-*.jar," +
                "jaxws-rt-*.jar," +
                "jdom-*.jar," +
                "jetty-*.jar," +
                "jmx-tools.jar," +
                "jmx.jar," +
                "jsp-api.jar," +
                "jstl.jar," +
                "jta*.jar," +
                "junit-*.jar," +
                "junit.jar," +
                "log4j*.jar," +
                "mail*.jar," +
                "objenesis-*.jar," +
                "oraclepki.jar," +
                "oro-*.jar," +
                "servlet-api-*.jar," +
                "servlet-api.jar," +
                "slf4j*.jar," +
                "taglibs-standard-spec-*.jar," +
                "tagsoup-*.jar," +
                "tomcat-api.jar," +
                "tomcat-coyote.jar," +
                "tomcat-dbcp.jar," +
                "tomcat-i18n-*.jar," +
                "tomcat-jdbc.jar," +
                "tomcat-jni.jar," +
                "tomcat-juli-adapters.jar," +
                "tomcat-juli.jar," +
                "tomcat-util-scan.jar," +
                "tomcat-util.jar," +
                "tomcat-websocket.jar," +
                "tools.jar," +
                "websocket-api.jar," +
                "wsdl4j*.jar," +
                "xercesImpl.jar," +
                "xml-apis.jar," +
                "xmlParserAPIs-*.jar," +
                "xmlParserAPIs.jar," +
                "xom-*.jar," +
                // specific
                "activation-*.jar," +
                "apache-mime4j-core-*.jar," +
                "apache-mime4j-dom-*.jar," +
                "asm-*.jar," +
                "bcmail-jdk15on-*.jar," +
                "bcpkix-jdk15on-*.jar," +
                "bcprov-jdk15on-*.jar," +
                "bcutil-jdk15on-*.jar," +
                "boilerpipe-*.jar," +
                "c3p0*.jar," +
                "cglib-*.jar," +
                "checker-qual-*.jar," +
                "classmate-*.jar," +
                "commons-beanutils-*.jar," +
                "commons-codec-*.jar," +
                "commons-collections-*.jar," +
                "commons-collections*.jar," +
                "commons-compress-*.jar," +
                "commons-csv-*.jar," +
                "commons-dbcp*.1.jar," +
                "commons-digester-*.jar," +
                "commons-digester*.jar," +
                "commons-exec-*.jar," +
                "commons-fileupload-*.jar," +
                "commons-http*.jar," +
                "commons-io-*.jar," +
                "commons-lang*.jar," +
                "commons-logging-*.jar," +
                "commons-math*.jar," +
                "commons-pool*.jar," +
                "commons-text-*.jar," +
                "curvesapi-*.jar," +
                "dd-plist-*.jar," +
                "dec-*.jar," +
                "ehcache-core-*.jar," +
                "ehcache-web-*.jar," +
                "error_prone_annotations-*.jar," +
                "failureaccess-*.jar," +
                "fontbox-*.jar," +
                "freemarker-*.jar," +
                "gson-*.jar," +
                "guava-*-jre.jar," +
                "hibernate-validator-*.Final.jar," +
                "HikariCP-java*.jar," +
                "hsqldb-*.jar," +
                "isoparser-*.jar," +
                "istack-commons-runtime-*.jar," +
                "j2objc-annotations-*.jar," +
                "jackson-annotations-*.jar," +
                "jackson-core-*.jar," +
                "jackson-databind-*.jar," +
                "jackson-jaxrs-*.jar," +
                "jackson-mapper-asl-*.jar," +
                "jackson-module-parameter-names-*.jar," +
                "jackson-xc-*.jar," +
                "jai-imageio-core-*.jar," +
                "jakarta.activation-api-*.jar," +
                "jakarta.annotation-api-*.jar," +
                "jakarta.xml.bind-api-*.jar," +
                "javassist-*.jar," +
                "javax.annotation-api-*.jar," +
                "javax.inject-1.jar," +
                "javax.mail-*.jar," +
                "javax.persistence-*.jar," +
                "jaxb-runtime-*.jar," +
                "jbig2-imageio-*.jar," +
                "jboss-logging-*.Final.jar," +
                "jcip-annotations-*.jar," +
                "jcommander-*.jar," +
                "jdom*.jar," +
                "jempbox-*.jar," +
                "jersey-atom-*.jar," +
                "jersey-client-*.jar," +
                "jersey-core-*.jar," +
                "jersey-json-*.jar," +
                "jersey-server-*.jar," +
                "jersey-spring-*.jar," +
                "jettison-*.jar," +
                "jhighlight-*.jar," +
                "jmatio-*.jar," +
                "jna-*.jar," +
                "json-simple-*.jar," +
                "jsoup-*.jar," +
                "jsr*.jar," +
                "jtidy-*.jar," +
                "jwt-*.jar," +
                "jjwt-*.jar," +
                "juniversalchardet-*.jar," +
                "junrar-*.jar," +
                "library-freemarker-*.jar," +
                "library-jmx-api-*.jar," +
                "library-jwt-*.jar," +
                "library-lucene-*.jar," +
                "library-rbac-api-*.jar," +
                "library-signrequest-*.jar," +
                "library-user-api-*.jar," +
                "library-workflow-core-*.jar," +
                "library-workgroup-api-*.jar," +
                "listenablefuture-*.jar," +
                "log4j-*-api-*.jar," +
                "log4j-api-*.jar," +
                "log4j-core-*.jar," +
                "log4j-jcl-*.jar," +
                "log4j-slf4j-impl-*.jar," +
                "log4j-web-*.jar," +
                "lucene-analyzers-common-*.jar," +
                "lucene-core-*.jar," +
                "lucene-highlighter-*.jar," +
                "lucene-memory-*.jar," +
                "lucene-misc-*.jar," +
                "lucene-queries-*.jar," +
                "lucene-queryparser-*.jar," +
                "lucene-sandbox-*.jar," +
                "lucene-suggest-*.jar," +
                "lutece-core-*.jar," +
                "mchange-commons-java-*.jar," +
                "metadata-extractor-*.jar," +
                "mysql-connector-java-*.jar," +
                "opencsv-*.jar," +
                "openjson-*.jar," +
                "parso-*.jar," +
                "pdfbox-*.jar," +
                "pdfbox-tools-*.jar," +
                "plugin-rest-*.jar," +
                "poi-*.jar," +
                "poi-ooxml-*.jar," +
                "poi-ooxml-schemas-*.jar," +
                "poi-scratchpad-*.jar," +
                "preflight-*.jar," +
                "protobuf-java-*.jar," +
                "quartz-*.jar," +
                "rome-*.jar," +
                "rome-utils-*.jar," +
                "scannotation-*.jar," +
                "sentiment-analysis-parser-*.jar," +
                "slf4j-api-*.jar," +
                "SparseBitSet-*.jar," +
                "spring-aop-*.jar," +
                "spring-beans-*.jar," +
                "spring-context-*.jar," +
                "spring-core-*.jar," +
                "spring-expression-*.jar," +
                "spring-jdbc-*.jar," +
                "spring-orm-*.jar," +
                "spring-tx-*.jar," +
                "spring-web-*.jar," +
                "stax-api-*.jar," +
                "stax2-api-*.jar," +
                "tagsoup-*.jar," +
                "tika-core-*.jar," +
                "tika-parsers-*.jar," +
                "txw*.jar," +
                "validation-api-*.Final.jar," +
                "woodstox-core-*.jar," +
                "xercesImpl-*.jar," +
                "xml-apis-*.jar," +
                "xmlbeans-*.jar," +
                "xmpbox-*.jar," +
                "xmpcore-shaded-*.jar," +
                "xalan-*.jar," +
                "xz-*.jar" +
                (jarFilterExclusions != null && !jarFilterExclusions.isEmpty() ? "," + jarFilterExclusions : ""));

        final StandardJarScanner jarScanner = new StandardJarScanner();
        jarScanner.setScanBootstrapClassPath(false);
        jarScanner.setScanClassPath(false);
        jarScanner.setJarScanFilter(jarScanFilter);
        return jarScanner;
    }

    private void stop(final Tomcat tomcat) {
        try {
            tomcat.stop();
            tomcat.destroy();
            final Server server = tomcat.getServer();
            if (server != null) { // give a change to stop the utility executor otherwise it just leaks and stop later
                final ExecutorService utilityExecutor = server.getUtilityExecutor();
                if (utilityExecutor != null) {
                    try {
                        utilityExecutor.awaitTermination(1, TimeUnit.MINUTES);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch (final LifecycleException e) {
            throw new IllegalStateException(e);
        }
    }

    private void initHSQLDBDatabase(final File dir, final ServletContext servletContext) throws ServletException {
        try (final Connection c = servletContext.getClassLoader()
                .loadClass("org.hsqldb.jdbc.JDBCDriver")
                .asSubclass(Driver.class)
                .getConstructor()
                .newInstance()
                .connect("jdbc:hsqldb:mem:lutece", new Properties() {{
                    setProperty("user", "sa");
                    setProperty("password", "");
                }});
             final Statement statement = c.createStatement()) {
            final Path base = dir.toPath();
            hsqldbStatement(base.resolve("WEB-INF/sql/create_db_lutece_core.sql"), statement);
            hsqldbStatement(base.resolve("WEB-INF/sql/init_db_lutece_core.sql"), statement);
            if (sqlScripts != null && !sqlScripts.isEmpty()) {
                for (final String script : sqlScripts) {
                    hsqldbStatement(Paths.get(script), statement);
                }
            }
        } catch (final Exception e) {
            throw new ServletException(e);
        }
    }

    private void hsqldbStatement(final Path script, final Statement statement) throws SQLException, ServletException {
        try (final BufferedReader reader = Files.newBufferedReader(script)) {
            String line;
            final boolean debug = getLog().isDebugEnabled();
            final StringBuilder buffer = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("--") || line.trim().isEmpty()) {
                    if (buffer.length() > 0) {
                        doExecuteHSQLDB(statement, buffer.toString(), debug);
                        buffer.setLength(0);
                    }
                    continue;
                }

                if (buffer.length() > 0) {
                    buffer.append(' ');
                }
                buffer.append(line.trim());
                if (buffer.length() > 0 && buffer.charAt(buffer.length() - 1) == ';') {
                    doExecuteHSQLDB(statement, buffer.toString(), debug);
                    buffer.setLength(0);
                }
            }
            if (buffer.length() > 0) {
                doExecuteHSQLDB(statement, buffer.toString(), debug);
            }
        } catch (final IOException e) {
            throw new ServletException(e);
        }
    }

    private void doExecuteHSQLDB(final Statement statement, final String line, final boolean debug) throws SQLException {
        if (debug) {
            getLog().debug("Executing " + line);
        }
        statement.execute(rewriteHSQLDBStatement(line));
    }

    private String rewriteHSQLDBStatement(final String line) {
        final String testable = line.toLowerCase(ROOT)
                .replace("  ", " "); // there are some typo in some scrpits, tolerate them
        if (testable.contains("create table")) { // if we are a DDL statement, we fix the types for HSQLDB
            return line
                    .replace(" AUTO_INCREMENT", " IDENTITY")
                    .replace(" auto_increment", " IDENTITY")
                    .replace(" LONG VARCHAR", " LONGVARCHAR")
                    .replace(" long varchar", " LONGVARCHAR")
                    .replace(" LONG VARBINARY", " LONGVARBINARY")
                    .replace(" long varbinary", " LONGVARBINARY")
                    .replace(" LONG ", " BIGINT ")
                    .replace(" long ", " BIGINT ");
        }
        if (testable.startsWith("insert into")) {
            return replaceHSQLDBBinaryValue(line, testable)
                    .replace("\\'", "''");
        }
        return line;
    }

    private String replaceHSQLDBBinaryValue(final String fallback, final String testable) {
        final int binaryStart = testable.indexOf(",0x");
        if (binaryStart > 0) {
            final int end = testable.indexOf(")", binaryStart);
            final int previousEnd = testable.indexOf(",", binaryStart + 1);
            final int usedEnd = previousEnd < 0 ? end : previousEnd;
            final String value = testable.substring(0, binaryStart) +
                    ",X'" +
                    testable.substring(binaryStart + ",0x".length(), usedEnd) + "'" +
                    testable.substring(usedEnd);
            return replaceHSQLDBBinaryValue(value, value);
        }
        return fallback;
    }

    private static class NoBaseDirTomcat extends Tomcat {
        @Override
        protected void initBaseDir() {
            // no-op
        }
    }

    private static class FastSessionIdGenerator extends StandardSessionIdGenerator {
        @Override
        protected void getRandomBytes(final byte bytes[]) {
            ThreadLocalRandom.current().nextBytes(bytes);
        }
    }
}
