# Bài tập Lập trình mạng – Java RMI

Gồm 2 chương trình:

1. **chat-rmi** – Chat nhiều người bằng RMI (chat nhóm + nhắn riêng, có bản console và bản giao diện Swing).
2. **db-sync-rmi** – Đồng bộ 3 cơ sở dữ liệu trên 3 server (multi-master, dùng RMI).

Yêu cầu: JDK 11 trở lên (`javac -version` để kiểm tra).

## 0. Biên dịch

- Linux/macOS: `./build.sh`
- Windows: bấm đúp `build.bat` (hoặc chạy trong cmd)

Kết quả nằm trong thư mục `out/`. Các lệnh chạy bên dưới đều chạy từ thư mục gốc của project.

> Windows: trước khi chạy hãy gõ `chcp 65001` trong cmd để hiển thị tiếng Việt, và dùng dấu `;` thay cho `:` trong `-cp`.
> (Windows: `-cp "out;lib/*"`   –   Linux/macOS: `-cp "out:lib/*"`)

---

## 1. Chat RMI

### Kiến trúc
- `ChatService` (remote interface, **server** cung cấp): `join`, `leave`, `broadcast`, `sendPrivate`, `getUsers`.
- `ChatListener` (remote interface, **client** cung cấp): `onMessage`, `onUsers` – server gọi ngược (callback) để đẩy tin tới client.
- `ChatServerImpl`: giữ danh sách client, mỗi client có một luồng gửi riêng (đúng thứ tự, client chậm không kẹt người khác), tự gỡ client mất kết nối.
- `ChatServerMain`, `ChatClientConsole`, `ChatClientGUI`: các chương trình chạy.

### Chạy trên 2 máy
Giả sử máy A (IP `192.168.1.10`) làm server, máy B làm client.

**Máy A – server**
```
java -cp out chat.ChatServerMain 192.168.1.10 1099
```
**Máy A và máy B – client (giao diện)**
```
java -cp out chat.ChatClientGUI
```
Nhập IP server `192.168.1.10`, cổng `1099`, tên của bạn.

Hoặc client console:
```
java -cp out chat.ChatClientConsole 192.168.1.10 1099 an
```
Lệnh trong console: gõ chữ để chat nhóm, `/to binh nội dung` nhắn riêng, `/users` xem người online, `/quit` thoát.

Kiểm tra nhanh trên 1 máy: `java -cp out chat.ChatSelfTest`

### Lưu ý khi chạy 2 máy
- Tắt/mở cổng firewall cho cổng `1099` (Windows Defender Firewall → cho phép Java, hoặc tạm tắt khi thử).
- Hai máy phải cùng mạng LAN và ping thấy nhau.
- Phải truyền đúng IP LAN của server (không dùng `localhost`) – chương trình đã gọi `System.setProperty("java.rmi.server.hostname", ip)`, thiếu dòng này thường bị lỗi *connection refused to host 127.0.0.1*.

---

## 2. Đồng bộ 3 CSDL trên 3 server

### Ý tưởng
Cả 3 node ngang hàng (peer-to-peer, **multi-master**): nhập dữ liệu ở node nào cũng được.
Bảng `students(id, name, email, updated_at, origin, deleted)`.

| Cơ chế | Mô tả |
|---|---|
| **Đẩy tức thì** | Khi thêm/sửa/xoá ở một node, node đó ghi vào CSDL của mình rồi gọi RMI `applyChange()` sang 2 node còn lại. |
| **Kéo định kỳ** | Mỗi `sync.interval` giây (và ngay lúc khởi động), node gọi RMI `getAll()` của 2 node kia để đối chiếu → node bị tắt rồi bật lại tự bắt kịp. |
| **Xung đột** | Cùng một bản ghi bị sửa ở 2 node: bản có `updated_at` mới hơn thắng (nếu bằng nhau thì so tên node) → cả 3 CSDL luôn hội tụ về cùng giá trị. |
| **Xoá** | Xoá logic (`deleted=1`, dấu xoá) để lệnh xoá cũng được đồng bộ, không bị node khác "hồi sinh" lại. |
| **Node offline** | Gọi RMI có timeout 4 giây, node nào chết thì bỏ qua và đồng bộ bù ở lần sau. |

Các file: `Student` (bản ghi), `SyncService` (remote interface), `NodeServer` (RMI server + client + đồng bộ), `Db` (JDBC), `NodeConfig`, `NodeMain` (menu console), `SelfTest`.

### Cách A – Chạy thử nhanh, 1 máy, không cần cài CSDL (SQLite)
Mở 3 cửa sổ terminal:
```
java -cp "out:lib/*" dbsync.NodeMain db-sync-rmi/config/node1-sqlite.properties
java -cp "out:lib/*" dbsync.NodeMain db-sync-rmi/config/node2-sqlite.properties
java -cp "out:lib/*" dbsync.NodeMain db-sync-rmi/config/node3-sqlite.properties
```
Kiểm thử tự động: `java -cp "out:lib/*" dbsync.SelfTest`

### Cách B – Chạy đúng yêu cầu: 2 máy tính, 3 server, MySQL
1. Cài MySQL trên cả 2 máy. Tải **MySQL Connector/J** (`mysql-connector-j-x.x.x.jar`) bỏ vào thư mục `lib/`.
2. Chạy `db-sync-rmi/schema_mysql.sql` để tạo database: máy A cần `db_node1`, `db_node2`; máy B cần `db_node3` (tạo cả 3 ở mỗi máy cũng không sao).
3. Phân bổ: **Máy A** chạy node1 + node2 (2 cổng khác nhau, 2 database khác nhau), **Máy B** chạy node3.
4. Sửa IP trong `node1-mysql.properties`, `node2-mysql.properties`, `node3-mysql.properties` cho khớp IP thật của 2 máy (mặc định ví dụ: máy A = `192.168.1.10`, máy B = `192.168.1.20`) và mật khẩu MySQL.
5. Mở firewall cho các cổng `1101`, `1102`, `1103`.
6. Chạy:

   Máy A (2 cửa sổ):
   ```
   java -cp "out:lib/*" dbsync.NodeMain db-sync-rmi/config/node1-mysql.properties
   java -cp "out:lib/*" dbsync.NodeMain db-sync-rmi/config/node2-mysql.properties
   ```
   Máy B:
   ```
   java -cp "out:lib/*" dbsync.NodeMain db-sync-rmi/config/node3-mysql.properties
   ```

> Dùng SQL Server / PostgreSQL: chỉ cần đổi `jdbc.url`, `jdbc.user`, `jdbc.password` và thêm driver tương ứng vào `lib/`.
> Câu `CREATE TABLE IF NOT EXISTS` dùng được với MySQL, PostgreSQL, SQLite. SQL Server phải sửa lại câu này trong `Db.init()`.

### Kịch bản demo cho thầy
1. Bật cả 3 node → menu 5 cho thấy các node ONLINE.
2. Node1 thêm SV01, node2 thêm SV02, node3 thêm SV03 → menu 3 ở mỗi node đều thấy cả 3 sinh viên (có thể mở thẳng MySQL để chứng minh 3 database giống nhau).
3. Node2 sửa SV01 → node1, node3 cập nhật theo.
4. Tắt node3. Node1 xoá SV02, node2 thêm SV04. Bật lại node3 → tự động nhận đủ thay đổi (hoặc bấm menu 4 để đồng bộ ngay).
5. Node1 và node3 cùng sửa SV03 → sau vài giây 3 node cùng hội tụ về một giá trị (bản sửa sau cùng).

### Lưu ý
- Đồng hồ của các máy nên chạy gần đúng nhau (bật đồng bộ giờ tự động) vì quy tắc phân xử xung đột dựa trên thời gian.
- Bản này đồng bộ **toàn bộ bảng** mỗi chu kỳ cho đơn giản và đảm bảo đúng; với dữ liệu lớn nên cải tiến thành chỉ kéo các dòng có `updated_at` lớn hơn mốc lần đồng bộ trước.
