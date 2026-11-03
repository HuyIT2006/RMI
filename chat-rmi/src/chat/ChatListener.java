package chat;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Interface REMOTE mà CLIENT cung cấp cho SERVER gọi ngược lại (callback).
 * Nhờ đó server "đẩy" tin nhắn tới client mà client không cần hỏi liên tục.
 */
public interface ChatListener extends Remote {
    void onMessage(String text) throws RemoteException;

    void onUsers(List<String> users) throws RemoteException;
}
