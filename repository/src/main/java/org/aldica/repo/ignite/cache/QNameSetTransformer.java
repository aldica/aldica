/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.cache;

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
public class QNameSetTransformer implements CacheValueTransformer<Serializable, Serializable>, InitializingBean
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
    public Serializable transformToExternalValue(final Serializable cacheValue)
    {
        Set<QName> qnames = null;
        if (cacheValue instanceof QNameSetValueHolder)
        {
            qnames = ((QNameSetValueHolder) cacheValue).getQnames();
            if (qnames == null)
            {
                final long[] qnameIds = ((QNameSetValueHolder) cacheValue).getQnameIds();
                qnames = new HashSet<>();
                for (final long qnameId : qnameIds)
                {
                    final Pair<Long, QName> qNamePair = this.qnameDAO.getQName(qnameId);
                    qnames.add(qNamePair.getSecond());
                }

                // store for re-use
                ((QNameSetValueHolder) cacheValue).setQnames(qnames);
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
    public Serializable transformToCacheValue(final Serializable externalValue)
    {
        Serializable cacheValue = externalValue;
        if (externalValue instanceof Set<?>)
        {
            @SuppressWarnings("unchecked")
            final Set<QName> qnamesEx = (Set<QName>) externalValue;
            final long[] qnameIds = new long[qnamesEx.size()];

            int idx = 0;
            boolean allQNamesExistInDatabase = true;

            final List<QName> sorted = new ArrayList<>(qnamesEx);
            Collections.sort(sorted);

            for (final QName qname : sorted)
            {
                final Pair<Long, QName> qNamePair = this.qnameDAO.getQName(qname);

                // technically this should never happen as DbNodeServiceImpl should only put QNames in node.aspectsCache which have already
                // been persisted to the database
                // but tests for a pilot use have shown the potential of this to still occur, so we need to safeguard
                allQNamesExistInDatabase = allQNamesExistInDatabase && qNamePair != null;
                qnameIds[idx++] = qNamePair != null ? qNamePair.getFirst().longValue() : -1;
            }

            if (allQNamesExistInDatabase)
            {
                cacheValue = new QNameSetValueHolder(qnameIds, qnamesEx);
            }
        }
        return cacheValue;
    }

}
