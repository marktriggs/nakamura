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
package org.sakaiproject.nakamura.basiclti;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.basiclti.VirtualToolDataProvider;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;

@RunWith(MockitoJUnitRunner.class)
public class BasicLTICLEToolPropertyServletTest {
  BasicLTICLEToolPropertyServlet basicLTICLEToolPropertyServlet;
  VirtualToolDataProvider virtualToolDataProvider;
  @Mock
  SlingHttpServletRequest request;
  @Mock
  SlingHttpServletResponse response;
  @Mock
  PrintWriter printWriter;

  @Before
  public void setUp() throws IOException {
    basicLTICLEToolPropertyServlet = new BasicLTICLEToolPropertyServlet();
    virtualToolDataProvider = new CLEVirtualToolDataProvider();
    basicLTICLEToolPropertyServlet.virtualToolDataProvider = virtualToolDataProvider;

    when(response.getWriter()).thenReturn(printWriter);
  }

  @Test
  public void testDoGet() throws ServletException, IOException {
    basicLTICLEToolPropertyServlet.doGet(request, response);
    verify(printWriter, atLeastOnce()).write(anyString());
    verify(printWriter, times(1)).write(contains("toolList"));
    verify(printWriter, times(1)).write(
        contains(CLEVirtualToolDataProvider.DEFAULT_TOOL_LIST[0]));
    verify(printWriter, times(1)).write(
        contains(CLEVirtualToolDataProvider.DEFAULT_TOOL_LIST[1]));
    verify(printWriter, times(1)).write(
        contains(CLEVirtualToolDataProvider.DEFAULT_TOOL_LIST[2]));
  }

}
