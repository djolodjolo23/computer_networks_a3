import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;


public class Server {

  public static final int TFTPPORT = 69;
  public static final int BUFSIZE = 516;
  public static final String READDIR = "read/";

  public static final short OP_RRQ = 1;
  public static final short OP_WRQ = 2;
  public static final short OP_DAT = 3;
  public static final short OP_ACK = 4;
  public static final short OP_ERR = 5;
  public static final short ERR_LOST = 0;
  public static final short ERR_FNF = 1;
  public static final short ERR_ACCESS = 2;
  public static final short ERR_EXISTS = 6;
  public static String mode = "octet";

  public static void main(String[] args) {
    argsCheck(args);
    try (DatagramSocket serverSocket = new DatagramSocket(Integer.parseInt(args[0]))) {
      // ready to receive messages
      byte[] receiveData = new byte[BUFSIZE];
      System.out.println("Server has successfully started. Listening on the port " + args[0] + "...");
      URL url = Server.class.getResource("Server.class");
      assert url != null;
      String path = url.getPath();
      System.out.println("Detailed file path: " + path + "\n");
      while (true) {
        // client that's accepted
        // can accept multiple connections since in while(true) loop
        try {
          //DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
          // connection established
          // check if all array elements are 0 or not
          DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
          serverSocket.receive(receivePacket); // receive incoming request
          //if (!isFilledWithZeros(receiveData)) {
            //serverSocket.receive(new DatagramPacket(receiveData, receiveData.length));
          String request = "";
          if (receivePacket.getData()[1] == 1) {
            request = "READ";
          }
          if (receivePacket.getData()[1] == 2) {
            request = "WRITE";
          }
          System.out.println("Incoming request from " + receivePacket.getAddress() + " on port " + receivePacket.getPort());
          System.out.println("Request type: " + request);
          RequestHandler requestHandler = new RequestHandler(receivePacket);
          Thread thread = new Thread(requestHandler);
          thread.start();
          //serverSocket.receive(receivePacket);
          //System.out.println("testing, testing" + receivePacket.getAddress());
          //thread.join();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


  static boolean integerCheck(String var0) {
    try {
      Integer.parseInt(var0);
      return true;
    } catch (NumberFormatException var2) {
      return false;
    }
  }

  static boolean checkIfNameIsNotRestricted(String folderName) {
    return !folderName.equals(".idea") && !folderName.equals("out") && !folderName.equals("src");
  }

  static boolean checkIfPublicFolderExists(String folderName) {
    boolean exist = false;
    File currentDir = new File(folderName);
    if (currentDir.exists()) {
      exist = true;
    }
    return exist;
  }

  /**
   * Checks the program arguments.
   *
   * @param args are the program arguments, port number and a name of the folder.
   */
  static void argsCheck(String[] args) {
    if (args.length != 2) {
      System.err.println("There must be two program arguments, the listening port and a relative folder path");
      System.exit(1);
    } else if (!integerCheck(args[0])) {
      System.err.println("error, the port number is not an integer value");
      System.exit(1);
    } else if (args[0].length() > 5) {
      System.err.println("error, the port number is longer than 5");
      System.exit(1);
    } else if (!checkIfPublicFolderExists(args[1])) {
      System.err.println("The folder provided does not exist!");
      System.exit(1);
    } else if (!checkIfNameIsNotRestricted(args[1])) {
      System.out.println("The name of the folder provided has a restricted access");
      System.exit(1);
    }
  }


  public static boolean isFilledWithZeros(byte[] array) {
    for (int i = 0; i < array.length; i++) {
      if (array[i] != 0) {
        return false;
      }
    }
    return true;
  }

  private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) {
    DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);
    try {
      socket.receive(receivePacket);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return new InetSocketAddress(receivePacket.getAddress(),receivePacket.getPort());
  }

}
