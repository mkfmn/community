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

import org.neo4j.cypher.pipes.Pipe
import org.neo4j.cypher.pipes.aggregation._
abstract sealed class ReturnItem(val identifier: Identifier) extends (Map[String, Any] => Map[String, Any]) {
  def assertDependencies(source: Pipe)

  def columnName = identifier match {
    case UnboundIdentifier(name, None) => name;
    case UnboundIdentifier(name, id) => id.get.name;
    case identifier: Identifier => identifier.name;
  }

  def concreteReturnItem = this
}

case class ValueReturnItem(value: Value) extends ReturnItem(value.identifier) {
  def apply(m: Map[String, Any]): Map[String, Any] = Map(columnName -> value.value(m))

  def assertDependencies(source: Pipe) {
    value.checkAvailable(source.symbols)
  }
}


case class ValueAggregationItem(value: AggregationValue) extends AggregationItem(value.identifier.name) {

  def assertDependencies(source: Pipe) {
    value.checkAvailable(source.symbols)
  }
   def createAggregationFunction: AggregationFunction = value.createAggregationFunction
}

abstract sealed class AggregationItem(name: String) extends ReturnItem(AggregationIdentifier(name)) {
  def apply(m: Map[String, Any]): Map[String, Any] = m

  def createAggregationFunction: AggregationFunction
}


case class CountStar() extends AggregationItem("count(*)") {
  def createAggregationFunction: AggregationFunction = new CountStarFunction

  def assertDependencies(source: Pipe) {}
}
/*
case class Count(inner: ReturnItem)
  extends AggregationItem("count(" + inner.columnName + ")") with InnerReturnItem {
  def createAggregationFunction = new CountFunction(inner)
}
*/
case class Sum(inner: ReturnItem)
  extends AggregationItem("sum(" + inner.columnName + ")") with InnerReturnItem {
  def createAggregationFunction = new SumFunction(inner)
}

case class Avg(inner: ReturnItem)
  extends AggregationItem("avg(" + inner.columnName + ")") with InnerReturnItem {

  def createAggregationFunction = new AvgFunction(inner)
}

case class Max(inner: ReturnItem)
  extends AggregationItem("max(" + inner.columnName + ")") with InnerReturnItem {

  def createAggregationFunction = new MaxFunction(inner)
}

case class Min(inner: ReturnItem)
  extends AggregationItem("min(" + inner.columnName + ")") with InnerReturnItem {

  def createAggregationFunction = new MinFunction(inner)
}

trait InnerReturnItem extends AggregationItem {
  def inner: ReturnItem

  def assertDependencies(source: Pipe) {
    inner.assertDependencies(source)
  }

  override def concreteReturnItem = inner
}