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
        // Tự động kiểm tra và dọn dẹp các kết nối "ma" sau mỗi 10 giây
        setConnectionLostTimeout(10);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // Mới kết nối chưa có tên
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

            // --- CẢI TIẾN: Nếu tên đã tồn tại, hãy xóa kết nối cũ trước khi cho người mới
            // vào ---
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
            broadcast("CHAT:[" + username + " đã tham gia phòng]");
            updateOnlineList();

        } else if (message.startsWith("MSG:")) {
            String username = onlineUsers.get(conn);
            if (username != null) {
                broadcast("CHAT:[" + username + "]: " + message.substring(4));
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        // Bỏ qua lỗi nhỏ
    }

    @Override
    public void onStart() {
        System.out.println("Server đã sẵn sàng tại port: " + getPort());
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
