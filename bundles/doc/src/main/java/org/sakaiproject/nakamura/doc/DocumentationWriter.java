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

import org.sakaiproject.nakamura.api.doc.DocumentationConstants;

import java.io.PrintWriter;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

public class DocumentationWriter {

  /**
   * The title that should be outputted as a h1 on the listing.
   */
  private String title;
  private PrintWriter writer;

  /**
   * @param title       The title that this writer should print.
   * @param printWriter The actual writer where content should be written to.
   */
  public DocumentationWriter(String title, PrintWriter printWriter) {
    this.title = title;
    this.setWriter(printWriter);
  }

  /**
   * Write out a list of nodes.
   *
   * @param session The current JCR session.
   * @param writer  The writer where the response should go to.
   * @param query   The query to use to retrieve the nodes.
   * @throws InvalidQueryException
   * @throws RepositoryException
   */
  public void writeNodes(Session session, String query, String servlet)
      throws RepositoryException {

    writer.append("<h1><a name=\"").append(getTitle()).append("\">").append(getTitle()).append("</a></h1>");
    writer.append("<table class=\"").append(
        DocumentationConstants.CSS_CLASS_DOCUMENTATION_LIST).append("\">");
    writer.append("<thead>");
    writer.append("<th>Name/Description</th>");
    writer.append("<th>Path</th>");
    writer.append("</thead>");
    writer.append("<tbody>");

    QueryManager qm = session.getWorkspace().getQueryManager();
    Query q = qm.createQuery(query, Query.XPATH);
    QueryResult result = q.execute();
    NodeIterator iterator = result.getNodes();
    while (iterator.hasNext()) {
      Node node = iterator.nextNode();
      NodeDocumentation doc = new NodeDocumentation(node);

      writer.append("<tr>");
      writer.append("<td><a href=\"");
      writer.append(servlet);
      writer.append("?p=");
      writer.append(doc.getPath());
      writer.append("\">");
      if (doc.getTitle() != null && !doc.getTitle().equals("")) {
        writer.append(doc.getTitle());
      } else {
        writer.append(doc.getPath());
      }
      writer.append("</a>");
      String desc = doc.getShortDescription();
      if (desc == null) {
        if (doc.getDescription() == null || doc.getDescription().length == 0) {
          writer.append("<p>No documentation available.</p>");
        } else {
          for (String d : doc.getDescription()) {
            writer.append("<p>").append(d).append("</p>");
          }
        }
      } else {
        writer.append("<p>").append(desc).append("</p>");
      }
      writer.append("</td>");

      writer.append("<td>");
      writer.append(doc.getPath());
      writer.append("</td>");

      writer.append("</tr>");

    }

    writer.append("</tbody></table>");

  }

  /**
   * Write info for a specific node.
   *
   * @param path    The path to the node.
   * @param session The current JCR session.
   * @param writer  The writer to send the response to.
   * @throws RepositoryException
   */
  public void writeSearchInfo(String path, Session session) throws RepositoryException {
    Node node = (Node) session.getItem(path);
    NodeDocumentation doc = new NodeDocumentation(node);
    writer.append(DocumentationConstants.HTML_HEADER);
    writer.append("<h1>");
    writer.append(doc.getTitle());
    writer.append("</h1>");
    doc.send(writer);
    writer.append(DocumentationConstants.HTML_FOOTER);
  }

  /**
   * @return the The title that should be outputted as a h1 on the listing.
   */
  public String getTitle() {
    return title;
  }

  /**
   * @param writer the writer to write the info to.
   */
  public void setWriter(PrintWriter writer) {
    this.writer = writer;
  }

  /**
   * @return the writer
   */
  public PrintWriter getWriter() {
    return writer;
  }
}
