/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.value;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.aldica.repo.ignite.cache.NodeAspectsCacheSet;
import org.alfresco.model.ContentModel;
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
public class NodeAspectsBinarySerializerTests extends GridTestsBase
{

    private static final QName[] ASPECT_QNAMES = { ContentModel.ASPECT_REFERENCEABLE, ContentModel.ASPECT_AUDITABLE,
            ContentModel.ASPECT_ARCHIVED, ContentModel.ASPECT_AUTHOR, ContentModel.ASPECT_CLASSIFIABLE, ContentModel.ASPECT_CHECKED_OUT,
            ContentModel.ASPECT_UNDELETABLE, ContentModel.ASPECT_UNMOVABLE, ContentModel.ASPECT_LOCKABLE, ContentModel.ASPECT_HIDDEN };

    protected static GenericApplicationContext createApplicationContext()
    {
        final GenericApplicationContext appContext = new GenericApplicationContext();

        final QNameDAO qnameDAO = EasyMock.partialMockBuilder(QNameDAOImpl.class).addMockedMethod("getQName", Long.class)
                .addMockedMethod("getQName", QName.class).createMock();
        appContext.getBeanFactory().registerSingleton("qnameDAO", qnameDAO);
        appContext.refresh();

        for (int idx = 0; idx < ASPECT_QNAMES.length; idx++)
        {
            EasyMock.expect(qnameDAO.getQName(Long.valueOf(idx))).andStubReturn(new Pair<>(Long.valueOf(idx), ASPECT_QNAMES[idx]));
            EasyMock.expect(qnameDAO.getQName(ASPECT_QNAMES[idx])).andStubReturn(new Pair<>(Long.valueOf(idx), ASPECT_QNAMES[idx]));
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

        final NodeAspectsBinarySerializer serializer = new NodeAspectsBinarySerializer();
        serializer.setApplicationContext(applicationContext);
        serializer.setUseIdsWhenReasonable(idsWhenReasonable);
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);

        final BinaryTypeConfiguration binaryTypeConfigurationForNodeAspectsCacheSet = new BinaryTypeConfiguration();
        binaryTypeConfigurationForNodeAspectsCacheSet.setTypeName(NodeAspectsCacheSet.class.getName());
        binaryTypeConfigurationForNodeAspectsCacheSet.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForNodeAspectsCacheSet));
        conf.setBinaryConfiguration(binaryConfiguration);

        return conf;
    }

    @Test
    public void defaultFormCorrectness()
    {
        final IgniteConfiguration conf = createConfiguration(null, false, false);
        this.correctnessImpl(conf);
    }

    @Test
    public void defaultFormQNameIdSubstitutionCorrectness()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(appContext, true, false);
            this.correctnessImpl(conf);
        }
    }

    @Category(ExpensiveTestCategory.class)
    @Test
    public void defaultFormEfficiency()
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

                // default uses HashSet.writeObject and Serializable all the way through, which is already very efficient
                // this actually intrinsically deduplicates common objects / values (e.g. namespace URIs)
                // without ID substitution (or our custom QNameBinarySerializer), our serialisation cannot come close - -115%
                this.efficiencyImpl(referenceGrid, defaultGrid, "aldica optimised", "Ignite default", -1.15);

                // ID substitution is everything - 70%
                this.efficiencyImpl(referenceGrid, useQNameIdGrid, "aldica optimised (QName ID substitution)", "Ignite default", 0.7);

                // ID substitution is everything - 86%
                this.efficiencyImpl(defaultGrid, useQNameIdGrid, "aldica optimised (QName ID substitution)", "aldica optimised", 0.86);
            }
            finally
            {
                Ignition.stopAll(true);
            }
        }
    }

    @Test
    public void rawSerialFormCorrectness()
    {
        final IgniteConfiguration conf = createConfiguration(null, false, true);
        this.correctnessImpl(conf);
    }

    @Test
    public void rawSerialFormQNameIdSubstitutionCorrectness()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(appContext, true, true);
            this.correctnessImpl(conf);
        }
    }

    @Category(ExpensiveTestCategory.class)
    @Test
    public void rawSerialFormEfficiency()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration referenceConf = createConfiguration(null, false, false);
            referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");

            final IgniteConfiguration defaultConf = createConfiguration(null, false, true);
            final IgniteConfiguration useQNameIdConf = createConfiguration(appContext, true, true);

            useQNameIdConf.setIgniteInstanceName(useQNameIdConf.getIgniteInstanceName() + "-qnameIdSubstitution");

            try
            {
                final Ignite referenceGrid = Ignition.start(referenceConf);
                final Ignite defaultGrid = Ignition.start(defaultConf);
                final Ignite useQNameIdGrid = Ignition.start(useQNameIdConf);

                // only a slight improvement due to metadata + collection handling - 0.5%
                this.efficiencyImpl(referenceGrid, defaultGrid, "aldica raw serial", "aldica optimised", 0.005);

                // ID substitution + variable length integers are critical - 94%
                this.efficiencyImpl(referenceGrid, useQNameIdGrid, "aldica raw serial (ID substitution)", "aldica optimised", 0.94);

                // ID substitution is everything - 94%
                this.efficiencyImpl(defaultGrid, useQNameIdGrid, "aldica raw serial (ID substitution)", "aldica raw serial", 0.94);
            }
            finally
            {
                Ignition.stopAll(true);
            }
        }
    }

    protected void correctnessImpl(final IgniteConfiguration conf)
    {
        try (Ignite grid = Ignition.start(conf))
        {
            @SuppressWarnings("deprecation")
            final Marshaller marshaller = grid.configuration().getMarshaller();

            NodeAspectsCacheSet controlValue;
            NodeAspectsCacheSet serialisedValue;

            controlValue = new NodeAspectsCacheSet();
            for (final QName aspectQName : ASPECT_QNAMES)
            {
                controlValue.add(aspectQName);
            }

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved (different value instances)
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

        final Supplier<NodeAspectsCacheSet> comparisonValueSupplier = () -> {
            final NodeAspectsCacheSet value = new NodeAspectsCacheSet();

            final int countAspects = (ASPECT_QNAMES.length / 2) + rnJesus.nextInt(ASPECT_QNAMES.length / 2);
            while (value.size() < countAspects)
            {
                value.add(ASPECT_QNAMES[rnJesus.nextInt(ASPECT_QNAMES.length)]);
            }

            return value;
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "NodeAspectsCacheSet", referenceSerialisationType, serialisationType,
                comparisonValueSupplier, marginFraction);
    }
}
