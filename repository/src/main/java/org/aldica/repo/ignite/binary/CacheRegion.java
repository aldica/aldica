/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.util.HashMap;
import java.util.Map;

import org.alfresco.repo.cache.lookup.CacheRegionKey;
import org.alfresco.repo.cache.lookup.CacheRegionValueKey;

/**
 * The values of this enum represent the well-known cache region names used in standard Alfresco, as well as a placeholder for potential
 * custom names. This enumeration is meant to be used to optimise the serial form of {@link CacheRegionKey} and {@link CacheRegionValueKey}
 * instances by replacing the multi-character name with a single enum ordinal for all well-known region names.
 *
 * @author Axel Faust
 */
public enum CacheRegion
{

    // note: no public constants for cache region names exist in Alfresco classes, so we have to put our own literal values in enum literal
    // constructor calls
    DEFAULT("DEFAULT"),
    NAMESPACE("Namespace"),
    LOCALE("Locale"),
    ENCODING("Encoding"),
    QNAME("QName"),
    ROOT_NODES("N.RN"),
    NODES("N.N"),
    NODES_ASPECTS("N.A"),
    NODES_PROPERTIES("N.P"),
    CONTENT_DATA("ContentData"),
    CONTENT_URL("ContentUrl"),
    ACL("Acl"),
    AUTHORITY("Authority"),
    PERMISSION("Permission"),
    PROPERTY_CLASS("PropertyClass"),
    PROPERTY_DATE_VALUE("PropertyDateValue"),
    PROPERTY_STRING_VALUE("PropertyStringValue"),
    PROPERTY_DOUBLE_VALUE("PropertyDoubleVlaue"),
    PROPERTY_SERIALIZABLE_VALUE("PropertySerializableValue"),
    PROPERTY_VALUE("PropertyValue"),
    PROPERTY("Property"),
    CUSTOM(null);

    private static final Map<String, CacheRegion> LOOKUP = new HashMap<>();
    static
    {
        for (final CacheRegion value : CacheRegion.values())
        {
            final String name = value.getCacheRegionName();
            if (name != null)
            {
                LOOKUP.put(name, value);
            }
        }
    }

    private final String cacheRegionName;

    private CacheRegion(final String cacheRegionName)
    {
        this.cacheRegionName = cacheRegionName;
    }

    /**
     * @return the cacheRegionName
     */
    public String getCacheRegionName()
    {
        return this.cacheRegionName;
    }

    /**
     * Retrieves the enumeration literal corresponding to the provided cache region name.
     *
     * @param cacheRegion
     *            the name of the cache region for which to retrieve the literal
     * @return the literal matching the cache region name - will never be {@code null} and fall back to {@link #CUSTOM} for any cache region
     *         name not matching the well known predefined Alfresco cache regions
     */
    public static CacheRegion getLiteral(final String cacheRegion)
    {
        final CacheRegion literal = LOOKUP.getOrDefault(cacheRegion, CUSTOM);
        return literal;
    }
}
