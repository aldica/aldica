/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package de.acosix.alfresco.ignite.repo.cache;

import java.io.Serializable;

import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Instances of this value transformer handle the conversion of immutable entity values (like {@link String} and {@link QName}) into
 * memory-optimised variants both at cache- and runtime. This includes String-{@link String#intern() interning}.
 *
 * @author Axel Faust
 */
public class ImmutableEntityTransformer implements CacheValueTransformer<Serializable, Serializable>, ApplicationContextAware
{

    protected ApplicationContext applicationContext;

    protected QNameDAO qNameDAO;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext)
    {
        this.applicationContext = applicationContext;
    }

    /**
     * @param qNameDAO
     *            the qNameDAO to set
     */
    public void setQNameDAO(final QNameDAO qNameDAO)
    {
        this.qNameDAO = qNameDAO;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Serializable transformToExternalValue(final Serializable cacheValue)
    {
        Serializable externalValue = cacheValue;
        if (cacheValue instanceof String)
        {
            externalValue = ((String) cacheValue).intern();
        }
        else if (cacheValue instanceof QNameValueHolder)
        {
            externalValue = ((QNameValueHolder) cacheValue).getActualValue();
            if (externalValue == null)
            {
                // QName has no readResolve for efficient deserialisation
                if (this.qNameDAO == null)
                {
                    this.qNameDAO = this.applicationContext.getBean("qnameDAOImpl", QNameDAO.class);
                }
                final long namespaceId = ((QNameValueHolder) cacheValue).getNamespaceId();
                final Pair<Long, String> namespacePair = this.qNameDAO.getNamespace(namespaceId);
                if (namespacePair == null)
                {
                    throw new IllegalStateException("Namespace ID " + namespaceId + " is not known to the QName DAO");
                }
                final String namespace = namespacePair.getSecond();
                externalValue = QName.createQName(namespace, ((QNameValueHolder) cacheValue).getLocalName());
                ((QNameValueHolder) cacheValue).setActualValue((QName) externalValue);
            }
        }
        // Locale.readResolve() already handles cache instances

        return externalValue;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Serializable transformToCacheValue(final Serializable externalValue)
    {
        // String/Locale do not need special handling - only QName does
        Serializable cacheValue = externalValue;
        if (externalValue instanceof QName)
        {
            if (this.qNameDAO == null)
            {
                this.qNameDAO = this.applicationContext.getBean("qnameDAOImpl", QNameDAO.class);
            }
            final Pair<Long, String> namespacePair = this.qNameDAO.getNamespace(((QName) externalValue).getNamespaceURI());
            if (namespacePair == null)
            {
                throw new IllegalStateException("Namespace URI of QName " + externalValue + " is not known to the QName DAO");
            }
            cacheValue = new QNameValueHolder(namespacePair.getFirst().longValue(), (QName) externalValue);
        }
        return cacheValue;
    }
}
