package org.example;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.net.InetSocketAddress;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer extends WebSocketServer {
    private static Map<WebSocket, String> onlineUsers = new ConcurrentHashMap<>();
    private static List<String> chatHistory = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_HISTORY = 50;

    public ChatServer(int port) {
        super(new InetSocketAddress(port));
        setConnectionLostTimeout(15);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String username = onlineUsers.remove(conn);
        if (username != null) {
            broadcastAndSave("CHAT:[Hệ thống]: " + username + " đã rời phòng");
            updateOnlineList();
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        if (message.startsWith("LOGIN:")) {
            handleLogin(conn, message.substring(6).trim());
        } else if (message.startsWith("MSG:")) {
            handlePublicMessage(conn, message.substring(4));
        } else if (message.startsWith("PRIVATE:")) {
            handlePrivateMessage(conn, message.substring(8));
        } else if (message.startsWith("TYPING:")) {
            broadcastExcept(conn, "SYSTEM_TYPING:" + onlineUsers.get(conn));
        }
    }

    private void handleLogin(WebSocket conn, String username) {
        // Đá kết nối cũ nếu trùng tên
        WebSocket oldConn = null;
        for (Map.Entry<WebSocket, String> entry : onlineUsers.entrySet()) {
            if (entry.getValue().equals(username)) {
                oldConn = entry.getKey();
                break;
            }
        }
        if (oldConn != null) {
            oldConn.close(1000, "Đăng nhập từ nơi khác");
            onlineUsers.remove(oldConn);
        }

        onlineUsers.put(conn, username);
        conn.send("LOGIN_SUCCESS");

        // Gửi lịch sử
        synchronized (chatHistory) {
            for (String h : chatHistory)
                conn.send(h);
        }

        broadcastAndSave("CHAT:[Hệ thống]: " + username + " đã tham gia phòng");
        updateOnlineList();
    }

    private void handlePublicMessage(WebSocket sender, String text) {
        String user = onlineUsers.get(sender);
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        broadcastAndSave("CHAT:[" + time + "] [" + user + "]: " + text);
    }

    private void handlePrivateMessage(WebSocket sender, String data) {
        // Cấu trúc: targetName|message
        int sep = data.indexOf("|");
        if (sep == -1)
            return;
        String targetName = data.substring(0, sep);
        String text = data.substring(sep + 1);
        String senderName = onlineUsers.get(sender);
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        String formatted = "PRIVATE_CHAT:[" + time + "] (Thì thầm) [" + senderName + "]: " + text;

        // Gửi cho người nhận và chính mình
        for (Map.Entry<WebSocket, String> entry : onlineUsers.entrySet()) {
            if (entry.getValue().equals(targetName) || entry.getValue().equals(senderName)) {
                entry.getKey().send(formatted);
            }
        }
    }

    private void broadcastAndSave(String message) {
        synchronized (chatHistory) {
            chatHistory.add(message);
            if (chatHistory.size() > MAX_HISTORY)
                chatHistory.remove(0);
        }
        broadcast(message);
    }

    private void broadcastExcept(WebSocket exclude, String msg) {
        for (WebSocket conn : onlineUsers.keySet()) {
            if (conn != exclude)
                conn.send(msg);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
    }

    @Override
    public void onStart() {
        System.out.println("Advanced Server started.");
    }

    private void updateOnlineList() {
        StringBuilder list = new StringBuilder("UPDATE_USER_LIST:");
        for (String user : onlineUsers.values()) {
            list.append(user).append(",");
        }
        broadcast(list.toString());
    }

    public static void main(String[] args) {
        int port = 8888;
        String envPort = System.getenv("PORT");
        if (envPort != null)
            port = Integer.parseInt(envPort);
        new ChatServer(port).start();
    }
}
