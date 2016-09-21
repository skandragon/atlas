/*
 * Copyright 2014-2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.atlas.lwcapi

import akka.actor.ActorRef
import com.netflix.atlas.core.util.Interner
import com.typesafe.scalalogging.StrictLogging

import scala.collection.mutable

case class SubscriptionManagerImpl() extends SubscriptionManager with StrictLogging {
  import SubscriptionManager._

  private val exprToStream = mutable.Map[String, Set[String]]().withDefaultValue(Set())
  private val streamToExpr = mutable.Map[String, Set[String]]().withDefaultValue(Set())
  private val streamToEntry = mutable.Map[String, Entry]()
  private val exprToSplit = ExpressionDatabaseImpl()

  val interner = Interner.forStrings
  def intern(s: String): String = interner.intern(s)

  def register(streamId: String, ref: ActorRef, name: String): Unit = {
    streamToEntry(intern(streamId)) = Entry(intern(streamId), ref, name, System.currentTimeMillis())
  }

  def subscribe(streamId: String, expressionId: String): Unit = {
    streamToExpr(intern(streamId)) += expressionId
    exprToStream(intern(expressionId)) += streamId
  }

  def unsubscribe(streamId: String, expressionId: String): Unit = {
    streamToExpr(intern(streamId)) -= expressionId
    exprToStream(intern(expressionId)) -= streamId
  }

  def unsubscribeAll(streamId: String): Unit = {
    val ids = streamToExpr.remove(streamId)
    if (ids.isDefined)
      ids.get.foreach(k => exprToStream(k) -= streamId)
  }

  def getActorsForExpressionId(expressionId: String): Set[ActorRef] = {
    exprToStream(expressionId).map(streamId => streamToEntry(streamId).actorRef)
  }

  def getExpressionsForStreamId(streamId: String): Set[String] = {
    streamToExpr(streamId)
  }

  def entries: List[Entry] = synchronized { streamToEntry.values.toList }
}
