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
package org.sakaiproject.nakamura.doc;

import org.apache.commons.io.IOUtils;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.DocumentationConstants;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.doc.servlet.DocumentationServlet;
import org.sakaiproject.nakamura.doc.servlet.ServletDocumentationRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@ServiceDocumentation(
    name = "General Documentation Servlet",
    okForVersion = "1.2",
    description = "Gets the documentation for servlets, proxies and search templates.",
    shortDescription = "Gets the documentation for servlets, proxies and search templates.",
    bindings = {
        @ServiceBinding(type = BindingType.PATH, bindings = {"system/doc"})
    },
    methods = {
        @ServiceMethod(
            name = "GET",
            description = "Get the documentation.",
            response = {
                @ServiceResponse(code = 200, description = "All processing finished successfully."),
                @ServiceResponse(code = 500, description = "Exception occurred during processing.")
            }
        )
    }
)
@SlingServlet(methods = {"GET"}, paths = {"/system/doc"})
public class GeneralDocumentationServlet extends SlingSafeMethodsServlet {

  private static final long serialVersionUID = 6866189047081436865L;

  @Reference
  protected transient ServletDocumentationRegistry servletDocumentationRegistry;

  @Reference
  DocumentationServlet documentationServlet;

  private byte[] style;

  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {

    RequestParameter p = request.getRequestParameter("p");
    if (p == null) {
      // Send out the categories.
      sendIndex(request, response);
    } else if ("style".equals(p.getString())) {
      // Send out the CSS file
      if (style == null) {
        InputStream in = null;
        try {
          in = this.getClass().getResourceAsStream("style.css");
          style = IOUtils.toByteArray(in);
        } finally {
          if (in != null) {
            in.close();
          }
        }
      }
      response.setContentType("text/css; charset=UTF-8");
      response.setContentLength(style.length);
      response.getOutputStream().write(style);
    }

  }

  private void sendIndex(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
    PrintWriter writer = response.getWriter();
    Session session = request.getResourceResolver().adaptTo(Session.class);
    response.setContentType("text/html");
    response.setCharacterEncoding("UTF-8");

    try {
      writer.append(DocumentationConstants.HTML_HEADER);

      writer.append("<ul class=\"topnav\">");
      writer.append("<li>Jump to: </li>");
      writer.append("<li><a href=\"#servlets\">Servlets</a></li>");
      writer.append("<li><a href=\"#Search nodes\">Search nodes</a></li>");
      writer.append("<li><a href=\"#Proxy nodes\">Proxy nodes</a></li>");
      writer.append("</ul>");

      // servlet docs
      documentationServlet.writeIndex(writer);

      // search node docs
      DocumentationWriter searchDocWriter = new DocumentationWriter("Search nodes", response
          .getWriter());
      String searchQuery = "//*[@sling:resourceType='sakai/solr-search' or @sling:resourceType='sakai/sparse-search'] order by @sakai:title";
      searchDocWriter.writeNodes(session, searchQuery, DocumentationConstants.PREFIX + "/search");

      // proxy node docs
      DocumentationWriter proxyDocWriter = new DocumentationWriter("Proxy nodes", response
          .getWriter());
      String proxyQuery = "//*[@sling:resourceType='sakai/proxy'] order by sakai:title";
      proxyDocWriter.writeNodes(session, proxyQuery, DocumentationConstants.PREFIX + "/proxy");

      writer.append(DocumentationConstants.HTML_FOOTER);

    } catch (ItemNotFoundException e) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
    } catch (RepositoryException e) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }


}
