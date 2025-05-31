import java.io.File;
import java.sql.*;
import java.util.*;

// ---------------------- User Class ----------------------
class User {
    String username;
    String password;

    User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    String getUsername() {
        return username;
    }

    boolean checkPassword(String inputPassword) {
        return this.password.equals(inputPassword);
    }

    @Override
    public String toString() {
        return username + "," + password;
    }
}

// ---------------------- Auth System with DB ----------------------
class AuthSystem {
    private Connection conn;

    public AuthSystem(Connection conn) {
        this.conn = conn;
        createUsersTable();
    }

    private void createUsersTable() {
        String sql = "CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY, password TEXT NOT NULL)";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.out.println("Error creating users table: " + e.getMessage());
        }
    }

    public void registerUser(String username, String password) throws Exception {
        if (userExists(username)) {
            throw new Exception("Username already exists.");
        }
        String sql = "INSERT INTO users(username, password) VALUES (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new Exception("Error registering user: " + e.getMessage());
        }
    }

    public User loginUser(String username, String password) throws Exception {
        String sql = "SELECT password FROM users WHERE username = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String storedPassword = rs.getString("password");
                if (storedPassword.equals(password)) {
                    return new User(username, password);
                } else {
                    throw new Exception("Incorrect password.");
                }
            } else {
                throw new Exception("Username not found.");
            }
        } catch (SQLException e) {
            throw new Exception("Error logging in: " + e.getMessage());
        }
    }

    private boolean userExists(String username) {
        String sql = "SELECT username FROM users WHERE username = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.out.println("Error checking user: " + e.getMessage());
            return false;
        }
    }
}

// ---------------------- Movie Class ----------------------
class Movie {
    String title;
    String time;
    int hallNumber;

    Movie(String title, String time, int hallNumber) {
        this.title = title;
        this.time = time;
        this.hallNumber = hallNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getTime() {
        return time;
    }

    public int getHallNumber() {
        return hallNumber;
    }

    @Override
    public String toString() {
        return "Hall " + hallNumber + ": " + title + " at " + time;
    }
}

// ---------------------- Hall Class with DB Seats ----------------------
class Hall {
    Movie movie;
    int rows, cols;
    char[][] seats; // 'A' Available, 'B' Booked
    Connection conn;

    Hall(Movie movie, int rows, int cols, Connection conn) {
        this.movie = movie;
        this.rows = rows;
        this.cols = cols;
        this.conn = conn;
        seats = new char[rows][cols];
        loadSeatsFromDB();
    }

    // Load seats from DB, if not found, initialize all to 'A' and insert into DB
    private void loadSeatsFromDB() {
        try {
            String createSeatsTable = "CREATE TABLE IF NOT EXISTS seats (" +
                    "hallNumber INTEGER, row INTEGER, col INTEGER, status TEXT, " +
                    "PRIMARY KEY(hallNumber, row, col))";
            Statement stmt = conn.createStatement();
            stmt.execute(createSeatsTable);
            stmt.close();

            // Try to load seats for this hall
            String sql = "SELECT row, col, status FROM seats WHERE hallNumber = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, movie.getHallNumber());
            ResultSet rs = pstmt.executeQuery();

            boolean hasSeats = false;
            for (int i = 0; i < rows; i++)
                Arrays.fill(seats[i], 'A'); // default all available first

            while (rs.next()) {
                hasSeats = true;
                int r = rs.getInt("row");
                int c = rs.getInt("col");
                String status = rs.getString("status");
                seats[r][c] = status.charAt(0);
            }
            rs.close();
            pstmt.close();

            // If no seats in DB, initialize and insert all as available
            if (!hasSeats) {
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        seats[r][c] = 'A';
                        insertSeatInDB(r, c, 'A');
                    }
                }
            }

        } catch (SQLException e) {
            System.out.println("Error loading seats: " + e.getMessage());
        }
    }

    private void insertSeatInDB(int row, int col, char status) throws SQLException {
        String sql = "INSERT INTO seats(hallNumber, row, col, status) VALUES (?, ?, ?, ?)";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setInt(1, movie.getHallNumber());
        pstmt.setInt(2, row);
        pstmt.setInt(3, col);
        pstmt.setString(4, String.valueOf(status));
        pstmt.executeUpdate();
        pstmt.close();
    }

    private void updateSeatInDB(int row, int col, char status) throws SQLException {
        String sql = "UPDATE seats SET status = ? WHERE hallNumber = ? AND row = ? AND col = ?";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, String.valueOf(status));
        pstmt.setInt(2, movie.getHallNumber());
        pstmt.setInt(3, row);
        pstmt.setInt(4, col);
        pstmt.executeUpdate();
        pstmt.close();
    }

    void displaySeats() {
        System.out.println(movie);
        System.out.print("    ");
        for (int c = 0; c < cols; c++) {
            System.out.printf("%2d ", c + 1);
        }
        System.out.println();

        for (int r = 0; r < rows; r++) {
            System.out.printf("Row %2d: ", r + 1);
            for (int c = 0; c < cols; c++) {
                System.out.print(seats[r][c] + "  ");
            }
            System.out.println();
        }
    }

    public void bookSeat(int row, int col) throws Exception {
        if (row < 0 || col < 0 || row >= rows || col >= cols)
            throw new Exception("Invalid seat.");
        if (seats[row][col] == 'B')
            throw new Exception("Seat already booked. Please try another seat.");

        seats[row][col] = 'B';
        // Update DB
        try {
            updateSeatInDB(row, col, 'B');
        } catch (SQLException e) {
            throw new Exception("Failed to update seat in database: " + e.getMessage());
        }
    }

    public Movie getMovie() {
        return movie;
    }
}

// ---------------------- Cinema System ----------------------
class CinemaSystem {
    List<Hall> halls = new ArrayList<>();
    Connection conn;

    CinemaSystem(Connection conn) {
        this.conn = conn;
    }

    void addShow(Movie movie, int rows, int cols) {
        halls.add(new Hall(movie, rows, cols, conn));
    }

    void viewShows() {
        for (int i = 0; i < halls.size(); i++) {
            System.out.println("[" + i + "] " + halls.get(i).getMovie());
        }
    }

    public void viewSeats(int index) {
        if (index >= 0 && index < halls.size())
            halls.get(index).displaySeats();
        else
            System.out.println("Invalid show index.");
    }

    public void bookSeat(int index, int row, int col) throws Exception {
        if (index >= 0 && index < halls.size())
            halls.get(index).bookSeat(row, col);
        else
            throw new Exception("Invalid show index.");
    }
}

// ---------------------- Main Class ----------------------
public class Main {
    public static void main(String[] args) {
        String dbFile = "movie_booking.db";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            Class.forName("org.sqlite.JDBC");

            AuthSystem authSystem = new AuthSystem(conn);
            Scanner sc = new Scanner(System.in);

            while (true) {  // Main dashboard loop
                User currentUser = null;

                while (true) {  // Register/login loop
                    System.out.println("\n--- Login & Registration System ---");
                    System.out.println("[1] Register");
                    System.out.println("[2] Login");
                    System.out.println("[0] Exit");
                    System.out.print("Choose option: ");
                    int choice = sc.nextInt();
                    sc.nextLine();

                    try {
                        if (choice == 1) {
                            System.out.print("Enter username: ");
                            String u = sc.nextLine().trim();
                            System.out.print("Enter password: ");
                            String p = sc.nextLine().trim();

                            authSystem.registerUser(u, p);
                            System.out.println("Registration successful! Please login.");

                        } else if (choice == 2) {
                            System.out.print("Enter username: ");
                            String u = sc.nextLine().trim();
                            System.out.print("Enter password: ");
                            String p = sc.nextLine().trim();

                            currentUser = authSystem.loginUser(u, p);
                            System.out.println("Welcome " + currentUser.getUsername() + "!");
                            break; // Logged in, exit login loop

                        } else if (choice == 0) {
                            System.out.println("Goodbye!");
                            sc.close();
                            System.exit(0);
                        } else {
                            System.out.println("Invalid choice.");
                        }
                    } catch (Exception e) {
                        System.out.println("Error: " + e.getMessage());
                    }
                }

                // Setup movies and halls
                CinemaSystem cinema = new CinemaSystem(conn);
                cinema.addShow(new Movie("The Batman", "18:00", 1), 5, 5);
                cinema.addShow(new Movie("Avatar 2", "20:00", 2), 6, 6);
                cinema.addShow(new Movie("Inception", "21:00", 3), 4, 7);

                while (true) {  // Movie booking system loop after login
                    System.out.println("\n--- Movie Booking System ---");
                    System.out.println("[1] View Shows");
                    System.out.println("[2] View Seats for Show");
                    System.out.println("[3] Book Seat");
                    System.out.println("[0] Logout");
                    System.out.print("Choose option: ");
                    int option = sc.nextInt();
                    sc.nextLine();

                    try {
                        if (option == 1) {
                            cinema.viewShows();
                        } else if (option == 2) {
                            System.out.print("Enter show index: ");
                            int showIdx = sc.nextInt();
                            sc.nextLine();
                            cinema.viewSeats(showIdx);
                        } else if (option == 3) {
                            System.out.print("Enter show index: ");
                            int showIdx = sc.nextInt();
                            System.out.print("Enter row number (starting from 1): ");
                            int row = sc.nextInt();
                            System.out.print("Enter column number (starting from 1): ");
                            int col = sc.nextInt();
                            sc.nextLine();

                            cinema.bookSeat(showIdx, row - 1, col - 1);
                            System.out.println("Seat booked successfully!");
                        } else if (option == 0) {
                            System.out.println("Logging out...");
                            break;  // Exit movie booking system loop, return to login/register menu
                        } else {
                            System.out.println("Invalid option.");
                        }
                    } catch (Exception e) {
                        System.out.println("Error: " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
