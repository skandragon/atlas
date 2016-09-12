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

import java.util.concurrent.{ConcurrentHashMap, ScheduledThreadPoolExecutor, TimeUnit}

import com.netflix.atlas.core.index.QueryIndex
import com.netflix.frigga.Names

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.collection.JavaConverters._

case class AlertMap() {
  import AlertMap._

  private val knownExpressions = new ConcurrentHashMap[String, DataItem]().asScala
  private var queryIndex = QueryIndex.create[(String, DataItem)](Nil)
  private var interner = new ExpressionSplitter.QueryInterner()

  private var queryListChanged @volatile = false
  private var testMode = false

  val ex = new ScheduledThreadPoolExecutor(1)
  val task = new Runnable {
    def run() = {
      if (queryListChanged) {
        regenerateQueryIndex()
      }
    }
  }
  val f = ex.scheduleAtFixedRate(task, 1, 1, TimeUnit.SECONDS)

  def setTestMode() = { testMode = true }

  def addExpr(expression: ExpressionWithFrequency): Unit = {
    val splitter = ExpressionSplitter(interner)
    val dataExpressions = splitter.split(expression.expression)
    if (dataExpressions.nonEmpty) {
      val dataItem = DataItem(expression.expression, expression.frequency, dataExpressions)
      val replaced = knownExpressions.putIfAbsent(dataItem.toKey, dataItem)
      queryListChanged = replaced.isEmpty
      if (testMode)
        regenerateQueryIndex()
    }
  }

  def delExpr(expression: ExpressionWithFrequency): Unit = {
    val expr = expression.expression
    val freq = expression.frequency
    val key = s"$freq $expr"
    val removed = knownExpressions.remove(key)
    queryListChanged = removed.isDefined
    if (testMode)
      regenerateQueryIndex()
  }

  def expressionsForCluster(cluster: String): List[ReturnableExpression] = {
    val name = Names.parseName(cluster)
    var tags = Map("nf.cluster" -> name.getCluster)
    if (name.getApp != null)
      tags = tags + ("nf.app" -> name.getApp)
    if (name.getStack != null)
      tags = tags + ("nf.stack" -> name.getStack)
    val matches = queryIndex.matchingEntries(tags)
    val matchingDataExpressions = mutable.Map[String, Boolean]()
    val matchingDataItems = mutable.Map[DataItem, Boolean]()
    matches.foreach(m => {
      matchingDataExpressions(m._1) = true
      matchingDataItems(m._2) = true
    })

    val ret = matchingDataItems.map {case (item, flag) =>
      val dataExprs = item.containers.map(x => x.dataExpr).filter(x =>
        matchingDataExpressions.contains(x)
      )
      ReturnableExpression(item.expression, item.frequency, dataExprs)
    }
    ret.toList.distinct
  }

  def regenerateQueryIndex() = {
    queryListChanged = false
    val map = knownExpressions.flatMap { case (exprKey, data) =>
      data.containers.map(container =>
        QueryIndex.Entry(container.matchExpr, (container.dataExpr, data))
      )
    }.toList
    queryIndex = QueryIndex.create(map)
  }
}

object AlertMap {
  case class DataItem(expression: String, frequency: Long, containers: List[ExpressionSplitter.QueryContainer]) {
    def toKey = s"$frequency $expression"
  }

  case class ReturnableExpression(expression: String, frequency: Long, dataExpressions: List[String]) {
    override def toString = s"ReturnableExpression<$expression> <$frequency> <$dataExpressions>"
  }

  lazy val globalAlertMap = new AlertMap()
}
