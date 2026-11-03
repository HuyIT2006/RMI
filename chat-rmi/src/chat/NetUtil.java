package chat;

import java.net.DatagramSocket;
import java.net.InetAddress;

public class NetUtil {
    /**
     * Tìm địa chỉ IP của máy này trong mạng LAN theo hướng đi tới 'host'.
     * (UDP connect không gửi gói tin nào, chỉ để hệ điều hành chọn card mạng phù hợp.)
     */
    public static String localIpToward(String host) {
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(InetAddress.getByName(host), 9);
            String ip = s.getLocalAddress().getHostAddress();
            if (!"0.0.0.0".equals(ip)) return ip;
        } catch (Exception ignored) {
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
