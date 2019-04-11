/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package de.acosix.alfresco.ignite.repo.cache;

import java.io.IOException;
import java.io.ObjectInputStream;
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
            // QName has no readResolve for efficient deserialisation
            String namespace = ((QNameValueHolder) cacheValue).getNamespace();
            if (namespace == null)
            {
                if (this.qNameDAO == null)
                {
                    this.qNameDAO = this.applicationContext.getBean("qnameDAOImpl", QNameDAO.class);
                }
                final Pair<Long, String> namespacePair = this.qNameDAO.getNamespace(((QNameValueHolder) cacheValue).getNamespaceId());
                namespace = namespacePair.getSecond();
            }
            externalValue = QName.createQName(namespace, ((QNameValueHolder) cacheValue).getLocalName());
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
            cacheValue = new QNameValueHolder(namespacePair.getFirst().longValue(), ((QName) externalValue).getLocalName());
        }
        return cacheValue;
    }

    protected static class QNameValueHolder implements Serializable
    {

        private static final long serialVersionUID = 1L;

        protected final long namespaceId;

        protected transient String namespace;

        // technically final but due to readObject + intern() requirement we keep it without
        protected String localName;

        protected QNameValueHolder(final long namespaceId, final String localName)
        {
            this.namespaceId = namespaceId;
            this.localName = localName;
        }

        /**
         * @return the namespaceId
         */
        public long getNamespaceId()
        {
            return this.namespaceId;
        }

        /**
         * @return the localName
         */
        public String getLocalName()
        {
            return this.localName;
        }

        /**
         * @return the namespace
         */
        public String getNamespace()
        {
            return this.namespace;
        }

        /**
         * @param namespace
         *            the namespace to set
         */
        public void setNamespace(final String namespace)
        {
            this.namespace = namespace;
        }

        private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException
        {
            in.defaultReadObject();

            this.localName = this.localName.intern();
        }
    }
}
