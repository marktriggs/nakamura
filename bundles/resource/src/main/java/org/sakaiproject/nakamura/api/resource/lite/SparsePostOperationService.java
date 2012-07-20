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
package org.sakaiproject.nakamura.api.resource.lite;

import org.apache.sling.servlets.post.Modification;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;

import java.io.IOException;
import java.util.List;

/**
 * The SparsePostOperationService provides the logic required to perform SparsePostOperation
 * actions.
 */
public interface SparsePostOperationService {

  /**
   * Copy a tree of resources from the {@code from} location to the {@code to} location. This does
   * not support replace, therefore you should {@link #delete(String)} the {@code to} location
   * prior to copying if it exists.
   * 
   * @param session
   * @param from
   * @param to
   * @return
   * @throws StorageClientException
   * @throws AccessDeniedException
   * @throws IOException If there is an issue copying resource streams from the source to the
   * destination.
   */
  public List<Modification> copy(Session session, String from, String to)
      throws StorageClientException, AccessDeniedException, IOException;
  
  /**
   * Move a tree of resources from the {@code from} location to the {@code to} location.
   * 
   * @param session
   * @param from
   * @param to
   * @param replace If true, the move will be forced, even if the destination already exists.
   * @param keepDestinationHistory If true, the history of the destination will be replaced (if forced).
   * @return
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  public List<Modification> move(Session session, String from, String to, boolean replace,
      boolean keepDestinationHistory) throws StorageClientException, AccessDeniedException;

}
