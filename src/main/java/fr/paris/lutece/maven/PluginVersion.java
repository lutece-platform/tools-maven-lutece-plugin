package fr.paris.lutece.maven;

import java.util.ArrayList;
import java.util.List;

/**
 * Compares versions. Immutable.
 * 
 * A version is a string such as "1.0.0", i.e. a dot separated list of integers. non-integer components are not supported.
 *
 */
public class PluginVersion implements Comparable<PluginVersion>
{
    private static final String SNAPSHOT = "-SNAPSHOT";
    private final List<Integer> components = new ArrayList<>();
    private static boolean acceptSnapshots = false;

    public static void setAcceptSnapshots(boolean acceptSnapshots)
    {
        PluginVersion.acceptSnapshots = acceptSnapshots;
    }

    private PluginVersion(String version)
    {
        if (version != null)
        {
            if (acceptSnapshots && version.endsWith(SNAPSHOT))// else : some runtime exception when parsing ints
                version = version.substring(0, version.length() - SNAPSHOT.length());
            for (String element : version.split("\\."))
                components.add(Integer.parseInt(element));
        }
    }

    public List<Integer> components()
    {
        return new ArrayList<>(components);
    }

    @Override
    public int compareTo(PluginVersion o)
    {
        if (o == null)
            return 1;
        for (int i = 0; i < Math.min(o.components.size(), components.size()); i++)
        {
            Integer i1 = components.get(i);
            Integer i2 = o.components.get(i);
            if (!i1.equals(i2))
                return i1.compareTo(i2);
        }
        return Integer.compare(components.size(), o.components.size());
    }

    /**
     * Parses and creates a PluginVersion from a version string.
     * 
     * Might throw a RuntimeException if parsing fails (bad format).
     * 
     * @param version the source string
     * @return a PluginVersion instance or null if the source is null
     */
    public static PluginVersion of(String version)
    {
        return version == null ? null : new PluginVersion(version);
    }

    @Override
    public String toString()
    {
        return components.toString();
    }
}
