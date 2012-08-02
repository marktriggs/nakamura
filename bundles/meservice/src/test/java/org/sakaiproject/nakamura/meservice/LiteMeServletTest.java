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
package org.sakaiproject.nakamura.meservice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.connections.ConnectionManager;
import org.sakaiproject.nakamura.api.message.LiteMessagingService;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.user.BasicUserInfoService;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class LiteMeServletTest {
  LiteMeServlet meServlet;
  @Mock
  LiteMessagingService messagingService;

  @Mock
  ConnectionManager connectionManager;

  @Mock
  SolrSearchServiceFactory searchServiceFactory;

  @Mock
  BasicUserInfoService basicUserInfoService;

  @Before
  public void setUp() {
    meServlet = new LiteMeServlet();
    meServlet.messagingService = messagingService;
    meServlet.connectionManager = connectionManager;
    meServlet.searchServiceFactory = searchServiceFactory;
    meServlet.basicUserInfoService = basicUserInfoService;
    
  }

  @Test
  public void testNothingForNow() {
    // I assume someone will add test coverage in the future; otherwise I would just
    // remove the entire class.
  }

}
