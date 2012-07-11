package org.sakaiproject.nakamura.media.util;

import java.io.IOException;
import org.junit.Test;
import java.io.File;
import static junit.framework.Assert.assertEquals;


public class DurableQueueTest {

  private String tempFile() throws IOException {
    File tempfile = File.createTempFile("durablequeue", "dat");
    tempfile.deleteOnExit();

    return tempfile.getPath();
  }

  @Test
  public void testBasicUsage() throws Exception {

    String path = tempFile();
    DurableQueue dq = new DurableQueue(path);

    dq.add("hello");
    dq.add("world");

    assertEquals("hello", dq.take(1000));
    assertEquals("world", dq.take(1000));
    assertEquals(null, dq.take(100));
  }


  @Test
  public void testDurability() throws Exception {

    String path = tempFile();
    DurableQueue dq = new DurableQueue(path);

    dq.add("hello");
    dq.add("world");

    assertEquals("hello", dq.take(1000));
    assertEquals("world", dq.take(1000));
    assertEquals(null, dq.take(100));

    DurableQueue dq2 = new DurableQueue(path);

    assertEquals("hello", dq2.take(1000));
    assertEquals("world", dq2.take(1000));
    assertEquals(null, dq2.take(100));
  }


  @Test
  public void testDeDupe() throws Exception {

    String path = tempFile();
    DurableQueue dq = new DurableQueue(path);

    dq.add("hello");
    dq.add("hello");
    dq.add("hello");
    dq.add("world");
    dq.add("hello");

    assertEquals("hello", dq.take(1000));
    assertEquals("world", dq.take(1000));
    assertEquals(null, dq.take(100));
  }


  @Test
  public void testAcknowledge() throws Exception {

    String path = tempFile();
    DurableQueue dq = new DurableQueue(path);

    dq.add("hello");
    dq.add("world");

    assertEquals("hello", dq.take(1000));
    assertEquals("world", dq.take(1000));
    assertEquals(null, dq.take(100));

    dq.acknowledge("hello");

    DurableQueue dq2 = new DurableQueue(path);

    assertEquals("world", dq2.take(1000));
    assertEquals(null, dq2.take(100));
  }


  @Test
  public void testWait() throws Exception {

    String path = tempFile();
    final DurableQueue dq = new DurableQueue(path);

    Thread producer = new Thread() {
        public void run() {
          try {
            Thread.sleep(1000);
            dq.add("hello");
          } catch (Exception e) {}
        }
      };

    producer.start();

    assertEquals("hello", dq.take(3000));
  }


  @Test
  public void testAcknowledgeDoesNotDequeue() throws Exception {

    String path = tempFile();
    final DurableQueue dq = new DurableQueue(path);

    dq.add("hello");
    dq.add("world");

    // You can imagine this might be intended to acknowledge a previous "hello"
    // message, not knowing that a new one had just been added.  We still want
    // to receive this new message when we ask for it.
    dq.acknowledge("hello");

    assertEquals("hello", dq.take(1000));
    assertEquals("world", dq.take(1000));
    assertEquals(null, dq.take(1000));
  }

}
