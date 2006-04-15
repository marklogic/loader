import java.io.*;
import java.net.Authenticator;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.*;

import com.marklogic.xdbc.*;
import com.marklogic.xdmp.*;
import com.marklogic.xdmp.util.*;
import org.w3c.tidy.*;
import org.w3c.dom.*;

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
      // HTML input files need to be tidied
      if (uri.toLowerCase().endsWith(".htm") ||
              uri.toLowerCase().endsWith(".html")) {
        // Force document type to XML
        XDMPDocInsertStream insert = con.openDocInsertStream(
                uri, Locale.getDefault(), true, null, null, 0, null,
                XDMPDocInsertStream.XDMP_ERROR_CORRECTION_FULL, null,
                XDMPDocInsertStream.XDMP_DOC_FORMAT_XML, "en");
        System.out.print("Tidying... ");
        Tidy tidy = new Tidy();
        tidy.setXmlOut(true);
        tidy.setShowWarnings(false);
        tidy.setQuiet(true);
        tidy.setFixBackslash(true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter catcher = new PrintWriter(baos);
        tidy.setErrout(catcher);
        Document doc = tidy.parseDOM(content, null);
        tidy.setCharEncoding(Configuration.UTF8);  // helps?
        tidy.pprint(doc, insert);
        insert.flush();
        insert.commit();
        if (tidy.getParseErrors() != 0) {
          System.err.println("Tidy parse problem on uri: " + uri);
          catcher.flush();
          System.err.println(baos.toString());
        }
      }

      // Other files just go straight in
      else {
        XDMPDocInsertStream insert = con.openDocInsertStream(uri);
        int count;
        byte[] buf = new byte[BUFSIZE];
        while ((count = content.read(buf)) >= 0) {
          insert.write(buf, 0, count);
        }
        insert.commit();
      }

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
        stream = new BufferedInputStream(new FileInputStream(filename));
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
      loadDirectory(target, "");  // 2nd arg is prepend text
    }
    else {
      // Load a single file
      load(path, unescape(path));
    }

    if (con != null) {
      try { con.close(); } catch (Exception e) { }
    }
  }

  private static void loadDirectory(File target, String prepend) {
    String[] targetlist = target.list();
    for (int i = 0; i < targetlist.length; i++) {
      String file = target + System.getProperty("file.separator") + targetlist[i];
      File innerTarget = new File(file);
      if (innerTarget.isDirectory()) {
        loadDirectory(innerTarget, prepend + targetlist[i] + "/");
      }
      else {
        load(file, unescape(prepend + targetlist[i]));
      }
    }
  }
}
