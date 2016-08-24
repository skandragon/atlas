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

case class ExpressionWithFrequency (expression: String, private var _frequency: Long = 0) {
  if (expression == null || expression.isEmpty) {
    throw new IllegalArgumentException("expression is empty or null")
  }

  _frequency = clampFrequency(_frequency)

  def frequency: Long = _frequency
  private def frequency_=(freq: Long) { _frequency = clampFrequency(freq) }

  private def clampFrequency(value: Long): Long = {
    if (value <= 0) ApiSettings.defaultFrequency else value
  }
}