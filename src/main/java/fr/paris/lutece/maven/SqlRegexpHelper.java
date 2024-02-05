package fr.paris.lutece.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class that knows how to locate, load and apply SQL regexps such as defined in build.properties and run by build.xml
 * 
 */
public class SqlRegexpHelper
{
    private static final String PORTAL_URL = "portal.url";// in db.properties
    private static final String DEFAULT_SGBD = "mysql";
    private static final Pattern JDBC_URL_VENDOR = Pattern.compile("jdbc:(.*?):.*");
    private final List<SqlReplace> items = new ArrayList<>();

    /**
     * Constructs a new SqlRegexpHelper
     * 
     * @param propertiesFile contains the regexps
     * @param sgbdName       rdbms vendor to use
     * @throws IOException if something goes wrong with reading the config file
     */
    public SqlRegexpHelper(File propertiesFile, String sgbdName) throws IOException
    {
        if (sgbdName == null)
            sgbdName = DEFAULT_SGBD;
        Properties properties = new Properties();
        if (propertiesFile != null)
            try (FileInputStream in = new FileInputStream(propertiesFile);)
            {
                properties.load(in);
            }
        for (String id : properties.getProperty("regexp." + sgbdName + ".list", "").split(","))
        {
            if (id != null && !id.trim().isEmpty())
            {
                String regexp = properties.getProperty("regexp." + sgbdName + "." + id, "");
                String replacement = properties.getProperty("replace." + sgbdName + "." + id, "");
                items.add(new SqlReplace(regexp, replacement));
            }
        }
    }

    /**
     * Helper to find jdbc vendor from db.properties.
     * 
     * uses same regexp as build.xml from build-config
     * 
     * @param dbProperties input path for db.properties
     * @return 'mysql' or any other vendor identifier
     * @throws Exception if vendor cannot be found for any reason
     */
    public static String findDbName(File dbProperties) throws Exception
    {
        try (FileInputStream in = new FileInputStream(dbProperties);)
        {
            Properties properties = new Properties();
            properties.load(in);
            String url = properties.getProperty(PORTAL_URL);
            Matcher m = JDBC_URL_VENDOR.matcher(url);
            if (!m.matches())
                throw new IllegalStateException("Cannot parse JDBC vendor from '" + url + "' in file " + dbProperties);
            return m.group(1);
        }
    }

    /**
     * Performs all substitutions on the given string
     * 
     * @param line input
     * @return output, possibly modified by substitutions
     */
    public String filter(String line)
    {
        if (line == null)
            return line;
        for (SqlReplace item : items)
            line = item.filter(line);
        return line;
    }

    /**
     * Holds a regexp/replacement pair
     */
    private static class SqlReplace
    {
        Pattern regexp;
        String replacement;

        public SqlReplace(String regexp, String replacement)
        {
            this.regexp = Pattern.compile(regexp);
            this.replacement = computeReplacement(replacement);
        }

        public String filter(String source)
        {
            if (source == null)
                return null;
            return regexp.matcher(source).replaceAll(replacement);
        }

        @Override
        public String toString()
        {
            return "SqlReplace [regexp=" + regexp + ", replacement=" + replacement + "]";
        }

        /**
         * This method ensures compatibility with the default behaviour of the ant "tag ReplaceRegExp".
         * 
         * It changes the syntax used for back references in replacement strings.
         * 
         * The following code is mostly taken from org/apache/tools/ant/util/regexp/Jdk14RegexpRegexp.java
         * 
         * @param argument replacement string
         * @return a replacement string suitable for use by standard java regexps
         */
        private static String computeReplacement(String argument)
        {
            StringBuilder subst = new StringBuilder();
            for (int i = 0; i < argument.length(); i++)
            {
                char c = argument.charAt(i);
                if (c == '$')
                    subst.append('\\').append('$');
                else if (c == '\\')
                {
                    if (++i < argument.length())
                    {
                        c = argument.charAt(i);
                        int value = Character.digit(c, 10);
                        if (value > -1)
                            subst.append("$").append(value);
                        else
                            subst.append(c);
                    } else
                        subst.append('\\');
                } else
                    subst.append(c);
            }
            return subst.toString();
        }
    }

    public static void main(String[] args)
    {
        try
        {
            new SqlRegexpHelper(null, null);
            SqlRegexpHelper h = new SqlRegexpHelper(new File(
                    "/home/user1/lutece/root-test-forms-liquibase/site-theme-forms-test-liquibase/target/site-theme-forms-1.3.7-SNAPSHOT/WEB-INF/sql/build.properties"),
                    null);
        } catch (Exception e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
