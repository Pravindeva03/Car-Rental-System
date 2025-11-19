import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/*
 Single-file Car Rental System (Ola/Uber-like) demo with:
 - User auth (signup/login)
 - Admin panel
 - Driver/car registration
 - Smart driver matching by simulated distance
 - Fare estimation (distance, car type multiplier, surge, promo codes)
 - ETA calculation
 - Promo codes
 - Booking lifecycle: request, active, complete, cancel (with fee)
 - Driver ratings
 - Fuel estimation
 - Voice-like messages
 - All data stored in-memory (ArrayLists)
*/

public class Main {
    static final Scanner sc = new Scanner(System.in);
    static final RideService rideService = new RideService();
    static final AuthService authService = new AuthService();
    static final PromoService promoService = new PromoService();
    static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static void main(String[] args) {
        seedDemo();
        System.out.println("=== Car Rental System (Ola/Uber-like) ===");
        boolean running = true;
        while (running) {
            System.out.println("\nMain Menu:");
            System.out.println("1. Sign up");
            System.out.println("2. Login");
            System.out.println("3. Admin Login");
            System.out.println("4. Exit");
            int opt = readInt("Choose: ");
            switch (opt) {
                case 1 -> signup();
                case 2 -> userMenu();
                case 3 -> adminMenu();
                case 4 -> running = false;
                default -> System.out.println("Invalid option.");
            }
        }
        System.out.println("Goodbye!");
    }

    private static void signup() {
        System.out.println("\n--- Sign Up ---");
        String uname = readString("Username: ");
        String pass = readString("Password: ");
        if (authService.register(uname, pass)) {
            System.out.println("Signup successful. You can now login.");
        } else {
            System.out.println("Username already exists.");
        }
    }

    private static void userMenu() {
        System.out.println("\n--- Login ---");
        String uname = readString("Username: ");
        String pass = readString("Password: ");
        User u = authService.login(uname, pass);
        if (u == null) {
            System.out.println("Invalid credentials.");
            return;
        }
        System.out.println("Welcome, " + u.getUsername() + "!");
        boolean exit = false;
        while (!exit) {
            System.out.println("\nUser Menu:");
            System.out.println("1. View Drivers");
            System.out.println("2. Request Ride");
            System.out.println("3. View Active Bookings");
            System.out.println("4. Complete Booking");
            System.out.println("5. Cancel Booking");
            System.out.println("6. Ride History");
            System.out.println("7. Apply Promo Codes (view available)");
            System.out.println("8. Logout");
            int ch = readInt("Choose: ");
            switch (ch) {
                case 1 -> listDrivers();
                case 2 -> requestRide(u);
                case 3 -> listActiveBookingsForUser(u);
                case 4 -> completeBooking(u);
                case 5 -> cancelBooking(u);
                case 6 -> rideHistory(u);
                case 7 -> promoService.listPromos();
                case 8 -> exit = true;
                default -> System.out.println("Invalid option.");
            }
        }
    }

    private static void adminMenu() {
        System.out.println("\n--- Admin Login ---");
        String pass = readString("Enter admin password: ");
        if (!"admin123".equals(pass)) {
            System.out.println("Wrong admin password.");
            return;
        }
        System.out.println("Admin access granted.");
        boolean stop = false;
        while (!stop) {
            System.out.println("\nAdmin Menu:");
            System.out.println("1. View drivers");
            System.out.println("2. Add driver");
            System.out.println("3. Remove driver");
            System.out.println("4. View all bookings");
            System.out.println("5. Add promo code");
            System.out.println("6. Remove promo code");
            System.out.println("7. Logout");
            int a = readInt("Choose: ");
            switch (a) {
                case 1 -> listDrivers();
                case 2 -> adminAddDriver();
                case 3 -> adminRemoveDriver();
                case 4 -> listAllBookings();
                case 5 -> adminAddPromo();
                case 6 -> adminRemovePromo();
                case 7 -> stop = true;
                default -> System.out.println("Invalid option.");
            }
        }
    }

    private static void listDrivers() {
        System.out.println("\n--- Drivers ---");
        for (Driver d : rideService.getDrivers()) System.out.println(d);
    }

    private static void requestRide(User user) {
        System.out.println("\n--- Request Ride ---");
        String pickup = readString("Pickup: ");
        String drop = readString("Drop: ");
        int kms = readInt("Estimated distance (km): ");
        System.out.println("Car types: 1. Mini 2. Sedan 3. SUV");
        int typeChoice = readInt("Choose car type: ");
        CarType type = switch (typeChoice) {
            case 2 -> CarType.SEDAN;
            case 3 -> CarType.SUV;
            default -> CarType.MINI;
        };
        // show promos
        promoService.listPromos();
        String promo = readString("Enter promo code or press Enter to skip: ");
        if (promo.isBlank()) promo = null;

        System.out.println("Estimating fare...");
        FareEstimate estimate = FareCalculator.estimateFare(kms, type, promoService, promo);
        System.out.printf("Estimated fare: ₹%.2f  | ETA: %d min  | Fuel est: %.2f L\n",
                estimate.finalFare, estimate.etaMinutes, estimate.estimatedFuelLiters);
        System.out.println("Voice: \"Searching for nearby drivers...\"");

        Booking b = rideService.requestRide(user.getUsername(), pickup, drop, kms, type, estimate);
        if (b == null) {
            System.out.println("No drivers available currently. Try later.");
            return;
        }
        b.setAppliedPromo(promo);
        b.setEstimatedFare(estimate.finalFare);
        b.setEtaMinutes(estimate.etaMinutes);
        b.setEstimatedFuelLiters(estimate.estimatedFuelLiters);
        System.out.println("Booking created: " + b.summary());
        System.out.println("Voice: \"Driver " + b.getDriver().getName() + " is on the way (ETA " + b.getEtaMinutes() + " mins).\"");
    }

    private static void listActiveBookingsForUser(User user) {
        System.out.println("\nActive bookings for you:");
        List<Booking> active = rideService.getActiveBookingsForRider(user.getUsername());
        if (active.isEmpty()) System.out.println("No active bookings.");
        else active.forEach(b -> System.out.println(b.detailed()));
    }

    private static void completeBooking(User user) {
        int id = readInt("Enter booking id to complete: ");
        Booking b = rideService.findBookingById(id);
        if (b == null) { System.out.println("Booking not found."); return; }
        if (!b.getRiderName().equals(user.getUsername())) { System.out.println("This booking isn't yours."); return; }
        if (b.getStatus() != BookingStatus.ACTIVE) { System.out.println("Booking not active."); return; }
        rideService.completeBooking(id);
        System.out.println("Booking completed. Thank you for riding.");
        int rating = readIntRange("Rate driver (1-5): ", 1, 5);
        rideService.rateDriver(b.getDriver().getId(), rating);
        System.out.println("Voice: \"Thanks! Your rating has been submitted.\"");
    }

    private static void cancelBooking(User user) {
        int id = readInt("Enter booking id to cancel: ");
        Booking b = rideService.findBookingById(id);
        if (b == null) { System.out.println("Booking not found."); return; }
        if (!b.getRiderName().equals(user.getUsername())) { System.out.println("This booking isn't yours."); return; }
        if (b.getStatus() != BookingStatus.ACTIVE) { System.out.println("Booking not active."); return; }

        System.out.println("Cancelling booking...");
        double fee = rideService.cancelBooking(id);
        System.out.printf("Booking cancelled. Cancellation fee: ₹%.2f\n", fee);
        System.out.println("Voice: \"Booking cancelled.\"");
    }

    private static void rideHistory(User user) {
        System.out.println("\n--- Ride History ---");
        List<Booking> hist = rideService.getBookingsForRider(user.getUsername());
        if (hist.isEmpty()) System.out.println("No rides yet.");
        else hist.forEach(b -> System.out.println(b.detailed()));
    }

    private static void listAllBookings() {
        System.out.println("\n--- All Bookings ---");
        List<Booking> all = rideService.getAllBookings();
        if (all.isEmpty()) System.out.println("No bookings yet.");
        else all.forEach(b -> System.out.println(b.detailed()));
    }

    private static void adminAddDriver() {
        System.out.println("\nAdd driver:");
        String name = readString("Name: ");
        System.out.println("Car types: 1. Mini 2. Sedan 3. SUV");
        int t = readInt("Choose: ");
        CarType type = switch (t) {
            case 2 -> CarType.SEDAN;
            case 3 -> CarType.SUV;
            default -> CarType.MINI;
        };
        String model = readString("Car model: ");
        String plate = readString("Plate: ");
        Driver d = rideService.registerDriver(name, model, plate, type);
        System.out.println("Added: " + d);
    }

    private static void adminRemoveDriver() {
        int id = readInt("Driver id to remove: ");
        boolean ok = rideService.removeDriver(id);
        if (ok) System.out.println("Driver removed.");
        else System.out.println("Driver not found or busy.");
    }

    private static void adminAddPromo() {
        String code = readString("Promo code (uppercase): ").toUpperCase(Locale.ROOT);
        double discount = Double.parseDouble(readString("Discount percent (e.g. 20 for 20%): "));
        promoService.addPromo(new Promo(code, discount, 5)); // default uses 5
        System.out.println("Promo added.");
    }

    private static void adminRemovePromo() {
        String code = readString("Promo code to remove: ").toUpperCase(Locale.ROOT);
        if (promoService.removePromo(code)) System.out.println("Removed.");
        else System.out.println("Not found.");
    }

    private static void seedDemo() {
        // drivers
        rideService.registerDriver("Aarav Etioson", "Toyota Etios", "TN07EX1234", CarType.SEDAN);
        rideService.registerDriver("Ryder Dzirex", "Swift Dzire", "TN11DZ5521", CarType.SEDAN);
        rideService.registerDriver("Ethan iDrive", "Hyundai i20", "TN09I27711", CarType.MINI);
        // promos
        promoService.addPromo(new Promo("FIRST50", 50.0, 1)); // 50% first ride
        promoService.addPromo(new Promo("SAVE20", 20.0, 5));
        // sample user
        authService.register("pravin", "pass");
    }

    // ---------- input helpers ----------
    private static String readString(String prompt) {
        System.out.print(prompt);
        return sc.nextLine().trim();
    }

    private static int readInt(String prompt) {
        while (true) {
            try {
                System.out.print(prompt);
                String l = sc.nextLine().trim();
                return Integer.parseInt(l);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid integer.");
            }
        }
    }

    private static int readIntRange(String prompt, int min, int max) {
        while (true) {
            int v = readInt(prompt);
            if (v >= min && v <= max) return v;
            System.out.printf("Enter value between %d and %d.%n", min, max);
        }
    }
}

/* =========================
   Domain & Service classes
   ========================= */

enum CarType { MINI, SEDAN, SUV; }

enum BookingStatus { ACTIVE, COMPLETED, CANCELLED }

class User {
    private final String username;
    private final String password; // in real app hash it

    public User(String u, String p) { username = u; password = p; }
    public String getUsername() { return username; }
    public boolean checkPass(String p) { return Objects.equals(password, p); }
}

class AuthService {
    private final Map<String, User> users = new HashMap<>();
    public boolean register(String u, String p) {
        if (users.containsKey(u)) return false;
        users.put(u, new User(u, p));
        return true;
    }
    public User login(String u, String p) {
        User user = users.get(u);
        if (user == null) return null;
        return user.checkPass(p) ? user : null;
    }
}

class Promo {
    private final String code;
    private final double percent;
    private int usesLeft;
    public Promo(String c, double p, int uses) { code = c; percent = p; usesLeft = uses; }
    public String getCode() { return code; }
    public double getPercent() { return percent; }
    public boolean use() {
        if (usesLeft <= 0) return false;
        usesLeft--; return true;
    }
    @Override public String toString() { return code + " (" + percent + "%), usesLeft=" + usesLeft; }
}

class PromoService {
    private final Map<String, Promo> promos = new HashMap<>();
    public void addPromo(Promo p) { promos.put(p.getCode(), p); }
    public boolean removePromo(String code) { return promos.remove(code) != null; }
    public Promo find(String code) {
        if (code == null) return null;
        return promos.get(code.toUpperCase(Locale.ROOT));
    }
    public FareDiscount apply(String code, double fare) {
        if (code == null || code.isBlank()) return new FareDiscount(fare, 0.0);
        Promo p = find(code);
        if (p == null) return new FareDiscount(fare, 0.0);
        if (!p.use()) return new FareDiscount(fare, 0.0);
        double disc = fare * (p.getPercent()/100.0);
        return new FareDiscount(fare - disc, disc);
    }
    public void listPromos() {
        if (promos.isEmpty()) System.out.println("No promos.");
        else {
            System.out.println("Available promos:");
            promos.values().forEach(pp -> System.out.println(" - " + pp));
        }
    }
}

class FareEstimate {
    double baseFare;
    double distanceFare;
    double surge;
    double promoDiscount;
    double finalFare;
    int etaMinutes;
    double estimatedFuelLiters;
}

class FareDiscount {
    double after;
    double discount;
    public FareDiscount(double a, double d) { after = a; discount = d; }
}

class FareCalculator {
    // base rates (example)
    static final double BASE = 30.0;
    static final double PER_KM = 10.0;
    static final Map<CarType, Double> multiplier = Map.of(
            CarType.MINI, 1.0,
            CarType.SEDAN, 1.3,
            CarType.SUV, 1.6
    );

    // surge simulated by random demand factor
    public static FareEstimate estimateFare(int km, CarType type, PromoService promoService, String promoCode) {
        FareEstimate e = new FareEstimate();
        e.baseFare = BASE;
        e.distanceFare = PER_KM * km * multiplier.get(type);
        // surge simulation: random 0.0-0.5 of distanceFare
        e.surge = Math.round((Math.random() * 0.5 * e.distanceFare) * 100.0) / 100.0;
        double raw = e.baseFare + e.distanceFare + e.surge;
        // apply promo
        FareDiscount fd = promoService.apply(promoCode == null ? null : promoCode.toUpperCase(Locale.ROOT), raw);
        e.promoDiscount = fd.discount;
        e.finalFare = Math.round(fd.after * 100.0) / 100.0;

        // ETA: 2-5 min per km (simulated), plus 2 min base
        e.etaMinutes = Math.max(2, (int)Math.round(2 + km * (2 + Math.random()*3)));
        // Fuel est: assume liters per km by type
        double lpk = type == CarType.SUV ? 0.12 : (type == CarType.SEDAN ? 0.09 : 0.07); // liters per km
        e.estimatedFuelLiters = Math.round((lpk * km) * 100.0) / 100.0;
        return e;
    }
}

class RideService {
    private final List<Driver> drivers = new ArrayList<>();
    private final List<Booking> bookings = new ArrayList<>();
    private int driverCounter = 1;
    private int carCounter = 1;
    private int bookingCounter = 1;

    public Driver registerDriver(String name, String model, String plate, CarType type) {
        Car c = new Car(nextCarId(), model, plate, type);
        Driver d = new Driver(nextDriverId(), name, c);
        drivers.add(d);
        return d;
    }
    // overloaded convenience
    public Driver registerDriver(String name, String model, String plate) {
        return registerDriver(name, model, plate, CarType.SEDAN);
    }

    public List<Driver> getDrivers() { return Collections.unmodifiableList(drivers); }
    public Booking requestRide(String rider, String pickup, String drop, int kms, CarType type, FareEstimate estimate) {
        // find nearest available driver (simulate distance per available driver)
        Driver best = null;
        double bestDist = Double.MAX_VALUE;
        Random r = new Random();
        for (Driver d : drivers) {
            if (!d.isAvailable()) continue;
            // prefer matching car type if possible (small bias)
            double simulatedDistance = 1 + r.nextDouble() * 10; // 1-11 km
            if (d.getCar().getType() != type) simulatedDistance += 2.0; // bias penalty
            if (simulatedDistance < bestDist) { bestDist = simulatedDistance; best = d; }
        }
        if (best == null) return null;
        Booking b = new Booking(nextBookingId(), rider, pickup, drop, best, kms, type);
        b.setEstimatedFare(estimate.finalFare);
        b.setEtaMinutes(estimate.etaMinutes);
        bookings.add(b);
        // mark driver busy
        best.setAvailable(false);
        // attach booking to driver statistics
        best.assignBooking(b);
        return b;
    }

    public List<Booking> getActiveBookingsForRider(String rider) {
        List<Booking> out = new ArrayList<>();
        for (Booking b : bookings) if (b.getRiderName().equals(rider) && b.getStatus() == BookingStatus.ACTIVE) out.add(b);
        return out;
    }

    public List<Booking> getActiveBookings() {
        List<Booking> out = new ArrayList<>();
        for (Booking b : bookings) if (b.getStatus() == BookingStatus.ACTIVE) out.add(b);
        return out;
    }

    public List<Booking> getAllBookings() { return Collections.unmodifiableList(bookings); }
    public List<Booking> getBookingsForRider(String rider) {
        List<Booking> out = new ArrayList<>();
        for (Booking b : bookings) if (b.getRiderName().equals(rider)) out.add(b);
        return out;
    }

    public Booking findBookingById(int id) {
        for (Booking b : bookings) if (b.getId() == id) return b;
        return null;
    }

    public boolean completeBooking(int bookingId) {
        Booking b = findBookingById(bookingId);
        if (b == null || b.getStatus() != BookingStatus.ACTIVE) return false;
        b.setStatus(BookingStatus.COMPLETED);
        b.setCompletedAt(LocalDateTime.now());
        b.getDriver().setAvailable(true);
        return true;
    }

    // returns cancellation fee charged
    public double cancelBooking(int bookingId) {
        Booking b = findBookingById(bookingId);
        if (b == null || b.getStatus() != BookingStatus.ACTIVE) return 0.0;
        // cancellation policy:
        // - if cancel within 2 minutes of booking (simulated) => no fee
        // - else: 10% of estimated fare but min 20
        int minutesSince = Math.max(0, (int) java.time.Duration.between(b.getCreatedAt(), LocalDateTime.now()).toMinutes());
        double fee = 0.0;
        if (minutesSince <= 2) fee = 0.0;
        else {
            fee = Math.max(20.0, 0.10 * b.getEstimatedFare());
        }
        b.setStatus(BookingStatus.CANCELLED);
        b.getDriver().setAvailable(true);
        b.setCancelledAt(LocalDateTime.now());
        return Math.round(fee * 100.0) / 100.0;
    }

    public boolean rateDriver(int driverId, int stars) {
        Driver d = findDriverById(driverId);
        if (d == null) return false;
        d.addRating(stars);
        return true;
    }

    public boolean removeDriver(int id) {
        for (Driver d : drivers) {
            if (d.getId() == id) {
                if (!d.isAvailable()) return false; // cannot remove busy driver
                drivers.remove(d);
                return true;
            }
        }
        return false;
    }

    private Driver findDriverById(int id) {
        for (Driver d : drivers) if (d.getId() == id) return d;
        return null;
    }

    private synchronized int nextDriverId() { return driverCounter++; }
    private synchronized int nextCarId() { return carCounter++; }
    private synchronized int nextBookingId() { return bookingCounter++; }
}

class Booking {
    private final int id;
    private final String riderName;
    private final String pickup;
    private final String drop;
    private final Driver driver;
    private final int kms;
    private final CarType requestedType;
    private BookingStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private double estimatedFare;
    private String appliedPromo;
    private int etaMinutes;
    private double estimatedFuelLiters;

    public Booking(int id, String riderName, String pickup, String drop, Driver driver, int kms, CarType type) {
        this.id = id; this.riderName = riderName; this.pickup = pickup; this.drop = drop;
        this.driver = driver; this.kms = kms; this.requestedType = type;
        this.status = BookingStatus.ACTIVE; this.createdAt = LocalDateTime.now();
    }

    public int getId() { return id; }
    public String getRiderName() { return riderName; }
    public Driver getDriver() { return driver; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus s) { status = s; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCompletedAt(LocalDateTime t) { completedAt = t; }
    public void setCancelledAt(LocalDateTime t) { cancelledAt = t; }
    public void setEstimatedFare(double f) { estimatedFare = f; }
    public double getEstimatedFare() { return estimatedFare; }
    public void setAppliedPromo(String p) { appliedPromo = p; }
    public void setEtaMinutes(int m) { etaMinutes = m; }
    public int getEtaMinutes() { return etaMinutes; }
    public void setEstimatedFuelLiters(double l) { estimatedFuelLiters = l; }

    public String summary() {
        return String.format("Booking #%d | Rider:%s | %s->%s | Driver:%s | Fare:₹%.2f | Status:%s",
                id, riderName, pickup, drop, driver.getName(), estimatedFare, status);
    }

    public String detailed() {
        StringBuilder sb = new StringBuilder();
        sb.append("Booking #").append(id).append("\n");
        sb.append(" Rider: ").append(riderName).append("\n");
        sb.append(" Pickup: ").append(pickup).append("\n");
        sb.append(" Drop: ").append(drop).append("\n");
        sb.append(" Distance: ").append(kms).append(" km\n");
        sb.append(" Driver: ").append(driver.brief()).append("\n");
        sb.append(String.format(" Estimated fare: ₹%.2f (Promo: %s)\n", estimatedFare, appliedPromo==null?"none":appliedPromo));
        sb.append(" ETA: ").append(etaMinutes).append(" mins\n");
        sb.append(String.format(" Fuel est: %.2f L\n", estimatedFuelLiters));
        sb.append(" Created: ").append(Main.dtf.format(createdAt)).append("\n");
        if (completedAt != null) sb.append(" Completed: ").append(Main.dtf.format(completedAt)).append("\n");
        if (cancelledAt != null) sb.append(" Cancelled: ").append(Main.dtf.format(cancelledAt)).append("\n");
        sb.append(" Status: ").append(status).append("\n");
        return sb.toString();
    }
}

class Driver {
    private final int id;
    private final String name;
    private final Car car;
    private boolean available;
    private int ratingSum;
    private int ratingCount;
    private Booking currentBooking;

    public Driver(int id, String name, Car car) {
        this.id = id; this.name = name; this.car = car; this.available = true;
    }
    public int getId() { return id; }
    public String getName() { return name; }
    public Car getCar() { return car; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean a) { available = a; }
    public void assignBooking(Booking b) { this.currentBooking = b; }
    public void addRating(int r) { ratingSum += r; ratingCount++; }
    public double getAverageRating() { return ratingCount==0?0.0:((double)ratingSum)/ratingCount; }
    public String brief() { return String.format("%s (%s) [%s]", name, car.getModel(), car.getPlate()); }

    @Override
    public String toString() {
        String avail = available ? "Available" : "Busy";
        String rating = ratingCount==0? "No ratings" : String.format("%.2f (%d)", getAverageRating(), ratingCount);
        return String.format("Driver #%d: %s | %s | %s | Rating: %s", id, name, car.brief(), avail, rating);
    }
}

class Car {
    private final int id;
    private final String model;
    private final String plate;
    private final CarType type;
    public Car(int id, String model, String plate, CarType type) { this.id = id; this.model = model; this.plate = plate; this.type = type; }
    public int getId() { return id; }
    public String getModel() { return model; }
    public String getPlate() { return plate; }
    public CarType getType() { return type; }
    public String brief() { return String.format("%s - %s - %s", model, plate, type); }
}
