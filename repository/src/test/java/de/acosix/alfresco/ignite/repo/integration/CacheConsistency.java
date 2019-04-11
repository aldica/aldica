package de.acosix.alfresco.ignite.repo.integration;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import de.acosix.alfresco.rest.client.api.AuthenticationV1;
import de.acosix.alfresco.rest.client.api.NodesV1;
import de.acosix.alfresco.rest.client.jackson.RestAPIBeanDeserializerModifier;
import de.acosix.alfresco.rest.client.model.authentication.TicketEntity;
import de.acosix.alfresco.rest.client.model.authentication.TicketRequest;
import de.acosix.alfresco.rest.client.model.nodes.CommonNodeEntity;
import de.acosix.alfresco.rest.client.model.nodes.NodeCreationRequestEntity;
import de.acosix.alfresco.rest.client.model.nodes.NodeResponseEntity;
import de.acosix.alfresco.rest.client.model.nodes.PermissionsInfo;
import de.acosix.alfresco.rest.client.resteasy.MultiValuedParamConverterProvider;

/**
 *
 * @author Axel Faust
 */
@RunWith(BlockJUnit4ClassRunner.class)
public class CacheConsistency
{

    private static final String baseUrlServer1 = "http://localhost:8082/alfresco";

    private static final String baseUrlServer2 = "http://localhost:8182/alfresco";

    private static ResteasyClient client;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

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

    private AuthenticationV1 createServerAuthenticationAPI(final ResteasyClient client, final String baseUrl)
    {
        final ResteasyWebTarget targetServer = client.target(UriBuilder.fromPath(baseUrl));
        return targetServer.proxy(AuthenticationV1.class);
    }

    private NodesV1 createServerNodesAPI(final ResteasyClient client, final String baseUrl)
    {
        final ResteasyWebTarget targetServer = client.target(UriBuilder.fromPath(baseUrl));

        final TicketRequest rq = new TicketRequest();
        rq.setUserId("admin");
        rq.setPassword("admin");

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
    public void ticketUniquenessInCluster()
    {
        final AuthenticationV1 server1AuthenticationAPI = this.createServerAuthenticationAPI(client, baseUrlServer1);
        final AuthenticationV1 server2AuthenticationAPI = this.createServerAuthenticationAPI(client, baseUrlServer2);

        final TicketRequest rq = new TicketRequest();
        rq.setUserId("admin");
        rq.setPassword("admin");

        final TicketEntity server1Ticket = server1AuthenticationAPI.createTicket(rq);
        final TicketEntity server2Ticket = server2AuthenticationAPI.createTicket(rq);

        Assert.assertEquals("Authentication tickets for the same user should be identical in cluster", server1Ticket.getId(),
                server2Ticket.getId());
    }

    @Test
    public void createAndAccessNode()
    {
        final NodesV1 server1NodesAPI = this.createServerNodesAPI(client, baseUrlServer1);
        final NodesV1 server2NodesAPI = this.createServerNodesAPI(client, baseUrlServer2);

        final NodeCreationRequestEntity nodeToCreate = new NodeCreationRequestEntity();
        nodeToCreate.setNodeType("cm:folder");
        final String name = "folder-" + UUID.randomUUID().toString();
        nodeToCreate.setName(name);

        final Map<String, Object> properties = new HashMap<>();
        properties.put("cm:title", "Test Folder");
        nodeToCreate.setProperties(properties);

        nodeToCreate.setAspectNames(Collections.singletonList("cm:effectivity"));

        final NodeResponseEntity server1CreatedNode = server1NodesAPI.createNode("-shared-", nodeToCreate);

        final NodeResponseEntity server1Node = server1NodesAPI.getNode(server1CreatedNode.getId());
        final NodeResponseEntity server2Node = server2NodesAPI.getNode(server1CreatedNode.getId());

        Assert.assertEquals("Aspects of created node should be consistent on both servers", server1Node.getAspectNames(),
                server2Node.getAspectNames());
        Assert.assertEquals("Properties of created node should be consistent on both servers", server1Node.getProperties(),
                server2Node.getProperties());
    }

    @Test
    public void updateCachedNode() throws Exception
    {
        final NodesV1 server1NodesAPI = this.createServerNodesAPI(client, baseUrlServer1);
        final NodesV1 server2NodesAPI = this.createServerNodesAPI(client, baseUrlServer2);

        // in order to avoid having to create a new node (overlap with createAndAccessNode test) we simply update the Shared folder
        NodeResponseEntity server1SharedFolder = server1NodesAPI.getNode("-shared-");
        NodeResponseEntity server2SharedFolder = server2NodesAPI.getNode("-shared-");

        Assert.assertEquals("ID of Shared folder should be identical (before update) in cluster", server1SharedFolder.getId(),
                server2SharedFolder.getId());
        Assert.assertEquals("Name of Shared folder should be identical (before update) in cluster", server1SharedFolder.getName(),
                server2SharedFolder.getName());
        Assert.assertEquals("Creation date of Shared folder should be identical (before update) in cluster",
                server1SharedFolder.getCreatedAt(), server2SharedFolder.getCreatedAt());
        Assert.assertEquals("Modified date of Shared folder should be identical (before update) in cluster",
                server1SharedFolder.getModifiedAt(), server2SharedFolder.getModifiedAt());

        Assert.assertEquals("Aspect names of Shared folder should be identical (before update) in cluster",
                new HashSet<>(server1SharedFolder.getAspectNames()), new HashSet<>(server2SharedFolder.getAspectNames()));
        Assert.assertEquals("Properties of Shared folder should be identical (before update) in cluster",
                server1SharedFolder.getProperties(), server2SharedFolder.getProperties());

        final CommonNodeEntity<PermissionsInfo> updates = new CommonNodeEntity<>();

        // need to augment the aspectNames originally retrieved as any aspect missing will be removed
        final List<String> aspectNames = server1SharedFolder.getAspectNames();
        aspectNames.add("cm:author");
        updates.setAspectNames(aspectNames);

        // properties do not need to be augmented
        final Map<String, Object> properties = new HashMap<>();
        properties.put("cm:author", "Charles Dickens");
        properties.put("cm:hits", Integer.valueOf(125));
        updates.setProperties(properties);

        // do the update - we don't care about the response but will validate the responses to the subsequent retrieval calls
        server1NodesAPI.updateNode(server1SharedFolder.getId(), updates);

        server1SharedFolder = server1NodesAPI.getNode("-shared-");
        server2SharedFolder = server2NodesAPI.getNode("-shared-");

        Assert.assertEquals("ID of Shared folder should be identical (after update) in cluster", server1SharedFolder.getId(),
                server2SharedFolder.getId());
        Assert.assertEquals("Name of Shared folder should be identical (after update) in cluster", server1SharedFolder.getName(),
                server2SharedFolder.getName());
        Assert.assertEquals("Creation date of Shared folder should be identical (after update) in cluster",
                server1SharedFolder.getCreatedAt(), server2SharedFolder.getCreatedAt());
        Assert.assertEquals("Modified date of Shared folder should be identical (after update) in cluster",
                server1SharedFolder.getModifiedAt(), server2SharedFolder.getModifiedAt());

        Assert.assertEquals("Aspect names of Shared folder should be identical (after update) in cluster",
                new HashSet<>(server1SharedFolder.getAspectNames()), new HashSet<>(server2SharedFolder.getAspectNames()));
        Assert.assertEquals("Properties of Shared folder should be identical (after update) in cluster",
                server1SharedFolder.getProperties(), server2SharedFolder.getProperties());
    }
}
