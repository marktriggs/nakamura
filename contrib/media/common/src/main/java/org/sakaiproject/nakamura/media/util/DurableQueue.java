package org.sakaiproject.nakamura.media.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;


public class DurableQueue {

  private File file;
  private List<String> store;
  private LinkedBlockingQueue<String> queue;


  public DurableQueue(String filename)
    throws FileNotFoundException, IOException {

    this.file = new File(filename);

    queue = new LinkedBlockingQueue<String>();
    store = new ArrayList<String>();

    if (!file.exists()) {
      return;
    }

    // Load the state from the original file
    BufferedInputStream fh = new BufferedInputStream(new FileInputStream(file));
    DataInputStream in = new DataInputStream(fh);

    while (true) {
      try {
        String pid = in.readUTF();
        store.add(pid);
        queue.add(pid);
      } catch (EOFException ex) {
        break;
      }
    }
  }


  private void snapshot() throws FileNotFoundException, IOException {

    File tmpfile = new File(file.getName() + ".tmp");

    BufferedOutputStream fh = null;
    DataOutputStream out = null;

    try {
      fh = new BufferedOutputStream(new FileOutputStream(tmpfile));
      out = new DataOutputStream(fh);

      for (String pid : store) {
        out.writeUTF(pid);
      }
    } finally {
      if (out != null) {
        out.close();
        tmpfile.renameTo(file);
      }
    }
  }


  synchronized public void add(String pid)
    throws FileNotFoundException, IOException {

    store.add(pid);
    snapshot();
    queue.add(pid);
  }


  synchronized public void acknowledge(String pid)
    throws FileNotFoundException, IOException {

    store.remove(pid);
    snapshot();
  }


  public String take(long ms) throws InterruptedException {
    String elt = queue.poll(ms, TimeUnit.MILLISECONDS);

    return elt;
  }


  public String peek() {
    return queue.peek();
  }
}
