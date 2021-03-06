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
package org.neo4j.cypher

import commands._
import org.junit.Test
import org.junit.Assert._
import org.neo4j.graphdb.{Direction, Node}

class SematicErrorTest extends ExecutionEngineTestBase {
  @Test def returnNodeThatsNotThere() {
    val node: Node = createNode()

    val query = Query(
      Return(ValueReturnItem(EntityValue("bar"))),
      Start(NodeById("foo", node.getId)))

    expectedError(query, """Unbound Identifier UnboundIdentifier(bar,None) not resolved!""")
  }

  @Test def throwOnDisconnectedPattern() {
    val node: Node = createNode()

    val query = Query(
      Return(ValueReturnItem(EntityValue("foo"))),
      Start(NodeById("foo", node.getId)),
      Match(RelatedTo("a", "b", None, None, Direction.BOTH)))

    expectedError(query, "All parts of the pattern must either directly or indirectly be connected to at least one bound entity. These identifiers were found to be disconnected: a, b")
  }

  @Test def defineNodeAndTreatItAsARelationship() {
    val node: Node = createNode()

    val query = Query(
      Return(ValueReturnItem(EntityValue("foo"))),
      Start(NodeById("foo", node.getId)),
      Match(RelatedTo("a", "b", Some("foo"), None, Direction.BOTH)))

    expectedError(query, "Identifier NodeIdentifier(foo) already defined with different type RelationshipIdentifier(foo)")
  }



  def expectedError(query: Query, message: String) {
    try {
      execute(query).toList
      fail("Did not get the expected syntax error, expected: " + message)
    } catch {
      case x: SyntaxException => assertEquals(message, x.getMessage)
    }
  }
}