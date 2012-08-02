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
package org.sakaiproject.nakamura.api.resource;

import org.apache.sling.servlets.post.Modification;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;

import java.util.List;

/**
 * <p>
 * Clean up, reset or otherwise fix data after it has been moved.
 * </p>
 * <p>
 * This is often needed when content has a path reference to some other part of content
 * and after a move the path reference becomes invalid.
 * </p>
 * <p>
 * A move cleaner is intended to respond to the moving of content based on resource type.
 * </p>
 */
public interface MoveCleaner {

  /**
   * Clean some content after it has been moved. This method is guaranteed to receive the
   * {@code fromPath} and {@code toPath} paths for every individual node that is moved in a move
   * operation. In the case of a recursive tree move, all individual nodes that are moved therein
   * will be passed through this method.
   *
   * @param fromPath
   *          The path the data was moved from.
   * @param toContent
   *          The content where the data now lives.
   * @param session
   *          The session of the user performing the operation
   * @return A list of modifications made when cleaning the moved data.
   */
  List<Modification> clean(String fromPath, String toPath, Session session)
      throws StorageClientException, AccessDeniedException;
}
