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
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.util;

import org.sakaiproject.nakamura.api.lite.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for interacting with SparseMapContent
 */
public class SparseUtils {

  private final static Logger LOGGER = LoggerFactory.getLogger(SparseUtils.class);

  /**
   * Log out of the given session, swallowing any tossed exceptions.
   * 
   * @param session
   */
  public static void logoutQuietly(Session session) {
    try {
      session.logout();
    } catch (Exception e) {
      LOGGER.warn("Error logging out of session, but swallowing exception.", e);
    }
  }
  
}
