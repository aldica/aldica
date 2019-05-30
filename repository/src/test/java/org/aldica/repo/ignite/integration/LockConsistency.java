/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.integration;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.UriBuilder;

import org.apache.commons.codec.binary.Base64;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.LocalResteasyProviderFactory;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import de.acosix.alfresco.rest.client.api.AuthenticationV1;
import de.acosix.alfresco.rest.client.api.NodesV1;
import de.acosix.alfresco.rest.client.api.NodesV1.IncludeOption;
import de.acosix.alfresco.rest.client.jackson.RestAPIBeanDeserializerModifier;
import de.acosix.alfresco.rest.client.model.authentication.TicketEntity;
import de.acosix.alfresco.rest.client.model.authentication.TicketRequest;
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

        final LocalResteasyProviderFactory resteasyProviderFactory = new LocalResteasyProviderFactory(new ResteasyProviderFactory());
        resteasyProviderFactory.register(resteasyJacksonProvider);
        // will cause a warning regarding Jackson provider which is already registered
        RegisterBuiltin.register(resteasyProviderFactory);
        resteasyProviderFactory.register(new MultiValuedParamConverterProvider());

        client = new ResteasyClientBuilder().providerFactory(resteasyProviderFactory).build();
    }

    private NodesV1 createServerNodesAPI(final ResteasyClient client, final String baseUrl, final String user, final String password)
    {
        final ResteasyWebTarget targetServer = client.target(UriBuilder.fromPath(baseUrl));

        final TicketRequest rq = new TicketRequest();
        rq.setUserId(user);
        rq.setPassword(password);

        final AuthenticationV1 authenticationAPI = targetServer.proxy(AuthenticationV1.class);
        final TicketEntity ticket = authenticationAPI.createTicket(rq);

        final ClientRequestFilter rqAuthFilter = (requestContext) -> {
            final String base64Token = Base64.encodeBase64String(ticket.getId().getBytes(StandardCharsets.UTF_8));
            requestContext.getHeaders().add("Authorization", "Basic " + base64Token);
        };
        targetServer.register(rqAuthFilter);

        return targetServer.proxy(NodesV1.class);
    }

    @Test
    public void defaultLockPropagation()
    {
        final NodesV1 server1NodesAPI = this.createServerNodesAPI(client, baseUrlServer1, "admin", "admin");
        final NodesV1 server2NodesAPI = this.createServerNodesAPI(client, baseUrlServer2, "admin", "admin");

        final NodeCreationRequestEntity nodeToCreate = new NodeCreationRequestEntity();
        nodeToCreate.setNodeType("cm:content");
        final String name = "lockContent-" + UUID.randomUUID().toString();
        nodeToCreate.setName(name);

        final NodeResponseEntity createdNode = server1NodesAPI.createNode("-shared-", nodeToCreate);

        final NodeLockRequestEntity lockToSet = new NodeLockRequestEntity();
        server1NodesAPI.lockNode(createdNode.getId(), lockToSet);

        final NodeResponseEntity nodeDetail = server2NodesAPI.getNode(createdNode.getId(), new MultiValuedParam<>(IncludeOption.IS_LOCKED),
                new MultiValuedParam<>());

        Assert.assertEquals(Boolean.TRUE, nodeDetail.getIsLocked());
    }

    @Test
    public void persistentLockPropagation() throws Exception
    {
        final NodesV1 server1NodesAPI = this.createServerNodesAPI(client, baseUrlServer1, "admin", "admin");
        final NodesV1 server2NodesAPI = this.createServerNodesAPI(client, baseUrlServer2, "admin", "admin");

        final NodeCreationRequestEntity nodeToCreate = new NodeCreationRequestEntity();
        nodeToCreate.setNodeType("cm:content");
        final String name = "lockContent-" + UUID.randomUUID().toString();
        nodeToCreate.setName(name);

        final NodeResponseEntity createdNode = server1NodesAPI.createNode("-shared-", nodeToCreate);

        final NodeLockRequestEntity lockToSet = new NodeLockRequestEntity();
        lockToSet.setLifetime(LockLifetime.PERSISTENT);
        lockToSet.setTimeToExpire(5);

        server1NodesAPI.lockNode(createdNode.getId(), lockToSet);

        NodeResponseEntity nodeDetail = server2NodesAPI.getNode(createdNode.getId(), new MultiValuedParam<>(IncludeOption.IS_LOCKED),
                new MultiValuedParam<>());

        Assert.assertEquals(Boolean.TRUE, nodeDetail.getIsLocked());

        Thread.sleep(5500);

        nodeDetail = server1NodesAPI.getNode(createdNode.getId(), new MultiValuedParam<>(IncludeOption.IS_LOCKED),
                new MultiValuedParam<>());

        Assert.assertEquals(Boolean.FALSE, nodeDetail.getIsLocked());

        nodeDetail = server2NodesAPI.getNode(createdNode.getId(), new MultiValuedParam<>(IncludeOption.IS_LOCKED),
                new MultiValuedParam<>());

        Assert.assertEquals(Boolean.FALSE, nodeDetail.getIsLocked());
    }

    @Test
    public void ephemeralLockPropagation() throws Exception
    {
        final NodesV1 server1NodesAPI = this.createServerNodesAPI(client, baseUrlServer1, "admin", "admin");
        final NodesV1 server2NodesAPI = this.createServerNodesAPI(client, baseUrlServer2, "admin", "admin");

        final NodeCreationRequestEntity nodeToCreate = new NodeCreationRequestEntity();
        nodeToCreate.setNodeType("cm:content");
        final String name = "lockContent-" + UUID.randomUUID().toString();
        nodeToCreate.setName(name);

        final NodeResponseEntity createdNode = server1NodesAPI.createNode("-shared-", nodeToCreate);

        final NodeLockRequestEntity lockToSet = new NodeLockRequestEntity();
        lockToSet.setLifetime(LockLifetime.EPHEMERAL);
        lockToSet.setTimeToExpire(5);

        server1NodesAPI.lockNode(createdNode.getId(), lockToSet);

        NodeResponseEntity nodeDetail = server2NodesAPI.getNode(createdNode.getId(), new MultiValuedParam<>(IncludeOption.IS_LOCKED),
                new MultiValuedParam<>());

        Assert.assertEquals(Boolean.TRUE, nodeDetail.getIsLocked());

        Thread.sleep(5500);

        nodeDetail = server1NodesAPI.getNode(createdNode.getId(), new MultiValuedParam<>(IncludeOption.IS_LOCKED),
                new MultiValuedParam<>());

        Assert.assertEquals(Boolean.FALSE, nodeDetail.getIsLocked());

        nodeDetail = server2NodesAPI.getNode(createdNode.getId(), new MultiValuedParam<>(IncludeOption.IS_LOCKED),
                new MultiValuedParam<>());

        Assert.assertEquals(Boolean.FALSE, nodeDetail.getIsLocked());
    }
}
