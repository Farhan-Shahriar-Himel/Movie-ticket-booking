public class TestDriver {
    public static void main(String[] args) {
        try {
            Class.forName("org.sqlite.JDBC");
            System.out.println("Driver loaded successfully!");
        } catch (ClassNotFoundException e) {
            System.out.println("Driver NOT found: " + e.getMessage());
        }
    }
}
