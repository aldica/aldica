/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.cache;

import java.util.HashSet;
import java.util.Set;

import org.alfresco.service.namespace.QName;
import org.apache.ignite.marshaller.Marshaller;

/**
 * Instances of this class are simply used to differentiate a node aspects set inside a cache from any regular set for the purpose of
 * {@link Marshaller marshalling}, and allows us to apply special serialisation handling based on this type.
 *
 * @author Axel Faust
 *
 */
public class NodeAspectsCacheSet extends HashSet<QName>
{

    private static final long serialVersionUID = -5207020576251149198L;

    /**
     * Creates a new empty instance.
     *
     */
    public NodeAspectsCacheSet()
    {
        super();
    }

    /**
     * Creates a new instance as the copy of an existing set.
     *
     * @param s
     *            the set to copy
     */
    public NodeAspectsCacheSet(final Set<QName> s)
    {
        super(s);
    }

}
