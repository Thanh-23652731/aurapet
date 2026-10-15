package com.aurapet.backend.infrastructure.discovery;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

@Component
public class UdpDiscoveryServer {

    private static final Logger log = LoggerFactory.getLogger(UdpDiscoveryServer.class);
    private static final int DISCOVERY_PORT = 8888;
    private static final String REQUEST_KEYWORD = "AURAPET_DISCOVER";
    private static final String RESPONSE_PREFIX = "AURAPET_SERVER:";

    @Value("${server.port:8080}")
    private int serverPort;

    private DatagramSocket socket;
    private volatile boolean running = false;
    private Thread listenerThread;

    @PostConstruct
    public void start() {
        running = true;
        listenerThread = new Thread(this::listen, "aurapet-udp-discovery");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void listen() {
        try {
            socket = new DatagramSocket(DISCOVERY_PORT);
            socket.setBroadcast(true);
            log.info("AuraPet UDP Auto-Discovery Service listening on UDP port {}", DISCOVERY_PORT);

            byte[] buffer = new byte[1024];
            while (running && !socket.isClosed()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                if (message.contains(REQUEST_KEYWORD)) {
                    String localIp = getLocalLanIp();
                    String responseMsg = RESPONSE_PREFIX + "http://" + localIp + ":" + serverPort;
                    byte[] responseBytes = responseMsg.getBytes(StandardCharsets.UTF_8);

                    DatagramPacket responsePacket = new DatagramPacket(
                            responseBytes,
                            responseBytes.length,
                            packet.getAddress(),
                            packet.getPort()
                    );
                    socket.send(responsePacket);
                    log.info("Auto-Discovery replied to {}:{} with URL: {}", packet.getAddress(), packet.getPort(), responseMsg);
                }
            }
        } catch (Exception e) {
            if (running) {
                log.warn("UDP Discovery Service stopped or port {} occupied: {}", DISCOVERY_PORT, e.getMessage());
            }
        }
    }

    private String getLocalLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            String candidateIp = null;
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;
                String name = iface.getDisplayName().toLowerCase();
                if (name.contains("vmware") || name.contains("virtual") || name.contains("vbox") || name.contains("vpn")) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.isSiteLocalAddress() && !addr.getHostAddress().contains(":")) {
                        String ip = addr.getHostAddress();
                        if (name.contains("wi-fi") || name.contains("wlan") || name.contains("wireless")) {
                            return ip; // Ưu tiên card Wi-Fi
                        }
                        if (candidateIp == null) {
                            candidateIp = ip;
                        }
                    }
                }
            }
            if (candidateIp != null) return candidateIp;
        } catch (Exception ignored) {
        }
        return "10.10.59.64";
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
