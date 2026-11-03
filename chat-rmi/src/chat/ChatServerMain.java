package chat;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Chạy server chat.
 * Cách dùng:  java chat.ChatServerMain [IP_của_máy_server] [cổng]
 * Ví dụ:      java chat.ChatServerMain 192.168.1.10 1099
 */
public class ChatServerMain {
    public static final String SERVICE_NAME = "ChatService";

    public static void main(String[] args) throws Exception {
        String ip = args.length > 0 ? args[0] : NetUtil.localIpToward("8.8.8.8");
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 1099;

        // QUAN TRỌNG: báo cho RMI biết địa chỉ mà client bên ngoài dùng để kết nối lại tới server.
        // Nếu thiếu dòng này, nhiều máy sẽ gặp lỗi "Connection refused to host: 127.0.0.1".
        System.setProperty("java.rmi.server.hostname", ip);

        ChatServerImpl service = new ChatServerImpl(port);
        Registry registry = LocateRegistry.createRegistry(port);
        registry.rebind(SERVICE_NAME, service);

        System.out.println("Chat server đang chạy tại " + ip + ":" + port + "  (dịch vụ: " + SERVICE_NAME + ")");
        System.out.println("Nhấn Ctrl+C để dừng.");
    }
}
