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

import akka.actor.Actor
import com.netflix.atlas.json.{Json, JsonSupport}
import com.netflix.spectator.api.Spectator
import com.redis._
import com.typesafe.scalalogging.StrictLogging

import scala.util.control.NonFatal

class ExpressionDatabaseActor extends Actor with StrictLogging {
  import ExpressionDatabaseActor._

  private val channel = "expressions"
  private var subClient: RedisClient = _
  private var pubClient: RedisClient = _

  private val registry = Spectator.globalRegistry()
  private val updatesId = registry.createId("atlas.lwcapi.db.updates")
  private val connectsId = registry.createId("atlas.lwcapi.redis.connects")
  private val connectRetriesId = registry.createId("atlas.lwcapi.redis.connectRetries")

  private val uuid = GlobalUUID.get

  private val ttl = ApiSettings.redisTTL
  private val host = ApiSettings.redisHost
  private val port = ApiSettings.redisPort
  private val keyPrefix = ApiSettings.redisKeyPrefix

  restartPubsub()

  def restartPubsub(): Unit = {
    subClient = connect("subscribe").get
    pubClient = connect("publish").get
    logger.info("Pubsub restarted!")
    subClient.subscribe(channel)(redisCallback)
  }

  def redisCallback(pubsub: PubSubMessage) = pubsub match {
    case S(chan, cnt) => logger.info(s"Subscribe to $chan, sub count is now $cnt")
    case U(chan, cnt) => logger.info(s"Unsubscribe from $chan, sub count is now $cnt")
    case E(exc) =>
      logger.error("redis pubsub: exception caught", exc)
      restartPubsub()
    case M(chan, msg) =>
      val request = RedisRequest.fromJson(msg)
      if (request.uuid != uuid) {
        val action = request.action
        val expression = request.expression
        logger.debug(s"PubSub received $action for $expression")
        action match {
          case "add" =>
            increment_counter("remote", "add")
            AlertMap.globalAlertMap.addExpr(expression)
          case "delete" =>
            increment_counter("remote", "delete")
            AlertMap.globalAlertMap.delExpr(expression)
        }
      }
  }

  def increment_counter(source: String, action: String) = {
    registry.counter(updatesId.withTag("source", source).withTag("action", action)).increment()
  }

  def receive = {
    case Publish(expression) =>
      logger.debug(s"PubSub add for $expression")
      val key = AlertMap.globalAlertMap.addExpr(expression)
      recordUpdate(expression, key, "add")
    case Unpublish(expression) =>
      logger.debug(s"PubSub delete for $expression")
      val key = AlertMap.globalAlertMap.delExpr(expression)
      recordUpdate(expression, key, "delete")
  }

  def recordUpdate(expression: ExpressionWithFrequency, key: String, action: String) = {
    val json = RedisRequest(expression, uuid, action).toJson
    pubClient.publish(channel, json)
    if (action != "delete") {
      val keyname = s"$keyPrefix.$key"
      val count = pubClient.psetex(keyname, ttl, json)
    }
    increment_counter("local", action)
  }

  private def connect(source: String): Option[RedisClient] = {
    var tries = 1
    var success = false
    while (!success) {
      try {
        logger.info(s"Connecting to redis($source), tries $tries")

        registry.counter(connectsId.withTag("source", source)).increment()
        if (tries != 1) {
          registry.counter(connectRetriesId.withTag("source", source)).increment()
        }
        Thread.sleep(1000)
        val client = new RedisClient(host, port)
        return Some(client)
      } catch {
        case NonFatal(ex) =>
          logger.warn("Connection error: " + ex.getMessage)
          tries += 1
      }
    }
    None
  }
}

object ExpressionDatabaseActor {
  case class RedisRequest(expression: ExpressionWithFrequency, uuid: String, action: String) extends JsonSupport

  object RedisRequest {
    def fromJson(json: String): RedisRequest = Json.decode[RedisRequest](json)
  }

  case class Publish(expression: ExpressionWithFrequency) extends JsonSupport
  case class Unpublish(expression: ExpressionWithFrequency) extends JsonSupport
}
