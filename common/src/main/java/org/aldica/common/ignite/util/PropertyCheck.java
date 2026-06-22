/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.util;

import java.text.MessageFormat;

/**
 * This utility class exists to streamline common property checks. Originally, we used {@link org.aldica.common.ignite.util.PropertyCheck Alfresco's
 * class} but this introduced unwanted dependencies for the companion application.
 *
 * @author Axel Faust
 */
public class PropertyCheck
{

    private static final String ERR_PROPERTY_NOT_SET = "Property ''{0}'' has not been set: {1} ({2})";

    /**
     * Checks that the property with the given name is not null.
     *
     * @param target
     *     the object on which the property must have been set
     * @param propertyName
     *     the name of the property
     * @param value
     *     of the property value
     */
    public static void mandatory(final Object target, final String propertyName, final Object value)
    {
        if (value == null)
        {
            throw new RuntimeException(MessageFormat.format(ERR_PROPERTY_NOT_SET, propertyName, target, target.getClass()));
        }
    }
}
