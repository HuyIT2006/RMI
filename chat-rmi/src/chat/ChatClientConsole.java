package chat;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Scanner;

/**
 * Client chat dạng dòng lệnh.
 * Cách dùng: java chat.ChatClientConsole <IP_server> <cổng> <tên> [IP_máy_mình]
 * Lệnh:  /to <tên> <nội dung>  : nhắn riêng
 *        /users                : xem ai đang online
 *        /quit                 : thoát
 */
public class ChatClientConsole {

    static class Listener extends UnicastRemoteObject implements ChatListener {
        Listener() throws RemoteException {
            super(0);
        }

        @Override
        public void onMessage(String text) {
            System.out.println(text);
        }

        @Override
        public void onUsers(List<String> users) {
            // console không cần in lại danh sách mỗi lần; dùng lệnh /users
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Cách dùng: java chat.ChatClientConsole <IP_server> <cổng> <tên> [IP_máy_mình]");
            return;
        }
        String serverIp = args[0];
        int port = Integer.parseInt(args[1]);
        String name = args[2];
        String myIp = args.length > 3 ? args[3] : NetUtil.localIpToward(serverIp);

        // Server gọi ngược (callback) về client nên client cũng phải khai báo IP của mình.
        System.setProperty("java.rmi.server.hostname", myIp);

        ChatService service = (ChatService) LocateRegistry.getRegistry(serverIp, port)
                .lookup(ChatServerMain.SERVICE_NAME);

        Listener listener = new Listener();
        if (!service.join(name, listener)) {
            System.out.println("Tên '" + name + "' đã có người dùng hoặc không hợp lệ.");
            System.exit(1);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                service.leave(name);
            } catch (Exception ignored) {
            }
        }));

        System.out.println("Đã vào phòng chat với tên '" + name + "'. Gõ /quit để thoát.");
        Scanner sc = new Scanner(System.in, "UTF-8");
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            if (line.equals("/quit")) break;
            if (line.equals("/users")) {
                System.out.println("Đang online: " + service.getUsers());
            } else if (line.startsWith("/to ")) {
                String[] p = line.split("\\s+", 3);
                if (p.length < 3) {
                    System.out.println("Cú pháp: /to <tên> <nội dung>");
                } else if (!service.sendPrivate(name, p[1], p[2])) {
                    System.out.println("Người dùng '" + p[1] + "' không online.");
                }
            } else {
                service.broadcast(name, line);
            }
        }
        service.leave(name);
        System.exit(0);
    }
}
