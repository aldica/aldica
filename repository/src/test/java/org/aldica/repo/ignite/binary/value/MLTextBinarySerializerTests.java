/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.value;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.alfresco.repo.domain.locale.LocaleDAO;
import org.alfresco.repo.domain.locale.ibatis.LocaleDAOImpl;
import org.alfresco.service.cmr.repository.MLText;
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
public class MLTextBinarySerializerTests extends GridTestsBase
{

    private static final Locale[] LOCALES = { Locale.ENGLISH, Locale.GERMAN, Locale.FRENCH, Locale.CHINESE, Locale.JAPANESE, Locale.ITALIAN,
            Locale.SIMPLIFIED_CHINESE, Locale.US, Locale.UK, Locale.GERMANY };

    protected static GenericApplicationContext createApplicationContext()
    {
        final GenericApplicationContext appContext = new GenericApplicationContext();

        final LocaleDAO localeDAO = EasyMock.partialMockBuilder(LocaleDAOImpl.class).addMockedMethod("getLocalePair", Long.class)
                .addMockedMethod("getLocalePair", Locale.class).createMock();
        appContext.getBeanFactory().registerSingleton("localeDAO", localeDAO);
        appContext.refresh();

        for (int idx = 0; idx < LOCALES.length; idx++)
        {
            EasyMock.expect(localeDAO.getLocalePair(Long.valueOf(idx))).andStubReturn(new Pair<>(Long.valueOf(idx), LOCALES[idx]));
            EasyMock.expect(localeDAO.getLocalePair(LOCALES[idx])).andStubReturn(new Pair<>(Long.valueOf(idx), LOCALES[idx]));
        }
        EasyMock.expect(localeDAO.getLocalePair(EasyMock.anyObject(Locale.class))).andStubReturn(null);

        EasyMock.replay(localeDAO);

        return appContext;
    }

    protected static IgniteConfiguration createConfiguration(final ApplicationContext applicationContext, final boolean idsWhenReasonable,
            final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForMLText = new BinaryTypeConfiguration();
        binaryTypeConfigurationForMLText.setTypeName(MLText.class.getName());
        final MLTextBinarySerializer serializer = new MLTextBinarySerializer();
        serializer.setApplicationContext(applicationContext);
        serializer.setUseIdsWhenReasonable(idsWhenReasonable);
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        binaryTypeConfigurationForMLText.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForMLText));
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
    public void defaultFormIdSubstitutionCorrectness()
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
            final IgniteConfiguration useIdConf = createConfiguration(appContext, true, false);
            useIdConf.setIgniteInstanceName(useIdConf.getIgniteInstanceName() + "-idSubstitution");

            try
            {
                final Ignite referenceGrid = Ignition.start(referenceConf);
                final Ignite defaultGrid = Ignition.start(defaultConf);
                final Ignite useIdGrid = Ignition.start(useIdConf);

                // since our optimised serialisation replaces Locale with textual representation, we can provide a significant benefit - 31%
                this.efficiencyImpl(referenceGrid, defaultGrid, "aldica optimised", "Ignite default", 0.31);

                // ID substitution should have roughly similar benefit when language AND country variant are used - 32%
                this.efficiencyImpl(referenceGrid, useIdGrid, "aldica optimised (ID substitution)", "Ignite default", 0.32);

                // since switching Locale for its textual representation in default optimisation
                // ID substitution provides equal benefits
                this.efficiencyImpl(defaultGrid, useIdGrid, "aldica optimised (ID substitution)", "aldica optimised", 0.01);
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
    public void rawSerialFormIdSubstitutionCorrectness()
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
            final IgniteConfiguration useIdConf = createConfiguration(appContext, true, true);
            useIdConf.setIgniteInstanceName(useIdConf.getIgniteInstanceName() + "-idSubstitution");

            try
            {
                final Ignite referenceGrid = Ignition.start(referenceConf);
                final Ignite defaultGrid = Ignition.start(defaultConf);
                final Ignite useIdGrid = Ignition.start(useIdConf);

                // normally, for objects with a single field, there is no benefit in raw serial form
                // but MLText is able to use String optimisations via variable length primitives - 14%
                this.efficiencyImpl(referenceGrid, defaultGrid, "aldica raw serial", "aldica optimised", 0.14);

                // aldica default already provides decent improvements by using Locale textual representation
                // serial form with ID substitution can still provide benefits via variable length primitives for ID substitution - 21%
                this.efficiencyImpl(referenceGrid, useIdGrid, "aldica raw serial (ID substitution)", "aldica optimised", 0.21);

                // only benefit comes from variable length primitive long being more efficient than short text - 7%
                this.efficiencyImpl(defaultGrid, useIdGrid, "aldica raw serial (ID substitution)", "aldica raw serial", 0.07);
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

            MLText controlValue;
            MLText serialisedValue;

            controlValue = new MLText(Locale.ENGLISH, "English text");
            controlValue.addValue(Locale.GERMAN, "German text");
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);

            // test unsupported locale
            controlValue = new MLText(Locale.UK, "English text");
            controlValue.addValue(Locale.GERMANY, "German text");
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
        final Supplier<MLText> comparisonValueSupplier = () -> {
            final MLText value = new MLText(Locale.US, UUID.randomUUID().toString());
            value.addValue(Locale.GERMANY, UUID.randomUUID().toString());
            value.addValue(Locale.UK, UUID.randomUUID().toString());

            return value;
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "MLText", referenceSerialisationType, serialisationType,
                comparisonValueSupplier, marginFraction);
    }
}
