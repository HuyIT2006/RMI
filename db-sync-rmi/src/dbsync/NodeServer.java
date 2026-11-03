package dbsync;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Một node (server) trong hệ thống 3 CSDL. Mỗi node:
 *  1. Có CSDL riêng (Db).
 *  2. Là RMI server: 2 node kia gọi applyChange()/getAll() để đẩy/kéo dữ liệu.
 *  3. Là RMI client: khi có thay đổi cục bộ thì đẩy ngay sang 2 node còn lại (replicate).
 *  4. Định kỳ kéo dữ liệu của các node kia về đối chiếu (anti-entropy) -> node tắt rồi bật lại vẫn tự bắt kịp.
 */
public class NodeServer extends UnicastRemoteObject implements SyncService {
    public static final String BIND_NAME = "SyncNode";
    private static final int CALL_TIMEOUT_SEC = 4;

    private interface PeerCall<T> {
        T run(SyncService s) throws Exception;
    }

    private final NodeConfig cfg;
    private final Db db;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    private ScheduledExecutorService scheduler;
    private Registry registry;
    private volatile Consumer<String> logger = System.out::println;

    public NodeServer(NodeConfig cfg) throws RemoteException {
        super(cfg.port);   // export object cùng cổng với registry => chỉ cần mở 1 cổng
        this.cfg = cfg;
        this.db = new Db(cfg.jdbcUrl, cfg.jdbcUser, cfg.jdbcPass);
    }

    public void setLogger(Consumer<String> l) {
        this.logger = l;
    }

    public Db getDb() {
        return db;
    }

    public String getId() {
        return cfg.nodeId;
    }

    private void log(String s) {
        logger.accept("[" + cfg.nodeId + "] " + s);
    }

    // ------------------------------------------------------- vòng đời

    public void start() throws Exception {
        db.init();
        registry = LocateRegistry.createRegistry(cfg.port);
        registry.rebind(BIND_NAME, this);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                syncNow(false);
            } catch (Throwable ignored) {
            }
        }, cfg.syncIntervalSec, cfg.syncIntervalSec, TimeUnit.SECONDS);

        log("đã chạy tại " + cfg.host + ":" + cfg.port + ", peers = " + cfg.peers);
        pool.submit(() -> syncNow(true));   // bù dữ liệu bỏ lỡ lúc tắt máy
    }

    public void stop() {
        try {
            if (scheduler != null) scheduler.shutdownNow();
            if (registry != null) {
                try {
                    registry.unbind(BIND_NAME);
                } catch (Exception ignored) {
                }
                UnicastRemoteObject.unexportObject(registry, true);
            }
            UnicastRemoteObject.unexportObject(this, true);
        } catch (Exception ignored) {
        }
        pool.shutdownNow();
    }

    // ------------------------------------------------------- phía SERVER (node khác gọi vào)

    @Override
    public void applyChange(Student s) throws RemoteException {
        try {
            if (db.applyIfNewer(s)) log("← nhận từ " + s.origin + ": " + s);
        } catch (SQLException e) {
            throw new RemoteException("Lỗi CSDL: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Student> getAll() throws RemoteException {
        try {
            return new ArrayList<>(db.getAll(true));
        } catch (SQLException e) {
            throw new RemoteException("Lỗi CSDL: " + e.getMessage(), e);
        }
    }

    @Override
    public String getNodeId() {
        return cfg.nodeId;
    }

    // ------------------------------------------------------- thao tác cục bộ (người dùng nhập)

    public Student upsert(String id, String name, String email) throws SQLException {
        Student s = db.writeLocal(id, name, email, false, cfg.nodeId);
        replicate(s);
        return s;
    }

    public boolean delete(String id) throws SQLException {
        Student cur = db.get(id);
        if (cur == null || cur.deleted) return false;
        Student s = db.writeLocal(id, cur.name, cur.email, true, cfg.nodeId);
        replicate(s);
        return true;
    }

    public List<Student> list() throws SQLException {
        return db.getAll(false);
    }

    // ------------------------------------------------------- phía CLIENT (gọi sang node khác)

    private SyncService lookup(NodeConfig.Peer p) throws Exception {
        return (SyncService) LocateRegistry.getRegistry(p.host, p.port).lookup(BIND_NAME);
    }

    /** Gọi RMI sang peer có giới hạn thời gian để node offline không làm treo chương trình. */
    private <T> T call(NodeConfig.Peer p, PeerCall<T> fn) throws Exception {
        Future<T> f = pool.submit(() -> fn.run(lookup(p)));
        try {
            return f.get(CALL_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            throw (c instanceof Exception) ? (Exception) c : e;
        }
    }

    /** Đẩy một thay đổi sang tất cả node còn lại (chạy nền, node nào offline thì để đồng bộ định kỳ lo). */
    private void replicate(Student s) {
        for (NodeConfig.Peer p : cfg.peers) {
            pool.submit(() -> {
                try {
                    call(p, svc -> {
                        svc.applyChange(s);
                        return null;
                    });
                    log("→ đã gửi " + s.id + " tới " + p);
                } catch (Exception e) {
                    log("✗ " + p + " không phản hồi, sẽ đồng bộ bù sau");
                }
            });
        }
    }

    /** Kéo dữ liệu từ các node khác về và gộp theo quy tắc bản mới hơn thắng. */
    public String syncNow(boolean verbose) {
        int applied = 0;
        StringBuilder sb = new StringBuilder();
        for (NodeConfig.Peer p : cfg.peers) {
            try {
                List<Student> remote = call(p, SyncService::getAll);
                for (Student s : remote) {
                    if (db.applyIfNewer(s)) applied++;
                }
                sb.append(p).append(": OK  ");
            } catch (Exception e) {
                sb.append(p).append(": OFFLINE  ");
            }
        }
        if (applied > 0) log("⟳ đồng bộ: cập nhật " + applied + " bản ghi");
        else if (verbose) log("⟳ đồng bộ: không có gì mới. " + sb);
        return sb.toString() + "(cập nhật " + applied + " bản ghi)";
    }

    public String peerStatus() {
        StringBuilder sb = new StringBuilder();
        for (NodeConfig.Peer p : cfg.peers) {
            try {
                String id = call(p, SyncService::getNodeId);
                sb.append("  ").append(p).append("  ->  ONLINE (").append(id).append(")\n");
            } catch (Exception e) {
                sb.append("  ").append(p).append("  ->  OFFLINE\n");
            }
        }
        return sb.toString();
    }
}
