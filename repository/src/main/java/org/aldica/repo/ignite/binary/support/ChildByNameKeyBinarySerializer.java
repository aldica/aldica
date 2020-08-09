/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.support;

import java.lang.reflect.Field;

import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
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
 * Instances of this class handle (de-)serialisations of {@code ChildByNameKey} instances in order to optimise their serial form. This
 * implementation aims to optimise by replacing the {@link QName qualified association type name} with its numerical ID and using the
 * {@link #setUseVariableLengthIntegers(boolean) variable length integer} feature for the parent node and type ID in the raw serial form.
 * Since {@code ChildByNameKey} instances are used for lookup as well as mapping, there may very well be scenarios where a particular
 * {@link QName} instance has not yet been persisted to the database, and as such no ID can be resolved for it. In that case, the raw value
 * is kept in the serial form.
 *
 * @author Axel Faust
 */
public class ChildByNameKeyBinarySerializer extends AbstractCustomBinarySerializer implements ApplicationContextAware
{

    private static final String QNAME_ID_FLAG = "qNameIdFlag";

    private static final String PARENT_NODE_ID = "parentNodeId";

    private static final String ASSOC_TYPE_QNAME_ID = "assocTypeQNameId";

    private static final String ASSOC_TYPE_QNAME = "assocTypeQName";

    private static final String CHILD_NODE_NAME = "childNodeName";

    // cannot use a literal because class has default visibility
    private static final Class<?> CHILD_BY_NAME_KEY_CLASS;

    private static final Field PARENT_NODE_ID_FIELD;

    private static final Field ASSOC_TYPE_QNAME_FIELD;

    private static final Field CHILD_NODE_NAME_FIELD;

    static
    {
        try
        {
            // class is package protected
            CHILD_BY_NAME_KEY_CLASS = Class.forName("org.alfresco.repo.domain.node.ChildByNameKey");
            PARENT_NODE_ID_FIELD = CHILD_BY_NAME_KEY_CLASS.getDeclaredField(PARENT_NODE_ID);
            ASSOC_TYPE_QNAME_FIELD = CHILD_BY_NAME_KEY_CLASS.getDeclaredField(ASSOC_TYPE_QNAME);
            CHILD_NODE_NAME_FIELD = CHILD_BY_NAME_KEY_CLASS.getDeclaredField(CHILD_NODE_NAME);

            PARENT_NODE_ID_FIELD.setAccessible(true);
            ASSOC_TYPE_QNAME_FIELD.setAccessible(true);
            CHILD_NODE_NAME_FIELD.setAccessible(true);
        }
        catch (final ClassNotFoundException | NoSuchFieldException e)
        {
            throw new RuntimeException("Failed to initialise reflective class or field accessors", e);
        }
    }

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
     *
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(CHILD_BY_NAME_KEY_CLASS))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        this.ensureDAOsAvailable();

        try
        {
            if (this.useRawSerialForm)
            {
                final BinaryRawWriter rawWriter = writer.rawWriter();

                this.writeRawSerialForm(obj, rawWriter);
            }
            else
            {
                this.writeRegularSerialForm(obj, writer);
            }
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to retrieve fields to write", iae);
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
        if (!cls.equals(CHILD_BY_NAME_KEY_CLASS))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        this.ensureDAOsAvailable();

        try
        {
            if (this.useRawSerialForm)
            {
                this.readRawSerialForm(obj, reader);
            }
            else
            {
                this.readRegularSerialForm(obj, reader);
            }
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to write deserialised field values", iae);
        }
    }

    /**
     * Writes the raw serial form of a {@code ChildByNameKey} instance.
     *
     * @param obj
     *            the instance from which to obtain the state to write
     * @param rawWriter
     *            the writer for the state
     * @throws IllegalAccessException
     *             if any reflective access errors occur when writing the instance state
     */
    protected void writeRawSerialForm(final Object obj, final BinaryRawWriter rawWriter) throws IllegalAccessException
    {
        final Long parentNodeId = (Long) PARENT_NODE_ID_FIELD.get(obj);
        final QName assocTypeQName = (QName) ASSOC_TYPE_QNAME_FIELD.get(obj);
        final String childNodeName = (String) CHILD_NODE_NAME_FIELD.get(obj);

        this.writeDbId(parentNodeId, rawWriter);

        if (this.useIdsWhenReasonable)
        {
            final Pair<Long, QName> qNamePair = this.qnameDAO.getQName(assocTypeQName);
            if (qNamePair != null)
            {
                final Long qNameId = qNamePair.getFirst();
                rawWriter.writeBoolean(true);
                this.writeDbId(qNameId, rawWriter);
            }
            else
            {
                rawWriter.writeBoolean(false);
                rawWriter.writeObject(assocTypeQName);
            }
        }
        else
        {
            rawWriter.writeBoolean(false);
            rawWriter.writeObject(assocTypeQName);
        }

        this.write(childNodeName, rawWriter);
    }

    /**
     * Writes the regular serial form of a {@code ChildByNameKey} instance.
     *
     * @param obj
     *            the instance from which to obtain the state to write
     * @param writer
     *            the writer for the state
     * @throws IllegalAccessException
     *             if any reflective access errors occur when writing the instance state
     */
    protected void writeRegularSerialForm(final Object obj, final BinaryWriter writer) throws IllegalAccessException
    {
        final Long parentNodeId = (Long) PARENT_NODE_ID_FIELD.get(obj);
        final QName assocTypeQName = (QName) ASSOC_TYPE_QNAME_FIELD.get(obj);
        final String childNodeName = (String) CHILD_NODE_NAME_FIELD.get(obj);

        writer.writeLong(PARENT_NODE_ID, parentNodeId);

        if (this.useIdsWhenReasonable)
        {
            final Pair<Long, QName> qNamePair = this.qnameDAO.getQName(assocTypeQName);
            if (qNamePair != null)
            {
                final Long qNameId = qNamePair.getFirst();
                writer.writeBoolean(QNAME_ID_FLAG, true);
                writer.writeLong(ASSOC_TYPE_QNAME_ID, qNameId);
            }
            else
            {
                writer.writeBoolean(QNAME_ID_FLAG, false);
                writer.writeObject(ASSOC_TYPE_QNAME, assocTypeQName);
            }
        }
        else
        {
            writer.writeBoolean(QNAME_ID_FLAG, false);
            writer.writeObject(ASSOC_TYPE_QNAME, assocTypeQName);
        }

        writer.writeString(CHILD_NODE_NAME, childNodeName);
    }

    /**
     * Reads the raw serial form of a {@code ChildByNameKey} instance.
     *
     * @param obj
     *            the instance to which to write the read state
     * @param reader
     *            the reader for the state
     * @throws IllegalAccessException
     *             if any reflective access errors occur when writing the state to the instance
     */
    protected void readRawSerialForm(final Object obj, final BinaryReader reader) throws IllegalAccessException
    {
        final Long parentNodeId;
        final QName assocTypeQName;
        final String childNodeName;

        final BinaryRawReader rawReader = reader.rawReader();

        parentNodeId = this.readDbId(rawReader);
        final boolean idSubstitutedQName = rawReader.readBoolean();

        if (!this.useIdsWhenReasonable && idSubstitutedQName)
        {
            throw new BinaryObjectException("Serializer is not configured to use IDs in place of QName");
        }
        if (idSubstitutedQName)
        {
            final Long qnameId = this.readDbId(rawReader);
            final Pair<Long, QName> qnamePair = this.qnameDAO.getQName(qnameId);
            if (qnamePair == null)
            {
                throw new BinaryObjectException("Cannot resolve QName for ID " + qnameId);
            }
            assocTypeQName = qnamePair.getSecond();
        }
        else
        {
            assocTypeQName = rawReader.readObject();
        }

        childNodeName = this.readString(rawReader);

        PARENT_NODE_ID_FIELD.set(obj, parentNodeId);
        ASSOC_TYPE_QNAME_FIELD.set(obj, assocTypeQName);
        CHILD_NODE_NAME_FIELD.set(obj, childNodeName);

    }

    /**
     * Reads the regular serial form of a {@code ChildByNameKey} instance.
     *
     * @param obj
     *            the instance to which to write the read state
     * @param reader
     *            the reader for the state
     * @throws IllegalAccessException
     *             if any reflective access errors occur when writing the state to the instance
     */
    protected void readRegularSerialForm(final Object obj, final BinaryReader reader) throws IllegalAccessException
    {
        final Long parentNodeId;
        final QName assocTypeQName;
        final String childNodeName;

        parentNodeId = reader.readLong(PARENT_NODE_ID);

        final boolean idSubstitutedQName = reader.readBoolean(QNAME_ID_FLAG);

        if (!this.useIdsWhenReasonable && idSubstitutedQName)
        {
            throw new BinaryObjectException("Serializer is not configured to use IDs in place of QName");
        }
        if (idSubstitutedQName)
        {
            final Long qnameId = reader.readLong(ASSOC_TYPE_QNAME_ID);
            final Pair<Long, QName> qnamePair = this.qnameDAO.getQName(qnameId);
            if (qnamePair == null)
            {
                throw new BinaryObjectException("Cannot resolve QName for ID " + qnameId);
            }
            assocTypeQName = qnamePair.getSecond();
        }
        else
        {
            assocTypeQName = reader.readObject(ASSOC_TYPE_QNAME);
        }

        childNodeName = reader.readString(CHILD_NODE_NAME);

        PARENT_NODE_ID_FIELD.set(obj, parentNodeId);
        ASSOC_TYPE_QNAME_FIELD.set(obj, assocTypeQName);
        CHILD_NODE_NAME_FIELD.set(obj, childNodeName);
    }

    /**
     * Ensures that the relevant DAO references have been lazily obtained based on the configured mode of this instance.
     *
     * @throws BinaryObjectException
     *             if this instance is not ready because a DAO reference could not be obtained
     */
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
