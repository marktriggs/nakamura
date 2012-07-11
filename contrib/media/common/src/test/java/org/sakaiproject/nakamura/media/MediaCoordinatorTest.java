package org.sakaiproject.nakamura.media;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.lang.reflect.Field;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.sakaiproject.nakamura.api.media.MediaService;
import org.sakaiproject.nakamura.api.media.ErrorHandler;
import org.sakaiproject.nakamura.api.media.MediaStatus;
import org.sakaiproject.nakamura.api.media.MediaServiceException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.media.MediaCoordinator;
import org.sakaiproject.nakamura.media.MediaUtils;

import org.sakaiproject.nakamura.api.lite.Session;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.fail;
import com.google.common.collect.ImmutableMap;

import org.sakaiproject.nakamura.media.util.DurableQueue;


public class MediaCoordinatorTest {

  private int POLL_MS = 100;
  private int MAX_WAIT_MS = 10000;

  private class MockMediaService implements MediaService {

    public List<Map<String, String>> created = Collections.synchronizedList(new ArrayList<Map<String, String>>());
    public List<Map<String, String>> updated = Collections.synchronizedList(new ArrayList<Map<String, String>>());

    public boolean alwaysFail = false;
    public boolean failOnNextCreate = false;

    volatile public int failCount = 0;

    public String createMedia(InputStream media, String title, String description, String extension, String[] tags)
      throws MediaServiceException {

      if (failOnNextCreate) {
        failOnNextCreate = false;
        failCount++;
        throw new RuntimeException("Transient failure!");
      }

      if (alwaysFail) {
        failCount++;
        throw new RuntimeException("Permanent failure!");
      }


      created.add(ImmutableMap.of("title", title,
                                  "description", description,
                                  "extension", extension,
                                  "tags", Arrays.asList(tags).toString()));

      return String.valueOf(System.currentTimeMillis());
    }


    public String updateMedia(String id, String title, String description, String[] tags)
      throws MediaServiceException {

      updated.add(ImmutableMap.of("id", id,
                                  "title", title,
                                  "description", description,
                                  "tags", Arrays.asList(tags).toString()));

      return id;
    }

    public MediaStatus getStatus(String id) throws MediaServiceException, IOException {
      return new MediaStatus() {
        public boolean isReady() {
          return true;
        }

        public boolean isProcessing() {
          return false;
        }

        public boolean isError() {
          return false;
        }
      };
    }

    public String getPlayerFragment(String id) {
      return "[the player]";
    }

    public String getMimeType() {
      return "application/x-media-testsuite";
    }

    public boolean acceptsFileType(String mimeType, String extension) {
      // Sure!
      return true;
    }
  }


  private Repository repository;
  private Session adminSession;
  private ContentManager cm;

  MockMediaService mediaService;
  MediaCoordinator mc;

  DurableQueue queue;

  @Before
  public void setUp() throws Exception {
    repository = new BaseMemoryRepository().getRepository();
    adminSession = repository.loginAdministrative();
    cm = adminSession.getContentManager();

    File tempfile = File.createTempFile("durablequeue", "dat");
    tempfile.deleteOnExit();

    queue = new DurableQueue(tempfile.getPath());

    mediaService = new MockMediaService();
    mc = new MediaCoordinator(queue, repository, mediaService,
        MediaListenerImpl.MAX_RETRIES_DEFAULT, MediaListenerImpl.RETRY_MS_DEFAULT,
        MediaListenerImpl.WORKER_COUNT_DEFAULT, 500);

    mc.start();
  }


  private void contentUpdated(String pid) {
    try {
      queue.add(pid);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }


  private void saveVersion(String path, String mimeType, String extension, String title,
                               String description, String ... tags)
    throws Exception {
    Map<String, Object> props = new HashMap<String, Object>();

    props.put("_path", path);
    props.put("_mimeType", mimeType);
    props.put("media:extension", extension);
    props.put("sakai:pooled-content-file-name", title);
    props.put("sakai:description", description);

    cm.update(new Content(path, props));
    cm.saveVersion(path);
  }


  @Test
  public void testSimpleVideo() throws Exception {

    saveVersion("abc", "application/x-media-testsuite", "mpg", "test video 1", "hello, world");

    contentUpdated("abc");

    for (int i = 0; i < (MAX_WAIT_MS / POLL_MS); i++) {
      List<Map<String,String>> created = mediaService.created;

      if (created.size() > 0) {
        assertEquals(created.get(0).get("title"), "test video 1");
        assertEquals(created.get(0).get("description"), "hello, world");

        return;
      }

      Thread.sleep(POLL_MS);
    }

    fail("Video upload didn't arrive in time.");
  }


  @Test
  public void testTwoVersions() throws Exception {

    saveVersion("abc", "application/x-media-testsuite", "mpg", "test video 1", "hello, world");
    saveVersion("abc", "application/x-media-testsuite", "avi", "test video 1", "hello, world (updated)");

    contentUpdated("abc");

    for (int i = 0; i < (MAX_WAIT_MS / POLL_MS); i++) {
      List<Map<String,String>> created = mediaService.created;

      if (created.size() == 2) {
        assertTrue(("mpg".equals(created.get(0).get("extension")) && "avi".equals(created.get(1).get("extension"))) ||
                   ("avi".equals(created.get(0).get("extension")) && "mpg".equals(created.get(1).get("extension"))));

        return;
      }

      Thread.sleep(POLL_MS);
    }

    fail("Video upload didn't arrive in time.");
  }


  @Test
  public void testNonVideoVersion() throws Exception {

    saveVersion("abc", "application/x-media-testsuite", "mpg", "test video 1", "hello, world");
    saveVersion("abc", "image/png", "png", "test video 1", "this isn't a video");
    saveVersion("abc", "application/x-media-testsuite", "avi", "test video 1", "hello, world (updated)");

    contentUpdated("abc");

    for (int i = 0; i < (MAX_WAIT_MS / POLL_MS); i++) {
      List<Map<String,String>> created = mediaService.created;

      if (created.size() == 2) {
        assertTrue(("mpg".equals(created.get(0).get("extension")) && "avi".equals(created.get(1).get("extension"))) ||
                   ("avi".equals(created.get(0).get("extension")) && "mpg".equals(created.get(1).get("extension"))));

        return;
      }

      Thread.sleep(POLL_MS);
    }

    fail("Video upload didn't arrive in time.");
  }


  @Test
  public void testNonVideoInitialVersion() throws Exception {

    saveVersion("abc", "image/png", "png", "test video 1", "this isn't a video");
    saveVersion("abc", "application/x-media-testsuite", "mpg", "test video 1", "hello, world");
    saveVersion("abc", "application/x-media-testsuite", "avi", "test video 1", "hello, world (updated)");

    contentUpdated("abc");

    for (int i = 0; i < (MAX_WAIT_MS / POLL_MS); i++) {
      List<Map<String,String>> created = mediaService.created;

      if (created.size() == 2) {
        assertTrue(("mpg".equals(created.get(0).get("extension")) && "avi".equals(created.get(1).get("extension"))) ||
                   ("avi".equals(created.get(0).get("extension")) && "mpg".equals(created.get(1).get("extension"))));

        return;
      }

      Thread.sleep(POLL_MS);
    }

    fail("Video upload didn't arrive in time.");
  }


  @Test
  public void testUpdateMetadata() throws Exception {

    saveVersion("abc", "application/x-media-testsuite", "mpg", "test video 1", "hello, world");

    contentUpdated("abc");

    boolean wasCreated = false;
    for (int i = 0; i < (MAX_WAIT_MS / POLL_MS); i++) {
      List<Map<String,String>> created = mediaService.created;

      if (created.size() == 1) {
        assertEquals(created.get(0).get("title"), "test video 1");

        wasCreated = true;
        break;
      }

      Thread.sleep(POLL_MS);
    }

    if (!wasCreated) {
      fail("Video upload didn't arrive in time.");
      return;
    }

    // Update the metadata without creating a new version and verify that it propagates
    Content obj = cm.get("abc");
    obj.setProperty("sakai:description", "a new description");
    obj.setProperty("sakai:tags", new String[] { "anewtag", "anothernewtag" });
    cm.update(obj);

    contentUpdated("abc");

    for (int i = 0; i < (MAX_WAIT_MS / POLL_MS); i++) {
      List<Map<String,String>> updated = mediaService.updated;

      if (updated.size() == 1) {
        assertEquals(updated.get(0).get("description"), "a new description");
        assertEquals(updated.get(0).get("tags"), "[anewtag, anothernewtag]");

        return;
      }

      Thread.sleep(POLL_MS);
    }

    fail("Video metadata update didn't arrive in time.");
  }


  @Test
  public void testRetryOnFail() throws Exception {

    mediaService.failOnNextCreate = true;

    Field retry_ms = MediaCoordinator.class.getDeclaredField("retryMs");
    retry_ms.setAccessible(true);
    retry_ms.setInt(mc, 200);

    saveVersion("abc", "application/x-media-testsuite", "mpg", "test video 1", "hello, world");
    contentUpdated("abc");

    boolean wasCreated = false;
    for (int i = 0; i < (MAX_WAIT_MS / POLL_MS); i++) {
      List<Map<String,String>> created = mediaService.created;

      if (created.size() == 1) {
        // The first attempt failed...
        assertFalse(mediaService.failOnNextCreate);

        // ... but we got there in the end.
        assertEquals(created.get(0).get("title"), "test video 1");

        return;
      }

      Thread.sleep(POLL_MS);
    }

    if (!wasCreated) {
      fail("Video upload didn't arrive in time.");
      return;
    }
  }


  @Test
  public void testMaxRetries() throws Exception {

    mediaService.alwaysFail = true;

    Field retry_ms = MediaCoordinator.class.getDeclaredField("retryMs");
    retry_ms.setAccessible(true);
    retry_ms.setInt(mc, 200);

    Field max_retries = MediaCoordinator.class.getDeclaredField("maxRetries");
    max_retries.setAccessible(true);
    max_retries.setInt(mc, 2);

    final Semaphore semaphore = new Semaphore(0);

    mc.setErrorHandler(new ErrorHandler() {
        public void error(String pid) {
          if ("abc".equals(pid)) {
            semaphore.release();
          }
        }
      });

    saveVersion("abc", "application/x-media-testsuite", "mpg", "test video 1", "hello, world");
    contentUpdated("abc");

    for (int i = 0; i < (MAX_WAIT_MS / POLL_MS); i++) {
      if (semaphore.tryAcquire(POLL_MS, TimeUnit.MILLISECONDS)) {
        System.out.println("Hooray");
        return;
      }
    }

    fail("Failed upload didn't give up in time");
  }


  @After
  public void tearDown() throws Exception {
    mc.shutdown();
    adminSession.logout();
  }
}
