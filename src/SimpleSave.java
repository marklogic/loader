import java.io.*;
import java.net.Authenticator;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.Enumeration;

import com.marklogic.xdbc.*;
import com.marklogic.xdmp.*;
import com.marklogic.xdmp.util.*;

public class SimpleSave {

  private static final int BUFSIZE = 2 << 15;

  static XDMPConnection con;

  private static void printUsage() {
    System.err.println("SimpleSave usage: java SimpleSave <host> <port> <username> <password> <dir-to-save>");
  }

  private static String escape(String s) {
    s = s.replaceAll("/", "%2f");
    s = s.replaceAll("\\\\", "%5c");
    s = s.replaceAll(":", "%3a");
    s = s.replaceAll("\\?", "%3f");
    return s;
  }

  private static void save(Reader content, String uri) {
    long start = System.currentTimeMillis();
    try {
      FileWriter writer = new FileWriter(uri);
      char[] buf = new char[BUFSIZE];
      int count = 0;
      while ((count = content.read(buf)) >= 0) {
        writer.write(buf, 0, count);
      }
      writer.close();
      long end = System.currentTimeMillis();
      System.out.println("" + (end-start) + " ms.");
    }
    catch (Exception e) {
      System.err.println("Problem saving to the URI " + uri);
      e.printStackTrace(System.err);
    }
  }

  private static void save(InputStream inputStream, String uri) {
    long start = System.currentTimeMillis();
    try {
      FileOutputStream fileOutputStream = new FileOutputStream(uri);
      byte[] buf = new byte[BUFSIZE];
      int count = 0;
      while ((count = inputStream.read(buf)) != -1) {
        fileOutputStream.write(buf, 0, count);
      }
      fileOutputStream.flush();
      fileOutputStream.close();
      long end = System.currentTimeMillis();
      System.out.println("" + (end-start) + " ms.");
    }
    catch (Exception e) {
      System.err.println("Problem saving to the URI " + uri);
      e.printStackTrace(System.err);
    }
  }

  private static void save(String directory) {
    XDBCStatement stmt = null;
    XDBCResultSequence result = null;
    try {
      String query = "for $i in input() return (base-uri($i), $i/node())";
      stmt = con.createStatement();
      result = stmt.executeQuery(query);
      while (result.hasNext()) {
        result.next();
        String baseuri = result.get_String();
        String uri = directory + System.getProperty("file.separator") + escape(baseuri);
        result.next();
        switch (result.getItemType()) {
          case XDBCResultSequence.XDBC_Text:
          case XDBCResultSequence.XDBC_Binary:
            InputStream is = result.getInputStream();
            save(is,uri);
            break;
          default:
            Reader reader = result.getReader();
            save(reader,uri);
        }
      }
    }
    catch (XDBCException e) {
      e.printStackTrace(System.err);
      return;
    }
    finally {
      if (result != null) { try { result.close(); } catch (Exception e) { } }
      if (stmt != null) { try { stmt.close(); } catch (Exception e) { } }
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

    File dir = new File(path);
    if (!dir.exists()) {
      boolean success = dir.mkdir();
      if (!success) {
        System.err.println("Could not create directory " + path);
        return;
      }
    }
    else if (!dir.isDirectory()) {
      System.err.println("Path " + path + " was a file; needs to be a directory");
      printUsage();
      return;
    }

    save(path);

    if (con != null) {
      try { con.close(); } catch (Exception e) { }
    }
  }
}
