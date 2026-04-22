package org.example;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer extends WebSocketServer {
    private static Map<WebSocket, String> onlineUsers = new ConcurrentHashMap<>();

    public ChatServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Kết nối mới từ: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String username = onlineUsers.remove(conn);
        if (username != null) {
            broadcast("CHAT:[" + username + " đã rời phòng]");
            updateOnlineList();
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        if (message.startsWith("LOGIN:")) {
            String username = message.substring(6).trim();
            if (onlineUsers.containsValue(username)) {
                conn.send("ERROR:Tên đã tồn tại!");
            } else {
                onlineUsers.put(conn, username);
                conn.send("LOGIN_SUCCESS");
                broadcast("CHAT:[" + username + " đã tham gia phòng]");
                updateOnlineList();
            }
        } else if (message.startsWith("MSG:")) {
            String username = onlineUsers.get(conn);
            if (username != null) {
                broadcast("CHAT:[" + username + "]: " + message.substring(4));
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket Server đang chạy tại port: " + getPort());
    }

    private void updateOnlineList() {
        StringBuilder list = new StringBuilder("UPDATE_USER_LIST:");
        for (String user : onlineUsers.values()) {
            list.append(user).append(",");
        }
        broadcast(list.toString());
    }

    public static void main(String[] args) {
        // Render cấp Port qua biến môi trường $PORT
        int port = 8888;
        String envPort = System.getenv("PORT");
        if (envPort != null) {
            port = Integer.parseInt(envPort);
        }

        ChatServer server = new ChatServer(port);
        server.start();
    }
}
