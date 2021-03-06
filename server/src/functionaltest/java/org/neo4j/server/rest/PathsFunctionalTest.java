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

import java.util.Collection;
import java.util.Map;

import javax.ws.rs.core.Response.Status;

import org.junit.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.kernel.impl.annotations.Documented;
import org.neo4j.server.rest.domain.JsonHelper;
import org.neo4j.server.rest.domain.JsonParseException;
import org.neo4j.server.rest.web.PropertyValueException;
import org.neo4j.test.GraphDescription;
import org.neo4j.test.GraphDescription.Graph;
import org.neo4j.test.GraphDescription.NODE;
import org.neo4j.test.GraphDescription.PROP;
import org.neo4j.test.GraphDescription.REL;
import org.neo4j.test.TestData.Title;

public class PathsFunctionalTest extends AbstractRestFunctionalTestBase
{
    private static final String NODES = "http://localhost:7474/db/data/node/";

    /**
     * The +shortestPath+ algorithm can find multiple paths between the same
     * nodes, like in this example.
     */
    @Test
    @Graph( value = { "a to c", "a to d", "c to b", "d to e", "b to f", "c to f", "f to g", "d to g", "e to g",
    "c to g" } )
    @Documented
    @Title( "Find all shortest paths" )
    public void shouldBeAbleToFindAllShortestPaths() throws PropertyValueException
    {

        // Get all shortest paths
        long a = nodeId( data.get(), "a" );
        long g = nodeId( data.get(), "g" );
        String response = gen.get()
        .expectedStatus( Status.OK.getStatusCode() )
        .payload( getAllShortestPathPayLoad( g ) )
        .post( "http://localhost:7474/db/data/node/" + a + "/paths" )
        .entity();
        Collection<?> result = (Collection<?>) JsonHelper.jsonToSingleValue( response );
        assertEquals( 2, result.size() );
        for ( Object representation : result )
        {
            Map<?, ?> path = (Map<?, ?>) representation;

            assertThatPathStartsWith( path, a );
            assertThatPathEndsWith( path, g );
            assertThatPathHasLength( path, 2 );
        }
    }

    /**
     * If no path algorithm is specified, a +ShortestPath+ algorithm with a max
     * depth of 1 will be chosen. In this example, the +max_depth+ is set to +3+
     * in order to find the shortest path between 3 linked nodes.
     */
    @Title( "Find one of the shortest paths between nodes" )
    @Test
    @Graph( value = { "a to c", "a to d", "c to b", "d to e", "b to f", "c to f", "f to g", "d to g", "e to g",
    "c to g" } )
    @Documented
    public void shouldBeAbleToFetchSingleShortestPath() throws JsonParseException
    {
        long a = nodeId( data.get(), "a" );
        long g = nodeId( data.get(), "g" );
        String response = gen.get()
        .expectedStatus( Status.OK.getStatusCode() )
        .payload( getAllShortestPathPayLoad( g ) )
        .post( "http://localhost:7474/db/data/node/" + a + "/path" )
        .entity();
        // Get single shortest path

        Map<?, ?> path = JsonHelper.jsonToMap( response );

        assertThatPathStartsWith( path, a );
        assertThatPathEndsWith( path, g );
        assertThatPathHasLength( path, 2 );
    }

    private void assertThatPathStartsWith( final Map<?, ?> path, final long start )
    {
        assertTrue( "Path should start with " + start + "\nBut it was " + path, path.get( "start" )
                .toString()
                .endsWith( "/node/" + start ) );
    }

    private void assertThatPathEndsWith( final Map<?, ?> path, final long start )
    {
        assertTrue( "Path should end with " + start + "\nBut it was " + path, path.get( "end" )
                .toString()
                .endsWith( "/node/" + start ) );
    }

    private void assertThatPathHasLength( final Map<?, ?> path, final int length )
    {
        Object actual = path.get( "length" );

        assertEquals( "Expected path to have a length of " + length + "\nBut it was " + actual, length, actual );
    }

    /**
     * This example is running a Dijkstra algorithm over a graph with different
     * cost properties on different relationships.
     */
    @Test
    @Graph( nodes = { @NODE( name = "start", setNameProperty = true ), @NODE( name = "a", setNameProperty = true ),
            @NODE( name = "b", setNameProperty = true ), @NODE( name = "c", setNameProperty = true ),
            @NODE( name = "d", setNameProperty = true ), @NODE( name = "e", setNameProperty = true ),
            @NODE( name = "f", setNameProperty = true ), @NODE( name = "x", setNameProperty = true ),
            @NODE( name = "y", setNameProperty = true ) }, relationships = {
            @REL( start = "start", end = "a", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "a", end = "x", type = "to", properties = { @PROP( key = "cost", value = "9", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "a", end = "b", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "b", end = "x", type = "to", properties = { @PROP( key = "cost", value = "7", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "b", end = "c", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "c", end = "x", type = "to", properties = { @PROP( key = "cost", value = "5", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "c", end = "x", type = "to", properties = { @PROP( key = "cost", value = "4", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "c", end = "d", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "d", end = "x", type = "to", properties = { @PROP( key = "cost", value = "3", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "d", end = "e", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "e", end = "x", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "e", end = "f", type = "to", properties = { @PROP( key = "cost", value = "2", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "x", end = "y", type = "to", properties = { @PROP( key = "cost", value = "2", type = GraphDescription.PropType.DOUBLE ) } ) } )
            @Title( "Execute a Dijkstra algorithm with weights on relationships" )
            public void shouldGetCorrectDijkstraPathsWithWeights() throws Exception
            {
        // Get cheapest paths using Dijkstra
        long start = nodeId( data.get(), "start" );
        long x = nodeId( data.get(), "x" );
        String response = gen.get()
        .expectedStatus( Status.OK.getStatusCode() )
        .payload( getAllPathsUsingDijkstraPayLoad( x, false ) )
        .post( "http://localhost:7474/db/data/node/" + start + "/path" )
        .entity();
        //
        Map<?, ?> path = JsonHelper.jsonToMap( response );
        assertThatPathStartsWith( path, start );
        assertThatPathEndsWith( path, x );
        assertThatPathHasLength( path, 6 );
        assertEquals( 6.0, path.get( "weight" ) );
            }

    /**
     * The following is executing a Dijkstra search on a graph with equal
     * weights on all relationships.
     */
    @Test
    @Graph( nodes = { @NODE( name = "start", setNameProperty = true ), @NODE( name = "a", setNameProperty = true ),
            @NODE( name = "b", setNameProperty = true ), @NODE( name = "c", setNameProperty = true ),
            @NODE( name = "d", setNameProperty = true ), @NODE( name = "e", setNameProperty = true ),
            @NODE( name = "f", setNameProperty = true ), @NODE( name = "x", setNameProperty = true ),
            @NODE( name = "y", setNameProperty = true ) }, relationships = {
            @REL( start = "start", end = "a", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "a", end = "x", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "a", end = "b", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "b", end = "x", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "b", end = "c", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "c", end = "x", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "c", end = "x", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "c", end = "d", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "d", end = "x", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "d", end = "e", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "e", end = "x", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "e", end = "f", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ),
            @REL( start = "x", end = "y", type = "to", properties = { @PROP( key = "cost", value = "1", type = GraphDescription.PropType.DOUBLE ) } ) } )
            @Title( "Execute a Dijkstra algorithm with similar weights on relationships" )
            public void shouldGetCorrectDijkstraPathsWithWeightsWithDefaultCost() throws Exception
            {
        // Get cheapest paths using Dijkstra
        long start = nodeId( data.get(), "start" );
        long x = nodeId( data.get(), "x" );
        String response = gen.get()
        .expectedStatus( Status.OK.getStatusCode() )
        .payload( getAllPathsUsingDijkstraPayLoad( x, false ) )
        .post( "http://localhost:7474/db/data/node/" + start + "/path" )
        .entity();
        //
        Map<?, ?> path = JsonHelper.jsonToMap( response );
        assertThatPathStartsWith( path, start );
        assertThatPathEndsWith( path, x );
        assertThatPathHasLength( path, 2 );
        assertEquals( 2.0, path.get( "weight" ) );
            }

    @Test
    @Graph( value = { "a to c", "a to d", "c to b", "d to e", "b to f", "c to f", "f to g", "d to g", "e to g",
    "c to g" } )
    public void shouldReturn404WhenFailingToFindASinglePath() throws JsonParseException
    {
        long a = nodeId( data.get(), "a" );
        long g = nodeId( data.get(), "g" );
        String noHitsJson = "{\"to\":\""
            + nodeUri( g )
            + "\", \"max_depth\":1, \"relationships\":{\"type\":\"dummy\", \"direction\":\"in\"}, \"algorithm\":\"shortestPath\"}";
        String entity = gen.get()
        .expectedStatus( Status.NOT_FOUND.getStatusCode() )
        .payload( noHitsJson )
        .post( "http://localhost:7474/db/data/node/" + a + "/path" )
        .entity();
        System.out.println( entity );
    }

    private long nodeId( final Map<String, Node> map, final String string )
    {
        return map.get( string )
        .getId();
    }

    private String nodeUri( final long l )
    {
        return NODES + l;
    }

    private String getAllShortestPathPayLoad( final long to )
    {
        String json = "{\"to\":\""
            + nodeUri( to )
            + "\", \"max_depth\":3, \"relationships\":{\"type\":\"to\", \"direction\":\"out\"}, \"algorithm\":\"shortestPath\"}";
        return JSONPrettifier.parse( json );
    }

    //
    private String getAllPathsUsingDijkstraPayLoad( final long to, final boolean includeDefaultCost )
    {
        String json = "{\"to\":\"" + nodeUri( to ) + "\"" + ", \"cost_property\":\"cost\""
        + ( includeDefaultCost ? ", \"default_cost\":1" : "" )
        + ", \"relationships\":{\"type\":\"to\", \"direction\":\"out\"}, \"algorithm\":\"dijkstra\"}";
        return JSONPrettifier.parse( json );
    }

}
