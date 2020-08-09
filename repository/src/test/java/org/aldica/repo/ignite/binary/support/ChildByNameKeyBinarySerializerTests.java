/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.model.RenditionModel;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.repo.domain.qname.ibatis.QNameDAOImpl;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryTypeConfiguration;
import org.apache.ignite.configuration.BinaryConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.marshaller.Marshaller;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

/**
 * @author Axel Faust
 */
public class ChildByNameKeyBinarySerializerTests extends GridTestsBase
{

    private static final Class<?> CHILD_BY_NAME_KEY_CLASS;

    private static final Constructor<?> CHILD_BY_NAME_KEY_CTOR;

    private static final Field PARENT_NODE_ID_FIELD;

    private static final Field ASSOC_TYPE_QNAME_FIELD;

    private static final Field CHILD_NODE_NAME_FIELD;

    static
    {
        try
        {
            // class is package protected
            CHILD_BY_NAME_KEY_CLASS = Class.forName("org.alfresco.repo.domain.node.ChildByNameKey");
            CHILD_BY_NAME_KEY_CTOR = CHILD_BY_NAME_KEY_CLASS.getDeclaredConstructor(Long.class, QName.class, String.class);
            PARENT_NODE_ID_FIELD = CHILD_BY_NAME_KEY_CLASS.getDeclaredField("parentNodeId");
            ASSOC_TYPE_QNAME_FIELD = CHILD_BY_NAME_KEY_CLASS.getDeclaredField("assocTypeQName");
            CHILD_NODE_NAME_FIELD = CHILD_BY_NAME_KEY_CLASS.getDeclaredField("childNodeName");

            CHILD_BY_NAME_KEY_CTOR.setAccessible(true);
            PARENT_NODE_ID_FIELD.setAccessible(true);
            ASSOC_TYPE_QNAME_FIELD.setAccessible(true);
            CHILD_NODE_NAME_FIELD.setAccessible(true);
        }
        catch (final ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e)
        {
            throw new AlfrescoRuntimeException("Failed to lookup class / constructor / fields");
        }
    }

    private static final QName[] ASSOC_TYPE_QNAMES = { ContentModel.ASSOC_CONTAINS, ContentModel.ASSOC_CHILDREN,
            ContentModel.ASSOC_ATTACHMENTS, ContentModel.ASSOC_AVATAR, ContentModel.ASSOC_IN_ZONE, ContentModel.ASSOC_RATINGS };

    protected static GenericApplicationContext createApplicationContext()
    {
        final GenericApplicationContext appContext = new GenericApplicationContext();

        final QNameDAO qnameDAO = EasyMock.partialMockBuilder(QNameDAOImpl.class).addMockedMethod("getQName", Long.class)
                .addMockedMethod("getQName", QName.class).createMock();
        appContext.getBeanFactory().registerSingleton("qnameDAO", qnameDAO);
        appContext.refresh();

        for (int idx = 0; idx < ASSOC_TYPE_QNAMES.length; idx++)
        {
            EasyMock.expect(qnameDAO.getQName(Long.valueOf(idx))).andStubReturn(new Pair<>(Long.valueOf(idx), ASSOC_TYPE_QNAMES[idx]));
            EasyMock.expect(qnameDAO.getQName(ASSOC_TYPE_QNAMES[idx])).andStubReturn(new Pair<>(Long.valueOf(idx), ASSOC_TYPE_QNAMES[idx]));
        }
        EasyMock.expect(qnameDAO.getQName(EasyMock.anyObject(QName.class))).andStubReturn(null);

        EasyMock.replay(qnameDAO);

        return appContext;
    }

    protected static IgniteConfiguration createConfiguration(final ApplicationContext applicationContext, final boolean idsWhenReasonable,
            final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForChildByNameKey = new BinaryTypeConfiguration();
        binaryTypeConfigurationForChildByNameKey.setTypeName(CHILD_BY_NAME_KEY_CLASS.getName());
        final ChildByNameKeyBinarySerializer serializer = new ChildByNameKeyBinarySerializer();
        serializer.setApplicationContext(applicationContext);
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        serializer.setUseIdsWhenReasonable(idsWhenReasonable);
        binaryTypeConfigurationForChildByNameKey.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForChildByNameKey));
        conf.setBinaryConfiguration(binaryConfiguration);

        return conf;
    }

    @Test
    public void defaultFormCorrectness() throws Exception
    {
        final IgniteConfiguration conf = createConfiguration(null, false, false);
        this.correctnessImpl(conf);
    }

    @Test
    public void defaultFormQNameIdSubstitutionCorrectness() throws Exception
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(appContext, true, false);
            this.correctnessImpl(conf);
        }
    }

    @Category(ExpensiveTestCategory.class)
    @Test
    public void defaultFormEfficiency() throws Exception
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration referenceConf = createConfiguration(1, false, null);
            referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");

            final IgniteConfiguration defaultConf = createConfiguration(null, false, false);
            final IgniteConfiguration useQNameIdConf = createConfiguration(appContext, true, false);

            useQNameIdConf.setIgniteInstanceName(useQNameIdConf.getIgniteInstanceName() + "-qnameIdSubstitution");

            try
            {
                final Ignite referenceGrid = Ignition.start(referenceConf);
                final Ignite defaultGrid = Ignition.start(defaultConf);
                final Ignite useQNameIdGrid = Ignition.start(useQNameIdConf);

                // minor drawback due to extra flag - -2%
                this.efficiencyImpl(referenceGrid, defaultGrid, "aldica optimised", "Ignite default", -0.02);

                // significant difference (QName is a large part of key) - 47%
                this.efficiencyImpl(referenceGrid, useQNameIdGrid, "aldica optimised (QName ID substitution)", "Ignite default", 0.47);

                // significant difference (QName is a large part of key) - 48%
                this.efficiencyImpl(defaultGrid, useQNameIdGrid, "aldica optimised (QName ID substitution)", "aldica optimised", 0.48);
            }
            finally
            {
                Ignition.stopAll(true);
            }
        }
    }

    @Test
    public void rawSerialFormCorrectness() throws Exception
    {
        final IgniteConfiguration conf = createConfiguration(null, false, true);
        this.correctnessImpl(conf);
    }

    @Test
    public void rawSerialFormQNameIdSubstitutionCorrectness() throws Exception
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(appContext, true, true);
            this.correctnessImpl(conf);
        }
    }

    @Category(ExpensiveTestCategory.class)
    @Test
    public void rawSerialFormEfficiency() throws Exception
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration referenceConf = createConfiguration(1, false, null);
            referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");

            final IgniteConfiguration defaultConf = createConfiguration(null, false, true);
            final IgniteConfiguration useQNameIdConf = createConfiguration(appContext, true, true);

            useQNameIdConf.setIgniteInstanceName(useQNameIdConf.getIgniteInstanceName() + "-qnameIdSubstitution");

            try
            {
                final Ignite referenceGrid = Ignition.start(referenceConf);
                final Ignite defaultGrid = Ignition.start(defaultConf);
                final Ignite useQNameIdGrid = Ignition.start(useQNameIdConf);

                // slight improvement due to variable length integers - 6%
                this.efficiencyImpl(referenceGrid, defaultGrid, "aldica raw serial", "aldica optimised", 0.06);

                // significant difference (QName is a large part of key) - 59%
                this.efficiencyImpl(referenceGrid, useQNameIdGrid, "aldica raw serial (QName ID substitution)", "aldica optimised", 0.59);

                // significant difference (QName is a large part of key) - 57%
                this.efficiencyImpl(defaultGrid, useQNameIdGrid, "aldica raw serial (QName ID substitution)", "aldica raw serial", 0.57);
            }
            finally
            {
                Ignition.stopAll(true);
            }
        }
    }

    protected void correctnessImpl(final IgniteConfiguration conf)
            throws InstantiationException, InvocationTargetException, IllegalAccessException
    {
        try (Ignite grid = Ignition.start(conf))
        {
            @SuppressWarnings("deprecation")
            final Marshaller marshaller = grid.configuration().getMarshaller();

            Object controlValue;
            Object serialisedValue;

            controlValue = CHILD_BY_NAME_KEY_CTOR.newInstance(Long.valueOf(1l), ContentModel.ASSOC_CONTAINS, "Company Home");
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);

            // association not covered by QNameDAO
            controlValue = CHILD_BY_NAME_KEY_CTOR.newInstance(Long.valueOf(1l), RenditionModel.ASSOC_RENDITION, "System");
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);
        }
        catch (final IgniteCheckedException ice)
        {
            throw new IllegalStateException("Serialisation failed in correctness test", ice);
        }
    }

    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid, final String serialisationType,
            final String referenceSerialisationType, final double marginFraction)
    {
        final SecureRandom rnJesus = new SecureRandom();

        final Supplier<Object> comparisonValueSupplier = () -> {
            final int idx = 1000 + rnJesus.nextInt(100000000);
            final QName assocTypeQName = ASSOC_TYPE_QNAMES[rnJesus.nextInt(ASSOC_TYPE_QNAMES.length)];
            final String childNodeName = UUID.randomUUID().toString();

            try
            {
                final Object value = CHILD_BY_NAME_KEY_CTOR.newInstance((long) idx, assocTypeQName, childNodeName);

                return value;
            }
            catch (final InstantiationException | InvocationTargetException | IllegalAccessException e)
            {
                throw new RuntimeException("Failed to instantiate benchmark value");
            }
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "ChildByNameKey", referenceSerialisationType, serialisationType,
                comparisonValueSupplier, marginFraction);
    }
}
