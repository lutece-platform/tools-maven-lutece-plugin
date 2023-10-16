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

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds a site's final WAR from a Lutece core artifact and a set of Lutece
 * plugin artifacts.<br/> Note that the Lutece dependencies (core and plugins)
 * will only be updated the first time the WAR is created. Subsequent calls to
 * this goal will only update the site's specific files.<br/> If you wish to
 * force webapp re-creation (for instance, if you changed the version of a
 * dependency), call the <code>clean</code> phase before this goal.
 *
 * @goal liquibase-sql
 * @execute phase="process-resources"
 * @requiresDependencyResolution compile+runtime
 */

@Mojo(name = "liquibase-sql")

public class LiquiBaseSqlMojo extends AbstractLuteceWebappMojo {

    private static final String LIQUIBASE_SQL_HEADER = "-- liquibase formatted sql";
    private static final String EOL = System.lineSeparator();
    private static final String SQL_DIRECTORY = "./src/sql";
    private static final String TARGET_DIRECTORY = "./target/liquibasesql/";
    private static final String LIQUIBASE_DIRECTORY = "./src/liquibasesql/";

    private static final Logger LOGGER = LogManager.getLogger(LiquiBaseSqlMojo.class);

    @Parameter(property = "inTarget")
    private boolean inTarget;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            processSqlFiles();
        } catch (IOException e) {
            LOGGER.error("An error occurred while processing SQL files.", e);
            throw new MojoExecutionException("Failed to process SQL files.", e);
        }
    }

    private void processSqlFiles() throws IOException {
        try (Stream<Path> filePathStream = Files.walk(Paths.get(SQL_DIRECTORY))) {
            filePathStream
                    .filter(Files::isRegularFile)
                    .forEach(this::transformFile);
        }
    }

    private void transformFile(Path path) {
        LOGGER.info("Processing file: {}", path.getFileName());
        try {
            String content = new String(Files.readAllBytes(path));
            if (!content.startsWith(LIQUIBASE_SQL_HEADER)) {
                StringBuffer result = new StringBuffer();
                result.append(LIQUIBASE_SQL_HEADER).append(EOL);
                String pluginName = extractPluginName(path);

                Statements statements = parseStatements(content, path);

                for (Statement stmt : statements.getStatements()) {
                    String res = analyse(pluginName, stmt, path);
                    if (res != null) {
                        result.append(res);
                        break;
                    } else {
                        LOGGER.error("Error processing file: {}", path.getFileName());
                        break;
                    }
                }

                result.append(content);
                Path outputPath = generateOutputPath(path);
                writeToFile(result.toString(), outputPath);
            } else {
                LOGGER.info("File already in Liquibase format, ignoring: {}", path.getFileName());
            }
        } catch (Exception e) {
            LOGGER.error("Error processing file: {}", path.getFileName(), e);
        }
    }

    private String extractPluginName(Path path) {
        try {
            Pattern r = Pattern.compile("plugins/([^/]+)/");
            Matcher m = r.matcher(path.toString().replace("\\", "/"));
            if (m.find()) {
                return m.group(1);
            } else {
                LOGGER.error("No plugin name found in the path (plugins/<plugin_name>), skipping file.");
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("Error extracting plugin name from path: {}", path.getFileName(), e);
            return null;
        }
    }

    private Statements parseStatements(String content, Path path) {
      /*  try {
            return CCJSqlParserUtil.parseStatements(content);
        } catch (JSQLParserException e) {
            LOGGER.error("Error parsing SQL statements: {}", e.getMessage());
            return new Statements();
        }*/

        String[] individualStatements = content.split(";");
        Statements statements = new Statements();
        for (String individualStatement : individualStatements) {
            try {
                Statement stmt = CCJSqlParserUtil.parse(individualStatement);
                statements.addStatements(stmt);

            } catch (JSQLParserException e) {
                // Handle parsing exceptions here
                System.err.println("Error parsing SQL statement in file : " + path.getFileName() + " " + e.getMessage());
            }
        }
        return statements;
    }

    private Path generateOutputPath(Path inputPath) {
        String subPathSqlFile = inTarget ?
                TARGET_DIRECTORY + inputPath.subpath(3, inputPath.getNameCount()) :
                LIQUIBASE_DIRECTORY + inputPath.subpath(3, inputPath.getNameCount());

        Path outputPath = Paths.get(subPathSqlFile);
        try {
            Files.createDirectories(outputPath.getParent());
        } catch (IOException e) {
            LOGGER.error("Error creating output directories: {}", outputPath.getParent(), e);
        }
        return outputPath;
    }

    private void writeToFile(String content, Path outputPath) {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write(content);
        } catch (IOException e) {
            LOGGER.error("Error writing to file: {}", outputPath.getFileName(), e);
        }
    }

    private String analyse(String pluginName, Statement stmt, Path path) {
        if (stmt instanceof Alter) {

            final StringBuffer alter = new StringBuffer();
            String tableName = ((Alter) stmt).getTable().getName();
            ((Alter) stmt).getAlterExpressions().stream().filter((elt) -> elt.getOperation().name().equals("ADD") || elt.getOperation().name().equals("DROP")).findFirst().ifPresent(alterTest -> {
                if (alterTest != null) {
                    if (alterTest.getOperation().name().equals("ADD")) {
                        alterTest.getColDataTypeList().stream().findFirst().ifPresent(col -> {
                            alter.append(
                                    "-- changeset forms:alter-table-" + tableName + "-" + col.getColumnName() + EOL +
                                            "-- preconditions onFail:MARK_RAN onError:WARN" + EOL +
                                            "-- precondition-sql-check expectedResult:0 SELECT 1 FROM " + tableName + " having count(" + col.getColumnName() + ")>=0" + EOL);
                            // cette précondition vérifie que la colonne spécifiée n'existe pas dans la table spécifiée
                        });
                    }

                    if (alterTest.getOperation().name().equals("DROP")) {
                        alterTest.getColDataTypeList().stream().findFirst().ifPresent(col -> {
                            alter.append(
                                    "-- changeset <forms:alter-table-drop-" + tableName + "-" + col.getColumnName() + EOL +
                                            "-- preconditions onFail:MARK_RAN onError:WARN" + EOL +
                                            "-- precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.columns WHERE table_name = '" + tableName + "' AND column_name = '" + col.getColumnName() + "'" + EOL);
                            // cette précondition vérifie qu'il existe la colonne spécifiée dans la table spécifiée
                        });
                    }
                }
            });

            return alter.toString();

        } else if (stmt instanceof CreateTable) {

            String tableName = ((CreateTable) stmt).getTable().getName();
            return (
                    "-- changeset forms:create-table-" + tableName + EOL +
                            //"-- changeset "+pluginName+":create-table-"+tableName+EOL+
                            "-- preconditions onFail:MARK_RAN onError:WARN" + EOL +
                            "-- precondition-sql-check expectedResult:0 SELECT 1 FROM " + tableName + " limit 1" + EOL
                    // cette précondition vérifie que la table spécifiée n'existe pas
            );

        } else if (stmt instanceof Drop) {

            final StringBuffer drop = new StringBuffer();
            Table tableName = ((Drop) stmt).getName();
            String tableName2 = tableName.toString();

            List<String> stmts = allStatements(path);
            List<Statement> statements = convertToStmts(stmts);

            boolean hasCreate = false;

            for (int i = 0; i < statements.size(); i++) {
                Statement nextStmt = statements.get(i);
                if (nextStmt instanceof CreateTable) {
                    String testTableName = ((CreateTable) nextStmt).getTable().getName();
                    if (testTableName.equals(tableName2)) {
                        hasCreate = true;
                        break;
                        // teste s'il y a un CREATE d'une table du même nom que celle qu'on veut DROP
                    }
                }
            }

            if (hasCreate) {
                drop.append(
                        "-- changeset forms:create-table-" + tableName + EOL +
                                //"-- changeset "+pluginName+":create-table-"+tableName+EOL+
                                "-- preconditions onFail:MARK_RAN onError:WARN" + EOL +
                                "-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '" + tableName + "'" + EOL
                        // cette précondition vérifie que la table spécifiée n'existe pas
                );
            } else {
                drop.append(
                        "-- changeset forms:drop-table-" + tableName + EOL +
                                //"-- changeset "+pluginName+":drop-table-"+tableName+EOL+
                                "-- preconditions onFail:MARK_RAN onError:WARN" + EOL +
                                "-- precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '" + tableName + "'" + EOL
                        // cette précondition vérifie que la table spécifiée existe
                );
            }

            return drop.toString();

        } else if (stmt instanceof Insert) {

            final StringBuilder insert = new StringBuilder();
            String tableName = ((Insert) stmt).getTable().getName();

            insert.append(
                    "-- changeset " + pluginName + ":insert-" + tableName + EOL +
                            "-- preconditions onFail:MARK_RAN onError:WARN" + EOL
            );

            // cas de certains fichiers sql ou il est impossible de récuperer la liste des valeurs en raison du non respect des standars sql
            if ( (((Insert) stmt).getItemsList()) != null ){

            List<Column> columns = ((Insert) stmt).getColumns();
            List<Expression> values = ((ExpressionList) ((Insert) stmt).getItemsList()).getExpressions();

            insert.append("-- precondition-sql-check expectedResult:0 SELECT 1 FROM " + tableName + " WHERE ");
            // Ajout de la condition "column = value" pour chaque :
            for (int i = 0; i < columns.size(); i++) {
                Column column = columns.get(i);
                String columnName = column.getColumnName();
                String value = values.get(i).toString();

                insert.append(columnName + " = " + value);

                if (i < columns.size() - 1) {
                    insert.append(" AND ");
                }
            }
        }

            insert.append(EOL);
            // cette précondition vérifie que l'insert n'a pas déjà été réalisé

            return insert.toString();

        } else {
            // Handle other types of statements or return null if not supported
            LOGGER.warn("Unsupported SQL statement in file {}: {}", path.getFileName(), stmt);
            return null;
        }
    }

    private static List<String> allStatements(Path path) {

        Stream<String> stream = null;
        String resultat = null;
        List<String> allStmt = new ArrayList<>();
        try {
            stream = Files.lines(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (stream != null) {
            resultat = stream.filter(str -> str.length() > 1)
                    .peek(System.out::println)
                    .collect(Collectors.joining());

            //tream.forEach(System.out::println);
        }

        //String content = Files.readString(path);
        if (resultat != null) {
            String[] statementArray = resultat.split(";");
            for (String stmt : statementArray) {
                stmt = stmt.trim();
                if (!stmt.isEmpty()) {
                    // pour éviter d'ajouter des éléments vides à la liste
                    allStmt.add(stmt);
                }
            }
        }
        return allStmt;
        // permet d'obtenir une List<String> de tous les statements
    }

    private static List<Statement> convertToStmts(List<String> stmts) {
        List<Statement> statements = new ArrayList<>();
        try {
            for (String stmt : stmts) {
                Statement statement = CCJSqlParserUtil.parse(stmt);
                statements.add(statement);
            }
        } catch (JSQLParserException e) {
            e.printStackTrace();
        }
        return statements;
        // transforme une List<String> en List<Statement>
    }
}