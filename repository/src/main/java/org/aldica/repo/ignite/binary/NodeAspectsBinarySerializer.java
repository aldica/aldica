/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.aldica.repo.ignite.cache.NodeAspectsCacheSet;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinarySerializer;
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
public class NodeAspectsBinarySerializer implements BinarySerializer, ApplicationContextAware
{

    private static final String VALUES = "values";

    protected ApplicationContext applicationContext;

    protected QNameDAO qnameDAO;

    protected boolean useIdsWhenReasonable = false;

    protected boolean useRawSerialForm = false;

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
     * @param useRawSerialForm
     *            the useRawSerialForm to set
     */
    public void setUseRawSerialForm(final boolean useRawSerialForm)
    {
        this.useRawSerialForm = useRawSerialForm;
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
        rawWriter.writeInt(size);

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
                rawWriter.writeLong(qnamePair.getFirst());
            }
            else
            {
                rawWriter.writeObject(aspectQName);
            }
        }
    }

    protected void readAspectsRawSerialForm(final NodeAspectsCacheSet aspects, final BinaryRawReader rawReader) throws BinaryObjectException
    {
        final int size = rawReader.readInt();

        for (int idx = 0; idx < size; idx++)
        {
            QName aspectQName;
            if (this.useIdsWhenReasonable)
            {
                final long id = rawReader.readLong();
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
        if (this.useIdsWhenReasonable)
        {
            final Set<Long> ids = aspects.stream().map(aspectQName -> {
                final Pair<Long, QName> qnamePair = this.qnameDAO.getQName(aspectQName);
                if (qnamePair == null)
                {
                    throw new AlfrescoRuntimeException("Cannot resolve " + aspectQName + " to DB ID");
                }

                return qnamePair.getFirst();
            }).collect(Collectors.toSet());

            writer.writeCollection(VALUES, ids);
        }
        else
        {
            // must be wrapped otherwise it would be written as self-referential handle
            // effectively preventing ANY values from being written
            writer.writeCollection(VALUES, Collections.unmodifiableSet(aspects));
        }
    }

    protected void readAspectsRegularSerialForm(final NodeAspectsCacheSet aspects, final BinaryReader reader)
    {
        final Collection<?> values = reader.readCollection(VALUES);
        values.forEach(value -> {
            QName aspectQName;
            if (value instanceof Long)
            {
                if (!this.useIdsWhenReasonable)
                {
                    throw new BinaryObjectException("Serializer is not configured to use IDs in place of QName keys");
                }
                final Pair<Long, QName> qnamePair = this.qnameDAO.getQName((Long) value);
                if (qnamePair == null)
                {
                    throw new BinaryObjectException("Cannot resolve QName for ID " + value);
                }
                aspectQName = qnamePair.getSecond();
            }
            else
            {
                aspectQName = (QName) value;
            }
            aspects.add(aspectQName);
        });
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
