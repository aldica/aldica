/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.value;

import java.util.Collections;
import java.util.HashSet;

import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
import org.aldica.repo.ignite.cache.NodeAspectsCacheSet;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Instances of this class handle (de-)serialisations of {@link NodeAspectsCacheSet} instances. By using that sub-class of
 * {@link HashSet} to persist cached aspects and this serializer implementation we are able to apply optimisations during
 * marshalling, resulting in generally smaller binary representations. This implementation is capable of replacing {@link QName aspects
 * names} with their corresponding IDs for a more efficient serial form.
 *
 * @author Axel Faust
 */
public class NodeAspectsBinarySerializer extends AbstractCustomBinarySerializer implements ApplicationContextAware
{

    private static final String QNAME_ID_FLAG = "qNameIdFlag";

    private static final String VALUES = "values";

    protected ApplicationContext applicationContext;

    protected QNameDAO qnameDAO;

    protected boolean useIdsWhenReasonable = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
    {
        this.applicationContext = applicationContext;
    }

    /**
     * @param useIdsWhenReasonable
     *            the useIdsWhenReasonable to set
     */
    public void setUseIdsWhenReasonable(final boolean useIdsWhenReasonable)
    {
        this.useIdsWhenReasonable = useIdsWhenReasonable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(NodeAspectsCacheSet.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        this.ensureDAOsAvailable();

        final NodeAspectsCacheSet aspects = (NodeAspectsCacheSet) obj;

        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();
            this.writeAspectsRawSerialForm(aspects, rawWriter);
        }
        else
        {
            this.writeAspectsRegularSerialForm(aspects, writer);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBinary(final Object obj, final BinaryReader reader) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(NodeAspectsCacheSet.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        this.ensureDAOsAvailable();

        final NodeAspectsCacheSet aspects = (NodeAspectsCacheSet) obj;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();
            this.readAspectsRawSerialForm(aspects, rawReader);
        }
        else
        {
            this.readAspectsRegularSerialForm(aspects, reader);
        }
    }

    protected void writeAspectsRawSerialForm(final NodeAspectsCacheSet aspects, final BinaryRawWriter rawWriter)
    {
        final int size = aspects.size();
        this.write(size, true, rawWriter);
        rawWriter.writeBoolean(this.useIdsWhenReasonable);

        for (final QName aspectQName : aspects)
        {
            if (this.useIdsWhenReasonable)
            {
                // technically may be null, but practically guaranteed to always be valid
                final Pair<Long, QName> qnamePair = this.qnameDAO.getQName(aspectQName);
                if (qnamePair == null)
                {
                    throw new AlfrescoRuntimeException("Cannot resolve " + aspectQName + " to DB ID");
                }
                this.writeDbId(qnamePair.getFirst(), rawWriter);
            }
            else
            {
                rawWriter.writeObject(aspectQName);
            }
        }
    }

    protected void readAspectsRawSerialForm(final NodeAspectsCacheSet aspects, final BinaryRawReader rawReader) throws BinaryObjectException
    {
        final int size = this.readInt(true, rawReader);
        final boolean usesIds = rawReader.readBoolean();

        if (usesIds && !this.useIdsWhenReasonable)
        {
            throw new BinaryObjectException("Serializer is not configured to use IDs in place of QName");
        }

        for (int idx = 0; idx < size; idx++)
        {
            QName aspectQName;
            if (usesIds)
            {
                final long id = this.readDbId(rawReader);
                final Pair<Long, QName> qnamePair = this.qnameDAO.getQName(id);
                if (qnamePair == null)
                {
                    throw new BinaryObjectException("Cannot resolve QName for ID " + id);
                }
                aspectQName = qnamePair.getSecond();
            }
            else
            {
                aspectQName = rawReader.readObject();
            }
            aspects.add(aspectQName);
        }
    }

    protected void writeAspectsRegularSerialForm(final NodeAspectsCacheSet aspects, final BinaryWriter writer)
    {
        writer.writeBoolean(QNAME_ID_FLAG, this.useIdsWhenReasonable);
        if (this.useIdsWhenReasonable)
        {
            final long[] ids = new long[aspects.size()];
            int idx = 0;
            for (final QName aspectQName : aspects)
            {
                final Pair<Long, QName> qnamePair = this.qnameDAO.getQName(aspectQName);
                if (qnamePair == null)
                {
                    throw new AlfrescoRuntimeException("Cannot resolve " + aspectQName + " to DB ID");
                }

                ids[idx++] = qnamePair.getFirst().longValue();
            }

            writer.writeLongArray(VALUES, ids);
        }
        else
        {
            writer.writeObjectArray(VALUES, aspects.toArray(new QName[0]));
        }
    }

    protected void readAspectsRegularSerialForm(final NodeAspectsCacheSet aspects, final BinaryReader reader)
    {
        final boolean usesIds = reader.readBoolean(QNAME_ID_FLAG);

        if (usesIds && !this.useIdsWhenReasonable)
        {
            throw new BinaryObjectException("Serializer is not configured to use IDs in place of QName");
        }

        if (usesIds)
        {
            final long[] ids = reader.readLongArray(VALUES);
            for (final long id : ids)
            {
                final Pair<Long, QName> qnamePair = this.qnameDAO.getQName(id);
                if (qnamePair == null)
                {
                    throw new BinaryObjectException("Cannot resolve QName for ID " + id);
                }
                aspects.add(qnamePair.getSecond());
            }
        }
        else
        {
            final QName[] values = (QName[]) reader.readObjectArray(VALUES);
            Collections.addAll(aspects, values);
        }
    }

    protected void ensureDAOsAvailable() throws BinaryObjectException
    {
        if (this.useIdsWhenReasonable && this.qnameDAO == null)
        {
            try
            {
                this.qnameDAO = this.applicationContext.getBean("qnameDAO", QNameDAO.class);
            }
            catch (final BeansException be)
            {
                throw new BinaryObjectException("Cannot (de-)serialise node properties in current configuration without access to QNameDAO",
                        be);
            }
        }
    }
}
