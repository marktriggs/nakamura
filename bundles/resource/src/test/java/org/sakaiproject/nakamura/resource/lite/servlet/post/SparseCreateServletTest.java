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
package org.sakaiproject.nakamura.resource.lite.servlet.post;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.http.HttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class SparseCreateServletTest {
  SparseCreateServlet sparseCreateServlet;
  @Mock
  SlingHttpServletRequest request;
  @Mock
  SlingHttpServletResponse response;
  @Mock
  Resource resource;
  @Mock
  ResourceResolver resourceResolver;

  @Before
  public void setUp() throws Exception {
    when(request.getResource()).thenReturn(resource);
    when(resource.getPath()).thenReturn("mHAY1acZec/id470170/id7577541/sakai2gradebook");
    when(request.getResourceResolver()).thenReturn(resourceResolver);
    sparseCreateServlet = new SparseCreateServlet();
  }

  /**
   * Test happy path where the non-existing resource is NOT a user or group management
   * path. {@link UserGroupManagementOptingServlet#accepts(SlingHttpServletRequest)}
   */
  @Test
  public void testAcceptsNonUserManagementPath() {
    final boolean accept = sparseCreateServlet.accepts(request);
    verify(request).getResource();
    verify(resource).getPath();
    assertFalse(accept);
  }

  /**
   * Test edge case where the resource path is null.
   * {@link UserGroupManagementOptingServlet#accepts(SlingHttpServletRequest)}
   */
  @Test
  public void testAcceptsNullPath() {
    when(resource.getPath()).thenReturn(null);

    final boolean accept = sparseCreateServlet.accepts(request);
    verify(request).getResource();
    verify(resource).getPath();
    assertFalse(accept);
  }

  /**
   * Test happy case where POST is to a non-existing resource under a user management
   * path. {@link UserGroupManagementOptingServlet#accepts(SlingHttpServletRequest)}
   */
  @Test
  public void testAcceptsUserManagementPath() {
    when(resource.getPath()).thenReturn("/system/userManager/user");

    final boolean accept = sparseCreateServlet.accepts(request);
    verify(request).getResource();
    verify(resource).getPath();
    assertTrue(accept);
  }

  /**
   * Test happy case where POST is to a non-existing resource under a group management
   * path. {@link UserGroupManagementOptingServlet#accepts(SlingHttpServletRequest)}
   */
  @Test
  public void testAcceptsGroupManagementPath() {
    when(resource.getPath()).thenReturn("/system/userManager/group");

    final boolean accept = sparseCreateServlet.accepts(request);
    verify(request).getResource();
    verify(resource).getPath();
    assertTrue(accept);
  }

  /**
   * Happy case where the servlet should always send a 404 error for a non-existing
   * resource under a user or group management path.
   * {@link UserGroupManagementOptingServlet#doPost(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoPost() throws Exception {
    when(resource.getPath()).thenReturn("/system/userManager/user");

    sparseCreateServlet.doPost(request, response);
    verify(response).sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());
  }
}
