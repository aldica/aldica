/*
 * Copyright 2016 - 2019 Acosix GmbH
 */
package de.acosix.alfresco.ignite.repo.cache;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.alfresco.util.PropertyCheck;
import org.springframework.beans.factory.InitializingBean;

/**
 * Instances of this value transformer handle the conversion between sets of {@link QName} instances and their corresponding IDs.
 *
 * @author Axel Faust
 */
public class QNameSetTransformer implements CacheValueTransformer<Serializable, QNameSetValueHolder>, InitializingBean
{

    protected QNameDAO qnameDAO;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "qnameDAO", this.qnameDAO);
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
     *
     * {@inheritDoc}
     */
    @Override
    public Serializable transformToExternalValue(final QNameSetValueHolder cacheValue)
    {
        Set<QName> qnames = null;
        if (cacheValue != null)
        {
            qnames = cacheValue.getQnames();
            if (qnames == null)
            {
                final long[] qnameIds = cacheValue.getQnameIds();
                qnames = new HashSet<>();
                for (final long qnameId : qnameIds)
                {
                    final Pair<Long, QName> qNamePair = this.qnameDAO.getQName(qnameId);
                    qnames.add(qNamePair.getSecond());
                }

                // store for re-use
                cacheValue.setQnames(qnames);
            }

            // shallow copy - avoid modification
            qnames = new HashSet<>(qnames);
        }

        // we know it is either null or a serializable HashSet by now
        return (Serializable) qnames;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public QNameSetValueHolder transformToCacheValue(final Serializable externalValue)
    {
        QNameSetValueHolder cacheValue = null;
        if (externalValue instanceof Set<?>)
        {
            @SuppressWarnings("unchecked")
            final Set<QName> qnamesEx = (Set<QName>) externalValue;
            final long[] qnameIds = new long[qnamesEx.size()];
            int idx = 0;
            final List<QName> sorted = new ArrayList<>(qnamesEx);
            Collections.sort(sorted);
            for (final QName qname : sorted)
            {
                final Pair<Long, QName> qNamePair = this.qnameDAO.getQName(qname);
                qnameIds[idx++] = qNamePair.getFirst().longValue();
            }

            cacheValue = new QNameSetValueHolder(qnameIds, qnamesEx);
        }
        return cacheValue;
    }

}
