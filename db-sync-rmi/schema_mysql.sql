-- Chạy file này trên MySQL của MỖI máy (máy A tạo db_node1 + db_node2, máy B tạo db_node3).
-- Bảng students sẽ được chương trình tự tạo, bạn chỉ cần tạo database.
CREATE DATABASE IF NOT EXISTS db_node1 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS db_node2 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS db_node3 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- (Tham khảo) cấu trúc bảng mà chương trình tạo:
-- CREATE TABLE students (
--   id         VARCHAR(50) PRIMARY KEY,
--   name       VARCHAR(100),
--   email      VARCHAR(100),
--   updated_at BIGINT NOT NULL,      -- thời điểm sửa cuối (ms)
--   origin     VARCHAR(20) NOT NULL, -- node tạo ra phiên bản này
--   deleted    INT NOT NULL DEFAULT 0 -- 1 = đã xoá (dấu xoá để đồng bộ lệnh xoá)
-- );
