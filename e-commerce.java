import java.io.*;
import java.net.*;
import java.util.*;
import java.time.LocalDateTime;
import org.json.JSONObject;

class Product {
    String id;
    String name;
    double price;
    int stock;
    Product(String id, String name, double price, int stock) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }
}

class OrderItem {
    Product product;
    int quantity;
    OrderItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }
}

class Customer {
    String id;
    String name;
    String email;
    String address;
    boolean isPremiumMember;
    Customer(String id, String name, String email, String address, boolean isPremiumMember) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.address = address;
        this.isPremiumMember = isPremiumMember;
    }
}

class HttpClient {
    public static String post(String urlStr, String payload) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes());
        }
        InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) response.append(inputLine);
        in.close();
        return response.toString();
    }
}

class PaymentGateway {
    public static boolean processPayment(String customerId, double amount) {
        try {
            String payload = new JSONObject().put("customerId", customerId).put("amount", amount).toString();
            String response = HttpClient.post("https://jsonplaceholder.typicode.com/posts", payload);
            JSONObject json = new JSONObject(response);
            return json.has("id");
        } catch (Exception e) {
            return false;
        }
    }
}

class ShippingService {
    public static String createShipment(String orderId, String address) {
        try {
            String payload = new JSONObject().put("orderId", orderId).put("address", address).toString();
            String response = HttpClient.post("https://jsonplaceholder.typicode.com/posts", payload);
            return "SHIP-" + orderId + "-" + UUID.randomUUID().toString().substring(0, 6);
        } catch (Exception e) {
            return null;
        }
    }
}

class NotificationService {
    public static void sendEmail(String to, String subject, String body) {
        try {
            String payload = new JSONObject().put("to", to).put("subject", subject).put("body", body).toString();
            HttpClient.post("https://jsonplaceholder.typicode.com/posts", payload);
        } catch (Exception ignored) {}
    }
}

public class RealWorldOrderProcessingSystem {
    private static final double TAX_RATE = 0.18;
    private static final double PREMIUM_DISCOUNT = 0.10;
    private static final double COUPON_DISCOUNT = 0.05;

    public static void main(String[] args) {
        Map<String, Product> inventory = new HashMap<>();
        inventory.put("P1", new Product("P1", "Laptop", 55000, 5));
        inventory.put("P2", new Product("P2", "Headphones", 2000, 20));
        inventory.put("P3", new Product("P3", "Mouse", 500, 30));

        Customer customer = new Customer("C101", "Akhil", "akhil@example.com", "Calicut, India", true);

        List<OrderItem> cart = new ArrayList<>();
        cart.add(new OrderItem(inventory.get("P1"), 1));
        cart.add(new OrderItem(inventory.get("P2"), 2));

        try {
            processOrder("ORD123", customer, cart, inventory, true);
        } catch (Exception e) {
            System.out.println("Order Failed: " + e.getMessage());
        }
    }

    public static void processOrder(String orderId, Customer customer, List<OrderItem> cart,
                                    Map<String, Product> inventory, boolean couponApplied) throws Exception {
        double subtotal = 0.0;
        for (OrderItem item : cart) {
            if (item.product.stock < item.quantity) throw new Exception("Not enough stock for: " + item.product.name);
            subtotal += item.product.price * item.quantity;
        }
        double discount = 0.0;
        if (customer.isPremiumMember) discount += subtotal * PREMIUM_DISCOUNT;
        if (couponApplied) discount += subtotal * COUPON_DISCOUNT;
        double discountedTotal = subtotal - discount;
        double tax = discountedTotal * TAX_RATE;
        double finalAmount = discountedTotal + tax;
        boolean paymentSuccess = PaymentGateway.processPayment(customer.id, finalAmount);
        if (!paymentSuccess) throw new Exception("Payment failed");
        for (OrderItem item : cart) inventory.get(item.product.id).stock -= item.quantity;
        String shipmentId = ShippingService.createShipment(orderId, customer.address);
        if (shipmentId == null) throw new Exception("Shipment creation failed");
        NotificationService.sendEmail(customer.email, "Order Confirmation", "Order " + orderId + " placed. Shipment ID: " + shipmentId);
        System.out.println("===== INVOICE =====");
        System.out.println("Order ID: " + orderId);
        System.out.println("Date: " + LocalDateTime.now());
        System.out.println("Customer: " + customer.name + " (" + customer.email + ")");
        for (OrderItem item : cart) {
            System.out.println(item.product.name + " x " + item.quantity + " = ₹" + (item.product.price * item.quantity));
        }
        System.out.println("Subtotal: ₹" + subtotal);
        System.out.println("Discount: ₹" + discount);
        System.out.println("Tax: ₹" + tax);
        System.out.println("TOTAL: ₹" + finalAmount);
        System.out.println("Shipment ID: " + shipmentId);
        System.out.println("===================");
    }
}
