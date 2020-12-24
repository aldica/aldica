/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.alfresco.model.ContentModel;
import org.alfresco.service.namespace.QName;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryTypeConfiguration;
import org.apache.ignite.configuration.BinaryConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.marshaller.Marshaller;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author Axel Faust
 */
public class QNameBinarySerializerTests extends GridTestsBase
{

    private static final QName[] QNAMES;

    static
    {
        try
        {
            final List<QName> qnames = new ArrayList<>();
            final Field[] fields = ContentModel.class.getDeclaredFields();
            for (final Field field : fields)
            {
                if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())
                        && QName.class.equals(field.getType()))
                {
                    final QName qname = (QName) field.get(null);
                    qnames.add(qname);
                }
            }
            QNAMES = qnames.toArray(new QName[0]);
        }
        catch (final IllegalAccessException iaex)
        {
            throw new RuntimeException("Failed to initialise QName test data set", iaex);
        }
    }

    protected static IgniteConfiguration createConfiguration(final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForQName = new BinaryTypeConfiguration();
        binaryTypeConfigurationForQName.setTypeName(QName.class.getName());
        final QNameBinarySerializer serializer = new QNameBinarySerializer();
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        binaryTypeConfigurationForQName.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForQName));
        conf.setBinaryConfiguration(binaryConfiguration);

        return conf;
    }

    @Test
    public void defaultFormCorrectness()
    {
        final IgniteConfiguration conf = createConfiguration(false);
        this.correctnessImpl(conf);
    }

    @Category(ExpensiveTestCategory.class)
    @Test
    public void defaultFormEfficiency()
    {
        final IgniteConfiguration referenceConf = createConfiguration(1, false, null);
        referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");
        final IgniteConfiguration conf = createConfiguration(false);

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite grid = Ignition.start(conf);

            // savings on namespace should be substantial - 53%
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", 0.53);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void rawSerialFormCorrectness()
    {
        final IgniteConfiguration conf = createConfiguration(true);
        this.correctnessImpl(conf);
    }

    @Category(ExpensiveTestCategory.class)
    @Test
    public void rawSerialFormEfficiency()
    {
        final IgniteConfiguration referenceConf = createConfiguration(false);
        referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");
        final IgniteConfiguration conf = createConfiguration(true);

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite grid = Ignition.start(conf);

            // saving potential is limited - 15%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", 0.15);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    protected void correctnessImpl(final IgniteConfiguration conf)
    {
        try (Ignite grid = Ignition.start(conf))
        {
            @SuppressWarnings("deprecation")
            final Marshaller marshaller = grid.configuration().getMarshaller();

            QName controlValue;
            QName serialisedValue;

            controlValue = ContentModel.ASPECT_AUDITABLE;
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertNotSame(controlValue.getLocalName(), serialisedValue.getLocalName());
            // namespace should be reused for well-known ones
            Assert.assertSame(controlValue.getNamespaceURI(), serialisedValue.getNamespaceURI());

            // random value, no well known namespace
            controlValue = QName.createQName(UUID.randomUUID().toString(), UUID.randomUUID().toString());
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertNotSame(controlValue.getLocalName(), serialisedValue.getLocalName());
            Assert.assertNotSame(controlValue.getNamespaceURI(), serialisedValue.getNamespaceURI());
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

        final Supplier<QName> comparisonValueSupplier = () -> {
            final QName value = QNAMES[rnJesus.nextInt(QNAMES.length)];
            return value;
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "QName", referenceSerialisationType, serialisationType,
                comparisonValueSupplier, marginFraction);
    }
}
