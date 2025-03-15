package main;

import java.io.*;
import java.net.*;

public class UDPUtil {
    /**
     * Serializes a Message object and sends it via UDP to the given destination.
     */
    public static void sendMessage(Message message, InetSocketAddress destination) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(byteStream)) {
            out.writeObject(message);
            out.flush();
        }
        byte[] data = byteStream.toByteArray();
        DatagramPacket packet = new DatagramPacket(data, data.length, destination.getAddress(), destination.getPort());
        DatagramSocket socket = new DatagramSocket();
        socket.send(packet);
        socket.close();
    }
}
