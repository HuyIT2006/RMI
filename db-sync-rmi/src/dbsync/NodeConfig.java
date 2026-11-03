package dbsync;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Cấu hình của một node, đọc từ file .properties */
public class NodeConfig {
    public static class Peer {
        public final String host;
        public final int port;

        public Peer(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    public String nodeId;
    public String host;
    public int port;
    public long syncIntervalSec = 10;
    public String jdbcUrl, jdbcUser, jdbcPass;
    public List<Peer> peers = new ArrayList<>();

    public static NodeConfig load(String path) throws IOException {
        Properties p = new Properties();
        try (InputStreamReader r = new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8)) {
            p.load(r);
        }
        NodeConfig c = new NodeConfig();
        c.nodeId = p.getProperty("node.id").trim();
        c.host = p.getProperty("node.host", "127.0.0.1").trim();
        c.port = Integer.parseInt(p.getProperty("node.port").trim());
        c.syncIntervalSec = Long.parseLong(p.getProperty("sync.interval", "10").trim());
        c.jdbcUrl = p.getProperty("jdbc.url").trim();
        c.jdbcUser = p.getProperty("jdbc.user", "").trim();
        c.jdbcPass = p.getProperty("jdbc.password", "");
        for (String s : p.getProperty("peers", "").split(",")) {
            s = s.trim();
            if (s.isEmpty()) continue;
            int i = s.lastIndexOf(':');
            c.peers.add(new Peer(s.substring(0, i), Integer.parseInt(s.substring(i + 1))));
        }
        return c;
    }
}
