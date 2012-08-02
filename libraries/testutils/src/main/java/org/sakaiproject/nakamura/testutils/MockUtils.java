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
package org.sakaiproject.nakamura.testutils;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.mockito.Mockito;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;

/**
 * Utility methods to help mock common complex objects.
 */
public class MockUtils {

  /**
   * Create a mock {@link SlingHttpServletRequest} instance that is bound to the given
   * {@code session}.
   * 
   * @param session
   * @return
   */
  public static SlingHttpServletRequest mockRequestWithSession(Session session) {
    SlingHttpServletRequest request = Mockito.mock(SlingHttpServletRequest.class);
    ResourceResolver resourceResolver = Mockito.mock(ResourceResolver.class);
    javax.jcr.Session jcrSession = Mockito.mock(javax.jcr.Session.class, Mockito.withSettings()
        .extraInterfaces(SessionAdaptable.class));
    
    Mockito.when(request.getResourceResolver()).thenReturn(resourceResolver);
    Mockito.when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);
    Mockito.when(((SessionAdaptable) jcrSession).getSession()).thenReturn(session);
    
    return request;
  }

}
