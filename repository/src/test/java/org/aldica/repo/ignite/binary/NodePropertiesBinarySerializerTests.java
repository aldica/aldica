/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.aldica.repo.ignite.cache.NodePropertiesCacheMap;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.content.filestore.FileContentStore;
import org.alfresco.repo.content.filestore.FileContentUrlProvider;
import org.alfresco.repo.domain.contentdata.ContentDataDAO;
import org.alfresco.repo.domain.contentdata.ibatis.ContentDataDAOImpl;
import org.alfresco.repo.domain.node.ContentDataWithId;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.repo.domain.qname.ibatis.QNameDAOImpl;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
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
public class NodePropertiesBinarySerializerTests extends GridTestsBase
{

    private static final QName[] PROP_QNAMES = { ContentModel.PROP_NAME, ContentModel.PROP_MODIFIED, ContentModel.PROP_CREATED,
            ContentModel.PROP_CREATOR, ContentModel.PROP_MODIFIER, ContentModel.PROP_CONTENT, ContentModel.PROP_CATEGORIES };

    private static final String[] MIMETYPES = { MimetypeMap.MIMETYPE_PDF, MimetypeMap.MIMETYPE_JSON, MimetypeMap.MIMETYPE_TEXT_PLAIN,
            MimetypeMap.MIMETYPE_OPENDOCUMENT_TEXT, MimetypeMap.MIMETYPE_OPENDOCUMENT_SPREADSHEET,
            MimetypeMap.MIMETYPE_OPENDOCUMENT_PRESENTATION };

    private static final String[] ENCODINGS = { StandardCharsets.UTF_8.name(), StandardCharsets.UTF_16.name(),
            StandardCharsets.ISO_8859_1.name() };

    private static final Locale[] LOCALES = { Locale.ENGLISH, Locale.GERMAN, Locale.FRENCH, Locale.CHINESE, Locale.JAPANESE, Locale.ITALIAN,
            Locale.SIMPLIFIED_CHINESE };

    private static final int UNIQUE_CONTENT_DATA_COUNT = 100;

    protected static GenericApplicationContext createApplicationContext()
    {
        final GenericApplicationContext appContext = new GenericApplicationContext();

        final QNameDAO qnameDAO = EasyMock.partialMockBuilder(QNameDAOImpl.class).addMockedMethod("getQName", Long.class)
                .addMockedMethod("getQName", QName.class).createMock();
        final ContentDataDAO contentDataDAO = EasyMock.partialMockBuilder(ContentDataDAOImpl.class)
                .addMockedMethod("getContentData", Long.class).createMock();
        appContext.getBeanFactory().registerSingleton("qnameDAO", qnameDAO);
        appContext.getBeanFactory().registerSingleton("contentDataDAO", contentDataDAO);
        appContext.refresh();

        for (int idx = 0; idx < PROP_QNAMES.length; idx++)
        {
            EasyMock.expect(qnameDAO.getQName(Long.valueOf(idx))).andStubReturn(new Pair<>(Long.valueOf(idx), PROP_QNAMES[idx]));
            EasyMock.expect(qnameDAO.getQName(PROP_QNAMES[idx])).andStubReturn(new Pair<>(Long.valueOf(idx), PROP_QNAMES[idx]));
        }
        EasyMock.expect(qnameDAO.getQName(EasyMock.anyObject(QName.class))).andStubReturn(null);

        // default Alfresco classes are inaccessible (package-protected visibility)
        final FileContentUrlProvider urlProvider = () -> FileContentStore.STORE_PROTOCOL + "://" + UUID.randomUUID().toString();
        final SecureRandom rnJesus = new SecureRandom();
        for (int idx = 0; idx < UNIQUE_CONTENT_DATA_COUNT; idx++)
        {
            final ContentDataWithId value = new ContentDataWithId(new ContentData(urlProvider.createNewFileStoreUrl(),
                    MIMETYPES[rnJesus.nextInt(MIMETYPES.length)], rnJesus.nextInt(Integer.MAX_VALUE),
                    ENCODINGS[rnJesus.nextInt(ENCODINGS.length)], LOCALES[rnJesus.nextInt(LOCALES.length)]), Long.valueOf(idx));

            EasyMock.expect(contentDataDAO.getContentData(Long.valueOf(idx))).andStubReturn(new Pair<>(Long.valueOf(idx), value));
        }

        EasyMock.replay(qnameDAO);
        EasyMock.replay(contentDataDAO);

        return appContext;
    }

    protected static IgniteConfiguration createConfiguration(final ApplicationContext applicationContext, final boolean idsWhenReasonable,
            final boolean idsWhenPossible, final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final NodePropertiesBinarySerializer serializer = new NodePropertiesBinarySerializer();
        serializer.setApplicationContext(applicationContext);
        serializer.setUseIdsWhenReasonable(idsWhenReasonable);
        serializer.setUseIdsWhenPossible(idsWhenPossible);
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);

        final BinaryTypeConfiguration binaryTypeConfigurationForNodePropertiesCacheMap = new BinaryTypeConfiguration();
        binaryTypeConfigurationForNodePropertiesCacheMap.setTypeName(NodePropertiesCacheMap.class.getName());
        binaryTypeConfigurationForNodePropertiesCacheMap.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForNodePropertiesCacheMap));
        conf.setBinaryConfiguration(binaryConfiguration);

        return conf;
    }

    @Test
    public void defaultFormCorrectness()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(null, false, false, false);
            final ContentDataDAO contentDataDAO = appContext.getBean("contentDataDAO", ContentDataDAO.class);
            this.correctnessImpl(conf, contentDataDAO);
        }
    }

    @Test
    public void defaultFormQNameIdSubstitutionCorrectness()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(appContext, true, false, false);
            final ContentDataDAO contentDataDAO = appContext.getBean("contentDataDAO", ContentDataDAO.class);
            this.correctnessImpl(conf, contentDataDAO);
        }
    }

    @Test
    public void defaultFormQNameAndContentDataIdSubstitutionCorrectness()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(appContext, true, true, false);
            final ContentDataDAO contentDataDAO = appContext.getBean("contentDataDAO", ContentDataDAO.class);
            this.correctnessImpl(conf, contentDataDAO);
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

            final IgniteConfiguration defaultConf = createConfiguration(null, false, false, false);
            final IgniteConfiguration useQNameIdConf = createConfiguration(appContext, true, false, false);
            final IgniteConfiguration useAllIdConf = createConfiguration(appContext, true, true, false);

            useQNameIdConf.setIgniteInstanceName(useQNameIdConf.getIgniteInstanceName() + "-qnameIdSubstitution");
            useAllIdConf.setIgniteInstanceName(useQNameIdConf.getIgniteInstanceName() + "-allIdSubstitution");

            final ContentDataDAO contentDataDAO = appContext.getBean("contentDataDAO", ContentDataDAO.class);

            try
            {
                final Ignite referenceGrid = Ignition.start(referenceConf);
                final Ignite defaultGrid = Ignition.start(defaultConf);
                final Ignite useQNameIdGrid = Ignition.start(useQNameIdConf);
                final Ignite useAllIdGrid = Ignition.start(useAllIdConf);

                // default uses HashMap.writeObject and Serializable all the way through, which is already very efficient
                // this actually intrinsically deduplicates common objects / values (e.g. namespace URIs)
                // without ID substitution (or our custom QNameBinarySerializer), our serialisation cannot come close - -77%
                this.efficiencyImpl(referenceGrid, defaultGrid, contentDataDAO, "aldica optimised", "Ignite default", -0.77);

                // replacing full QName with ID saves a lot and overcomes base disadvantage
                // 26%
                this.efficiencyImpl(referenceGrid, useQNameIdGrid, contentDataDAO, "aldica optimised (QName ID substitution)",
                        "Ignite default", 0.26);

                // savings are more pronounced compared to our own base
                // 58%
                this.efficiencyImpl(defaultGrid, useQNameIdGrid, contentDataDAO, "aldica optimised (QName ID substitution)",
                        "aldica optimised", 0.58);

                // savings should be more pronounced with both QName and ContentDataWithId replaced
                // 61%
                this.efficiencyImpl(referenceGrid, useAllIdGrid, contentDataDAO, "aldica optimised (QName + ContentData ID substitution)",
                        "Ignite default", 0.61);

                // savings are extremely more pronounced compared to our own base
                // 78%
                this.efficiencyImpl(defaultGrid, useAllIdGrid, contentDataDAO, "aldica optimised (QName + ContentData ID substitution)",
                        "aldica optimised", 0.78);

                // ContentDataWithId is quite complex, so significant savings in addition to QName ID substitution if sparse metadata
                // 47%
                this.efficiencyImpl(useQNameIdGrid, useAllIdGrid, contentDataDAO, "aldica optimised (QName + ContentData ID substitution)",
                        "aldica optimised (QName ID substitution)", 0.47);
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
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(null, false, false, true);
            final ContentDataDAO contentDataDAO = appContext.getBean("contentDataDAO", ContentDataDAO.class);
            this.correctnessImpl(conf, contentDataDAO);
        }
    }

    @Test
    public void rawSerialFormQNameIdSubstitutionCorrectness()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(appContext, true, false, true);
            final ContentDataDAO contentDataDAO = appContext.getBean("contentDataDAO", ContentDataDAO.class);
            this.correctnessImpl(conf, contentDataDAO);
        }
    }

    @Test
    public void rawSerialFormQNameAndContentDataIdSubstitutionCorrectness()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(appContext, true, true, true);
            final ContentDataDAO contentDataDAO = appContext.getBean("contentDataDAO", ContentDataDAO.class);
            this.correctnessImpl(conf, contentDataDAO);
        }
    }

    @Category(ExpensiveTestCategory.class)
    @Test
    public void rawSerialFormEfficiency()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration referenceConf = createConfiguration(null, false, false, false);
            referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");

            final IgniteConfiguration defaultConf = createConfiguration(null, false, false, true);
            final IgniteConfiguration useQNameIdConf = createConfiguration(appContext, true, false, true);
            final IgniteConfiguration useAllIdConf = createConfiguration(appContext, true, true, true);

            useQNameIdConf.setIgniteInstanceName(useQNameIdConf.getIgniteInstanceName() + "-qnameIdSubstitution");
            useAllIdConf.setIgniteInstanceName(useAllIdConf.getIgniteInstanceName() + "-allIdSubstitution");

            final ContentDataDAO contentDataDAO = appContext.getBean("contentDataDAO", ContentDataDAO.class);

            try
            {
                final Ignite referenceGrid = Ignition.start(referenceConf);
                final Ignite defaultGrid = Ignition.start(defaultConf);
                final Ignite useQNameIdGrid = Ignition.start(useQNameIdConf);
                final Ignite useAllIdGrid = Ignition.start(useAllIdConf);

                // virtually no difference as map handling has almost no overhead to reduce
                this.efficiencyImpl(referenceGrid, defaultGrid, contentDataDAO, "aldica raw serial", "aldica optimised", -0.01);

                // QNames are expensive due to namespace + local name
                // 64%
                this.efficiencyImpl(referenceGrid, useQNameIdGrid, contentDataDAO, "aldica raw serial (ID substitution)",
                        "aldica optimised", 0.64);

                // 64%
                this.efficiencyImpl(defaultGrid, useQNameIdGrid, contentDataDAO, "aldica raw serial (ID substitution)", "aldica raw serial",
                        0.64);

                // savings should be more pronounced with both QName and ContentDataWithId replaced
                // 86%
                this.efficiencyImpl(referenceGrid, useAllIdGrid, contentDataDAO, "aldica raw serial (QName + ContentData ID substitution)",
                        "aldica optimised", 0.86);

                // 86%
                this.efficiencyImpl(defaultGrid, useAllIdGrid, contentDataDAO, "aldica raw serial (QName + ContentData ID substitution)",
                        "aldica raw serial", 0.86);

                // ContentDataWithId is quite complex, so significant savings in addition to QName ID substitution if sparse metadata
                // 61%
                this.efficiencyImpl(useQNameIdGrid, useAllIdGrid, contentDataDAO, "aldica raw serial (QName + ContentData ID substitution)",
                        "aldica raw serial (QName ID substitution)", 0.61);
            }
            finally
            {
                Ignition.stopAll(true);
            }
        }
    }

    protected void correctnessImpl(final IgniteConfiguration conf, final ContentDataDAO contentDataDAO)
    {
        try (Ignite grid = Ignition.start(conf))
        {
            @SuppressWarnings("deprecation")
            final Marshaller marshaller = grid.configuration().getMarshaller();

            NodePropertiesCacheMap controlValue;
            NodePropertiesCacheMap serialisedValue;
            ArrayList<NodeRef> categories;

            controlValue = new NodePropertiesCacheMap();
            controlValue.put(ContentModel.PROP_CREATOR, "admin");
            controlValue.put(ContentModel.PROP_CREATED,
                    Date.from(LocalDateTime.of(2020, Month.JANUARY, 1, 6, 0, 0).toInstant(ZoneOffset.UTC)));
            controlValue.put(ContentModel.PROP_MODIFIER, "editor");
            controlValue.put(ContentModel.PROP_MODIFIED,
                    Date.from(LocalDateTime.of(2020, Month.JULY, 1, 23, 12, 45).toInstant(ZoneOffset.UTC)));
            controlValue.put(ContentModel.PROP_NAME, UUID.randomUUID().toString());
            controlValue.put(ContentModel.PROP_CONTENT, contentDataDAO.getContentData(1l));

            categories = new ArrayList<>();
            categories.add(new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, UUID.randomUUID().toString()));
            categories.add(new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, UUID.randomUUID().toString()));
            controlValue.put(ContentModel.PROP_CATEGORIES, categories);

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

    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid, final ContentDataDAO contentDataDAO,
            final String serialisationType, final String referenceSerialisationType, final double marginFraction)
    {
        final int msPerYear = 365 * 24 * 60 * 60 * 1000;
        final long msOffset = LocalDateTime.of(2020, Month.JANUARY, 1, 0, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli();

        final SecureRandom rnJesus = new SecureRandom();

        final Supplier<NodePropertiesCacheMap> comparisonValueSupplier = () -> {
            final int idx = 1000 + rnJesus.nextInt(100000000);
            final NodePropertiesCacheMap value = new NodePropertiesCacheMap();

            final int msOffsetCreated = rnJesus.nextInt(msPerYear / 2);
            final int msOffsetModified = msOffsetCreated + rnJesus.nextInt(msPerYear - msOffsetCreated);

            value.put(ContentModel.PROP_CREATOR, "admin" + (idx % 100));
            value.put(ContentModel.PROP_CREATED, new Date(msOffset + msOffsetCreated));
            value.put(ContentModel.PROP_MODIFIER, "editor" + (idx % 100));
            value.put(ContentModel.PROP_MODIFIED, new Date(msOffset + msOffsetModified));
            value.put(ContentModel.PROP_NAME, UUID.randomUUID().toString());
            value.put(ContentModel.PROP_CONTENT,
                    contentDataDAO.getContentData(Long.valueOf(rnJesus.nextInt(UNIQUE_CONTENT_DATA_COUNT))).getSecond());

            return value;
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "NodePropertiesCacheMap", referenceSerialisationType,
                serialisationType, comparisonValueSupplier, marginFraction);
    }
}
