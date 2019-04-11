/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package de.acosix.alfresco.ignite.share.surf;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.alfresco.util.EqualsHelper;
import org.alfresco.util.Pair;
import org.alfresco.util.PropertyCheck;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.extensions.surf.ClusterMessageAware;
import org.springframework.extensions.surf.ClusterService;

import de.acosix.alfresco.ignite.common.lifecycle.IgniteInstanceLifecycleAware;

/**
 * @author Axel Faust
 */
public class ClusterServiceImpl implements ClusterService, InitializingBean, IgniteInstanceLifecycleAware, ApplicationContextAware
{

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterServiceImpl.class);

    protected ApplicationContext applicationContext;

    protected boolean enabled;

    protected String gridName;

    protected String topicName;

    protected boolean gridStarted = false;

    protected final Map<String, Pair<String, ClusterMessageAware>> messageTypeBeans = new HashMap<>();

    protected final List<Pair<String, ClusterMessageAware>> clusterBeans = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet() throws Exception
    {
        PropertyCheck.mandatory(this, "gridName", this.gridName);
        PropertyCheck.mandatory(this, "topicName", this.topicName);

        if (this.enabled)
        {
            // find the beans that are interested in cluster messages and register them with the service
            final Map<String, ClusterMessageAware> beans = this.applicationContext.getBeansOfType(ClusterMessageAware.class);
            beans.forEach((id, bean) -> {
                final String messageType = bean.getClusterMessageType();

                // beans that do not specify a message type can still post messages via the service or just keep a reference
                // but they are not registered in the list of handler beans that can accept cluster messages
                if (messageType != null)
                {
                    if (this.messageTypeBeans.containsKey(messageType))
                    {
                        throw new IllegalStateException("ClusterMessageAware bean with id '" + id
                                + "' attempted to register with existing message type: " + messageType);
                    }
                    this.messageTypeBeans.put(messageType, new Pair<>(id, bean));
                }

                this.clusterBeans.add(new Pair<>(id, bean));
                bean.setClusterService(this);
            });

            if (LOGGER.isDebugEnabled())
            {
                final StringBuilder sb = new StringBuilder(1024);
                this.clusterBeans.forEach((idAndBean) -> {
                    if (sb.length() > 0)
                    {
                        sb.append(", ");
                    }
                    sb.append(idAndBean.getFirst()).append(" [").append(idAndBean.getSecond().getClusterMessageType()).append(']');
                });
                LOGGER.debug("Registered beans for cluster messages from {}", sb);
            }

            LOGGER.info("Ignite-backed cluster service registered with all ClusterMessageAware beans in context");
        }
        else
        {
            LOGGER.info("Ignite-backed cluster service is disabled");
        }
    }

    /**
     * @param enabled
     *            the enabled to set
     */
    public void setEnabled(final boolean enabled)
    {
        this.enabled = enabled;
    }

    /**
     * @param gridName
     *            the gridName to set
     */
    public void setGridName(final String gridName)
    {
        this.gridName = gridName;
    }

    /**
     * @param topicName
     *            the topicName to set
     */
    public void setTopicName(final String topicName)
    {
        this.topicName = topicName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
    {
        this.applicationContext = applicationContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeInstanceStartup(final String gridName)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterInstanceStartup(final String gridName)
    {
        if (this.enabled && EqualsHelper.nullSafeEquals(this.gridName, gridName))
        {
            this.gridStarted = true;

            Ignition.ignite(this.gridName).message().localListen(this.topicName, this::onMessage);

            LOGGER.info("Ignite-backed cluster service registered grid listener on topic {}", this.topicName);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeInstanceShutdown(final String gridName)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterInstanceShutdown(final String gridName)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void publishClusterMessage(final String messageType, final Map<String, Serializable> payload)
    {
        if (this.gridStarted)
        {
            final Ignite ignite = Ignition.ignite(this.gridName);

            final ClusterMessage message = new ClusterMessage(messageType, payload);
            final ClusterGroup remotes = ignite.cluster().forRemotes();
            if (!remotes.nodes().isEmpty())
            {
                LOGGER.debug("Pushing message: {}", message);
                ignite.message(remotes).send(this.topicName, message);
            }
            else
            {
                LOGGER.debug("Not sending message as there are no remote nodes: {}", this.topicName, message);
            }
        }
    }

    protected boolean onMessage(final UUID uuid, final Object message)
    {
        if (message instanceof ClusterMessage)
        {
            final ClusterMessage clusterMessage = (ClusterMessage) message;
            final String messageType = clusterMessage.getMessageType();

            final Map<String, Serializable> payload = clusterMessage.getPayload();
            LOGGER.debug("Received message of type {}: {}", messageType, payload);

            final Pair<String, ClusterMessageAware> idAndBean = this.messageTypeBeans.get(messageType);
            if (idAndBean != null)
            {
                idAndBean.getSecond().onClusterMessage(clusterMessage.payload);
            }
            else
            {
                LOGGER.warn("Received message of unknown type - no handler bean found for {}", messageType);
            }
        }
        // keep listening
        return true;
    }

    protected static class ClusterMessage implements Serializable
    {

        private static final long serialVersionUID = -3709188033019405549L;

        private final String messageType;

        private final Map<String, Serializable> payload;

        protected ClusterMessage(final String messageType, final Map<String, Serializable> payload)
        {
            this.messageType = messageType;
            this.payload = payload;
        }

        /**
         * @return the messageType
         */
        public String getMessageType()
        {
            return this.messageType;
        }

        /**
         * @return the payload
         */
        public Map<String, Serializable> getPayload()
        {
            return this.payload;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString()
        {
            return "ClusterMessage [messageType=" + this.messageType + ", payload=" + this.payload + "]";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((this.messageType == null) ? 0 : this.messageType.hashCode());
            result = prime * result + ((this.payload == null) ? 0 : this.payload.hashCode());
            return result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(final Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (this.getClass() != obj.getClass())
            {
                return false;
            }
            final ClusterMessage other = (ClusterMessage) obj;
            if (this.messageType == null)
            {
                if (other.messageType != null)
                {
                    return false;
                }
            }
            else if (!this.messageType.equals(other.messageType))
            {
                return false;
            }
            if (this.payload == null)
            {
                if (other.payload != null)
                {
                    return false;
                }
            }
            else if (!this.payload.equals(other.payload))
            {
                return false;
            }
            return true;
        }

    }
}