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

import com.netflix.atlas.lwcapi.RegisterApi.RegisterRequest
import org.scalatest.FunSuite


class RegisterApiJsonSuite extends FunSuite {
  test("default is applied") {
  assert(ExpressionWithFrequency("this") === ExpressionWithFrequency("this", ApiSettings.defaultFrequency))
  }

  test("encode and decode batch") {
    val original: List[ExpressionWithFrequency] = List(
      ExpressionWithFrequency("this", 1234),
      ExpressionWithFrequency("that", 4321)
    )
    val decoded = RegisterApi.RegisterRequest(RegisterRequest(original).toJson)
    assert(original === decoded.expressions)
  }

  test("decode empty list") {
    val decoded = RegisterApi.RegisterRequest("[]")
    assert(decoded.expressions.size === 0)
  }

  test("decode bad expression") {
    intercept[IllegalArgumentException] {
      RegisterApi.RegisterRequest("[{}]")
    }
  }

  test("decode list") {
    val decoded = RegisterApi.RegisterRequest("""
      [
        { "expression": "this", "frequency": 12345 },
        { "expression": "that", "frequency": 54321 },
        { "expression": "those" }
      ]
      """)
    assert(decoded.expressions.size === 3)
  }

  test("decode batch bad object") {
    intercept[IllegalArgumentException] {
      RegisterApi.RegisterRequest("""{"foo":"bar"}""")
    }
  }
}
