package dbsync;

import java.io.Serializable;

/**
 * Một bản ghi của bảng students. Được truyền qua RMI nên phải Serializable.
 * updatedAt + origin dùng để phân xử khi cùng một bản ghi bị sửa ở nhiều node (bản mới hơn thắng).
 * deleted = true là "dấu xoá" (tombstone): xoá logic để lệnh xoá cũng được đồng bộ sang node khác.
 */
public class Student implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String id;
    public final String name;
    public final String email;
    public final long updatedAt;   // thời điểm sửa (ms)
    public final String origin;    // node nào đã tạo ra phiên bản này
    public final boolean deleted;

    public Student(String id, String name, String email, long updatedAt, String origin, boolean deleted) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.updatedAt = updatedAt;
        this.origin = origin;
        this.deleted = deleted;
    }

    /** Quy tắc "last-write-wins": thời gian lớn hơn thắng; bằng nhau thì so tên node cho kết quả giống nhau ở mọi nơi. */
    public boolean isNewerThan(Student o) {
        if (updatedAt != o.updatedAt) return updatedAt > o.updatedAt;
        return origin.compareTo(o.origin) > 0;
    }

    @Override
    public String toString() {
        return id + " | " + name + " | " + email + (deleted ? " | (đã xoá)" : "")
                + " | v" + updatedAt + "@" + origin;
    }
}
