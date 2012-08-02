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
package org.sakaiproject.nakamura.atlasanalytics;


import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Dictionary;
import javax.servlet.ServletException;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Component(metatype = true, policy = ConfigurationPolicy.REQUIRE)
@SlingServlet(methods = "GET", paths = "/system/atlasanalytics", generateComponent = false)
public class AnalyticsServlet extends SlingAllMethodsServlet {
  private Logger LOGGER = LoggerFactory.getLogger(AnalyticsServlet.class);
  private Analytics analytics;

  @Property(description = "The directory to store analytics log files in")
  public static final String OUTPUT_DIR = "analyticsOutputDir";

  @Property(description = "The timestamp format to use in log filenames", value = "yyyy-MM-dd")
  public static final String TIMESTAMP_FORMAT = "timestampFormat";

  @Property(description = "The format string to use for log filenames (%s is the timestamp)",
    value = "%s-analytics.log")
  public static final String FILENAME_FORMAT = "filenameFormat";


  protected void activate(ComponentContext context) {
    Dictionary props = context.getProperties();

    String analyticsOutputDir = PropertiesUtil.toString(props.get(OUTPUT_DIR), "").trim();

    if ("".equals(analyticsOutputDir)) {
      throw new ComponentException("You need to set " + OUTPUT_DIR);
    }

    String timestampFormat = PropertiesUtil.toString(props.get(TIMESTAMP_FORMAT), "yyyy-MM-dd").trim();
    String filenameFormat = PropertiesUtil.toString(props.get(FILENAME_FORMAT), "%s-analytics.log");

    analytics = new Analytics(new File(analyticsOutputDir), timestampFormat, filenameFormat);
  }


  protected void deactivate(ComponentContext context) {
    if (analytics != null) {
      analytics.close();
    }
  }


  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
    throws ServletException, IOException {
    String data = request.getParameter("data");

    if (data != null && analytics != null) {
      analytics.record(request.getRemoteUser(), data);
    }

    response.setStatus(SlingHttpServletResponse.SC_OK);
  }
}

