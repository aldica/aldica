/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.io.Externalizable;

import org.alfresco.repo.module.ModuleVersionNumber;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryMarshaller;
import org.apache.ignite.internal.binary.BinaryWriterEx;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;
import org.apache.ignite.internal.marshaller.optimized.OptimizedMarshaller;
import org.apache.ignite.internal.util.GridUnsafe;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * Instances of this class handle (de-)serialisations of {@link ModuleVersionNumber} instances which cannot be marshalled by Ignite's
 * default {@link BinaryMarshaller} and requires a fallback to {@link OptimizedMarshaller} on account of implementing {@link Externalizable}
 * with custom {@link Externalizable#writeExternal(java.io.ObjectOutput) writeExternal} /
 * {@link Externalizable#readExternal(java.io.ObjectInput) readExternal} operations.
 *
 * @author Axel Faust
 */
public class ModuleVersionNumberBinarySerializer extends AbstractBinarySerializer<ModuleVersionNumber>
{

    private static final String VERSION = "version";

    private static final long DELEGATE_FIELD_OFFSET = getFieldOffset(ModuleVersionNumber.class, "delegate", ComparableVersion.class);

    public ModuleVersionNumberBinarySerializer()
    {
        super(ModuleVersionNumber.class);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final ModuleVersionNumber moduleVersion, final BinaryWriterEx rawWriter)
    {
        final BinaryOutputStream out = rawWriter.out();
        this.writeString(moduleVersion.toString(), out);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final ModuleVersionNumber moduleVersion, final BinaryWriter writer)
    {
        writer.writeString(VERSION, moduleVersion.toString());
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final ModuleVersionNumber moduleVersion, final BinaryRawReader rawReader)
    {
        final String version = this.readString(rawReader);
        final ComparableVersion delegate = new ComparableVersion(version != null ? version : "");

        GridUnsafe.putObjectField(moduleVersion, DELEGATE_FIELD_OFFSET, delegate);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final ModuleVersionNumber moduleVersion, final BinaryReader reader)
    {
        final String version = reader.readString(VERSION);
        final ComparableVersion delegate = new ComparableVersion(version != null ? version : "");

        GridUnsafe.putObjectField(moduleVersion, DELEGATE_FIELD_OFFSET, delegate);
    }

}
