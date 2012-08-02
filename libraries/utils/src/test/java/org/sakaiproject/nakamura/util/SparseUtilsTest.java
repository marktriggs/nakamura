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
package org.sakaiproject.nakamura.util;

import org.junit.Assert;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.lite.RepositoryImpl;

import java.io.IOException;

/**
 * Test class for testing operations in {@link SparseUtils}.
 */
public class SparseUtilsTest {

  /**
   * Verify that the session is successfully logged out under normal circumstances.
   * 
   * @throws ClientPoolException
   * @throws StorageClientException
   * @throws AccessDeniedException
   * @throws ClassNotFoundException
   * @throws IOException
   */
  @Test
  public void testLogoutQuietlySuccessful() throws ClientPoolException, StorageClientException, AccessDeniedException, ClassNotFoundException, IOException {
    RepositoryImpl repo = new BaseMemoryRepository().getRepository();
    Session session = repo.loginAdministrative();
    SparseUtils.logoutQuietly(session);
    try {
      session.getContentManager();
      Assert.fail("Session does not appear to be logged out.");
    } catch (StorageClientException e) {}
  }
  
  /**
   * Verify that if the session is null, there is no exception thrown.
   */
  @Test
  public void testLogoutQuietlySwallowNull() {
    SparseUtils.logoutQuietly(null);
  }
  
  /**
   * Verify that if the session is closed twice, there is no exception thrown.
   * 
   * @throws ClientPoolException
   * @throws StorageClientException
   * @throws AccessDeniedException
   * @throws ClassNotFoundException
   * @throws IOException
   */
  @Test
  public void testLogoutQuietlySwallowAlreadyClosed() throws ClientPoolException, StorageClientException, AccessDeniedException, ClassNotFoundException, IOException {
    RepositoryImpl repo = new BaseMemoryRepository().getRepository();
    Session session = repo.loginAdministrative();
    SparseUtils.logoutQuietly(session);
    SparseUtils.logoutQuietly(session);
  }

  
}
