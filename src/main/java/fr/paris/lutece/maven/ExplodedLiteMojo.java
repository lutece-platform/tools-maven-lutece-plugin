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


import java.io.File;
import java.io.IOException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Mojo to explode a web application for Lutece projects while excluding Java class files and JAR dependencies.
 * <p>
 * This plugin is designed to be used for Lutece core, plugin, and site projects.
 * This Mojo extracts a web application for Lutece projects while excluding Java class files and JAR dependencies.
 * It focuses on unpacking the structure of the web application without including the compiled binaries, ensuring a cleaner,
 * lightweight setup, primarily useful for development purposes.
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
 */
@Mojo(name = "exploded-lite", requiresDependencyCollection = ResolutionScope.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class ExplodedLiteMojo extends AbstractLuteceWebappMojo {

    /**
     * Executes the goal.
     *
     * <p>
     * This method proceeds to execute the project by calling
     * {@link #executeProject()}.
     * </p>
     *
     * @throws MojoExecutionException if the packaging type is not supported.
     * @throws MojoFailureException if an error occurs during execution.
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        logBanner();
        String packaging = project.getPackaging();

        if (!isValidPackaging(packaging)) {
            throw new MojoExecutionException("This goal can be invoked only on a " +
                                              LUTECE_CORE_PACKAGING + ", " +
                                              LUTECE_PLUGIN_PACKAGING + ", " +
                                              LUTECE_SITE_PACKAGING + " project.");
        }
        executeProject();
    }

    /**
     * Adds a Lutece artifact to the exploded web application directory.
     *
     * @param luteceArtifact the artifact to be added.
     * @param webappDir the directory of the exploded web application.
     * @throws MojoExecutionException if an error occurs while resolving or unpacking the artifact.
     */
    @Override
    protected void addToExplodedWebapp(Artifact luteceArtifact, File webappDir) throws MojoExecutionException {
        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(new DefaultArtifact(
                luteceArtifact.getGroupId(),
                luteceArtifact.getArtifactId(),
                WEBAPP_CLASSIFIER,
                "zip",
                luteceArtifact.getVersion()
        ));
        request.setRepositories(remoteProjectRepositories);

        ArtifactResult result;
        try {
            result = repoSystem.resolveArtifact(repoSession, request);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(
                    "Error while resolving Artifact " + request.toString(), e);
        }
        File resolvedFile = result.getArtifact().getFile();
        try {
            unArchiver.setSourceFile(resolvedFile);
            unArchiver.setDestDirectory(webappDir);
            unArchiver.extract();
        } catch (Exception e) {
            throw new MojoExecutionException(
                    "Error while unpacking file " + resolvedFile.getAbsolutePath(), e);
        }
    }

    /**
     * Checks if the provided packaging type is valid for the Lutece projects.
     *
     * @param packaging the packaging type to validate.
     * @return true if the packaging type is valid; false otherwise.
     */
    private boolean isValidPackaging(String packaging) {
        return LUTECE_CORE_PACKAGING.equals(packaging) ||
               LUTECE_PLUGIN_PACKAGING.equals(packaging) ||
               LUTECE_SITE_PACKAGING.equals(packaging) ||
               POM_PACKAGING.equals(packaging);
    }

    /**
     * Executes the project-specific tasks.
     *
     * <p>
     * This method handles the explosion of the Lutece web application
     * and configuration files.
     * </p>
     *
     * @throws MojoExecutionException if an error occurs during project execution.
     */
    private void executeProject() throws MojoExecutionException {
        // Uncomment the following line if needed.
        // PluginDataService.generatePluginsDataFile(testWebappDirectory.getAbsolutePath());
        explodeLuteceWebapp(webappDirectory);
        explodeConfigurationFiles(webappDirectory);
    }

    /**
     * Explodes the Lutece web application into the specified target directory.
     *
     * @param targetDir the directory where the web application will be exploded.
     * @throws MojoExecutionException if an error occurs during the explosion process.
     */
    private void explodeLuteceWebapp(File targetDir) throws MojoExecutionException {
        try {
            boolean isInplace = targetDir.equals(webappSourceDirectory);
            boolean isUpdate = targetDir.exists();

            getLog().info((isUpdate ? "Updating" : "Exploding") + " webapp in " + targetDir + "...");

            targetDir.mkdirs();

            if (!isInplace && !isUpdate) {
                explodeCore(targetDir);
                explodePlugins(targetDir);
                explodeSites(targetDir);
            }

            copyBuildConfig(targetDir);

            if (!isInplace && webappSourceDirectory.exists()) {
                copyDirectoryStructure(webappSourceDirectory, targetDir, isUpdate);
            }

            copySQLFiles(targetDir, isInplace, isUpdate);
            copySiteUserFiles(targetDir, isInplace, isUpdate);

        } catch (IOException e) {
            throw new MojoExecutionException("Error while copying resources", e);
        }
    }

    /**
     * Copies the directory structure from the source to the target directory.
     *
     * @param sourceDir the source directory.
     * @param targetDir the target directory.
     * @param isUpdate indicates if the operation is an update.
     * @throws IOException if an error occurs during directory copying.
     */
    private void copyDirectoryStructure(File sourceDir, File targetDir, boolean isUpdate) throws IOException {
        if (!isUpdate) {
            FileUtils.copyDirectoryStructure(sourceDir, targetDir);
            logFileCopyStatus();
        } else {
            FileUtils.copyDirectoryStructureIfModified(sourceDir, targetDir);
            logFileModifiedStatus();
        }
    }

    /**
     * Copies SQL files from the SQL directory to the target directory.
     *
     * @param targetDir the target directory for SQL files.
     * @param isInplace indicates if the operation is inplace.
     * @param isUpdate indicates if the operation is an update.
     * @throws IOException if an error occurs during file copying.
     */
    private void copySQLFiles(File targetDir, boolean isInplace, boolean isUpdate) throws IOException {
        if (!isInplace && sqlDirectory.exists()) {
            getLog().debug("Copying SQL files from " + sqlDirectory.getAbsolutePath());
            File sqlTargetDir = new File(targetDir, WEB_INF_SQL_PATH);
            copyDirectoryStructure(sqlDirectory, sqlTargetDir, isUpdate);
        }
    }

    /**
     * Copies user files from the site directory to the target directory.
     *
     * @param targetDir the target directory for site user files.
     * @param isInplace indicates if the operation is inplace.
     * @param isUpdate indicates if the operation is an update.
     * @throws IOException if an error occurs during file copying.
     */
    private void copySiteUserFiles(File targetDir, boolean isInplace, boolean isUpdate) throws IOException {
        if (!isInplace && siteDirectory.exists()) {
            getLog().debug("Copying Site User files from " + siteDirectory.getAbsolutePath());
            File siteUserTargetDir = new File(targetDir, WEB_INF_DOC_XML_PATH);
            copyDirectoryStructure(siteDirectory, siteUserTargetDir, isUpdate);
        }
    }

    /**
     * Logs the status of copied files to the console.
     */
    private void logFileCopyStatus() {
        int copiedFiles = FileUtils.getNbFileCopy();
        if (copiedFiles == 0) {
            getLog().info("Nothing to copy - all files are up to date.");
        } else {
            getLog().info("Copying " + copiedFiles + " files.");
        }
        FileUtils.setNbFileCopy(0);
    }

    /**
     * Logs the status of modified files to the console.
     */
    private void logFileModifiedStatus() {
        int modifiedFiles = FileUtils.getNbFileModified();
        if (modifiedFiles == 0) {
            getLog().info("Nothing to update - all files are up to date.");
        } else {
            getLog().info("Copying " + modifiedFiles + " files.");
        }
        FileUtils.setNbFileModified(0);
    }
}