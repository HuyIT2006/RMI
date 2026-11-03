package chat;

import javax.swing.*;
import java.awt.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

/** Client chat có giao diện Swing. Chạy: java chat.ChatClientGUI */
public class ChatClientGUI extends JFrame {
    private static final String ALL = "Tất cả";

    private final JTextArea area = new JTextArea();
    private final JComboBox<String> target = new JComboBox<>(new String[]{ALL});
    private final JTextField input = new JTextField();
    private final ChatService service;
    private final String name;

    /** Callback: server gọi các hàm này khi có tin mới. */
    class Listener extends UnicastRemoteObject implements ChatListener {
        Listener() throws RemoteException {
            super(0);
        }

        @Override
        public void onMessage(String text) {
            SwingUtilities.invokeLater(() -> {
                area.append(text + "\n");
                area.setCaretPosition(area.getDocument().getLength());
            });
        }

        @Override
        public void onUsers(List<String> users) {
            SwingUtilities.invokeLater(() -> {
                Object selected = target.getSelectedItem();
                target.removeAllItems();
                target.addItem(ALL);
                for (String u : users) if (!u.equals(name)) target.addItem(u);
                if (selected != null) target.setSelectedItem(selected);
            });
        }
    }

    public ChatClientGUI(ChatService service, String name, String serverInfo) {
        super("RMI Chat - " + name + " @ " + serverInfo);
        this.service = service;
        this.name = name;

        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);

        JButton send = new JButton("Gửi");
        JPanel bottom = new JPanel(new BorderLayout(5, 5));
        bottom.add(target, BorderLayout.WEST);
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);

        setLayout(new BorderLayout(5, 5));
        add(new JScrollPane(area), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        send.addActionListener(e -> sendMessage());
        input.addActionListener(e -> sendMessage());

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                try {
                    service.leave(name);
                } catch (Exception ignored) {
                }
                System.exit(0);
            }
        });
        setSize(560, 420);
        setLocationRelativeTo(null);
    }

    private void sendMessage() {
        final String text = input.getText().trim();
        if (text.isEmpty()) return;
        final String to = (String) target.getSelectedItem();
        input.setText("");
        new Thread(() -> {
            try {
                if (ALL.equals(to)) {
                    service.broadcast(name, text);
                } else if (!service.sendPrivate(name, to, text)) {
                    SwingUtilities.invokeLater(() -> area.append("(Người dùng '" + to + "' không còn online)\n"));
                }
            } catch (RemoteException ex) {
                SwingUtilities.invokeLater(() -> area.append("(Lỗi gửi tin: " + ex.getMessage() + ")\n"));
            }
        }).start();
    }

    public static void main(String[] args) throws Exception {
        JTextField ip = new JTextField("localhost");
        JTextField port = new JTextField("1099");
        JTextField user = new JTextField();
        JPanel form = new JPanel(new GridLayout(0, 2, 5, 5));
        form.add(new JLabel("IP server:"));
        form.add(ip);
        form.add(new JLabel("Cổng:"));
        form.add(port);
        form.add(new JLabel("Tên của bạn:"));
        form.add(user);
        if (JOptionPane.showConfirmDialog(null, form, "Kết nối chat", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) return;

        String serverIp = ip.getText().trim();
        int p = Integer.parseInt(port.getText().trim());
        String name = user.getText().trim();
        try {
            System.setProperty("java.rmi.server.hostname", NetUtil.localIpToward(serverIp));
            ChatService service = (ChatService) LocateRegistry.getRegistry(serverIp, p)
                    .lookup(ChatServerMain.SERVICE_NAME);
            // tạo frame trước để Listener cập nhật được giao diện ngay khi join
            ChatClientGUI gui = new ChatClientGUI(service, name, serverIp + ":" + p);
            Listener listener = gui.new Listener();
            if (!service.join(name, listener)) {
                JOptionPane.showMessageDialog(null, "Tên đã có người dùng hoặc không hợp lệ.");
                System.exit(1);
            }
            gui.setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Không kết nối được server:\n" + ex);
            System.exit(1);
        }
    }
}
