package org.neo4j.cypher.parser

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

import org.neo4j.cypher.commands._
import scala.util.parsing.combinator._

trait ReturnItems extends JavaTokenParsers with Tokens with Values {
  def returnItem: Parser[ReturnItem] = nullablePropertyOutput | relationshipTypeOutput | propertyOutput | nodeOutput

  def nodeOutput: Parser[ReturnItem] = entityValue ^^ {
    case x => ValueReturnItem(x)
  }

  def propertyOutput: Parser[ReturnItem] = identity ~ "." ~ identity ^^ {
    case c ~ "." ~ p => ValueReturnItem(PropertyValue(c,p))
  }

  def nullablePropertyOutput: Parser[ReturnItem] = identity ~ "." ~ identity ~ "?" ^^ {
    case c ~ "." ~ p ~ "?" => ValueReturnItem(NullablePropertyValue(c, p))
  }

  def relationshipTypeOutput: Parser[ReturnItem] = identity <~ "~TYPE" ^^ {
    case c => ValueReturnItem(RelationshipTypeValue(c))
  }

  private def lowerCaseIdent = ident ^^ {
    case c => c.toLowerCase
  }

  def aggregationValueFunction: Parser[AggregationItem] = "count" ~ "(" ~ ( nullableProperty | value | entityValue )~ ")" ^^ {
    case "count" ~ "(" ~ inner ~ ")" => ValueAggregationItem(AggregationValue("count",inner))
  }


  def aggregationFunction: Parser[AggregationItem] = lowerCaseIdent ~ "(" ~ returnItem ~ ")" ^^ {
    case "min" ~ "(" ~ inner ~ ")" => Min(inner)
    case "max" ~ "(" ~ inner ~ ")" => Max(inner)
    case "sum" ~ "(" ~ inner ~ ")" => Sum(inner)
    case "avg" ~ "(" ~ inner ~ ")" => Avg(inner)
  }

  def countStar: Parser[AggregationItem] = ignoreCase("count") ~> "(*)" ^^ {
    case "(*)" => CountStar()
  }

  def aggregate:Parser[AggregationItem] = countStar | aggregationValueFunction | aggregationFunction
}





