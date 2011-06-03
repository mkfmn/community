/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.kernel.impl.annotations.Documented;
import org.neo4j.server.NeoServerWithEmbeddedWebServer;
import org.neo4j.server.ServerBuilder;
import org.neo4j.server.modules.RESTApiModule;
import org.neo4j.server.rest.domain.GraphDbHelper;
import org.neo4j.server.rest.domain.JsonHelper;
import org.neo4j.server.rest.domain.JsonParseException;
import org.neo4j.test.TestData;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;

public class BatchOperationFunctionalTest
{
    
    public @Rule
    TestData<DocsGenerator> gen = TestData.producedThrough( DocsGenerator.PRODUCER );
    
    private NeoServerWithEmbeddedWebServer server;
    private FunctionalTestHelper functionalTestHelper;
    private GraphDbHelper helper;
    
    @Before
    public void setupServer() throws IOException {
        server = ServerBuilder.server().withRandomDatabaseDir().withSpecificServerModules(RESTApiModule.class).withPassingStartupHealthcheck().build();
        server.start();
        functionalTestHelper = new FunctionalTestHelper(server);
        helper = functionalTestHelper.getGraphDbHelper();
    }

    @After
    public void stopServer() {
        server.stop();
        server = null;
    }
    
    /**
     * Execute multiple operations in batch.
     * 
     * This lets you execute multiple API calls through a single HTTP call,
     * significantly improving performance for large insert and update operations.
     * 
     * The batch service expects an array of job descriptions as input, 
     * each job description describing an action to be performed via the 
     * normal server API.
     * 
     * This service is transactional. If any of the operations performed
     * fails (returns a non-2xx HTTP status code), the transaction will be 
     * rolled back and all changes will be undone.
     * 
     * Each job description should contain a path attribute, with a value relative to the
     * data API root (so http://localhost/db/data/node becomes just /node), and
     * a a method attribute containing HTTP verb to use. 
     * 
     * Optionally you may provide a body attribute, and an id attribute to help you keep 
     * track of responses, although responses are guaranteed to be returned in the same 
     * order the job descriptions are recieved.
     */
    @Documented
    @SuppressWarnings( "unchecked" )
    @Test
    public void shouldPerformMultipleOperations() throws JsonParseException, ClientHandlerException, UniformInterfaceException {
        
        String jsonString = "[" +
          "{ " +
            "\"method\":\"PUT\"," +
            "\"to\":\"/node/0/properties\", " +
            "\"body\":{ \"age\":1 }," +
            "\"id\":0"+
          "},"+
          "{ " +
            "\"method\":\"GET\"," +
            "\"to\":\"/node/0\"" +
          "},"+
          "{ " +
            "\"method\":\"POST\"," +
            "\"to\":\"/node\", " +
            "\"id\":2,"+
            "\"body\":{ \"age\":1 }" +
          "},"+
          "{ " +
            "\"method\":\"POST\"," +
            "\"to\":\"/node\", " +
            "\"id\":3,"+
            "\"body\":{ \"age\":12 }" +
          "}"+
        "]";

        String uri = functionalTestHelper.dataUri() + "batch";
        
        ClientResponse response = Client.create()
          .resource( uri)
          .type(MediaType.APPLICATION_JSON ).accept( MediaType.APPLICATION_JSON )
          .entity( jsonString ).post( ClientResponse.class );
        
        assertEquals(200, response.getStatus());
        
        List<Map<String, Object>> results = JsonHelper.jsonToList( response.getEntity( String.class ));
        
        assertEquals(4, results.size());

        Map<String, Object> putResult = results.get( 0 );
        Map<String, Object> getResult = results.get( 1 );
        Map<String, Object> firstPostResult = results.get( 2 );
        Map<String, Object> secondPostResult = results.get( 3 );
        
        // Ids should be ok
        assertEquals(0, putResult.get("id"));
        assertEquals(2, firstPostResult.get("id"));
        assertEquals(3, secondPostResult.get("id"));

        // Should contain "from"
        assertEquals("/node/0/properties", putResult.get( "from" ));
        assertEquals("/node/0", getResult.get("from"));
        assertEquals("/node", firstPostResult.get("from"));
        assertEquals("/node", secondPostResult.get("from"));
        
        // Post should contain location
        assertTrue(((String)firstPostResult.get( "location" )).length() > 0);
        assertTrue(((String)secondPostResult.get( "location" )).length() > 0);

        // Should have created by the first PUT request
        Map<String, Object> body = (Map<String, Object>) getResult.get("body");
        assertEquals(1, ((Map<String, Object>)body.get("data")).get( "age" ));
        
        gen.get()
            .payload( jsonString )
            .expectedStatus( 200 )
            .post( uri );
    }
    
    /**
     * Referring to items created earlier in the same batch job.
     * 
     * The batch operation API allows you to refer to the URL returned
     * from a created resource in subsequent job descriptions, within the same batch call. 
     * 
     * Use the "{[JOB ID]}" special syntax to inject URLs from created
     * resources into JSON strings in subsequent job descriptions.
     */
    @Documented
    @Test
    public void shouldBeAbleToReferToCreatedResource() throws JsonParseException, ClientHandlerException, UniformInterfaceException {
        
        String jsonString = "[" +
          "{ " +
            "\"method\":\"POST\"," +
            "\"to\":\"/node\", " +
            "\"id\":0,"+
            "\"body\":{ \"age\":1 }" +
          "},"+
          "{ " +
            "\"method\":\"POST\"," +
            "\"to\":\"/node\", " +
            "\"id\":1,"+
            "\"body\":{ \"age\":12 }" +
          "},"+
          "{ " +
            "\"method\":\"POST\"," +
            "\"to\":\"{0}/relationships\", " +
            "\"id\":3,"+
            "\"body\":{ "+
              "\"to\":\"{1}\"," +
              "\"data\":{\"name\":\"bob\"}," +
              "\"type\":\"KNOWS\"" +
            " }" +
          "},"+
          "{ " +
            "\"method\":\"POST\"," +
            "\"to\":\"/index/relationship/my_rels/name/bob\", " +
            "\"id\":4,"+
            "\"body\": \"{3}\""+
          "}"+
        "]";

        String uri = functionalTestHelper.dataUri() + "batch";
        
        ClientResponse response = Client.create()
          .resource( uri)
          .type(MediaType.APPLICATION_JSON ).accept( MediaType.APPLICATION_JSON )
          .entity( jsonString ).post( ClientResponse.class );
        
        assertEquals(200, response.getStatus());
        
        List<Map<String, Object>> results = JsonHelper.jsonToList( response.getEntity( String.class ));
        
        assertEquals(4, results.size());
        assertEquals( 1, helper.getIndexedRelationships( "my_rels", "name", "bob" ).size() );
        
        gen.get()
            .payload( jsonString )
            .expectedStatus( 200 )
            .post( uri );
    }
    
    @Test
    public void shouldGetLocationHeadersWhenCreatingThings() throws JsonParseException, ClientHandlerException, UniformInterfaceException {
        
        String jsonString = "[" +
          "{ " +
            "\"method\":\"POST\"," +
            "\"to\":\"/node\", " +
            "\"body\":{ \"age\":1 }" +
          "}"+
        "]";
        
        int originalNodeCount = helper.getNumberOfNodes();
        
        ClientResponse response = Client.create()
          .resource( functionalTestHelper.dataUri() + "batch")
          .type(MediaType.APPLICATION_JSON ).accept( MediaType.APPLICATION_JSON )
          .entity( jsonString ).post( ClientResponse.class );
        
        assertEquals(200, response.getStatus());
        assertEquals(originalNodeCount + 1, helper.getNumberOfNodes());
        
        List<Map<String, Object>> results = JsonHelper.jsonToList( response.getEntity( String.class ));
        
        assertEquals(1, results.size());
        
        Map<String, Object> result = results.get( 0 );
        assertTrue(((String)result.get( "location" )).length() > 0);
    }
    
    @Test
    public void shouldRollbackAllWhenGivenIncorrectRequest() throws JsonParseException, ClientHandlerException, UniformInterfaceException {
        
        String jsonString = "[" +
          "{ " +
            "\"method\":\"POST\"," +
            "\"to\":\"/node\", " +
            "\"body\":{ \"age\":1 }" +
          "},"+
          "{ " +
            "\"method\":\"POST\"," +
            "\"to\":\"/node\", " +
            "\"body\":[\"a_list\",\"this_makes_no_sense\"]" +
          "}"+
        "]";
        
        int originalNodeCount = helper.getNumberOfNodes();
        
        ClientResponse response = Client.create()
          .resource( functionalTestHelper.dataUri() + "batch")
          .type(MediaType.APPLICATION_JSON ).accept( MediaType.APPLICATION_JSON )
          .entity( jsonString ).post( ClientResponse.class );
        
        assertEquals(400, response.getStatus());
        assertEquals(originalNodeCount, helper.getNumberOfNodes());
        
    }
    
    @Test
    public void shouldRollbackAllWhenInsertingIllegalData() throws JsonParseException, ClientHandlerException, UniformInterfaceException {
        
        String jsonString = "[" +
          "{ " +
            "\"method\":\"POST\"," +
            "\"to\":\"/node\", " +
            "\"body\":{ \"age\":1 }" +
          "},"+
          "{ " +
            "\"method\":\"POST\"," +
            "\"to\":\"/node\", " +
            "\"body\":{ \"age\":{ \"age\":{ \"age\":1 } } }" +
          "}"+
        "]";
        
        int originalNodeCount = helper.getNumberOfNodes();
        
        ClientResponse response = Client.create()
          .resource( functionalTestHelper.dataUri() + "batch")
          .type(MediaType.APPLICATION_JSON ).accept( MediaType.APPLICATION_JSON )
          .entity( jsonString ).post( ClientResponse.class );
        
        assertEquals(400, response.getStatus());
        assertEquals(originalNodeCount, helper.getNumberOfNodes());
        
    }
    

    @Test
    public void shouldRollbackAllOnSingle404() throws JsonParseException, ClientHandlerException, UniformInterfaceException {
        
        String jsonString = "[" +
          "{ " +
            "\"method\":\"POST\"," +
            "\"to\":\"/node\", " +
            "\"body\":{ \"age\":1 }" +
          "},"+
          "{ " +
            "\"method\":\"POST\"," +
            "\"to\":\"www.google.com\"" +
          "}"+
        "]";
        
        int originalNodeCount = helper.getNumberOfNodes();
        
        ClientResponse response = Client.create()
          .resource( functionalTestHelper.dataUri() + "batch")
          .type(MediaType.APPLICATION_JSON ).accept( MediaType.APPLICATION_JSON )
          .entity( jsonString ).post( ClientResponse.class );
        
        assertEquals(400, response.getStatus());
        assertEquals(originalNodeCount, helper.getNumberOfNodes());
        
    }
}
