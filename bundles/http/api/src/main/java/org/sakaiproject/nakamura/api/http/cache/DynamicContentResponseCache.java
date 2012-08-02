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

package org.sakaiproject.nakamura.api.http.cache;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Use this to cache http response that are for dynamic, user-specific content.
 * The caching mechanism is ETag validation. If the client presents a fresh ETag
 * in the http If-None-Match header, the server will return http 304 Not Modified.
 */
public interface DynamicContentResponseCache {

  /**
   * Record this response in the cache and write the ETag to the response.
   * Call this method just before sending out a response that you want to be cached. This method
   * modifies the passed response to set the ETag header.
   *
   * @param cacheCategory The application category of the cache, eg. "User Info"
   * @param request       The request to use as the basis for a cache key
   * @param response      The response to cache
   */
  void recordResponse(String cacheCategory, HttpServletRequest request, HttpServletResponse response);

  /**
   * Invalidates all cache entries for a particular user in a particular cache category.
   *
   * @param cacheCategory The application category of the cache, eg. "User Info"
   * @param userID        The userID to invalidate.
   */
  void invalidate(String cacheCategory, String userID);

  /**
   * Returns true and sets http status 304 on the response if the client has presented a fresh
   * ETag on the request. Examines the request's If-None-Match header for an ETag that we have
   * cached previously. This method modifies the passed response if a fresh ETag is presented.
   *
   * @param cacheCategory The application category of the cache, eg. "User Info"
   * @param request       The request to use as the basis for a cache key
   * @param response      The response to cache. If it's a cache hit, response status will be set to 304.
   * @return True if response was set to 304 and the user has hit the cache; false otherwise.
   */
  boolean send304WhenClientHasFreshETag(String cacheCategory, HttpServletRequest request,
                                        HttpServletResponse response);

  /**
   * Drop all entries from the cache.
   */
  void clear();

}
