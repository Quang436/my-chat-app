package org.example;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.InetSocketAddress;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer extends WebSocketServer {
    private static Map<WebSocket, String> onlineUsers = new ConcurrentHashMap<>();
    private static Set<String> banList = Collections.synchronizedSet(new HashSet<>());
    private static Set<WebSocket> mutedUsers = Collections.synchronizedSet(new HashSet<>());
    private static List<String> chatHistory = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_HISTORY = 50;

    // GUI Components
    private JFrame frame;
    private JTable userTable;
    private DefaultTableModel tableModel;
    private JTextArea logArea;
    private JTextField broadcastField;
    private JLabel statsLabel;
    private int totalMessages = 0;

    public ChatServer(int port) {
        super(new InetSocketAddress(port));
        initGUI();
    }

    private void initGUI() {
        if (GraphicsEnvironment.isHeadless())
            return;

        frame = new JFrame("CPS Chat Admin Pro");
        frame.setSize(1000, 700);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Custom Look
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        // Left Panel: User Management
        String[] columns = { "Username", "IP Address", "Muted" };
        tableModel = new DefaultTableModel(columns, 0);
        userTable = new JTable(tableModel);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(350, 0));
        leftPanel.setBorder(BorderFactory.createTitledBorder("User Management"));
        leftPanel.add(new JScrollPane(userTable), BorderLayout.CENTER);

        JPanel userActions = new JPanel(new GridLayout(2, 2, 5, 5));
        JButton kickBtn = new JButton("Kick User");
        JButton muteBtn = new JButton("Mute/Unmute");
        JButton banBtn = new JButton("Ban User");
        JButton clearBanBtn = new JButton("Clear Ban List");

        kickBtn.addActionListener(e -> kickSelected());
        muteBtn.addActionListener(e -> muteSelected());
        banBtn.addActionListener(e -> banSelected());
        clearBanBtn.addActionListener(e -> {
            banList.clear();
            log("System: Ban list cleared.");
        });

        userActions.add(kickBtn);
        userActions.add(muteBtn);
        userActions.add(banBtn);
        userActions.add(clearBanBtn);
        leftPanel.add(userActions, BorderLayout.SOUTH);

        // Center Panel: Logs
        JPanel centerPanel = new JPanel(new BorderLayout());
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(0, 255, 0));
        centerPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        // Bottom Panel: Actions
        JPanel bottomPanel = new JPanel(new BorderLayout());
        broadcastField = new JTextField();
        JButton broadcastBtn = new JButton("Send Global Alert");
        JButton clearChatBtn = new JButton("Clear Global Chat");

        broadcastBtn.addActionListener(e -> sendGlobalAlert());
        clearChatBtn.addActionListener(e -> clearAllChat());

        JPanel chatActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        chatActions.add(clearChatBtn);
        chatActions.add(broadcastBtn);

        bottomPanel.add(new JLabel(" Broadcast Path: "), BorderLayout.WEST);
        bottomPanel.add(broadcastField, BorderLayout.CENTER);
        bottomPanel.add(chatActions, BorderLayout.EAST);

        // Stats Header
        statsLabel = new JLabel("Total Messages: 0 | Online: 0 | Banned: 0");
        statsLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        frame.add(statsLabel, BorderLayout.NORTH);

        frame.add(leftPanel, BorderLayout.WEST);
        frame.add(centerPanel, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private void log(String msg) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        if (!GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeLater(() -> logArea.append("[" + time + "] " + msg + "\n"));
        }
        System.out.println("[" + time + "] " + msg);
    }

    private void updateStats() {
        if (GraphicsEnvironment.isHeadless())
            return;
        SwingUtilities.invokeLater(() -> statsLabel.setText("Total Messages: " + totalMessages + " | Online: "
                + onlineUsers.size() + " | Banned: " + banList.size()));
    }

    private void kickSelected() {
        int row = userTable.getSelectedRow();
        if (row == -1)
            return;
        String name = (String) tableModel.getValueAt(row, 0);
        findAndClose(name, "Kicked by Admin", false);
    }

    private void muteSelected() {
        int row = userTable.getSelectedRow();
        if (row == -1)
            return;
        String name = (String) tableModel.getValueAt(row, 0);
        for (WebSocket ws : onlineUsers.keySet()) {
            if (onlineUsers.get(ws).equals(name)) {
                if (mutedUsers.contains(ws)) {
                    mutedUsers.remove(ws);
                    log("Unmuted: " + name);
                } else {
                    mutedUsers.add(ws);
                    log("Muted: " + name);
                }
                break;
            }
        }
        updateTable();
    }

    private void banSelected() {
        int row = userTable.getSelectedRow();
        if (row == -1)
            return;
        String name = (String) tableModel.getValueAt(row, 0);
        banList.add(name);
        findAndClose(name, "You have been BANNED", true);
    }

    private void findAndClose(String name, String reason, boolean isBan) {
        WebSocket target = null;
        for (Map.Entry<WebSocket, String> entry : onlineUsers.entrySet()) {
            if (entry.getValue().equals(name)) {
                target = entry.getKey();
                break;
            }
        }
        if (target != null) {
            target.send("CHAT:[Hệ thống]: " + reason);
            target.close(1000, reason);
            log((isBan ? "BANNED: " : "KICKED: ") + name);
        }
    }

    private void clearAllChat() {
        chatHistory.clear();
        broadcast("CHAT:[Hệ thống]: Lịch sử chat đã được Admin xóa sạch.");
        log("System: Chat history cleared.");
    }

    private void sendGlobalAlert() {
        String msg = broadcastField.getText().trim();
        if (!msg.isEmpty()) {
            broadcastAndSave("CHAT:[THÔNG BÁO QUAN TRỌNG]: " + msg);
            broadcastField.setText("");
            log("ALERT: " + msg);
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String user = onlineUsers.remove(conn);
        mutedUsers.remove(conn);
        if (user != null) {
            broadcastAndSave("CHAT:[Hệ thống]: " + user + " đã rời phòng");
            updateTable();
            log("EXIT: " + user);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        if (message.startsWith("LOGIN:")) {
            String name = message.substring(6).trim();
            if (banList.contains(name)) {
                conn.send("CHAT:[Hệ thống]: Bạn đã bị cấm khỏi Server này!");
                conn.close();
                log("BLOCKED LOGIN: " + name);
                return;
            }
            onlineUsers.put(conn, name);
            conn.send("LOGIN_SUCCESS");
            synchronized (chatHistory) {
                for (String h : chatHistory)
                    conn.send(h);
            }
            broadcastAndSave("CHAT:[Hệ thống]: " + name + " đã tham gia phòng");
            updateTable();
            log("LOGIN: " + name);
        } else if (message.startsWith("MSG:")) {
            if (mutedUsers.contains(conn)) {
                conn.send("CHAT:[Hệ thống]: Bạn đang bị cấm túc, không thể gửi tin nhắn!");
                return;
            }
            totalMessages++;
            handlePublicMessage(conn, message.substring(4));
            updateStats();
        } else if (message.startsWith("PRIVATE:")) {
            if (mutedUsers.contains(conn)) {
                conn.send("CHAT:[Hệ thống]: Bạn đang bị cấm túc!");
                return;
            }
            handlePrivateMessage(conn, message.substring(8));
        } else if (message.startsWith("TYPING:")) {
            broadcastExcept(conn, "SYSTEM_TYPING:" + onlineUsers.get(conn));
        }
    }

    private void handlePublicMessage(WebSocket sender, String text) {
        String user = onlineUsers.get(sender);
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        broadcastAndSave("CHAT:[" + time + "] [" + user + "]: " + text);
    }

    private void handlePrivateMessage(WebSocket sender, String data) {
        int sep = data.indexOf("|");
        if (sep == -1)
            return;
        String targetName = data.substring(0, sep);
        String text = data.substring(sep + 1);
        String senderName = onlineUsers.get(sender);
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String formatted = "PRIVATE_CHAT:[" + time + "] (Thì thầm) [" + senderName + "]: " + text;
        for (Map.Entry<WebSocket, String> entry : onlineUsers.entrySet()) {
            if (entry.getValue().equals(targetName) || entry.getValue().equals(senderName)) {
                entry.getKey().send(formatted);
            }
        }
    }

    private void updateTable() {
        if (GraphicsEnvironment.isHeadless())
            return;
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            for (Map.Entry<WebSocket, String> entry : onlineUsers.entrySet()) {
                tableModel.addRow(new Object[] {
                        entry.getValue(),
                        entry.getKey().getRemoteSocketAddress().toString(),
                        mutedUsers.contains(entry.getKey()) ? "YES" : "NO"
                });
            }
            updateStats();

            StringBuilder list = new StringBuilder("UPDATE_USER_LIST:");
            for (String user : onlineUsers.values())
                list.append(user).append(",");
            broadcast(list.toString());
        });
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
        for (WebSocket conn : onlineUsers.keySet())
            if (conn != exclude)
                conn.send(msg);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        log("ERROR: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        log("Admin Server started on port: " + getPort());
        updateStats();
    }

    public static void main(String[] args) {
        int port = 8888;
        String envPort = System.getenv("PORT");
        if (envPort != null)
            port = Integer.parseInt(envPort);
        new ChatServer(port).start();
    }
}
