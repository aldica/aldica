/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.base;

import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinarySerializer;
import org.apache.ignite.binary.BinaryWriter;

/**
 * Instances of this class provide a no-op implementation for custom serialisation. Since the companion app will never (de-)serialise any
 * cache values, it technically does not require any custom serialiser at all. But in order to succeed in Ignite's configuration checks
 * within a distributed grid, the same serialiser classes need to be configured for the same key and value classes. This no-op
 * implementation class provides the basis for all specific serialiser classes that only exist to satisfy the validation.
 *
 * @author Axel Faust
 */
public class NoOpBinarySerializer implements BinarySerializer
{

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBinary(final Object obj, final BinaryReader reader) throws BinaryObjectException
    {
        throw new UnsupportedOperationException("Deserialising binary objects is not supported in the companion app");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        throw new UnsupportedOperationException("Serialising binary objects is not supported in the companion app");
    }
}
