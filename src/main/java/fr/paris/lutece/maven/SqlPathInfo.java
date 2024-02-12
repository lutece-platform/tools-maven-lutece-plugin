package fr.paris.lutece.maven;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.shared.utils.StringUtils;

public class SqlPathInfo
{
    /** true when create_XXX or init_XXX, false when update_XXX */
    private boolean create;
    /** plugin name ("core", if not a plugin) */
    private String plugin;
    /** module name (example : "template" for forms-template) */
    private String module;
    /** starting version for an update script. Only available when create is false */
    private PluginVersion srcVersion;
    /** destination version for an update script. Only available when create is false */
    private PluginVersion dstVersion;

    public boolean isCreate()
    {
        return create;
    }

    public String getPlugin()
    {
        return plugin;
    }

    public String getFullPluginName()
    {
        return StringUtils.isEmpty(module) ? plugin : (plugin + "-" + module);
    }

    public PluginVersion getSrcVersion()
    {
        return srcVersion;
    }

    public PluginVersion getDstVersion()
    {
        return dstVersion;
    }

    @Override
    public String toString()
    {
        return "SqlPathInfo [create=" + create + ", plugin=" + getFullPluginName() + ", module=" + module + ", srcVersion=" + srcVersion + ", dstVersion=" + dstVersion
                + "]";
    }

    // having a pattern that does everything is not easy to read
    // so we declare several patterns
    // example : "sql/plugins/testpourliquibase/plugin/create_db_testpourliquibase.sql"
    private static final Pattern SQL_CREATE_PATTERN = Pattern
            .compile("sql/plugins/(?<plugin>[\\p{Alpha}\\-]+)(/modules/(?<module>[\\p{Alpha}]+))?/(core|plugin)/(init|create)[\\p{Alpha}_\\-]+\\.sql");
    // example : "sql/plugins/testpourliquibase/upgrade/update_db_testpourliquibase-0.0.9-1.0.0.sql"
    private static final Pattern SQL_UPDATE_PATTERN = Pattern.compile(
            "sql/plugins/(?<plugin>[\\p{Alpha}\\-]+)(/modules/(?<module>[\\p{AlPha}]+))?/upgrades?/(update|upgrade)[\\p{Alpha}\\-_]+[\\-_]?(?<srcVersion>([0-9]+(\\.[0-9]+)*))[\\-_](?<dstVersion>([0-9]+(\\.[0-9]+)*))\\.sql");

    // matches src/sql/create_db_lutece_core.sql and src/sql/init_db_lutece_core.sql
    private static final Pattern SQL_CORE_CREATE = Pattern.compile("sql/(init|create)[\\p{Alpha}_]+(?<plugin>core)\\.sql");
    private static final Pattern SQL_CORE_UPDATE = Pattern
            .compile("sql/upgrade/update_db_lutece_(?<plugin>core)-(?<srcVersion>([0-9]+(\\.[0-9]+)*))-(?<dstVersion>([0-9]+(\\.[0-9]+)*))\\.sql");
    // capturing group names (literally written in the patterns above for readability)
    private static final String DST_VERSION_GROUP = "dstVersion";
    private static final String SRC_VERSION_GROUP = "srcVersion";
    private static final String PLUGIN_GROUP = "plugin";
    private static final String MODULE_GROUP = "module";

    static SqlPathInfo parse(String changeLogPath)
    {
        Matcher matcher = SQL_CREATE_PATTERN.matcher(changeLogPath);
        if (matcher.matches())
        {
            return createInfo(matcher);
        }
        matcher = SQL_CORE_CREATE.matcher(changeLogPath);
        if (matcher.matches())
        {
            return createInfo(matcher);
        }
        matcher = SQL_UPDATE_PATTERN.matcher(changeLogPath);
        if (matcher.matches())
        {
            return updateInfo(matcher);
        }
        matcher = SQL_CORE_UPDATE.matcher(changeLogPath);
        if (matcher.matches())
        {
            return updateInfo(matcher);
        }
        return null;
    }

    private static SqlPathInfo basicInfo(Matcher matcher)
    {
        SqlPathInfo info = new SqlPathInfo();
        info.module = matcher.group(MODULE_GROUP);
        info.plugin = matcher.group(PLUGIN_GROUP);
        return info;
    }

    private static SqlPathInfo createInfo(Matcher matcher)
    {
        SqlPathInfo info = basicInfo(matcher);
        info.create = true;
        return info;
    }

    private static SqlPathInfo updateInfo(Matcher matcher)
    {
        SqlPathInfo info = basicInfo(matcher);
        info.create = false;
        info.srcVersion = PluginVersion.of(matcher.group(SRC_VERSION_GROUP));
        info.dstVersion = PluginVersion.of(matcher.group(DST_VERSION_GROUP));
        return info;
    }

}