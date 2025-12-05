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
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

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
    private static final String SQL_DIRECTORY = "./src/sql";
    private static final String TARGET_DIRECTORY = "./target/liquibasesql/";
    private static final String PLUGIN_CONF_DIRECTORY = "./webapp/WEB-INF/plugins";
   
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
        } catch (IOException e)
        {
            getLog().error("An error occurred while processing SQL files.", e);
            throw new MojoExecutionException("Failed to process SQL files.", e);
        }
    }

    private void processPluginXmls() throws IOException
    {
        // Supposes there will always be only one XML file.
        try (Stream<Path> filePathStream = Files.walk(Paths.get(PLUGIN_CONF_DIRECTORY)))
        {
            filePathStream.filter(pluginFileFilter).findAny().ifPresent(this::processPluginXml);
        }
    }

    private void processPluginXml(Path path)
    {
        try
        {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(path.toFile());
            version = doc.getElementsByTagName("version").item(0).getTextContent();
            pluginName = doc.getElementsByTagName("name").item(0).getTextContent();
        } catch (Exception e)
        {
            throw new RuntimeException(e);
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
        try (Stream<Path> filePathStream = Files.walk(Paths.get(SQL_DIRECTORY)))
        {
            filePathStream.filter(sqlFileFilter).forEach(this::transformFile);
        }
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
                getLog().info("path " + path);
                SqlPathInfo sqlPath = SqlPathInfo.parse(normalizeWebappPath(path.toString()).substring(6));// remove leading "./src/"
                if (!sqlPath.isCreate())
                {
                    if (sqlPath.getDstVersion() != null
                            && (mostRecentSqlScriptVersion == null || mostRecentSqlScriptVersion.compareTo(sqlPath.getDstVersion()) < 0))
                        mostRecentSqlScriptVersion = sqlPath.getDstVersion();
                }
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
            getLog().error("Error processing file: " + path.getFileName(), e);
            throw new RuntimeException(e);
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
        String subPathSqlFile = dryRun ? TARGET_DIRECTORY + inputPath.subpath(3, inputPath.getNameCount())
                : SQL_DIRECTORY + "/" + inputPath.subpath(3, inputPath.getNameCount());

        Path outputPath = Paths.get(subPathSqlFile);
        Files.createDirectories(outputPath.getParent());
        return outputPath;
    }
    
    /**
     * Normalizes the Webapp Path
     *
     * @param strPath
     *            The path to normalize
     * @return The normalized path
     */
    private static String normalizeWebappPath( String strPath )
    {
        String strNormalized = strPath;

        // For windows, remove the leading \
        if ( ( strNormalized.length( ) > 3 ) && ( strNormalized.indexOf( ':' ) == 2 ) )
        {
            strNormalized = strNormalized.substring( 1 );
        }

        // convert Windows path separator if present
        strNormalized = substitute( strNormalized, "/", "\\" );

        // remove the ending separator if present
        if ( strNormalized.endsWith( "/" ) )
        {
            strNormalized = strNormalized.substring( 0, strNormalized.length( ) - 1 );
        }

        return strNormalized;
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
     * This function substitutes all occurences of a given bookmark by a given value
     *
     * @param strSource
     *            The input string that contains bookmarks to replace
     * @param strValue
     *            The value to substitute to the bookmark
     * @param strBookmark
     *            The bookmark name
     * @return The output string.
     */
    public static String substitute( String strSource, String strValue, String strBookmark )
    {
        StringBuilder strResult = new StringBuilder( );
        int nPos = strSource.indexOf( strBookmark );
        String strModifySource = strSource;

        while ( nPos != -1 )
        {
            strResult.append( strModifySource.substring( 0, nPos ) );
            strResult.append( strValue );
            strModifySource = strModifySource.substring( nPos + strBookmark.length( ) );
            nPos = strModifySource.indexOf( strBookmark );
        }

        strResult.append( strModifySource );

        return strResult.toString( );
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
   private static String getAbsoluteSqlFilePath(File candidate,String strBasePath ) {
         return candidate.getPath().substring(strBasePath.length()-3 );
   } 

}
