package chat;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/** Interface REMOTE mà SERVER cung cấp cho các client gọi. */
public interface ChatService extends Remote {
    /** Tham gia phòng chat. Trả về false nếu tên đã có người dùng. */
    boolean join(String name, ChatListener listener) throws RemoteException;

    /** Rời phòng chat. */
    void leave(String name) throws RemoteException;

    /** Gửi tin nhắn cho tất cả mọi người. */
    void broadcast(String from, String message) throws RemoteException;

    /** Gửi tin nhắn riêng. Trả về false nếu người nhận không online. */
    boolean sendPrivate(String from, String to, String message) throws RemoteException;

    /** Danh sách người đang online. */
    List<String> getUsers() throws RemoteException;
}
