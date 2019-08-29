/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.integration;

import java.util.UUID;

import javax.ws.rs.core.UriBuilder;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.LocalResteasyProviderFactory;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import de.acosix.alfresco.rest.client.api.NodesV1;
import de.acosix.alfresco.rest.client.api.NodesV1.IncludeOption;
import de.acosix.alfresco.rest.client.jackson.RestAPIBeanDeserializerModifier;
import de.acosix.alfresco.rest.client.jaxrs.BasicAuthenticationClientRequestFilter;
import de.acosix.alfresco.rest.client.model.common.MultiValuedParam;
import de.acosix.alfresco.rest.client.model.nodes.NodeCreationRequestEntity;
import de.acosix.alfresco.rest.client.model.nodes.NodeLockRequestEntity;
import de.acosix.alfresco.rest.client.model.nodes.NodeLockRequestEntity.LockLifetime;
import de.acosix.alfresco.rest.client.model.nodes.NodeResponseEntity;
import de.acosix.alfresco.rest.client.resteasy.MultiValuedParamConverterProvider;

/**
 * Tests in this class check and verify that any locks set on nodes result in a consistent state across all members of the grid.
 *
 * @author Axel Faust
 */
public class LockConsistency
{

    private static final String baseUrlServer1 = "http://localhost:8180/alfresco";

    private static final String baseUrlServer2 = "http://localhost:8280/alfresco";

    private static ResteasyClient client;

    private final BasicAuthenticationClientRequestFilter server1AuthFilter = new BasicAuthenticationClientRequestFilter();

    private final BasicAuthenticationClientRequestFilter server2AuthFilter = new BasicAuthenticationClientRequestFilter();

    private NodesV1 server1NodesAPI;

    private NodesV1 server2NodesAPI;

    @BeforeClass
    public static void setup()
    {
        final SimpleModule module = new SimpleModule();
        module.setDeserializerModifier(new RestAPIBeanDeserializerModifier());

        final ResteasyJackson2Provider resteasyJacksonProvider = new ResteasyJackson2Provider();
        final ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_EMPTY);
        mapper.registerModule(module);
        resteasyJacksonProvider.setMapper(mapper);

        final LocalResteasyProviderFactory resteasyProviderFactory = new LocalResteasyProviderFactory(new ResteasyProviderFactoryImpl());
        resteasyProviderFactory.register(resteasyJacksonProvider);
        // will cause a warning regarding Jackson provider which is already registered
        RegisterBuiltin.register(resteasyProviderFactory);
        resteasyProviderFactory.register(new MultiValuedParamConverterProvider());

        client = new ResteasyClientBuilderImpl().providerFactory(resteasyProviderFactory).build();
    }

    @Before
    public void setupTest()
    {
        final ResteasyWebTarget targetServer1 = client.target(UriBuilder.fromPath(baseUrlServer1));
        targetServer1.register(this.server1AuthFilter);
        this.server1NodesAPI = targetServer1.proxy(NodesV1.class);

        this.server1AuthFilter.setUserName("admin");
        this.server1AuthFilter.setAuthentication("admin");

        final ResteasyWebTarget targetServer2 = client.target(UriBuilder.fromPath(baseUrlServer2));
        targetServer2.register(this.server2AuthFilter);
        this.server2NodesAPI = targetServer2.proxy(NodesV1.class);

        this.server2AuthFilter.setUserName("admin");
        this.server2AuthFilter.setAuthentication("admin");
    }

    @Test
    public void defaultLockPropagation()
    {
        final NodeCreationRequestEntity nodeToCreate = new NodeCreationRequestEntity();
        nodeToCreate.setNodeType("cm:content");
        final String name = "lockContent-" + UUID.randomUUID().toString();
        nodeToCreate.setName(name);

        final NodeResponseEntity createdNode = this.server1NodesAPI.createNode("-shared-", nodeToCreate);

        final NodeLockRequestEntity lockToSet = new NodeLockRequestEntity();
        this.server1NodesAPI.lockNode(createdNode.getId(), lockToSet);

        final NodeResponseEntity nodeDetail = this.server2NodesAPI.getNode(createdNode.getId(), new MultiValuedParam<>(IncludeOption.IS_LOCKED),
                new MultiValuedParam<>());

        Assert.assertEquals(Boolean.TRUE, nodeDetail.getIsLocked());
    }

    @Test
    public void persistentLockPropagation() throws Exception
    {
        final NodeCreationRequestEntity nodeToCreate = new NodeCreationRequestEntity();
        nodeToCreate.setNodeType("cm:content");
        final String name = "lockContent-" + UUID.randomUUID().toString();
        nodeToCreate.setName(name);

        final NodeResponseEntity createdNode = this.server1NodesAPI.createNode("-shared-", nodeToCreate);

        final NodeLockRequestEntity lockToSet = new NodeLockRequestEntity();
        lockToSet.setLifetime(LockLifetime.PERSISTENT);
        lockToSet.setTimeToExpire(5);

        this.server1NodesAPI.lockNode(createdNode.getId(), lockToSet);

        NodeResponseEntity nodeDetail = this.server2NodesAPI.getNode(createdNode.getId(), new MultiValuedParam<>(IncludeOption.IS_LOCKED),
                new MultiValuedParam<>());

        Assert.assertEquals(Boolean.TRUE, nodeDetail.getIsLocked());

        Thread.sleep(5500);

        nodeDetail = this.server1NodesAPI.getNode(createdNode.getId(), new MultiValuedParam<>(IncludeOption.IS_LOCKED),
                new MultiValuedParam<>());

        Assert.assertEquals(Boolean.FALSE, nodeDetail.getIsLocked());

        nodeDetail = this.server2NodesAPI.getNode(createdNode.getId(), new MultiValuedParam<>(IncludeOption.IS_LOCKED),
                new MultiValuedParam<>());

        Assert.assertEquals(Boolean.FALSE, nodeDetail.getIsLocked());
    }

    @Test
    public void ephemeralLockPropagation() throws Exception
    {
        final NodeCreationRequestEntity nodeToCreate = new NodeCreationRequestEntity();
        nodeToCreate.setNodeType("cm:content");
        final String name = "lockContent-" + UUID.randomUUID().toString();
        nodeToCreate.setName(name);

        final NodeResponseEntity createdNode = this.server1NodesAPI.createNode("-shared-", nodeToCreate);

        final NodeLockRequestEntity lockToSet = new NodeLockRequestEntity();
        lockToSet.setLifetime(LockLifetime.EPHEMERAL);
        lockToSet.setTimeToExpire(5);

        this.server1NodesAPI.lockNode(createdNode.getId(), lockToSet);

        NodeResponseEntity nodeDetail = this.server2NodesAPI.getNode(createdNode.getId(), new MultiValuedParam<>(IncludeOption.IS_LOCKED),
                new MultiValuedParam<>());

        Assert.assertEquals(Boolean.TRUE, nodeDetail.getIsLocked());

        Thread.sleep(5500);

        nodeDetail = this.server1NodesAPI.getNode(createdNode.getId(), new MultiValuedParam<>(IncludeOption.IS_LOCKED),
                new MultiValuedParam<>());

        Assert.assertEquals(Boolean.FALSE, nodeDetail.getIsLocked());

        nodeDetail = this.server2NodesAPI.getNode(createdNode.getId(), new MultiValuedParam<>(IncludeOption.IS_LOCKED),
                new MultiValuedParam<>());

        Assert.assertEquals(Boolean.FALSE, nodeDetail.getIsLocked());
    }
}
