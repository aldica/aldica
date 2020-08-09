/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.entity;

import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
import org.alfresco.repo.domain.node.TransactionEntity;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;

/**
 * Instances of this class handle (de-)serialisations of {@link TransactionEntity} instances in order to optimise their serial form.
 *
 * @author Axel Faust
 */
public class TransactionEntityBinarySerializer extends AbstractCustomBinarySerializer
{

    private static final String FLAGS = "flags";

    private static final String ID = "id";

    private static final String VERSION = "version";

    private static final String CHANGE_TXN_ID = "changeTxnId";

    private static final String COMMIT_TIME_MS = "commitTimeMs";

    // in some use cases (e.g. as constituent value of NodeEntity), version and commit time ms are not loaded
    // that is more by negligence (missing mapping in result template) than reasonable functional decision
    private static final byte FLAG_NO_VERSION = 1;

    private static final byte FLAG_NO_COMMIT_TIME_MS = 2;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(TransactionEntity.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final TransactionEntity transaction = (TransactionEntity) obj;

        final Long version = transaction.getVersion();
        final Long commitTimeMs = transaction.getCommitTimeMs();

        byte flags = 0;
        if (version == null)
        {
            flags |= FLAG_NO_VERSION;
        }
        if (commitTimeMs == null)
        {
            flags |= FLAG_NO_COMMIT_TIME_MS;
        }

        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();

            rawWriter.writeByte(flags);
            this.writeDbId(transaction.getId(), rawWriter);
            if (version != null)
            {
                // version for optimistic locking is effectively a DB ID as well
                this.writeDbId(version, rawWriter);
            }
            this.write(transaction.getChangeTxnId(), rawWriter);
            if (commitTimeMs != null)
            {
                // highly unlikely (impossible without DB manipulation) to have a commit time ms before 1970
                // 64 bit timestamp can also live with some bits knocked off for potential serial optimisation if enabled
                this.write(commitTimeMs, true, rawWriter);
            }
        }
        else
        {
            writer.writeByte(FLAGS, flags);
            writer.writeLong(ID, transaction.getId());
            if (version != null)
            {
                writer.writeLong(VERSION, version);
            }
            writer.writeString(CHANGE_TXN_ID, transaction.getChangeTxnId());
            if (commitTimeMs != null)
            {
                writer.writeLong(COMMIT_TIME_MS, commitTimeMs);
            }
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
        if (!cls.equals(TransactionEntity.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final TransactionEntity transaction = (TransactionEntity) obj;

        byte flags;
        Long id;
        Long version = null;
        String changeTxnId;
        Long commitTimeMs = null;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();

            flags = rawReader.readByte();
            id = this.readDbId(rawReader);
            if ((flags & FLAG_NO_VERSION) == 0)
            {
                version = this.readDbId(rawReader);
            }
            changeTxnId = this.readString(rawReader);
            if ((flags & FLAG_NO_COMMIT_TIME_MS) == 0)
            {
                commitTimeMs = this.readLong(true, rawReader);
            }
        }
        else
        {
            flags = reader.readByte(FLAGS);
            id = reader.readLong(ID);
            if ((flags & FLAG_NO_VERSION) == 0)
            {
                version = reader.readLong(VERSION);
            }
            changeTxnId = reader.readString(CHANGE_TXN_ID);
            if ((flags & FLAG_NO_COMMIT_TIME_MS) == 0)
            {
                commitTimeMs = reader.readLong(COMMIT_TIME_MS);
            }
        }

        transaction.setId(id);
        transaction.setVersion(version);
        transaction.setChangeTxnId(changeTxnId);
        transaction.setCommitTimeMs(commitTimeMs);
    }

}
