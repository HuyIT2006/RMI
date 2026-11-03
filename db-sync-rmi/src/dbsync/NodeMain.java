package dbsync;

import java.util.List;
import java.util.Scanner;

/**
 * Chạy một node đồng bộ CSDL.
 * Cách dùng: java dbsync.NodeMain <file_cấu_hình.properties>
 */
public class NodeMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Cách dùng: java dbsync.NodeMain <file_cấu_hình.properties>");
            return;
        }
        NodeConfig cfg = NodeConfig.load(args[0]);

        // Địa chỉ mà các node khác dùng để gọi vào máy này (bắt buộc đúng khi chạy trên nhiều máy).
        System.setProperty("java.rmi.server.hostname", cfg.host);

        NodeServer node = new NodeServer(cfg);
        node.start();

        Scanner sc = new Scanner(System.in, "UTF-8");
        while (true) {
            System.out.println();
            System.out.println("===== NODE " + cfg.nodeId + " =====");
            System.out.println("1. Thêm / sửa sinh viên");
            System.out.println("2. Xoá sinh viên");
            System.out.println("3. Xem dữ liệu của node này");
            System.out.println("4. Đồng bộ ngay");
            System.out.println("5. Kiểm tra kết nối các node khác");
            System.out.println("0. Thoát");
            System.out.print("Chọn: ");
            if (!sc.hasNextLine()) break;
            String choice = sc.nextLine().trim();
            try {
                switch (choice) {
                    case "1": {
                        System.out.print("Mã SV: ");
                        String id = sc.nextLine().trim();
                        System.out.print("Họ tên: ");
                        String name = sc.nextLine().trim();
                        System.out.print("Email: ");
                        String email = sc.nextLine().trim();
                        if (id.isEmpty()) {
                            System.out.println("Mã SV không được trống.");
                            break;
                        }
                        System.out.println("Đã lưu: " + node.upsert(id, name, email));
                        break;
                    }
                    case "2": {
                        System.out.print("Mã SV cần xoá: ");
                        String id = sc.nextLine().trim();
                        System.out.println(node.delete(id) ? "Đã xoá." : "Không tìm thấy mã SV này.");
                        break;
                    }
                    case "3": {
                        List<Student> list = node.list();
                        System.out.println("--- " + list.size() + " sinh viên ---");
                        for (Student s : list) System.out.println(s);
                        break;
                    }
                    case "4":
                        System.out.println(node.syncNow(true));
                        break;
                    case "5":
                        System.out.print(node.peerStatus());
                        break;
                    case "0":
                        node.stop();
                        System.exit(0);
                        return;
                    default:
                        System.out.println("Lựa chọn không hợp lệ.");
                }
            } catch (Exception e) {
                System.out.println("Lỗi: " + e);
            }
        }
        node.stop();
        System.exit(0);
    }
}
