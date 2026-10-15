import java.sql.*;

/** 全新库迁移自检：drop 掉临时库 coc_fresh_check（验证 V1__init.sql 在空库上可一次性建好） */
public class FreshCheck {
    public static void main(String[] args) throws Exception {
        String pwd = System.getenv("COC_DB_PASSWORD");
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:3306/?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false",
                "root", pwd)) {
            if (args.length > 0 && "inspect".equals(args[0])) {
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery(
                             "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='coc_fresh_check'")) {
                    rs.next();
                    System.out.println("coc_fresh_check tables=" + rs.getInt(1));
                }
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery(
                             "SELECT version, description, success FROM coc_fresh_check.flyway_schema_history ORDER BY installed_rank")) {
                    while (rs.next()) {
                        System.out.println("flyway: " + rs.getString(1) + " | " + rs.getString(2) + " | success=" + rs.getBoolean(3));
                    }
                }
            } else {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("DROP DATABASE IF EXISTS coc_fresh_check");
                    System.out.println("dropped coc_fresh_check");
                }
            }
        }
    }
}
