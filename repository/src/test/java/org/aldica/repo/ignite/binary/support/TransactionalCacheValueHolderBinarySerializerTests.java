/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.support;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.cache.TransactionalCache.ValueHolder;
import org.alfresco.service.cmr.repository.StoreRef;
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
public class TransactionalCacheValueHolderBinarySerializerTests extends GridTestsBase
{

    @SuppressWarnings("rawtypes")
    private static final Constructor<ValueHolder> VALUE_HOLDER_CTOR;

    static
    {
        try
        {
            VALUE_HOLDER_CTOR = ValueHolder.class.getDeclaredConstructor(Object.class);
            VALUE_HOLDER_CTOR.setAccessible(true);
        }
        catch (final NoSuchMethodException e)
        {
            throw new AlfrescoRuntimeException("Failed to lookup constructor");
        }
    }

    protected static IgniteConfiguration createConfiguration(final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForQName = new BinaryTypeConfiguration();
        binaryTypeConfigurationForQName.setTypeName(ValueHolder.class.getName());
        final TransactionalCacheValueHolderBinarySerializer serializer = new TransactionalCacheValueHolderBinarySerializer();
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

            // depending on values, default optimised form ends up slightly more efficient to equal or narrowly less efficient
            // small "added overhead" is the single byte for the type flag, which drives internal optimisations
            // strings / lists / sets will be narrowly less efficient due to the type flag byte
            // sentinel values for "not found" / "null" will be significantly more efficient as value is already encoded in the flag byte
            // dates are slightly more efficient as they are inlined

            // overall (with value distribution in efficiencyImpl) default optimised form is slightly more efficient - ~7%
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", 0.07);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void defaultIgniteClassHandlingCorrectness()
    {
        final IgniteConfiguration defaultConf = createConfiguration(1, false, null);
        defaultConf.setIgniteInstanceName(defaultConf.getIgniteInstanceName() + "-default");

        final Ignite defaultGrid = Ignition.start(defaultConf);
        try
        {
            @SuppressWarnings("deprecation")
            final Marshaller marshaller = defaultGrid.configuration().getMarshaller();

            Class<?> controlValue;
            Class<?> serialisedValue;

            controlValue = HashMap.class;
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);

            controlValue = ValueHolder.class;
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
        }
        catch (final IgniteCheckedException ice)
        {
            throw new IllegalStateException("Serialisation failed in correctness test", ice);
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

            // variable length integers for dates + size/length meta fields saves some memory - 18%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", 0.18);
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

            Object actualValue;
            ValueHolder<?> controlValue;
            ValueHolder<?> serialisedValue;
            Object serialisedActualValue;

            actualValue = TransactionalCacheValueHolderBinarySerializer.ENTITY_LOOKUP_CACHE_NOT_FOUND;
            controlValue = VALUE_HOLDER_CTOR.newInstance(actualValue);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            serialisedActualValue = serialisedValue.getValue();

            Assert.assertEquals(actualValue, serialisedActualValue);
            // in this case, no deep cloning should be taking place
            Assert.assertSame(actualValue, serialisedActualValue);

            actualValue = TransactionalCacheValueHolderBinarySerializer.ENTITY_LOOKUP_CACHE_NULL;
            controlValue = VALUE_HOLDER_CTOR.newInstance(actualValue);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            serialisedActualValue = serialisedValue.getValue();

            Assert.assertEquals(actualValue, serialisedActualValue);
            // in this case, no deep cloning should be taking place
            Assert.assertSame(actualValue, serialisedActualValue);

            actualValue = new Date(AbstractCustomBinarySerializer.LONG_AS_SHORT_SIGNED_POSITIVE_MAX + 100);
            controlValue = VALUE_HOLDER_CTOR.newInstance(actualValue);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            serialisedActualValue = serialisedValue.getValue();

            Assert.assertEquals(actualValue, serialisedActualValue);
            Assert.assertNotSame(actualValue, serialisedActualValue);

            actualValue = new Date(AbstractCustomBinarySerializer.LONG_AS_SHORT_SIGNED_POSITIVE_MAX - 100);
            controlValue = VALUE_HOLDER_CTOR.newInstance(actualValue);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            serialisedActualValue = serialisedValue.getValue();

            Assert.assertEquals(actualValue, serialisedActualValue);
            Assert.assertNotSame(actualValue, serialisedActualValue);

            actualValue = new ArrayList<>(Arrays.asList("Test1", "Test2", "Test3", "Test4"));
            controlValue = VALUE_HOLDER_CTOR.newInstance(actualValue);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            serialisedActualValue = serialisedValue.getValue();

            Assert.assertEquals(actualValue, serialisedActualValue);
            Assert.assertNotSame(actualValue, serialisedActualValue);
            Assert.assertEquals(actualValue.getClass(), serialisedActualValue.getClass());

            actualValue = new LinkedList<>(Arrays.asList("Test1", "Test2", "Test3", "Test4"));
            controlValue = VALUE_HOLDER_CTOR.newInstance(actualValue);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            serialisedActualValue = serialisedValue.getValue();

            Assert.assertEquals(actualValue, serialisedActualValue);
            Assert.assertNotSame(actualValue, serialisedActualValue);
            Assert.assertNotEquals(actualValue.getClass(), serialisedActualValue.getClass());
            Assert.assertEquals(ArrayList.class, serialisedActualValue.getClass());

            actualValue = new ArrayList<>(Arrays.asList(Long.valueOf(1), new Date()));
            controlValue = VALUE_HOLDER_CTOR.newInstance(actualValue);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            serialisedActualValue = serialisedValue.getValue();

            Assert.assertEquals(actualValue, serialisedActualValue);
            Assert.assertNotSame(actualValue, serialisedActualValue);
            Assert.assertEquals(actualValue.getClass(), serialisedActualValue.getClass());

            actualValue = new LinkedList<>(Arrays.asList(Long.valueOf(1), new Date()));
            controlValue = VALUE_HOLDER_CTOR.newInstance(actualValue);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            serialisedActualValue = serialisedValue.getValue();

            Assert.assertEquals(actualValue, serialisedActualValue);
            Assert.assertNotSame(actualValue, serialisedActualValue);
            Assert.assertNotEquals(actualValue.getClass(), serialisedActualValue.getClass());
            Assert.assertEquals(ArrayList.class, serialisedActualValue.getClass());

            actualValue = new HashSet<>(Arrays.asList("Test1", "Test2", "Test3", "Test4"));
            controlValue = VALUE_HOLDER_CTOR.newInstance(actualValue);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            serialisedActualValue = serialisedValue.getValue();

            Assert.assertEquals(actualValue, serialisedActualValue);
            Assert.assertNotSame(actualValue, serialisedActualValue);
            Assert.assertEquals(actualValue.getClass(), serialisedActualValue.getClass());

            actualValue = new LinkedHashSet<>(Arrays.asList("Test1", "Test2", "Test3", "Test4"));
            controlValue = VALUE_HOLDER_CTOR.newInstance(actualValue);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            serialisedActualValue = serialisedValue.getValue();

            Assert.assertEquals(actualValue, serialisedActualValue);
            Assert.assertNotSame(actualValue, serialisedActualValue);
            Assert.assertNotEquals(actualValue.getClass(), serialisedActualValue.getClass());
            Assert.assertEquals(HashSet.class, serialisedActualValue.getClass());

            actualValue = new HashSet<>(Arrays.asList(Long.valueOf(1), new Date()));
            controlValue = VALUE_HOLDER_CTOR.newInstance(actualValue);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            serialisedActualValue = serialisedValue.getValue();

            Assert.assertEquals(actualValue, serialisedActualValue);
            Assert.assertNotSame(actualValue, serialisedActualValue);
            Assert.assertEquals(actualValue.getClass(), serialisedActualValue.getClass());

            actualValue = new LinkedHashSet<>(Arrays.asList(Long.valueOf(1), new Date()));
            controlValue = VALUE_HOLDER_CTOR.newInstance(actualValue);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            serialisedActualValue = serialisedValue.getValue();

            Assert.assertEquals(actualValue, serialisedActualValue);
            Assert.assertNotSame(actualValue, serialisedActualValue);
            Assert.assertNotEquals(actualValue.getClass(), serialisedActualValue.getClass());
            Assert.assertEquals(HashSet.class, serialisedActualValue.getClass());

            actualValue = "Test123456798";
            controlValue = VALUE_HOLDER_CTOR.newInstance(actualValue);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            serialisedActualValue = serialisedValue.getValue();

            Assert.assertEquals(actualValue, serialisedActualValue);
            Assert.assertNotSame(actualValue, serialisedActualValue);

            actualValue = StoreRef.STORE_REF_WORKSPACE_SPACESSTORE;
            controlValue = VALUE_HOLDER_CTOR.newInstance(actualValue);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            serialisedActualValue = serialisedValue.getValue();

            Assert.assertEquals(actualValue, serialisedActualValue);
            Assert.assertNotSame(actualValue, serialisedActualValue);
        }
        catch (final InstantiationException | InvocationTargetException | IllegalAccessException e)
        {
            throw new RuntimeException("Failed to instantiate test value");
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

        // in this test we only vary on value types, but use same values for each type to keep fluctuation of sizes low
        final Supplier<ValueHolder<?>> comparisonValueSupplier = () -> {
            final double chanceSelector = rnJesus.nextDouble();
            final int type;

            // 15% each for sentinel values - these are the most prolific in real life as well
            if (chanceSelector < .15)
            {
                type = 0;
            }
            else if (chanceSelector < .3)
            {
                type = 1;
            }
            else
            {
                // equal chances for rest (we do not include mixed value collections as these are not used in real life uses)
                type = 2 + rnJesus.nextInt(8);
            }

            Serializable specificValue = null;

            switch (type)
            {
                case 0:
                    specificValue = TransactionalCacheValueHolderBinarySerializer.ENTITY_LOOKUP_CACHE_NOT_FOUND;
                    break;
                case 1:
                    specificValue = TransactionalCacheValueHolderBinarySerializer.ENTITY_LOOKUP_CACHE_NULL;
                    break;
                case 2:
                    specificValue = StoreRef.STORE_REF_WORKSPACE_SPACESSTORE;
                    break;
                case 3:
                    specificValue = "test1234";
                    break;
                case 4:
                    specificValue = new Date(AbstractCustomBinarySerializer.LONG_AS_SHORT_SIGNED_POSITIVE_MAX + 100);
                    break;
                case 5:
                    specificValue = new Date(AbstractCustomBinarySerializer.LONG_AS_SHORT_SIGNED_POSITIVE_MAX - 100);
                    break;
                case 6:
                    specificValue = new ArrayList<>(Arrays.asList("test1234", "test5678"));
                    break;
                case 7:
                    specificValue = new LinkedList<>(Arrays.asList("test1234", "test5678"));
                    break;
                case 8:
                    specificValue = new HashSet<>(Arrays.asList("test1234", "test5678"));
                    break;
                case 9:
                    specificValue = new LinkedHashSet<>(Arrays.asList("test1234", "test5678"));
                    break;
            }

            try
            {
                final ValueHolder<?> value = VALUE_HOLDER_CTOR.newInstance(specificValue);
                return value;
            }
            catch (final InstantiationException | InvocationTargetException | IllegalAccessException e)
            {
                throw new RuntimeException("Failed to instantiate benchmark value");
            }
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "TransactionalCache$ValueHolder", referenceSerialisationType,
                serialisationType, comparisonValueSupplier, marginFraction);
    }
}
