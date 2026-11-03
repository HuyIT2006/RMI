package chat;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

/** Tự kiểm tra nhanh trên 1 máy: java chat.ChatSelfTest */
public class ChatSelfTest {
    static class Rec extends UnicastRemoteObject implements ChatListener {
        final List<String> msgs = Collections.synchronizedList(new ArrayList<>());

        Rec() throws RemoteException {
            super(0);
        }

        public void onMessage(String t) {
            msgs.add(t);
        }

        public void onUsers(List<String> u) {
        }

        boolean has(String s) {
            synchronized (msgs) {
                for (String m : msgs) if (m.contains(s)) return true;
            }
            return false;
        }
    }

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  [OK]   " : "  [FAIL] ") + what);
        if (!ok) System.exit(1);
    }

    public static void main(String[] a) throws Exception {
        System.setProperty("java.rmi.server.hostname", "127.0.0.1");
        int port = 11990;
        ChatServerImpl srv = new ChatServerImpl(port);
        Registry reg = LocateRegistry.createRegistry(port);
        reg.rebind(ChatServerMain.SERVICE_NAME, srv);
        ChatService svc = (ChatService) LocateRegistry.getRegistry("127.0.0.1", port).lookup(ChatServerMain.SERVICE_NAME);

        Rec an = new Rec(), binh = new Rec(), cuong = new Rec();
        check(svc.join("an", an), "an tham gia");
        check(svc.join("binh", binh), "binh tham gia");
        check(!svc.join("an", cuong), "tên trùng bị từ chối");
        check(svc.join("cuong", cuong), "cuong tham gia");

        svc.broadcast("an", "xin chao moi nguoi");
        check(svc.sendPrivate("an", "binh", "tin rieng"), "gửi riêng cho binh");
        check(!svc.sendPrivate("an", "khongco", "x"), "gửi riêng cho người không tồn tại bị từ chối");
        Thread.sleep(500);

        check(an.has("xin chao") && binh.has("xin chao") && cuong.has("xin chao"), "broadcast tới cả 3 người");
        check(binh.has("tin rieng") && an.has("tin rieng"), "tin riêng tới người gửi và người nhận");
        check(!cuong.has("tin rieng"), "người thứ ba KHÔNG thấy tin riêng");
        check(svc.getUsers().equals(Arrays.asList("an", "binh", "cuong")), "danh sách online đúng: " + svc.getUsers());

        svc.leave("cuong");
        Thread.sleep(300);
        check(svc.getUsers().size() == 2 && an.has("cuong đã rời"), "rời phòng được thông báo");
        System.out.println("TẤT CẢ ĐỀU PASS");
        System.exit(0);
    }
}
