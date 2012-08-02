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
 * Clean up, reset or otherwise fix data after it has been copied.
 * </p>
 * <p>
 * This is often needed when content has a path reference to some other part of content
 * and after a copy the path reference becomes invalid.
 * </p>
 * <p>
 * A copy cleaner is intended to respond to the copying of content based on resource type.
 * </p>
 */
public interface CopyCleaner {

  /**
   * Clean some content after it has been copied. This method is guaranteed to receive the
   * {@code fromPath} and {@code toPath} paths for every individual node that is copied in a copy
   * operation. In the case of a recursive tree copy, all individual nodes that are copied therein
   * will be passed through this method.
   *
   * @param fromPath
   *          The path the data was copied from.
   * @param toPath
   *          The path to which the content was copied.
   * @param session
   *          The session of the current user
   * @return A list of modifications made when cleaning the moved data.
   */
  List<Modification> clean(String fromPath, String toPath, Session session)
      throws StorageClientException, AccessDeniedException;
}
