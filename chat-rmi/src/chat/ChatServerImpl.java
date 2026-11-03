package chat;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Cài đặt dịch vụ chat chạy trên server. */
public class ChatServerImpl extends UnicastRemoteObject implements ChatService {

    /** Mỗi client có 1 luồng gửi riêng: giữ đúng thứ tự tin nhắn và client chậm không làm kẹt người khác. */
    private static class Handle {
        final String name;
        final ChatListener listener;
        final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        Handle(String name, ChatListener listener) {
            this.name = name;
            this.listener = listener;
        }
    }

    private final Map<String, Handle> clients = new ConcurrentHashMap<>();

    public ChatServerImpl(int port) throws RemoteException {
        super(port);
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    @Override
    public boolean join(String name, ChatListener listener) {
        if (name == null || listener == null) return false;
        final String n = name.trim();
        if (n.isEmpty()) return false;
        if (clients.putIfAbsent(n, new Handle(n, listener)) != null) return false;
        System.out.println("[Server] " + n + " vào phòng chat");
        sendToAll("[" + now() + "] *** " + n + " đã tham gia phòng chat ***");
        pushUserList();
        return true;
    }

    @Override
    public void leave(String name) {
        Handle h = clients.remove(name);
        if (h != null) {
            h.sender.shutdown();
            System.out.println("[Server] " + name + " đã rời phòng chat");
            sendToAll("[" + now() + "] *** " + name + " đã rời phòng chat ***");
            pushUserList();
        }
    }

    @Override
    public void broadcast(String from, String message) {
        sendToAll("[" + now() + "] " + from + ": " + message);
    }

    @Override
    public boolean sendPrivate(String from, String to, String message) {
        Handle target = clients.get(to);
        if (target == null) return false;
        String text = "[" + now() + "] (riêng) " + from + " → " + to + ": " + message;
        deliver(target, text);
        Handle sender = clients.get(from);
        if (sender != null && sender != target) deliver(sender, text);
        return true;
    }

    @Override
    public List<String> getUsers() {
        List<String> list = new ArrayList<>(clients.keySet());
        Collections.sort(list);
        return list;
    }

    // ---------------------------------------------------------------- nội bộ

    private void sendToAll(String text) {
        for (Handle h : clients.values()) deliver(h, text);
    }

    private void pushUserList() {
        final List<String> users = getUsers();
        for (Handle h : clients.values()) {
            try {
                h.sender.submit(() -> {
                    try {
                        h.listener.onUsers(users);
                    } catch (RemoteException e) {
                        drop(h);
                    }
                });
            } catch (RejectedExecutionException ignored) {
            }
        }
    }

    private void deliver(Handle h, String text) {
        try {
            h.sender.submit(() -> {
                try {
                    h.listener.onMessage(text);
                } catch (RemoteException e) {
                    drop(h);
                }
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    /** Client mất kết nối (tắt đột ngột) -> gỡ khỏi danh sách. */
    private void drop(Handle h) {
        if (clients.remove(h.name, h)) {
            h.sender.shutdown();
            System.out.println("[Server] " + h.name + " mất kết nối");
            sendToAll("[" + now() + "] *** " + h.name + " mất kết nối ***");
            pushUserList();
        }
    }
}
