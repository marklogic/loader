import java.io.*;
import java.net.Authenticator;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.Enumeration;

import com.marklogic.xdbc.*;
import com.marklogic.xdmp.*;
import com.marklogic.xdmp.util.*;

public class SimpleLoad {

  private static final int BUFSIZE = 2 << 15;

  static XDMPConnection con;

  private static void printUsage() {
    System.err.println("SimpleLoad usage: java SimpleLoad <host> <port> <username> <password> <dir-file-or-zip>");
  }

  private static String unescape(String s) {
    s = s.replaceAll("%2f", "/");
    s = s.replaceAll("%5c", "\\");
    s = s.replaceAll("%3a", ":");
    s = s.replaceAll("%3f", "?");
    return s;
  }

  private static void load(InputStream content, String uri) {
    long start = System.currentTimeMillis();
    try {
      XDMPDocInsertStream insert = con.openDocInsertStream(uri);
      int count;
      byte[] buf = new byte[BUFSIZE];
      while ((count = content.read(buf)) >= 0) {
        insert.write(buf, 0, count);
      }
      insert.commit();
      long end = System.currentTimeMillis();
      System.out.println("" + (end-start) + " ms.");
    }
    catch (Exception e) {
      System.err.println("Problem loading to the URI " + uri);
      e.printStackTrace(System.err);
    }
  }

  private static void load(String filename, String uri) {
    if (filename.toLowerCase().endsWith(".zip")) {
      System.out.println("Loading from ZIP " + filename + "... ");
      try {
        ZipFile zip = new ZipFile(filename);
        Enumeration entries = zip.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = (ZipEntry) entries.nextElement();
          String zipuri = unescape(entry.getName());
          System.out.print("  Loading " + zipuri + "... ");
          InputStream stream = zip.getInputStream(entry);
          load(stream, zipuri);
        }
      }
      catch (IOException e) {
        System.err.println("Problem reading from ZIP " + filename);
        e.printStackTrace(System.err);
      }
    }
    else {
      InputStream stream = null;
      try {
        System.out.print("Loading " + uri + "... ");
        stream = new FileInputStream(filename);
        load(stream, uri);
      }
      catch (IOException e) {
        System.err.println("Problem loading from file " + filename);
        e.printStackTrace(System.err);
      }
      finally {
        if (stream != null) try { stream.close(); } catch (IOException e) { }
      }
    }
  }

  public static void main(String[] args) {
    String host, username, password, path;
    int port;

    // Read parameters. Print usage on error.
    try {
      host = args[0];
      port = Integer.parseInt(args[1]);
      username = args[2];
      password = args[3];
      path = args[4];
    }
    catch (ArrayIndexOutOfBoundsException e) {
      printUsage();
      return;
    }
    catch (NumberFormatException e) {
      printUsage();
      return;
    }

    Authenticator.setDefault(new XDMPAuthenticator(username, password));
    try {
      con = new XDMPConnection(host, port);
    }
    catch (XDBCException e) {
      System.err.println("Problem connecting to host: " + host + ", port: " + port);
      e.printStackTrace(System.err);
      return;
    }

    File target = new File(path);
    if (target.isDirectory()) {
      // Load every file directly under the directory
      String[] targetlist = target.list();
      for (int i = 0; i < targetlist.length; i++) {
        String file = path + System.getProperty("file.separator") + targetlist[i];
        load(file, unescape(targetlist[i]));
      }
    }
    else {
      // Load a single file
      load(path, unescape(path));
    }

    if (con != null) {
      try { con.close(); } catch (Exception e) { }
    }
  }
}