/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.sakaiproject.nakamura.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to delay execution.
 */
class Slowdown {

  private long delay;
  private long lastTime = -1;

  private static final Logger LOGGER = LoggerFactory
    .getLogger(Slowdown.class);


  public Slowdown(long delay) {
    this.delay = delay;
  }


  public void sleep() {
    long now = System.currentTimeMillis();

    if (lastTime > 0) {
      long elapsed = (now - lastTime);
      if (elapsed < delay) {
        try {
          LOGGER.info("Waiting...");
          Thread.sleep(delay - elapsed);
        } catch (InterruptedException e) {}
      }
    } else {
      lastTime = now;
    }
  }
}
