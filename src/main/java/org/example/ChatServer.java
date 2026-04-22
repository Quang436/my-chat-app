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
    // Danh sách lưu lịch sử tin nhắn (tối đa 50 tin)
    private static List<String> chatHistory = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_HISTORY = 50;

    public ChatServer(int port) {
        super(new InetSocketAddress(port));
        setConnectionLostTimeout(10);
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
            String username = message.substring(6).trim();
            handleLogin(conn, username);
        } else if (message.startsWith("MSG:")) {
            String username = onlineUsers.get(conn);
            if (username != null) {
                String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
                String formattedMsg = "CHAT:[" + time + "] [" + username + "]: " + message.substring(4);
                broadcastAndSave(formattedMsg);
            }
        }
    }

    private void handleLogin(WebSocket conn, String username) {
        // Xử lý đá kết nối cũ nếu trùng tên
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

        // --- GỬI LỊCH SỬ TIN NHẮN CHO NGƯỜI MỚI VÀO ---
        synchronized (chatHistory) {
            for (String historyMsg : chatHistory) {
                conn.send(historyMsg);
            }
        }

        broadcastAndSave("CHAT:[Hệ thống]: " + username + " đã tham gia phòng");
        updateOnlineList();
    }

    // Vừa gửi tin nhắn cho mọi người, vừa lưu vào lịch sử
    private void broadcastAndSave(String message) {
        synchronized (chatHistory) {
            chatHistory.add(message);
            if (chatHistory.size() > MAX_HISTORY) {
                chatHistory.remove(0); // Xóa tin cũ nhất nếu vượt quá giới hạn
            }
        }
        broadcast(message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
    }

    @Override
    public void onStart() {
        System.out.println("Cps Cloud Server đang chạy tại port: " + getPort());
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
        ChatServer server = new ChatServer(port);
        server.start();
    }
}
