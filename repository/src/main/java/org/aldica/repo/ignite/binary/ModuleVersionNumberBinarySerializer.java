/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.io.Externalizable;
import java.lang.reflect.Field;

import org.alfresco.repo.module.ModuleVersionNumber;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinarySerializer;
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
public class ModuleVersionNumberBinarySerializer implements BinarySerializer
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

    protected boolean useRawSerialForm = false;

    /**
     * @param useRawSerialForm
     *            the useRawSerialForm to set
     */
    public void setUseRawSerialForm(final boolean useRawSerialForm)
    {
        this.useRawSerialForm = useRawSerialForm;
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
            rawWriter.writeString(version);
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

        final String version = this.useRawSerialForm ? reader.rawReader().readString() : reader.readString(VERSION);
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
