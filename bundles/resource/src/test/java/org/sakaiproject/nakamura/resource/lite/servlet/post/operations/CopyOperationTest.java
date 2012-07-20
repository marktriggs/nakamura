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
package org.sakaiproject.nakamura.resource.lite.servlet.post.operations;

import com.google.common.collect.ImmutableMap;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.resource.lite.SparsePostOperationServiceImpl;
import org.sakaiproject.nakamura.util.ContentUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

/**
 * A series of tests to verify the functionality of the CopyOperation.
 */
@RunWith(MockitoJUnitRunner.class)
public class CopyOperationTest {

  private static final String NAMESPACE = "/tests/org/sakaiproject/nakamura/resource/lite/" +
  		"servlet/post/operations/CopyOperationTest";
  
  @Mock
  SlingHttpServletRequest request;
  
  @Mock
  ResourceResolver resourceResolver;
  
  @Mock
  MockableJcrSessionSessionAdaptable sessionAdaptable;
  
  @Mock
  HtmlResponse response;
  
  /**
   * Verify that a StorageClientException is thrown when the source location of a copy operation
   * does not exist.
   * 
   * @throws Exception
   */
  @Test(expected=StorageClientException.class)
  public void testSourceNonExistent() throws Exception {
    String fromPath = namespace("testSourceNonExistent/from");
    String toPath = namespace("testSourceNonExistent/to");
    
    Session adminSession = createRepository().loginAdministrative();
    
    Mockito.when(request.getParameter(CopyOperation.PROP_DEST)).thenReturn(toPath);
    Mockito.when(request.getResourceResolver()).thenReturn(resourceResolver);
    Mockito.when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(sessionAdaptable);
    Mockito.when(sessionAdaptable.getSession()).thenReturn(adminSession);
    createCopyOperation().doRun(request, response, adminSession.getContentManager(),
        new LinkedList<Modification>(), fromPath);
  }
  
  /**
   * Verify that a storage client exception is thrown when trying to copy to a location that
   * already exists.
   * 
   * @throws Exception
   */
  @Test(expected=StorageClientException.class)
  public void testCannotReplace() throws Exception {
    String fromPath = namespace("testCannotReplace/from");
    String toPath = namespace("testCannotReplace/to");
    
    Session adminSession = createRepository().loginAdministrative();
    ContentManager contentManager = adminSession.getContentManager();
    contentManager.update(new Content(fromPath, ImmutableMap.<String, Object>of("prop", "source")));
    contentManager.update(new Content(toPath, ImmutableMap.<String, Object>of("prop", "destination")));
    
    Mockito.when(request.getParameter(CopyOperation.PROP_DEST)).thenReturn(toPath);
    Mockito.when(request.getResourceResolver()).thenReturn(resourceResolver);
    Mockito.when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(sessionAdaptable);
    Mockito.when(sessionAdaptable.getSession()).thenReturn(adminSession);
    
    createCopyOperation().doRun(request, response, contentManager, new LinkedList<Modification>(), fromPath);
  }
  
  /**
   * Verify that if content used to exist in a location but has since deleted, it is still possible
   * to copy to that location. This test exists because some storage implementations may still
   * retain a node in that location that is simply flagged as deleted. It is important to make sure
   * that that node is reclaimed.
   * 
   * @throws Exception
   */
  @Test
  public void testCanReplaceDeletedNode() throws Exception {
    String fromPath = namespace("testCanReplaceDeletedNode/from");
    String toPath = namespace("testCanReplaceDeletedNode/to");
    
    Session adminSession = createRepository().loginAdministrative();
    ContentManager contentManager = adminSession.getContentManager();
    contentManager.update(new Content(fromPath, ImmutableMap.<String, Object>of("prop", "source")));
    contentManager.update(new Content(toPath, ImmutableMap.<String, Object>of("prop", "destination")));
    contentManager.delete(toPath);
    
    Mockito.when(request.getParameter(CopyOperation.PROP_DEST)).thenReturn(toPath);
    Mockito.when(request.getResourceResolver()).thenReturn(resourceResolver);
    Mockito.when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(sessionAdaptable);
    Mockito.when(sessionAdaptable.getSession()).thenReturn(adminSession);
    
    try {
      createCopyOperation().doRun(request, response, contentManager, new LinkedList<Modification>(), fromPath);
    } catch (StorageClientException e) {
      Assert.fail("Was not able to copy into a node that was previously deleted.");
    }
    
  }
  
  /**
   * Verify a trivial copy operation, where simply one node with one attribute is copied from the
   * source to the destination.
   * 
   * @throws Exception
   */
  @Test
  public void testCopySimple() throws Exception {
    String fromPath = namespace("testCopySimple/from");
    String toPath = namespace("testCopySimple/to");
    
    Session adminSession = createRepository().loginAdministrative();
    ContentManager contentManager = adminSession.getContentManager();
    contentManager.update(new Content(fromPath, ImmutableMap.<String, Object>of("prop", "source")));
    Mockito.when(request.getParameter(CopyOperation.PROP_DEST)).thenReturn(toPath);
    Mockito.when(request.getResourceResolver()).thenReturn(resourceResolver);
    Mockito.when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(sessionAdaptable);
    Mockito.when(sessionAdaptable.getSession()).thenReturn(adminSession);
    createCopyOperation().doRun(request, response, contentManager, new LinkedList<Modification>(), fromPath);
    
    //first verify the source
    Content c = contentManager.get(fromPath);
    Assert.assertNotNull("Source content was deleted.", c);
    Assert.assertEquals("Source property was changed", "source", c.getProperty("prop"));
    
    //then verify the destination
    c = contentManager.get(toPath);
    Assert.assertNotNull("Content did not copy", c);
    Assert.assertEquals("source", c.getProperty("prop"));
  }
  
  /**
   * Verify that a node with a stream body copies properly when using the CopyOperation.
   * 
   * @throws Exception
   */
  @Test
  public void testCopySimpleStream() throws Exception {
    String fromPath = namespace("testCopySimpleStream/from");
    String toPath = namespace("testCopySimpleStream/to");
    
    Session adminSession = createRepository().loginAdministrative();
    ContentManager contentManager = adminSession.getContentManager();
    contentManager.update(new Content(fromPath, ImmutableMap.<String, Object>of("prop", "source")));
    
    InputStream in = new ByteArrayInputStream("source".getBytes("UTF-8"));
    contentManager.writeBody(fromPath, in);
    in.close();
    
    Mockito.when(request.getParameter(CopyOperation.PROP_DEST)).thenReturn(toPath);
    Mockito.when(request.getResourceResolver()).thenReturn(resourceResolver);
    Mockito.when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(sessionAdaptable);
    Mockito.when(sessionAdaptable.getSession()).thenReturn(adminSession);
    createCopyOperation().doRun(request, response, contentManager, new LinkedList<Modification>(), fromPath);
    
    //first verify the source
    Content c = contentManager.get(fromPath);
    Assert.assertNotNull("Source was deleted", c);
    InputStream stream = contentManager.getInputStream(c.getPath());
    Assert.assertNotNull("Stream for source content was null", stream);
    Assert.assertEquals("Source content stream was garbled up.", "source", IOUtils.toString(stream, "UTF-8"));
    stream.close();
    Assert.assertEquals("source", c.getProperty("prop"));
    
    //verify destination
    c = contentManager.get(toPath);
    Assert.assertNotNull("Source was not copied at all", c);
    stream = contentManager.getInputStream(c.getPath());
    Assert.assertNotNull("Stream for target content was null", stream);
    Assert.assertEquals("Destination content stream was garbled up.", "source", IOUtils.toString(stream, "UTF-8"));
    stream.close();
    Assert.assertEquals("source", c.getProperty("prop"));
  }

  /**
   * Verify that a complex tree of content modeled after a SakaiDoc copies properly.
   * 
   * @throws Exception
   */
  @Test
  public void testCopyTree() throws Exception {
    String fromPath = namespace("testCopyTree/from");
    String toPath = namespace("testCopyTree/to");
    Session adminSession = createRepository().loginAdministrative();
    ContentManager contentManager = adminSession.getContentManager();
    ContentUtils.createContentFromJsonResource(contentManager, fromPath, getClassLoader(),
        "org/sakaiproject/nakamura/resource/lite/servlet/post/operations/CopyOperationTest1.json");
    
    Mockito.when(request.getParameter(CopyOperation.PROP_DEST)).thenReturn(toPath);
    Mockito.when(request.getResourceResolver()).thenReturn(resourceResolver);
    Mockito.when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(sessionAdaptable);
    Mockito.when(sessionAdaptable.getSession()).thenReturn(adminSession);
    createCopyOperation().doRun(request, response, contentManager, new LinkedList<Modification>(), fromPath);
    
    String pagePath = StorageClientUtils.newPath(toPath, "id4962581");
    String editorPath = StorageClientUtils.newPath(pagePath, "editor");
    String countsPath = StorageClientUtils.newPath(pagePath, "counts");
    
    Content
      root = contentManager.get(toPath),
      page = contentManager.get(pagePath),
      editor = contentManager.get(editorPath),
      counts = contentManager.get(countsPath);
    
    Assert.assertNotNull(root);
    Assert.assertNotNull(page);
    Assert.assertNotNull(editor);
    Assert.assertNotNull(counts);
  }
  
  /**
   * An internal test to verify the functionality of the copyTree functionality.
   * @throws Exception
   */
  @Test
  public void testInternalCreateTree() throws Exception {
    String path = namespace("testInternalCreateTree/content");
    Session adminSession = createRepository().loginAdministrative();
    ContentManager contentManager = adminSession.getContentManager();
    ContentUtils.createContentFromJsonResource(contentManager, path, getClassLoader(),
        "org/sakaiproject/nakamura/resource/lite/servlet/post/operations/CopyOperationTest1.json");
    
    String pagePath = StorageClientUtils.newPath(path, "id4962581");
    String editorPath = StorageClientUtils.newPath(pagePath, "editor");
    String countsPath = StorageClientUtils.newPath(pagePath, "counts");
    
    Content
      root = contentManager.get(path),
      page = contentManager.get(pagePath),
      editor = contentManager.get(editorPath),
      counts = contentManager.get(countsPath);
    
    Assert.assertNotNull(root);
    Assert.assertNotNull(page);
    Assert.assertNotNull(editor);
    Assert.assertNotNull(counts);
        
  }

  private ClassLoader getClassLoader() {
    return Thread.currentThread().getContextClassLoader();
  }
  
  private CopyOperation createCopyOperation() {
    CopyOperation copyOperation = new CopyOperation();
    copyOperation.sparsePostOperationService = new SparsePostOperationServiceImpl();
    return copyOperation;
  }
  
  private Repository createRepository() throws ClientPoolException, StorageClientException, AccessDeniedException, ClassNotFoundException, IOException {
    return (Repository) new BaseMemoryRepository().getRepository();
  }
  
  private String namespace(String path) {
    return String.format("%s/%s", NAMESPACE, path);
  }

}
