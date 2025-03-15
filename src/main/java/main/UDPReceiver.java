package main;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

public class UDPReceiver implements Runnable {
    private final DatagramSocket socket;
    private final Consumer<Message> messageHandler;

    public UDPReceiver(DatagramSocket socket, Consumer<Message> messageHandler) {
        this.socket = socket;
        this.messageHandler = messageHandler;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[4096];
        while (!Thread.currentThread().isInterrupted()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                ByteArrayInputStream byteStream = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                try (ObjectInputStream in = new ObjectInputStream(byteStream)) {
                    Message m = (Message) in.readObject();
                    messageHandler.accept(m);
                } catch (ClassNotFoundException ex) {
                    ex.printStackTrace();
                }
            } catch (IOException ex) {
                break;
            }
        }
    }
}
