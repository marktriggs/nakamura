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
package org.sakaiproject.nakamura.batch;

import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceParameter;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@SlingServlet(methods = {"GET"}, generateService = true, paths = {"/system/staticfiles"})
@ServiceDocumentation(name = "StaticFilesServlet", okForVersion = "1.2",
    shortDescription = "Bundles multiple static files into a single response.",
    description = "Allows multiple requests for static files to be executed in a single request. " +
        "Only suitable for static files that have the same content for all users.",
    bindings = @ServiceBinding(type = BindingType.PATH, bindings = "/system/staticfiles"),
    methods = @ServiceMethod(
        name = "GET",
        description = "Get multiple request responses into a single response. Only GET operations are allowed.",
        parameters = @ServiceParameter(
            name = "f",
            description = "A string (optionally multi-valued) representing a static file (or files) to get. <br />Example:" +
                "<pre>http://localhost:8080/system/staticfiles?f=/path/to/a/node&f=/path/to/another/node</pre>"
        ),
        response = {
            @ServiceResponse(code = 200,
                description = {
                    "All requests are successful. <br />",
                    "A JSON array is returned containing an object for each resource. Example:",
                    "<pre>[\n",
                    "{\"url\": \"/~admin/public/authprofile.json\",\n \"body\": \"{\"user\"...\",\n \"success\":true, \"status\": 200,\n \"headers\":{\"Content-Type\":\"application/json\"}\n} \n]</pre>"
                }
            ),
            @ServiceResponse(code = 400, description = "The 'f' parameter was malformed."),
            @ServiceResponse(code = 500, description = "Unable to get and parse all requests.")
        }))
public class StaticFilesServlet extends SlingSafeMethodsServlet {

  private static final long serialVersionUID = 419598445499567027L;

  private static final Logger LOGGER = LoggerFactory
      .getLogger(StaticFilesServlet.class);

  private static final String FILES_PARAMETER = "f";

  @Reference
  BatchHelper helper;

  @Override
  protected void doGet(SlingHttpServletRequest request,
                       SlingHttpServletResponse response) throws ServletException, IOException {
    RequestParameter[] filenames = request.getRequestParameters(FILES_PARAMETER);
    if (filenames == null || filenames.length == 0) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "You must pass the 'f' parameter");
      return;
    }
    JSONArray requests = new JSONArray();
    try {
      for (RequestParameter filename : filenames) {
        JSONObject req = new JSONObject();
        req.put("url", filename);
        req.put("method", "GET");
        requests.put(req);
      }
    } catch (JSONException e) {
      LOGGER.error("Got a JSON error building our own JSON object, should never happen", e);
    }
    helper.batchRequest(request, response, requests, false, false);
  }

}
