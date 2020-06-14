/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.cache;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.service.namespace.QName;
import org.apache.ignite.marshaller.Marshaller;

/**
 * Instances of this class are simply used to differentiate a node properties map inside a cache from any regular map for the purpose of
 * {@link Marshaller marshalling}, and allow us to apply special serialisation handling based on this type.
 *
 * @author Axel Faust
 *
 */
public class NodePropertiesCacheMap extends HashMap<QName, Serializable>
{

    private static final long serialVersionUID = -5207020576251149198L;

    /**
     * Creates a new instance as the copy of an existing map.
     *
     * @param m
     *            the map to copy
     */
    public NodePropertiesCacheMap(final Map<QName, Serializable> m)
    {
        super(m);
    }

}
