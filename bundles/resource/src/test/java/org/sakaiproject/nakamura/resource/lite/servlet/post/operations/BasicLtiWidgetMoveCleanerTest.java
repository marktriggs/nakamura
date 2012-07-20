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

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;

import java.io.IOException;

/**
 * Verify the functionality of the BasicLtiWidgetMoveCleaner. This is very similar to the
 * BasicLtiWidgetCopyCleanerTest, so it may simply extend it and perform whatever additional
 * assertions are necessary.
 */
@RunWith(value = MockitoJUnitRunner.class)
public class BasicLtiWidgetMoveCleanerTest extends BasicLtiWidgetCopyCleanerTest {

  private static final String NAMESPACE = "/tests/org/sakaiproject/nakamura/resource/lite/" +
      "servlet/post/operations/BasicLtiWidgetMoveCleanerTest";
  
  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.resource.lite.servlet.post.operations.BasicLtiWidgetCopyCleanerTest#testNothingToClean()
   */
  @Test
  @Override
  public void testNothingToClean() throws Exception {
    super.testNothingToClean();
  }

  /**
   * Verify that the protected ltiKeys have been copied to the destination location, as well
   * as being deleted from the source location. doTestProtectedKeysAreCopied does the former,
   * so we just need to verify the source is removed.
   * 
   * @throws Exception
   */
  @Test
  public void testProtectedKeysAreMoved() throws Exception {
    AbstractBasicLtiCleaner cleaner = createCleaner();
    
    doTestProtectedKeysAreCopied(cleaner);
    
    // additionally, verify the keys are removed after being copied
    String ltiKeysFrom = namespace("testProtectedKeysAreCopied/from/id2207414/basiclti/ltiKeys");
    Assert.assertFalse("Source ltiKeys still exist after move cleaner",
        cleaner.getRepository().loginAdministrative().getContentManager().exists(ltiKeysFrom));
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.resource.lite.servlet.post.operations.BasicLtiWidgetCopyCleanerTest#createCleaner()
   */
  @Override
  protected AbstractBasicLtiCleaner createCleaner() throws ClientPoolException,
      StorageClientException, AccessDeniedException, ClassNotFoundException, IOException {
    BasicLtiWidgetMoveCleaner cleaner = new BasicLtiWidgetMoveCleaner();
    cleaner.repository = createRepository();
    return cleaner;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.resource.lite.servlet.post.operations.BasicLtiWidgetCopyCleanerTest#namespace(java.lang.String)
   */
  @Override
  protected String namespace(String path) {
    return String.format("%s/%s", NAMESPACE, path);
  }

}
