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
package org.neo4j.cypher.commands

import org.neo4j.graphdb.{NotFoundException, Relationship, PropertyContainer}
import org.neo4j.cypher.{SyntaxException, SymbolTable}
import org.neo4j.cypher.pipes.aggregation.{CountFunction, AggregationFunction}

abstract sealed class Value {
  def value(m: Map[String, Any]): Any

  def identifier: Identifier

  def checkAvailable(symbols: SymbolTable)
}

case class Literal(v: Any) extends Value {
  def value(m: Map[String, Any]) = v

  def identifier: Identifier = LiteralIdentifier(v.toString)

  def checkAvailable(symbols: SymbolTable) {}
}

case class AggregationValue(functionName: String, inner: Value) extends Value {
  def value(m: Map[String, Any]) = m(identifier.name)

  def identifier: Identifier = AggregationIdentifier(functionName+"("+inner.identifier.name+")")

  def checkAvailable(symbols: SymbolTable) {
    inner.checkAvailable(symbols)
  }

  def createAggregationFunction: AggregationFunction = functionName match {
    case "count" => new CountFunction(inner)
    case _ => throw new UnsupportedOperationException("No such function "+functionName)
  }
}

case class NullablePropertyValue(subEntity: String, subProperty: String) extends PropertyValue(subEntity, subProperty) {
  protected override def handleNotFound(propertyContainer: PropertyContainer, x: NotFoundException): Any = null
}

case class PropertyValue(entity: String, property: String) extends Value {
  protected def handleNotFound(propertyContainer: PropertyContainer, x: NotFoundException): Any = throw new SyntaxException("%s.%s does not exist on %s".format(entity, property, propertyContainer), x)

  def value(m: Map[String, Any]): Any = {
    val propertyContainer = m(entity).asInstanceOf[PropertyContainer]
    try {
      propertyContainer.getProperty(property)
    } catch {
      case x: NotFoundException => handleNotFound(propertyContainer, x)
    }
  }

  def identifier: Identifier = PropertyIdentifier(entity, property)

  def checkAvailable(symbols: SymbolTable) {
    symbols.assertHas(entity)
  }
}

case class RelationshipTypeValue(relationship: String) extends Value {
  def value(m: Map[String, Any]): Any = m(relationship).asInstanceOf[Relationship].getType.name()

  def identifier: Identifier = RelationshipTypeIdentifier(relationship)

  def checkAvailable(symbols: SymbolTable) {
    symbols.assertHas(relationship)
  }
}

case class EntityValue(entityName:String) extends Value {
  def value(m: Map[String, Any]): Any = m.getOrElse(entityName, throw new NotFoundException)

  def identifier: Identifier = UnboundIdentifier(entityName, None)

  def checkAvailable(symbols: SymbolTable) {
     symbols.assertHas(entityName)
  }
}
