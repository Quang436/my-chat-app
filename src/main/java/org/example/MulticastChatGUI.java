package org.example;

import com.formdev.flatlaf.FlatDarkLaf;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;

public class MulticastChatGUI extends JFrame {
    private JTextField txtUrl, txtUsername, txtMessage;
    private JTextPane areaChat;
    private JList<String> listUsers;
    private DefaultListModel<String> userListModel;
    private JButton btnConnect, btnSend, btnDisconnect;
    private JLabel lblStatus;

    private CustomWebSocketClient client;

    public MulticastChatGUI() {
        setTitle("CPS Cloud Chat System - WebSockets (Render)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        setupUI();
    }

    private void setupUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder("Kết nối Cloud (Render)"));

        // Render URL sẽ có dạng ws://tên-app.onrender.com
        txtUrl = new JTextField("ws://localhost:8888", 25);
        txtUsername = new JTextField("User_" + (int) (Math.random() * 100), 10);
        btnConnect = new JButton("Kết nối Cloud");
        btnDisconnect = new JButton("Ngắt kết nối");
        btnDisconnect.setEnabled(false);

        topPanel.add(new JLabel("Server URL:"));
        topPanel.add(txtUrl);
        topPanel.add(new JLabel("Tên:"));
        topPanel.add(txtUsername);
        topPanel.add(btnConnect);
        topPanel.add(btnDisconnect);

        userListModel = new DefaultListModel<>();
        listUsers = new JList<>(userListModel);
        listUsers.setFont(new Font("Segoe UI", Font.BOLD, 14));
        JScrollPane scrollUsers = new JScrollPane(listUsers);
        scrollUsers.setPreferredSize(new Dimension(200, 0));
        scrollUsers.setBorder(BorderFactory.createTitledBorder("Thành viên Online"));

        areaChat = new JTextPane();
        areaChat.setEditable(false);
        areaChat.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        JScrollPane scrollChat = new JScrollPane(areaChat);
        scrollChat.setBorder(BorderFactory.createTitledBorder("Cuộc trò chuyện"));

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        txtMessage = new JTextField();
        txtMessage.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        txtMessage.setEnabled(false);
        btnSend = new JButton("Gửi");
        btnSend.setEnabled(false);
        btnSend.setPreferredSize(new Dimension(100, 40));

        lblStatus = new JLabel("Trạng thái: Chưa kết nối");
        lblStatus.setForeground(Color.GRAY);

        bottomPanel.add(txtMessage, BorderLayout.CENTER);
        bottomPanel.add(btnSend, BorderLayout.EAST);
        bottomPanel.add(lblStatus, BorderLayout.SOUTH);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollUsers, BorderLayout.WEST);
        mainPanel.add(scrollChat, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);

        btnConnect.addActionListener(e -> connect());
        btnDisconnect.addActionListener(e -> disconnect());
        btnSend.addActionListener(e -> sendMessage());
        txtMessage.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER)
                    sendMessage();
            }
        });
    }

    private void connect() {
        try {
            String url = txtUrl.getText().trim();
            client = new CustomWebSocketClient(new URI(url));
            client.connect();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "URL không hợp lệ: " + e.getMessage());
        }
    }

    private void disconnect() {
        if (client != null)
            client.close();
        toggleInputs(false);
    }

    private void sendMessage() {
        String msg = txtMessage.getText().trim();
        if (!msg.isEmpty() && client != null) {
            client.send("MSG:" + msg);
            txtMessage.setText("");
        }
    }

    private void toggleInputs(boolean connected) {
        btnConnect.setEnabled(!connected);
        btnDisconnect.setEnabled(connected);
        txtMessage.setEnabled(connected);
        btnSend.setEnabled(connected);
        txtUrl.setEnabled(!connected);
        txtUsername.setEnabled(!connected);
    }

    private void appendChat(String text) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = areaChat.getStyledDocument();
            SimpleAttributeSet set = new SimpleAttributeSet();
            try {
                StyleConstants.setForeground(set, text.contains("[Hệ thống]") ? Color.YELLOW : Color.WHITE);
                doc.insertString(doc.getLength(), text + "\n", set);
                areaChat.setCaretPosition(doc.getLength());
            } catch (Exception ignored) {
            }
        });
    }

    // Inner class WebSocket Client
    class CustomWebSocketClient extends WebSocketClient {
        public CustomWebSocketClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            send("LOGIN:" + txtUsername.getText().trim());
        }

        @Override
        public void onMessage(String message) {
            if (message.equals("LOGIN_SUCCESS")) {
                toggleInputs(true);
                lblStatus.setText("Trạng thái: Đã kết nối Cloud");
                lblStatus.setForeground(new Color(80, 220, 100));
                appendChat("[Hệ thống]: Đăng nhập thành công.");
            } else if (message.startsWith("ERROR:")) {
                JOptionPane.showMessageDialog(MulticastChatGUI.this, message.substring(6));
                close();
            } else if (message.startsWith("CHAT:")) {
                appendChat(message.substring(5));
            } else if (message.startsWith("UPDATE_USER_LIST:")) {
                String[] users = message.substring(17).split(",");
                SwingUtilities.invokeLater(() -> {
                    userListModel.clear();
                    for (String u : users)
                        if (!u.isEmpty())
                            userListModel.addElement(u);
                });
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            toggleInputs(false);
            lblStatus.setText("Trạng thái: Đã ngắt kết nối");
            lblStatus.setForeground(Color.GRAY);
            appendChat("[Hệ thống]: Đã rời khỏi Cloud.");
        }

        @Override
        public void onError(Exception ex) {
            appendChat("[Lỗi]: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> new MulticastChatGUI().setVisible(true));
    }
}
