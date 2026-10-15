import java.sql.*;

/**
 * 自测辅助（仅本地试运行用）：
 *   java -cp ... TestData.java seed    -> 直插一个临时管理员 900000 / admin12345
 *   java -cp ... TestData.java cleanup -> 清掉自测产生的账号(900001/900002)及其作品
 */
public class TestData {
    public static void main(String[] args) throws Exception {
        String pwd = System.getenv("COC_DB_PASSWORD");
        String url = "jdbc:mysql://127.0.0.1:3306/coc_platform?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
        Class.forName("com.mysql.cj.jdbc.Driver");
        String mode = args.length > 0 ? args[0] : "seed";
        try (Connection c = DriverManager.getConnection(url, "root", pwd)) {
            if ("cleanup".equals(mode)) {
                try (PreparedStatement p1 = c.prepareStatement(
                        "DELETE FROM project WHERE author_id IN (SELECT user_id FROM `user` WHERE qq_account IN ('900001','900002'))")) {
                    System.out.println("deleted projects: " + p1.executeUpdate());
                }
                try (PreparedStatement p2 = c.prepareStatement(
                        "DELETE FROM `user` WHERE qq_account IN ('900001','900002')")) {
                    System.out.println("deleted test users: " + p2.executeUpdate());
                }
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='coc_platform'")) {
                    rs.next();
                    System.out.println("tables_in_db=" + rs.getInt(1));
                }
            } else {
                org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder enc =
                        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
                String hash = enc.encode("admin12345");
                try (PreparedStatement del = c.prepareStatement("DELETE FROM `user` WHERE qq_account = ?")) {
                    del.setString(1, "900000");
                    del.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO `user` (qq_account, password_hash, nickname, type, status, created_at, updated_at) "
                                + "VALUES (?, ?, ?, 3, 1, NOW(), NOW())")) {
                    ps.setString(1, "900000");
                    ps.setString(2, hash);
                    ps.setString(3, "selftest-admin");
                    ps.executeUpdate();
                }
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT user_id, qq_account, nickname, type, status FROM `user` WHERE qq_account='900000'")) {
                    while (rs.next()) {
                        System.out.println("ADMIN OK: user_id=" + rs.getLong(1) + " qq=" + rs.getString(2)
                                + " nickname=" + rs.getString(3) + " type=" + rs.getInt(4) + " status=" + rs.getInt(5));
                    }
                }
            }
        }
    }
}
