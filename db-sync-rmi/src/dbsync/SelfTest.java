package dbsync;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Tự kiểm tra trên 1 máy: dựng 3 node (3 file SQLite riêng), thử thêm/sửa/xoá,
 * tắt 1 node rồi bật lại, sửa xung đột. Chạy: java -cp "out:lib/*" dbsync.SelfTest
 */
public class SelfTest {
    static final int[] PORTS = {21001, 21002, 21003};
    static File dir;

    static NodeConfig cfg(int i) {
        NodeConfig c = new NodeConfig();
        c.nodeId = "node" + (i + 1);
        c.host = "127.0.0.1";
        c.port = PORTS[i];
        c.syncIntervalSec = 1;
        c.jdbcUrl = "jdbc:sqlite:" + new File(dir, c.nodeId + ".db").getAbsolutePath();
        c.jdbcUser = "";
        c.jdbcPass = "";
        for (int j = 0; j < 3; j++) if (j != i) c.peers.add(new NodeConfig.Peer("127.0.0.1", PORTS[j]));
        return c;
    }

    static NodeServer startNode(int i) throws Exception {
        NodeServer n = new NodeServer(cfg(i));
        n.setLogger(s -> System.out.println("      " + s));
        n.start();
        return n;
    }

    static String dump(NodeServer n) throws Exception {
        List<String> rows = new ArrayList<>();
        for (Student s : n.getDb().getAll(true))
            rows.add(s.id + "|" + s.name + "|" + s.email + "|" + s.deleted);
        return String.join("; ", rows);
    }

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  [OK]   " : "  [FAIL] ") + what);
        if (!ok) System.exit(1);
    }

    static boolean same(NodeServer... ns) throws Exception {
        String first = dump(ns[0]);
        for (NodeServer n : ns) if (!dump(n).equals(first)) return false;
        return true;
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.rmi.server.hostname", "127.0.0.1");
        dir = Files.createTempDirectory("dbsync").toFile();

        System.out.println("1) Khởi động 3 node");
        NodeServer n1 = startNode(0), n2 = startNode(1), n3 = startNode(2);

        System.out.println("2) Mỗi node thêm 1 sinh viên");
        n1.upsert("SV01", "Nguyen Van A", "a@x.vn");
        n2.upsert("SV02", "Tran Thi B", "b@x.vn");
        n3.upsert("SV03", "Le Van C", "c@x.vn");
        Thread.sleep(1500);
        check(n1.list().size() == 3 && same(n1, n2, n3), "cả 3 CSDL đều có 3 sinh viên giống nhau");

        System.out.println("3) node2 sửa SV01");
        n2.upsert("SV01", "Nguyen Van A (da sua)", "a@x.vn");
        Thread.sleep(1500);
        check(n3.getDb().get("SV01").name.contains("da sua") && same(n1, n2, n3), "thay đổi lan sang cả 3 node");

        System.out.println("4) Tắt node3; node1 xoá SV02, node2 thêm SV04");
        n3.stop();
        Thread.sleep(300);
        n1.delete("SV02");
        n2.upsert("SV04", "Pham D", "d@x.vn");
        Thread.sleep(1500);
        check(same(n1, n2), "node1 và node2 vẫn đồng nhất khi node3 tắt");

        System.out.println("5) Bật lại node3 -> tự bắt kịp");
        n3 = startNode(2);
        Thread.sleep(2500);
        check(n3.list().size() == 3 && n3.getDb().get("SV02").deleted && same(n1, n2, n3),
                "node3 đã nhận lệnh xoá SV02 và SV04 bỏ lỡ: " + dump(n3));

        System.out.println("6) Xung đột: node1 và node3 cùng sửa SV03");
        n1.upsert("SV03", "Ban cua node1", "c@x.vn");
        n3.upsert("SV03", "Ban cua node3", "c@x.vn");
        Thread.sleep(2500);
        check(same(n1, n2, n3), "sau xung đột 3 node hội tụ về cùng 1 giá trị: " + n2.getDb().get("SV03").name);

        System.out.println("TẤT CẢ ĐỀU PASS");
        System.exit(0);
    }
}
