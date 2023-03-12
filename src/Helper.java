

import java.net.DatagramPacket;


public class Helper {

  public Helper() {
  }

  String extractFilename(DatagramPacket packet) {
    String filename = new String(packet.getData(), 2, packet.getLength() - 2);
    int nullIndex = filename.indexOf('\0');
    return nullIndex >= 0 ? filename.substring(0, nullIndex) : null;
  }

  /**
   * Extracts the data from the packet
   * @param data packet
   * @return data
   */
  short getData(DatagramPacket data) {
    byte[] buf = data.getData();
    return (short) ((buf[2] << 8) | (buf[3] & 0xFF));
  }

}
