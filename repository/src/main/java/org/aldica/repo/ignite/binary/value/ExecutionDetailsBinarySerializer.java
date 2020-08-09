/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.value;

import java.lang.reflect.Field;
import java.util.Date;

import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
import org.alfresco.service.cmr.action.ExecutionDetails;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;

/**
 * Instances of this class handle (de-)serialisations of {@link ExecutionDetails} instances in order to optimise their serial form. This
 * implementation primarily aims to optimise by simplified serialisation of the composite date, cancellation flag, and any null-state flags
 * of instance members.
 *
 * @author Axel Faust
 */
public class ExecutionDetailsBinarySerializer extends AbstractCustomBinarySerializer
{

    private static final String FLAGS = "flags";

    private static final String PERSISTED_ACTION_REF = "persistedActionRef";

    private static final String ACTIONED_UPON_NODE_REF = "actionedUponNodeRef";

    private static final String RUNNING_ON = "runningOn";

    private static final String STARTED_AT = "startedAt";

    private static final Field PERSISTED_ACTION_REF_FIELD;

    private static final Field ACTIONED_UPON_NODE_REF_FIELD;

    private static final Field RUNNING_ON_FIELD;

    private static final Field STARTED_AT_FIELD;

    private static final Field CANCEL_REQUESTED_FIELD;

    static
    {
        try
        {
            PERSISTED_ACTION_REF_FIELD = ExecutionDetails.class.getDeclaredField("persistedActionRef");
            ACTIONED_UPON_NODE_REF_FIELD = ExecutionDetails.class.getDeclaredField("actionedUponNodeRef");
            RUNNING_ON_FIELD = ExecutionDetails.class.getDeclaredField("runningOn");
            STARTED_AT_FIELD = ExecutionDetails.class.getDeclaredField("startedAt");
            CANCEL_REQUESTED_FIELD = ExecutionDetails.class.getDeclaredField("cancelRequested");

            PERSISTED_ACTION_REF_FIELD.setAccessible(true);
            ACTIONED_UPON_NODE_REF_FIELD.setAccessible(true);
            RUNNING_ON_FIELD.setAccessible(true);
            STARTED_AT_FIELD.setAccessible(true);
            CANCEL_REQUESTED_FIELD.setAccessible(true);
        }
        catch (final NoSuchFieldException nsfe)
        {
            throw new RuntimeException("Failed to initialise reflective field accessors", nsfe);
        }
    }

    private static final byte FLAG_CANCEL_REQUESTED = 0x01;

    private static final byte FLAG_NULL_ACTION_REF = 0x02;

    private static final byte FLAG_NULL_ACTIONED_NODE = 0x04;

    private static final byte FLAG_NULL_RUNNING_ON = 0x08;

    private static final byte FLAG_NULL_STARTED_AT = 0x10;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(ExecutionDetails.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final ExecutionDetails details = (ExecutionDetails) obj;

        final NodeRef persistedActionRef = details.getPersistedActionRef();
        final NodeRef actionedUponNodeRef = details.getActionedUponNodeRef();
        final String runningOn = details.getRunningOn();
        final Date startedAt = details.getStartedAt();
        final boolean cancelRequested = details.isCancelRequested();

        byte flags = 0;

        if (persistedActionRef == null)
        {
            flags |= FLAG_NULL_ACTION_REF;
        }
        if (actionedUponNodeRef == null)
        {
            flags |= FLAG_NULL_ACTIONED_NODE;
        }
        if (runningOn == null)
        {
            flags |= FLAG_NULL_RUNNING_ON;
        }
        if (startedAt == null)
        {
            flags |= FLAG_NULL_STARTED_AT;
        }
        if (cancelRequested)
        {
            flags |= FLAG_CANCEL_REQUESTED;
        }

        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();

            rawWriter.writeByte(flags);
            if (persistedActionRef != null)
            {
                rawWriter.writeObject(persistedActionRef);
            }
            if (actionedUponNodeRef != null)
            {
                rawWriter.writeObject(actionedUponNodeRef);
            }
            if (runningOn != null)
            {
                this.write(runningOn, rawWriter);
            }
            if (startedAt != null)
            {
                this.write(startedAt.getTime(), true, rawWriter);
            }
        }
        else
        {
            writer.writeByte(FLAGS, flags);
            if (persistedActionRef != null)
            {
                writer.writeObject(PERSISTED_ACTION_REF, persistedActionRef);
            }
            if (actionedUponNodeRef != null)
            {
                writer.writeObject(ACTIONED_UPON_NODE_REF, actionedUponNodeRef);
            }
            if (runningOn != null)
            {
                writer.writeString(RUNNING_ON, runningOn);
            }
            if (startedAt != null)
            {
                writer.writeLong(STARTED_AT, startedAt.getTime());
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
        if (!cls.equals(ExecutionDetails.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        NodeRef persistedActionRef = null;
        NodeRef actionedUponNodeRef = null;
        String runningOn = null;
        Date startedAt = null;
        boolean cancelRequested = false;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();

            final byte flags = rawReader.readByte();

            if ((flags & FLAG_NULL_ACTION_REF) == 0)
            {
                persistedActionRef = rawReader.readObject();
            }
            if ((flags & FLAG_NULL_ACTIONED_NODE) == 0)
            {
                actionedUponNodeRef = rawReader.readObject();
            }
            if ((flags & FLAG_NULL_RUNNING_ON) == 0)
            {
                runningOn = this.readString(rawReader);
            }
            if ((flags & FLAG_NULL_STARTED_AT) == 0)
            {
                startedAt = new Date(this.readLong(true, rawReader));
            }
            cancelRequested = (flags & FLAG_CANCEL_REQUESTED) == FLAG_CANCEL_REQUESTED;
        }
        else
        {

            final byte flags = reader.readByte(FLAGS);

            if ((flags & FLAG_NULL_ACTION_REF) == 0)
            {
                persistedActionRef = reader.readObject(PERSISTED_ACTION_REF);
            }
            if ((flags & FLAG_NULL_ACTIONED_NODE) == 0)
            {
                actionedUponNodeRef = reader.readObject(ACTIONED_UPON_NODE_REF);
            }
            if ((flags & FLAG_NULL_RUNNING_ON) == 0)
            {
                runningOn = reader.readString(RUNNING_ON);
            }
            if ((flags & FLAG_NULL_STARTED_AT) == 0)
            {
                startedAt = new Date(reader.readLong(STARTED_AT));
            }
            cancelRequested = (flags & FLAG_CANCEL_REQUESTED) == FLAG_CANCEL_REQUESTED;
        }

        try
        {
            PERSISTED_ACTION_REF_FIELD.set(obj, persistedActionRef);
            ACTIONED_UPON_NODE_REF_FIELD.set(obj, actionedUponNodeRef);
            RUNNING_ON_FIELD.set(obj, runningOn);
            STARTED_AT_FIELD.set(obj, startedAt);
            CANCEL_REQUESTED_FIELD.set(obj, cancelRequested);
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to write deserialised field values", iae);
        }
    }
}
