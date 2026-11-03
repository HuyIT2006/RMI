package dbsync;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Truy cập CSDL cục bộ của node bằng JDBC (MySQL, SQL Server, SQLite... đều được,
 * chỉ cần đổi jdbc.url và thêm driver vào thư mục lib).
 * Các hàm đều synchronized để 2 luồng (nhập tay + đồng bộ nền) không ghi chồng nhau.
 */
public class Db {
    private final String url, user, pass;

    public Db(String url, String user, String pass) {
        this.url = url;
        this.user = (user == null || user.isEmpty()) ? null : user;
        this.pass = pass;
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }

    public synchronized void init() throws SQLException {
        try (Connection c = open(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS students ("
                    + "id VARCHAR(50) PRIMARY KEY, "
                    + "name VARCHAR(100), "
                    + "email VARCHAR(100), "
                    + "updated_at BIGINT NOT NULL, "
                    + "origin VARCHAR(20) NOT NULL, "
                    + "deleted INT NOT NULL DEFAULT 0)");
        }
    }

    private static Student map(ResultSet rs) throws SQLException {
        return new Student(rs.getString("id"), rs.getString("name"), rs.getString("email"),
                rs.getLong("updated_at"), rs.getString("origin"), rs.getInt("deleted") != 0);
    }

    private Student find(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id,name,email,updated_at,origin,deleted FROM students WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    private void save(Connection c, Student s, boolean exists) throws SQLException {
        String sql = exists
                ? "UPDATE students SET name=?, email=?, updated_at=?, origin=?, deleted=? WHERE id=?"
                : "INSERT INTO students(name,email,updated_at,origin,deleted,id) VALUES(?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.name);
            ps.setString(2, s.email);
            ps.setLong(3, s.updatedAt);
            ps.setString(4, s.origin);
            ps.setInt(5, s.deleted ? 1 : 0);
            ps.setString(6, s.id);
            ps.executeUpdate();
        }
    }

    public synchronized Student get(String id) throws SQLException {
        try (Connection c = open()) {
            return find(c, id);
        }
    }

    /** Ghi một thay đổi do chính node này tạo ra (thêm/sửa/xoá), tự gán thời điểm mới. */
    public synchronized Student writeLocal(String id, String name, String email, boolean deleted, String origin)
            throws SQLException {
        try (Connection c = open()) {
            Student cur = find(c, id);
            long ts = System.currentTimeMillis();
            if (cur != null && ts <= cur.updatedAt) ts = cur.updatedAt + 1; // luôn mới hơn bản cũ
            Student s = new Student(id, name, email, ts, origin, deleted);
            save(c, s, cur != null);
            return s;
        }
    }

    /** Nhận thay đổi từ node khác: chỉ ghi nếu mới hơn. Trả về true nếu có thay đổi. */
    public synchronized boolean applyIfNewer(Student s) throws SQLException {
        try (Connection c = open()) {
            Student cur = find(c, s.id);
            if (cur != null && !s.isNewerThan(cur)) return false;
            save(c, s, cur != null);
            return true;
        }
    }

    public synchronized List<Student> getAll(boolean includeDeleted) throws SQLException {
        List<Student> list = new ArrayList<>();
        String sql = "SELECT id,name,email,updated_at,origin,deleted FROM students"
                + (includeDeleted ? "" : " WHERE deleted=0") + " ORDER BY id";
        try (Connection c = open(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }
}
