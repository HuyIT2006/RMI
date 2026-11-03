package dbsync;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/** Dịch vụ RMI mà mỗi node cung cấp cho 2 node còn lại. */
public interface SyncService extends Remote {
    /** Node khác đẩy một thay đổi sang; nhận nếu nó mới hơn bản đang có. */
    void applyChange(Student s) throws RemoteException;

    /** Node khác kéo toàn bộ dữ liệu (kể cả dấu xoá) về để đối chiếu. */
    List<Student> getAll() throws RemoteException;

    String getNodeId() throws RemoteException;
}
