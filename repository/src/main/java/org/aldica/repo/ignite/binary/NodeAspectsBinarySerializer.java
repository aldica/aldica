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
import org.alfresco.model.ContentModel;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryWriterExImpl;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;
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
public class NodeAspectsBinarySerializer extends AbstractExtendedBinarySerializer<NodeAspectsCacheSet> implements ApplicationContextAware
{

    private static final Set<QName> IMPLICIT_ASPECTS = Set.of(ContentModel.ASPECT_REFERENCEABLE, ContentModel.ASPECT_LOCALIZED);

    private static final String VALUES = "values";

    protected ApplicationContext applicationContext;

    protected QNameDAO qnameDAO;

    public NodeAspectsBinarySerializer()
    {
        super(NodeAspectsCacheSet.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
    {
        this.applicationContext = applicationContext;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
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

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final NodeAspectsCacheSet nodeAspectsCacheSet, final BinaryWriterExImpl rawWriter)
    {
        final BinaryOutputStream out = rawWriter.out();

        // these aspects "should" be implicit and not part of cached data
        // but in some use cases were observed to be present
        // (turns out Alfresco has a bug in AbstractNodeDAOImpl#removeNodeAspects that ends up with implicit aspects in cache data because
        // getNodeAspects adds them for the "before" state and this is not undone before "after" state is used in setNodeAspectsCached)
        // they cannot be resolved to QName IDs as they are never persisted
        nodeAspectsCacheSet.removeAll(IMPLICIT_ASPECTS);
        final int size = nodeAspectsCacheSet.size();

        int baseBytes = 2;
        // assume Long IDs compressed to short / avg. 14 character per QName
        // (QName serialisation should most often write single byte for common namespace URIs and ~14 characters local name, using default
        // model aspects as an estimation baseline)
        int estBytesPerAspect = 15;

        if (this.useIdsWhenReasonable)
        {
            estBytesPerAspect = 2;
            baseBytes += Math.ceil(size / 8.0);
        }

        // ensure estimated capacity to avoid multiple smaller allocations
        final int estBytes = baseBytes + (size * estBytesPerAspect);
        out.unsafeEnsure(estBytes);

        out.unsafeWriteBoolean(this.useIdsWhenReasonable);
        this.writeUnsigned(size, out);

        if (size != 0)
        {
            if (this.useIdsWhenReasonable)
            {
                this.doWriteRawIds(nodeAspectsCacheSet, size, out);
            }
            else
            {
                for (final QName aspect : nodeAspectsCacheSet)
                {
                    this.writeQName(aspect, out);
                }
            }
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final NodeAspectsCacheSet nodeAspectsCacheSet, final BinaryWriter writer)
    {
        // these aspects "should" be implicit and not part of cached data
        // but in some use cases were observed to be present
        // (turns out Alfresco has a bug in AbstractNodeDAOImpl#removeNodeAspects that ends up with implicit aspects in cache data because
        // getNodeAspects adds them for the "before" state and this is not undone before "after" state is used in setNodeAspectsCached)
        // they cannot be resolved to QName IDs as they are never persisted
        nodeAspectsCacheSet.removeAll(IMPLICIT_ASPECTS);

        if (this.useIdsWhenReasonable)
        {
            final Set<Long> ids = nodeAspectsCacheSet.stream().map(aspectQName -> {
                final Pair<Long, QName> qnamePair = this.qnameDAO.getQName(aspectQName);
                if (qnamePair == null)
                {
                    throw new BinaryObjectException("Cannot resolve " + aspectQName + " to DB ID");
                }

                return qnamePair.getFirst();
            }).collect(Collectors.toSet());

            writer.writeCollection(VALUES, ids);
        }
        else
        {
            // must be wrapped otherwise it would be written as self-referential handle
            // effectively preventing ANY values from being written
            writer.writeCollection(VALUES, Collections.unmodifiableSet(nodeAspectsCacheSet));
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final NodeAspectsCacheSet nodeAspectsCacheSet, final BinaryRawReader rawReader)
    {
        final boolean useIds = rawReader.readBoolean();
        if (useIds && !this.useIdsWhenReasonable)
        {
            throw new BinaryObjectException("Serializer is not configured to use IDs in place of QName keys");
        }

        final int size = this.readUnsignedInt(rawReader);

        if (size != 0)
        {
            if (useIds)
            {
                this.doReadRawIds(nodeAspectsCacheSet, rawReader, size);
            }
            else
            {
                for (int i = 0; i < size; i++)
                {
                    final QName aspect = this.readQName(rawReader);
                    nodeAspectsCacheSet.add(aspect);
                }
            }
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final NodeAspectsCacheSet nodeAspectsCacheSet, final BinaryReader reader)
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
            nodeAspectsCacheSet.add(aspectQName);
        });
    }

    protected void doWriteRawIds(final NodeAspectsCacheSet nodeAspectsCacheSet, final int size, final BinaryOutputStream out)
    {
        final byte[] unsignedFlagsArr = new byte[(int) Math.ceil(size / 8.0)];
        final int startPos = out.position();
        out.position(startPos + unsignedFlagsArr.length);

        int flagIdx = 0;
        int bit = 1;
        byte unsignedFlags = 0;
        for (final QName aspect : nodeAspectsCacheSet)
        {
            unsignedFlags |= this.writeValueId(aspect, this::qnameLookup, (byte) bit, out);

            if (bit == 0x80)
            {
                unsignedFlagsArr[flagIdx++] = unsignedFlags;
                unsignedFlags = 0;
                bit = 0x01;
            }
            else
            {
                bit *= 2;
            }
        }

        if (bit > 0x01)
        {
            unsignedFlagsArr[flagIdx] = unsignedFlags;
        }

        final int endPos = out.position();
        out.position(startPos);
        for (final byte b : unsignedFlagsArr)
        {
            out.unsafeWriteByte(b);
        }
        out.position(endPos);
    }

    protected void doReadRawIds(final NodeAspectsCacheSet nodeAspectsCacheSet, final BinaryRawReader rawReader, final int size)
    {
        final byte[] unsignedFlags = new byte[(int) Math.ceil(size / 8.0)];
        for (int i = 0; i < unsignedFlags.length; i++)
        {
            unsignedFlags[i] = rawReader.readByte();
        }

        int flagIdx = 0;
        int bit = 0x01;
        for (int i = 0; i < size; i++)
        {
            final boolean unsigned = (unsignedFlags[flagIdx] & bit) == bit;
            final QName aspect = this.readValueId(rawReader, this::qnameLookup, unsigned);
            nodeAspectsCacheSet.add(aspect);

            if (bit == 0x80)
            {
                flagIdx++;
                bit = 0x01;
            }
            else
            {
                bit *= 2;
            }
        }
    }

    protected Pair<Long, QName> qnameLookup(final QName qname)
    {
        return this.doLookup(LOOKUP_BUCKET_QNAME, qname, this.qnameDAO::getQName);
    }

    protected Pair<Long, QName> qnameLookup(final Long id)
    {
        return this.doLookup(LOOKUP_BUCKET_QNAME, id, this.qnameDAO::getQName);
    }
}
