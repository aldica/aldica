/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.value;

import java.io.Externalizable;
import java.lang.reflect.Field;

import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
import org.alfresco.repo.module.ModuleVersionNumber;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryMarshaller;
import org.apache.ignite.internal.marshaller.optimized.OptimizedMarshaller;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * Instances of this class handle (de-)serialisations of {@link ModuleVersionNumber} instances which cannot be marshalled by Ignite's
 * default {@link BinaryMarshaller} and requires a fallback to {@link OptimizedMarshaller} on account of implementing {@link Externalizable}
 * with custom {@link Externalizable#writeExternal(java.io.ObjectOutput) writeExternal} /
 * {@link Externalizable#readExternal(java.io.ObjectInput) readExternal} operations.
 *
 * @author Axel Faust
 */
public class ModuleVersionNumberBinarySerializer extends AbstractCustomBinarySerializer
{

    private static final String VERSION = "version";

    private static final Field DELEGATE_FIELD;

    static
    {
        try
        {
            DELEGATE_FIELD = ModuleVersionNumber.class.getDeclaredField("delegate");

            DELEGATE_FIELD.setAccessible(true);
        }
        catch (final NoSuchFieldException nsfe)
        {
            throw new RuntimeException("Failed to initialise reflective field accessors", nsfe);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(ModuleVersionNumber.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final String version = obj.toString();

        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();
            this.write(version, rawWriter);
        }
        else
        {
            writer.writeString(VERSION, version);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void readBinary(final Object obj, final BinaryReader reader) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(ModuleVersionNumber.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final String version = this.useRawSerialForm ? this.readString(reader.rawReader()) : reader.readString(VERSION);
        // null will never occur, but technically possible
        final ComparableVersion delegate = new ComparableVersion(version != null ? version : "");

        try
        {
            DELEGATE_FIELD.set(obj, delegate);
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to write deserialised field values", iae);
        }
    }

}
