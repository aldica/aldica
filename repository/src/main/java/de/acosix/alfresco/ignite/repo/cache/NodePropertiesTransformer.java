/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package de.acosix.alfresco.ignite.repo.cache;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.alfresco.repo.dictionary.constraint.ListOfValuesConstraint;
import org.alfresco.repo.dictionary.constraint.RegisteredConstraint;
import org.alfresco.repo.domain.contentdata.ContentDataDAO;
import org.alfresco.repo.domain.node.ContentDataWithId;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.service.cmr.dictionary.Constraint;
import org.alfresco.service.cmr.dictionary.ConstraintDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.alfresco.util.PropertyCheck;
import org.springframework.beans.factory.InitializingBean;

/**
 * Instances of this value transformer handle the conversion between maps of node properties and a memory-optimised value holder type for
 * caches. This will handle {@link QName} keys and {@link ContentData} values via their unique ID and apply {@link String#intern()
 * interning} on textual values from defined "list of values" constraints.
 *
 * @author Axel Faust
 */
public class NodePropertiesTransformer implements CacheValueTransformer<Serializable, NodePropertiesValueHolder>, InitializingBean
{

    protected QNameDAO qnameDAO;

    protected ContentDataDAO contentDataDAO;

    protected DictionaryService dictionaryService;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "qnameDAO", this.qnameDAO);
        PropertyCheck.mandatory(this, "contentDataDAO", this.contentDataDAO);
        PropertyCheck.mandatory(this, "dictionaryService", this.dictionaryService);
    }

    /**
     * @param qnameDAO
     *            the qnameDAO to set
     */
    public void setQnameDAO(final QNameDAO qnameDAO)
    {
        this.qnameDAO = qnameDAO;
    }

    /**
     * @param contentDataDAO
     *            the contentDataDAO to set
     */
    public void setContentDataDAO(final ContentDataDAO contentDataDAO)
    {
        this.contentDataDAO = contentDataDAO;
    }

    /**
     * @param dictionaryService
     *            the dictionaryService to set
     */
    public void setDictionaryService(final DictionaryService dictionaryService)
    {
        this.dictionaryService = dictionaryService;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Serializable transformToExternalValue(final NodePropertiesValueHolder cacheValue)
    {
        Map<QName, Serializable> properties = null;

        if (cacheValue != null)
        {
            properties = cacheValue.getProperties();
            if (properties == null)
            {
                properties = new HashMap<>();
                final long[] qnameIds = cacheValue.getQnameIds();
                final Serializable[] values = cacheValue.getValues();

                for (int idx = 0; idx < values.length && idx < qnameIds.length; idx++)
                {
                    final Pair<Long, QName> qnamePair = this.qnameDAO.getQName(Long.valueOf(qnameIds[idx]));
                    final QName qname = qnamePair.getSecond();

                    Serializable value = values[idx];
                    if (value != null)
                    {
                        if (value.getClass().isArray())
                        {
                            final List<Object> valueList = new ArrayList<>(Arrays.asList((Object[]) value));
                            value = (Serializable) valueList;
                            for (int lIdx = 0, max = valueList.size(); lIdx < max; lIdx++)
                            {
                                final Object valueEl = valueList.get(lIdx);
                                if (cacheValue.isContentDataQNameId(idx) && valueEl instanceof Long)
                                {
                                    final Pair<Long, ContentData> contentDataPair = this.contentDataDAO.getContentData((Long) value);
                                    valueList.set(lIdx, contentDataPair.getSecond());
                                }
                                else if (cacheValue.isLovQNameId(idx) && valueEl instanceof String)
                                {
                                    valueList.set(lIdx, ((String) valueEl).intern());
                                }
                            }
                        }
                        else if (cacheValue.isContentDataQNameId(idx) && value instanceof Long)
                        {
                            final Pair<Long, ContentData> contentDataPair = this.contentDataDAO.getContentData((Long) value);
                            value = contentDataPair.getSecond();
                        }
                        else if (cacheValue.isLovQNameId(idx) && value instanceof String)
                        {
                            value = ((String) value).intern();
                        }
                    }

                    properties.put(qname, value);
                }

                cacheValue.setProperties(properties);
            }
            properties = new HashMap<>(properties);
        }

        // we know it is either null or a serializable HashMap by now
        return (Serializable) properties;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public NodePropertiesValueHolder transformToCacheValue(final Serializable externalValue)
    {
        NodePropertiesValueHolder cacheValue = null;

        if (externalValue instanceof Map<?, ?>)
        {
            final List<Long> qnameIds = new ArrayList<>();
            final List<Integer> lovQNameIdxs = new ArrayList<>();
            final List<Integer> contentDataQNameIdxs = new ArrayList<>();
            final List<Serializable> values = new ArrayList<>();

            this.processInboundPropertyMap(externalValue, qnameIds, lovQNameIdxs, contentDataQNameIdxs, values);

            final long[] qnameIdsArr = new long[qnameIds.size()];
            final int[] contentDataQNameIdsArr = new int[contentDataQNameIdxs.size()];
            final int[] lovQNameIdsArr = new int[lovQNameIdxs.size()];
            for (int idx = 0; idx < qnameIdsArr.length; idx++)
            {
                qnameIdsArr[idx] = qnameIds.get(idx).longValue();
                if (idx < contentDataQNameIdsArr.length)
                {
                    contentDataQNameIdsArr[idx] = contentDataQNameIdxs.get(idx).intValue();
                }

                if (idx < lovQNameIdsArr.length)
                {
                    lovQNameIdsArr[idx] = lovQNameIdxs.get(idx).intValue();
                }
            }

            cacheValue = new NodePropertiesValueHolder(qnameIdsArr, values.toArray(new Serializable[0]), contentDataQNameIdsArr,
                    lovQNameIdsArr);

            @SuppressWarnings("unchecked")
            final Map<QName, Serializable> properties = (Map<QName, Serializable>) externalValue;
            cacheValue.setProperties(properties);
        }

        return cacheValue;
    }

    protected void processInboundPropertyMap(final Serializable externalValue, final List<Long> qnameIds, final List<Integer> lovQNameIdxs,
            final List<Integer> contentDataQNameIdxs, final List<Serializable> values)
    {
        @SuppressWarnings("unchecked")
        final Map<Object, Object> map = (Map<Object, Object>) externalValue;
        for (final Entry<Object, Object> entry : map.entrySet())
        {
            final Object key = entry.getKey();
            Serializable value = (Serializable) entry.getValue();

            if (key instanceof QName)
            {
                final Pair<Long, QName> qnamePair = this.qnameDAO.getQName((QName) key);
                qnameIds.add(qnamePair.getFirst());

                if (value instanceof List<?>)
                {
                    value = ((List<?>) value).toArray(new Serializable[0]);
                }

                final PropertyDefinition property = this.dictionaryService.getProperty((QName) key);
                if (property != null)
                {
                    final Integer qnameIdx = Integer.valueOf(qnameIds.size() - 1);
                    if (DataTypeDefinition.CONTENT.equals(property.getDataType().getName()))
                    {
                        contentDataQNameIdxs.add(qnameIdx);

                        if (value instanceof Serializable[])
                        {
                            final Serializable[] valueArr = (Serializable[]) value;
                            for (int valIdx = 0; valIdx < valueArr.length; valIdx++)
                            {
                                if (valueArr[valIdx] instanceof ContentDataWithId)
                                {
                                    valueArr[valIdx] = ((ContentDataWithId) valueArr[valIdx]).getId();
                                }
                            }
                        }
                        else if (value instanceof ContentDataWithId)
                        {
                            value = ((ContentDataWithId) value).getId();
                        }
                    }
                    else
                    {
                        boolean hasLovConstraint = false;
                        final List<ConstraintDefinition> constraints = property.getConstraints();
                        if (constraints != null)
                        {
                            for (final ConstraintDefinition constraint : constraints)
                            {
                                Constraint constraintImpl = constraint.getConstraint();
                                if (constraintImpl instanceof RegisteredConstraint)
                                {
                                    constraintImpl = ((RegisteredConstraint) constraintImpl).getRegisteredConstraint();
                                }

                                if (ListOfValuesConstraint.CONSTRAINT_TYPE.equals(constraintImpl.getType()))
                                {
                                    hasLovConstraint = true;
                                }
                            }
                        }

                        if (hasLovConstraint)
                        {
                            lovQNameIdxs.add(qnameIdx);
                        }
                    }
                }

                values.add(value);
            }
        }
    }
}
