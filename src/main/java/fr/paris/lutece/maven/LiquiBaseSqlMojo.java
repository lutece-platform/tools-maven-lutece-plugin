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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.google.common.base.Charsets;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;

/**
 * Tags SQL resources with liquibase tags.
 * 
 * 
 * @goal liquibase-sql
 * @execute phase="process-resources"
 * @requiresDependencyResolution compile+runtime
 */

@Mojo(name = "liquibase-sql")

public class LiquiBaseSqlMojo extends AbstractLuteceWebappMojo {

	private static final String CORE = "core";
	private static final String SQL_EXT = ".sql";
	private static final String LIQUIBASE_SQL_HEADER = "--liquibase formatted sql";
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

	private static final boolean fileFilter(Path path) {
		try {
			return Files.isRegularFile(path) && Files.size(path) > 0 && path.toString().toLowerCase().endsWith(SQL_EXT);
		} catch (IOException e) {
			return false;
		}
	}

	private void processSqlFiles() throws IOException {
		try (Stream<Path> filePathStream = Files.walk(Paths.get(SQL_DIRECTORY))) {
			filePathStream.filter(LiquiBaseSqlMojo::fileFilter).forEach(this::transformFile);
		}
	}

	/**
	 * 
	 * @param content
	 * @return
	 */
	public static boolean isTaggedWithLiquibase(String content) {
		return content.startsWith(LIQUIBASE_SQL_HEADER);
	}

	public static boolean isTaggedWithLiquibase(File candidate) {
		try (BufferedReader reader = new BufferedReader(new FileReader(candidate, Charsets.UTF_8));) {
			return isTaggedWithLiquibase(reader.readLine());
		} catch (Exception e) {
			// we do not care about the exact nature of the problem
			// if we could not read it, we just do not include it
			return false;
		}
	}

	private void transformFile(Path path) {
		getLog().info("Processing file " + path);
		try {
			// we suppose that all SQL files are UTF-8.
			// if that's not the case, we need a way to get that info for EACH input file
			String content = Files.readString(path, Charsets.UTF_8);
			if (!isTaggedWithLiquibase(content)) {
				StringBuilder result = new StringBuilder();
				result.append(LIQUIBASE_SQL_HEADER).append(EOL);
				String pluginName = extractPluginName(path);
				boolean done = false;
				String error = "";
				if (pluginName != null) {
					List<LazyStatement> statements = parseStatements(content, path);
					for (LazyStatement stmt : statements) {
						try {
							String res = analyse(pluginName, stmt, statements, path);
							if (res != null) {
								result.append(res);
								done = true;
								break;
							}
						} catch (Exception e) {
							// nothing to do, proceed to next statement
						}
					}
					if (!done) {
						LOGGER.info("Could not generate liquibase comment from content : \n{}", content);
						error = ". " + statements.size() + " statements analyzed.";
					}
				} else
					error = ". No plugin name found in the path (plugins/<plugin_name>).";
				if (!done)
					getLog().error("No automatic processing for " + path.getFileName() + error);

				result.append(content);
				Path outputPath = generateOutputPath(path);
				writeToFile(result.toString(), outputPath);
			} else {
				LOGGER.info("File already in Liquibase format, ignoring: {}", path.getFileName());
			}
		} catch (Exception e) {
			LOGGER.error("Error processing file: {}", path.getFileName(), e);
			throw new RuntimeException(e);
		}
	}

	private String extractPluginName(Path path) {
		try {
			Pattern r = Pattern.compile("plugins/([^/]+)/");
			Matcher m = r.matcher(path.toString().replace("\\", "/"));
			if (m.find()) {
				return m.group(1);
			} else if (path.toString().contains(CORE)) {
				return CORE;
			} else {
				return null;
			}
		} catch (Exception e) {
			LOGGER.error("Error extracting plugin name from path: {}", path.getFileName(), e);
			return null;
		}
	}

	// clean up extra CR and comments
	private static final String cleanup(String statement) {
		statement = statement.trim();
		while (!statement.isEmpty()) {
			if (statement.charAt(0) == '-') {
				int eol = statement.indexOf('\n');
				if (eol != -1)
					statement = statement.substring(eol + 1).trim();
				else
					return "";
			} else
				break;
		}
		return statement;
	}

	private List<LazyStatement> parseStatements(String content, Path path) {
		// we pre-split a whole file in statements, we avoid ";'" as it probably
		// means that we would be splitting on a literal ';' inside a query
		String[] individualStatements = content.split(";[^']");
		List<LazyStatement> statements = new ArrayList<>();
		for (String individualStatement : individualStatements) {
			individualStatement = cleanup(individualStatement);
			if (individualStatement.isEmpty())
				continue;
			statements.add(new LazyStatement(individualStatement));
		}
		return statements;
	}

	/**
	 * Holder for lazy evaluation of statements : no need to actually parse the whole SQL file from the start, so each statement will be parsed only when needed
	 */
	static class LazyStatement {
		private Statement s = null;
		private final String sql;

		public LazyStatement(String sql) {
			this.sql = sql;
		}

		public Statement get() throws JSQLParserException {
			if (s == null)
				s = CCJSqlParserUtil.parse(sql);
			return s;
		}
	}

	private Path generateOutputPath(Path inputPath) {
		String subPathSqlFile = inTarget ? TARGET_DIRECTORY + inputPath.subpath(3, inputPath.getNameCount())
				: LIQUIBASE_DIRECTORY + inputPath.subpath(3, inputPath.getNameCount());

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

	private String analyse(String pluginName, LazyStatement stm, List<LazyStatement> statements, Path path) throws JSQLParserException {
		Statement stmt = stm.get();
		if (stmt instanceof Alter) {
			final StringBuilder alter = new StringBuilder();
			String tableName = ((Alter) stmt).getTable().getName();
			((Alter) stmt).getAlterExpressions().stream().filter((elt) -> elt.getOperation().name().equals("ADD") || elt.getOperation().name().equals("DROP"))
					.findFirst().ifPresent(alterTest -> {
						if (alterTest != null) {
							if (alterTest.getOperation().name().equals("ADD")) {
								alterTest.getColDataTypeList().stream().findFirst().ifPresent(col -> {
									alter.append(liquibaseComments(path, pluginName, "0",
											"SELECT 1 FROM " + tableName + " having count(" + col.getColumnName() + ")>=0"));
									// check that the column does not exist in the table
								});
							}

							if (alterTest.getOperation().name().equals("DROP")) {
								alter.append(liquibaseComments(path, pluginName, "1", "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = '"
										+ tableName + "' AND column_name = '" + alterTest.getColumnName() + "'"));
								// check that the column exists in the table
							}
						}
					});

			return alter.toString();

		} else if (stmt instanceof CreateTable) {
			String tableName = ((CreateTable) stmt).getTable().getName();
			return liquibaseComments(path, pluginName, "0", "SELECT 1 FROM " + tableName + " limit 1");
		} else if (stmt instanceof Drop) {
			String tableName = ((Drop) stmt).getName().getName();

			boolean hasCreate = false;

			for (LazyStatement ls : statements) {
				Statement nextStmt = ls.get();
				if (nextStmt instanceof CreateTable) {
					String testTableName = ((CreateTable) nextStmt).getTable().getName();
					if (testTableName.equals(tableName)) {
						hasCreate = true;
						break;
						// teste s'il y a un CREATE d'une table du même nom que celle qu'on veut DROP
					}
				}
			}

			if (hasCreate) {
				return liquibaseComments(path, pluginName, "0", "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '" + tableName + "'");
				// cette précondition vérifie que la table spécifiée n'existe pas
			} else {
				return liquibaseComments(path, pluginName, "1", "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '" + tableName + "'");
			}
		} else if (stmt instanceof Insert) {
			Insert ins = (Insert) stmt;
			final StringBuilder insert = new StringBuilder();
			String tableName = ins.getTable().getName();

			insert.append("-- changeset " + pluginName + ":insert-" + tableName + EOL + "-- preconditions onFail:MARK_RAN onError:WARN" + EOL);

			// cas de certains fichiers sql ou il est impossible de récuperer la liste des
			// valeurs en raison du non respect des standars sql
			if ((ins.getItemsList()) != null) {

				List<Column> columns = ins.getColumns();
				List<Expression> values = ((ExpressionList) ins.getItemsList()).getExpressions();
				// cannot make a where clause if we do not known the column names (optional in inserts)
				if (columns != null && values != null && columns.size() == values.size()) {

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

					insert.append(EOL);
					// cette précondition vérifie que l'insert n'a pas déjà été réalisé

					return insert.toString();
				}
			}
			return null;
		} else if (stmt instanceof CreateIndex) {
			final CreateIndex cr = (CreateIndex) stmt;
			final String index = cr.getIndex().getName();
			final String tableName = cr.getTable().getName();
			return liquibaseComments(path, pluginName, "1",
					"SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE INDEX_NAME='" + index + "' AND TABLE_NAME='" + tableName + "' LIMIT 1;");
		} else if (stmt instanceof Truncate) {
			// FIXME the pre-condition might return 0
			// even if truncate wasn't run, for example if the table was already empty...
			// same problem for DELETE/UPDATE, which might be no-ops too.
			final Truncate tr = (Truncate) stmt;
			final String tableName = tr.getTable().getName();
			return liquibaseComments(path, pluginName, "1", "SELECT 1 FROM " + tableName + " LIMIT 1;");
		} else {
			// Handle other types of statements or return null if not supported
			if (!(stmt instanceof Update) && !(stmt instanceof Delete))
				LOGGER.error("Unsupported SQL statement in file {}: {}", path.getFileName(), stmt);
			return null;
		}
	}

	private static String liquibaseComments(Path path, String pluginName, String expectedResult, String sqlCheck) {
		final StringBuilder sb = new StringBuilder();
		sb.append("--changeset ").append(pluginName).append(":").append(path.getFileName()).append(EOL);
		sb.append("--preconditions onFail:MARK_RAN onError:WARN").append(EOL);
		sb.append("--precondition-sql-check expectedResult:").append(expectedResult).append(" ").append(sqlCheck).append(EOL);
		return sb.toString();
	}
}