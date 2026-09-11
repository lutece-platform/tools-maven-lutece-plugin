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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.w3c.dom.Document;

import fr.paris.lutece.utils.sql.PluginVersion;
import fr.paris.lutece.utils.sql.SqlPathInfo;

/**
 * Tags SQL resources with liquibase tags.
 * 
 * The default behaviour is to overwrite SQL files.
 * 
 */

@Mojo(name = "liquibase-sql" ,
requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
       )
@Execute ( goal = "liquibase-sql",
       phase=LifecyclePhase.PROCESS_RESOURCES )
public class LiquiBaseSqlMojo extends AbstractLuteceWebappMojo
{

    public static final String MICROPROFILE_CONFIG_PROPERTIES_FILE ="microprofile-config.properties";   
    private static final String CORE = "core";
    public static final String SQL_EXT = ".sql";
    private static final String XML_EXT = ".xml";
    private static final String LIQUIBASE_SQL_HEADER = "-- liquibase formatted sql";
    private static final String LIQUIBASE_SQL_HEADER_2 = "--liquibase formatted sql";

    private static final String EOL = "\n";
    /** Sub-directory of the build output directory used by {@link #dryRun}. */
    private static final String TARGET_SUBDIRECTORY = "liquibasesql";
    /** Location of the plugin descriptors, relative to the webapp source directory. */
    private static final String PLUGIN_CONF_SUBPATH = "WEB-INF/plugins";
    /** Prefix expected by {@link SqlPathInfo#parse(String)}. */
    private static final String SQL_PATH_PREFIX = "sql/";
    /** Directories in which {@link SqlPathInfo} expects a versioned upgrade script. */
    private static final Pattern UPGRADE_DIRECTORY_PATTERN = Pattern.compile(".*/upgrades?/[^/]+");
   
    /**
     * Dry run creates files in target instead of replacing
     */
    @Parameter(property = "dryRun", defaultValue = "false")
    private boolean dryRun;

    // default values for core : we suppose that the version is always good
    private String pluginName = CORE, version = null;
    // track most recent version number in update script
    private PluginVersion mostRecentSqlScriptVersion = null;

    public void execute() throws MojoExecutionException, MojoFailureException
    {
        try
        {
            processPluginXmls();
            processSqlFiles();
            if (!CORE.equals(pluginName))
            {
                getLog().info("Detected version is " + version + " for plugin " + pluginName + ". Please correct it if needed.");
                PluginVersion pluginVersion = PluginVersion.of(version);
                if (mostRecentSqlScriptVersion != null && mostRecentSqlScriptVersion.compareTo(pluginVersion) > 0)
                    getLog().error("Some SQL files have version " + mostRecentSqlScriptVersion + " for plugin " + pluginName + " with version " + version);
            }
        } catch (IOException | SqlProcessingException e)
        {
            throw new MojoExecutionException("Failed to process SQL files : " + e.getMessage(), e);
        }
    }

    private void processPluginXmls() throws IOException
    {
        Path pluginConfDirectory = webappSourceDirectory.toPath().resolve(PLUGIN_CONF_SUBPATH);

        if (!Files.isDirectory(pluginConfDirectory))
        {
            getLog().warn("No plugin descriptor directory " + pluginConfDirectory
                    + " : SQL files will be tagged as '" + CORE + "'");
            return;
        }

        // Supposes there will always be only one XML file.
        try (Stream<Path> filePathStream = Files.walk(pluginConfDirectory))
        {
            filePathStream.filter(pluginFileFilter).findAny().ifPresent(this::processPluginXml);
        }
    }

    private void processPluginXml(Path path)
    {
        try
        {
            Document doc = newSafeDocumentBuilder().parse(path.toFile());
            version = doc.getElementsByTagName("version").item(0).getTextContent();
            pluginName = doc.getElementsByTagName("name").item(0).getTextContent();
        } catch (Exception e)
        {
            throw new SqlProcessingException("Could not read the plugin descriptor " + path, e);
        }
    }

    /**
     * Builds a parser that resolves no external entity. A plugin descriptor is a project file,
     * but the build must not read whatever an entity points at, nor reach out to the network.
     * The DOCTYPE itself stays allowed : older descriptors may declare one.
     *
     * @return a hardened document builder
     * @throws ParserConfigurationException
     *             if the parser rejects the configuration
     */
    static DocumentBuilder newSafeDocumentBuilder() throws ParserConfigurationException
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        return factory.newDocumentBuilder();
    }

    /**
     * Raised when a SQL file or a plugin descriptor cannot be processed. Unchecked because the
     * processing runs inside a Stream, but always turned into a MojoExecutionException by
     * {@link #execute()} so that Maven reports it properly.
     */
    static class SqlProcessingException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        SqlProcessingException(String strMessage, Throwable cause)
        {
            super(strMessage, cause);
        }
    }

    private static final Predicate<? super Path> sqlFileFilter = path -> fileFilter(path, SQL_EXT);
    private static final Predicate<? super Path> pluginFileFilter = path -> fileFilter(path, XML_EXT);


    private static final boolean fileFilter(Path path, String ext)
    {
        try
        {
            return Files.isRegularFile(path) && Files.size(path) > 0 && path.toString().toLowerCase().endsWith(ext);
        } catch (IOException e)
        {
            return false;
        }
    }

    private void processSqlFiles() throws IOException
    {
        Path sqlRoot = getSqlRoot();

        if (!Files.isDirectory(sqlRoot))
        {
            getLog().info("No SQL directory " + sqlRoot + " : nothing to tag");
            return;
        }

        try (Stream<Path> filePathStream = Files.walk(sqlRoot))
        {
            filePathStream.filter(sqlFileFilter).forEach(this::transformFile);
        }
    }

    /**
     * @return the SQL source directory, as an absolute normalized path
     */
    private Path getSqlRoot()
    {
        return sqlDirectory.toPath().toAbsolutePath().normalize();
    }

    /**
     * Builds the path expected by {@link SqlPathInfo#parse(String)}, ie the path of the file
     * relative to the SQL source directory, prefixed by "sql/" and using "/" separators.
     *
     * @param path an SQL file located below the SQL source directory
     * @return the corresponding "sql/..." path
     */
    private String getSqlPathInfoPath(Path path)
    {
        Path relative = getSqlRoot().relativize(path.toAbsolutePath().normalize());

        return SQL_PATH_PREFIX + relative.toString().replace(File.separatorChar, '/');
    }

    /**
     * Returns true if the content is already tagged with a liquibase tag
     * 
     * @param content
     * @return the current "tagged" status
     */
    public static boolean isTaggedWithLiquibase(String content)
    {
        return content.startsWith(LIQUIBASE_SQL_HEADER) || content.startsWith(LIQUIBASE_SQL_HEADER_2);
    }

    public static boolean isTaggedWithLiquibase(File candidate,List<String> listFileErrors,String strBasePath)
    {
        try (BufferedReader reader = Files.newBufferedReader(candidate.toPath());)
        {
            boolean isTaggedWithLiquibase = isTaggedWithLiquibase(reader.readLine());
            //add to error list if not tagged
            if(!isTaggedWithLiquibase)
            {
                listFileErrors.add(getAbsoluteSqlFilePath(candidate, strBasePath));
            }
            return isTaggedWithLiquibase;
        } catch (Exception e)
        {
            // we do not care about the exact nature of the problem
            // if we could not read it, we just do not include it
              listFileErrors.add(getAbsoluteSqlFilePath(candidate, strBasePath)); 
              return false;
          
        }
    }

    /**
     * Prepends liquibase tags at the beginning of the given SQL file, it not present
     * 
     * @param path an SQL file to be processed
     */
    private void transformFile(Path path)
    {
        try
        {
            if (!CORE.equals(pluginName))
            {
                getLog().debug("Processing " + path);
                trackMostRecentVersion(path);
            }
            // we suppose that all SQL files are UTF-8.
            // if that's not the case, we need a way to get that info for EACH input file
            String content = readString(path);
            if(content.trim().isEmpty()||!containsSqlOrders(content))
            {
                getLog().info("Ignoring file without SQL commands: " + path);
            }
            else if (!isTaggedWithLiquibase(content))
            {
                StringBuilder result = new StringBuilder();
                result.append(LIQUIBASE_SQL_HEADER).append(EOL);
                result.append("-- changeset ").append(pluginName).append(":").append(path.getFileName()).append(EOL);
                result.append("-- preconditions onFail:MARK_RAN onError:WARN").append(EOL);
                result.append(content);
                Path outputPath = generateOutputPath(path);
                getLog().info("Writing tag+content to file " + outputPath);
                Files.write(outputPath, result.toString().getBytes(StandardCharsets.UTF_8));
            }else if(needsFixing(content))
            {
            	String fixedContent= fixLiquibaseComments(content);
                Path outputPath = generateOutputPath(path);
                Files.write(outputPath, fixedContent.getBytes(StandardCharsets.UTF_8));
                getLog().info("Fixing formatting in Liquibase file: " + path);
            }
            else
            {
                getLog().info("File already in Liquibase format, ignoring: " + path);
            }
        } catch (Exception e)
        {
            throw new SqlProcessingException("Error processing SQL file " + path, e);
        }
    }
    /**
     * Keeps track of the most recent version found in the upgrade script names, so that it can be
     * compared with the version declared in the plugin descriptor.
     *
     * @param path
     *            an SQL file located below the SQL source directory
     */
    private void trackMostRecentVersion(Path path)
    {
        String strSqlPath = getSqlPathInfoPath(path);
        SqlPathInfo sqlPath = SqlPathInfo.parse(strSqlPath);

        if (sqlPath == null)
        {
            getLog().warn("SQL file " + strSqlPath + " does not follow the Lutece SQL naming convention :"
                    + " its version cannot be checked and it will not be managed by Liquibase at runtime");
            return;
        }

        if (!sqlPath.isCreate() && sqlPath.getDstVersion() != null
                && (mostRecentSqlScriptVersion == null || mostRecentSqlScriptVersion.compareTo(sqlPath.getDstVersion()) < 0))
        {
            mostRecentSqlScriptVersion = sqlPath.getDstVersion();
        }
    }

    /** 
     * re-implementation of Files.readString (jdk11+), which does not exist in JDK8
     * @throws IOException 
     * */
    private static String readString(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private Path generateOutputPath(Path inputPath) throws IOException
    {
        Path sqlRoot = getSqlRoot();
        Path relative = sqlRoot.relativize(inputPath.toAbsolutePath().normalize());
        Path targetRoot = dryRun ? outputDirectory.toPath().resolve(TARGET_SUBDIRECTORY) : sqlRoot;

        Path outputPath = targetRoot.resolve(relative);
        Files.createDirectories(outputPath.getParent());
        return outputPath;
    }
    /**
     * fix Liquibase Comments
     * @param content the content to fix
     * @return the content fixed
     */
    private String fixLiquibaseComments(String content)
    {
        return content
                .replaceAll("(?m)^--(?=liquibase formatted sql)", "-- ")
                .replaceAll("(?m)^--(?=changeset)", "-- ")
                .replaceAll("(?m)^--(?=preconditions)", "-- ");
    }
    /**
     * Checks whether the content contains incorrectly formatted Liquibase comments.
     * This typically means missing a space after the double dash "--".
     *
     * The method looks for:
     * <ul>
     *   <li>--liquibase formatted sql</li>
     *   <li>--changeset</li>
     *   <li>--preconditions</li>
     * </ul>
     *
     * @param content The content of the SQL file
     * @return true if formatting needs to be fixed; false if already properly formatted
     */
    private boolean needsFixing(String content)
    {
        Pattern pattern = Pattern.compile("(?m)^--(?=liquibase formatted sql|changeset|preconditions)");
        Matcher matcher = pattern.matcher(content);
        return matcher.find();
    }


    /**
     * Checks if the given text contains SQL orders like SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, TRUNCATE, GRANT, REVOKE.
     * The check is case-insensitive and looks for whole words only.
     *
     * @param content The text to check
     * @return true if any SQL order is found; false otherwise
     */
    public static boolean containsSqlOrders(String content) {
        // Liste de mots-clés SQL à rechercher
        String regex = "\\b(SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|TRUNCATE|GRANT|REVOKE)\\b";
        
        // Insensible à la casse
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);

        return matcher.find();
    }

  public static boolean isFileManagedByLiquibase(File candidate,String strBasePath ) {

    
      return  SqlPathInfo.parse( getAbsoluteSqlFilePath(candidate, strBasePath)) !=null;
      
    }



    /** 
     * get the absolute sql file path from the candidate file
     * @param candidate
     * @param strBasePath
     * @return
     */
   static String getAbsoluteSqlFilePath(File candidate,String strBasePath ) {
         return normalizeSeparators( candidate.getPath().substring(strBasePath.length()-3 ) );
   }

    /**
     * Replaces Windows separators by "/", the separator the {@link SqlPathInfo} patterns are
     * written with. Without this, no path is ever recognized on Windows and no SQL file reaches
     * WEB-INF/classes/sql.
     *
     * @param strPath
     *            the path to normalize
     * @return the path with "/" separators
     */
    static String normalizeSeparators(String strPath)
    {
        return strPath.replace('\\', '/');
    }

    /**
     * Tells whether an SQL file sits in an upgrade directory, ie where {@link SqlPathInfo} expects
     * a versioned upgrade script. A file located there but not recognized by
     * {@link #isFileManagedByLiquibase(File, String)} is a naming fault worth reporting : it is
     * silently skipped both at packaging time and by the runtime changelog filter.
     *
     * @param candidate
     *            the SQL file
     * @param strBasePath
     *            the absolute path of the WEB-INF/sql directory
     * @return true if the file sits in an upgrade directory
     */
    static boolean isInUpgradeDirectory(File candidate, String strBasePath)
    {
        return UPGRADE_DIRECTORY_PATTERN.matcher(getAbsoluteSqlFilePath(candidate, strBasePath)).matches();
    }

}
