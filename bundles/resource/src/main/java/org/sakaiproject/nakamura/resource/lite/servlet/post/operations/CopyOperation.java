/**
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
package org.sakaiproject.nakamura.resource.lite.servlet.post.operations;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.lite.AbstractSparsePostOperation;
import org.sakaiproject.nakamura.api.resource.lite.SparsePostOperation;
import org.sakaiproject.nakamura.api.resource.lite.SparsePostOperationService;
import org.sakaiproject.nakamura.util.PathUtils;

import java.io.IOException;
import java.util.List;

/**
 * Copy operation copies a tree of data from the target POST path to the path specified by
 * request parameter {@link #PROP_DEST}.
 */
@Component
@Service(value = SparsePostOperation.class)
@Property(name = "sling.post.operation", value = "copy")
public class CopyOperation extends AbstractSparsePostOperation {
  public final static String PROP_DEST = ":dest";
  
  @Reference
  protected SparsePostOperationService sparsePostOperationService;
  
  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.resource.lite.AbstractSparsePostOperation#doRun(org.apache.sling.api.SlingHttpServletRequest, org.apache.sling.api.servlets.HtmlResponse, org.sakaiproject.nakamura.api.lite.content.ContentManager, java.util.List, java.lang.String)
   */
  @Override
  protected void doRun(SlingHttpServletRequest request, HtmlResponse response,
      ContentManager contentManager, List<Modification> changes, String contentPath)
      throws StorageClientException, AccessDeniedException, IOException {
    String from = contentPath;
    String to = getDestination(request);
    
    if (to == null) {
      throw new IllegalArgumentException(String.format("Must supply parameter %s to set copy destination.", PROP_DEST));
    }
    
    changes.addAll(sparsePostOperationService.copy(adaptToSession(request), from, to));
  }

  /**
   * Get the destination path from the {@code request}.
   * 
   * @param from
   * @param request
   * @return
   */
  private String getDestination(SlingHttpServletRequest request) {
    String to = request.getParameter(PROP_DEST);
    if (to == null) {
      return null;
    }
    return PathUtils.toUserContentPath(to);
  }

}
