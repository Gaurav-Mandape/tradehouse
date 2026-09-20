import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class Main {
    private static final int PORT = 8080;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Path DATA_FILE = Path.of("data", "market-data.csv");
    private static final Market market = new Market();
    private static final Portfolio portfolio = new Portfolio();

    public static void main(String[] args) throws Exception {
        market.load();
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/market/stocks", Main::stocks);
        server.createContext("/api/market/quote", Main::quote);
        server.createContext("/api/portfolio", Main::portfolio);
        server.createContext("/api/transactions", Main::transactions);
        server.createContext("/api/trade", Main::trade);
        server.createContext("/", Main::staticFiles);
        server.setExecutor(null);
        server.start();
        System.out.println("Tradehouse running at http://localhost:" + PORT);
    }

    private static void stocks(HttpExchange exchange) throws IOException {
        List<String> items = new ArrayList<>();
        for (String symbol : market.symbols()) {
            StockQuote quote = market.quote(symbol, market.latestDate(), market.latestTime());
            items.add("{\"symbol\":\"" + symbol + "\",\"name\":\"" + market.name(symbol) + "\",\"price\":" + money(quote.price) + ",\"change\":" + number(quote.change) + ",\"changePct\":" + number(quote.changePct) + "}");
        }
        sendJson(exchange, "[" + String.join(",", items) + "]");
    }

    private static void quote(HttpExchange exchange) throws IOException {
        Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
        String symbol = query.getOrDefault("symbol", market.symbols().get(0));
        LocalDate date = parseDate(query.get("date"), market.latestDate());
        LocalTime time = parseTime(query.get("time"), market.latestTime());
        StockQuote quote = market.quote(symbol, date, time);
        sendJson(exchange, "{\"symbol\":\"" + symbol + "\",\"name\":\"" + market.name(symbol) + "\",\"date\":\"" + quote.date + "\",\"time\":\"" + quote.time + "\",\"price\":" + money(quote.price) + ",\"change\":" + number(quote.change) + ",\"changePct\":" + number(quote.changePct) + "}");
    }

    private static synchronized void portfolio(HttpExchange exchange) throws IOException {
        sendJson(exchange, portfolio.toJson(market));
    }

    private static synchronized void transactions(HttpExchange exchange) throws IOException {
        sendJson(exchange, portfolio.transactionsJson());
    }

    private static synchronized void trade(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, "{\"error\":\"POST required\"}", 405);
            return;
        }
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> input = jsonObject(body);
            String symbol = input.get("symbol");
            String side = input.get("side");
            int quantity = Integer.parseInt(input.get("quantity"));
            if (!market.symbols().contains(symbol) || !("BUY".equals(side) || "SELL".equals(side)) || quantity <= 0) {
                throw new IllegalArgumentException("Enter a valid symbol, side, and quantity.");
            }
            StockQuote quote = market.quote(symbol, market.latestDate(), market.latestTime());
            portfolio.trade(symbol, side, quantity, quote.price, quote.date, quote.time);
            sendJson(exchange, "{\"message\":\"Order executed\",\"portfolio\":" + portfolio.toJson(market) + "}");
        } catch (Exception error) {
            sendJson(exchange, "{\"error\":\"" + escape(error.getMessage()) + "\"}", 400);
        }
    }

    private static void staticFiles(HttpExchange exchange) throws IOException {
        String requested = exchange.getRequestURI().getPath();
        if (requested.equals("/")) requested = "/index.html";
        Path root = Path.of("web").toAbsolutePath().normalize();
        Path file = root.resolve(requested.substring(1)).normalize();
        if (!file.startsWith(root) || !Files.exists(file)) {
            sendText(exchange, "Not found", "text/plain; charset=utf-8", 404);
            return;
        }
        String type = requested.endsWith(".css") ? "text/css" : requested.endsWith(".js") ? "text/javascript" : "text/html";
        byte[] bytes = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> result = new HashMap<>();
        if (raw == null) return result;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return result;
    }

    private static Map<String, String> jsonObject(String raw) {
        Map<String, String> result = new HashMap<>();
        String clean = raw.trim().replaceAll("^[{]|[}]$", "");
        for (String pair : clean.split(",")) {
            String[] parts = pair.split(":", 2);
            if (parts.length == 2) result.put(parts[0].trim().replaceAll("^\"|\"$", ""), parts[1].trim().replaceAll("^\"|\"$", ""));
        }
        return result;
    }

    private static LocalDate parseDate(String value, LocalDate fallback) { try { return value == null ? fallback : LocalDate.parse(value, DATE); } catch (Exception ignored) { return fallback; } }
    private static LocalTime parseTime(String value, LocalTime fallback) { try { return value == null ? fallback : LocalTime.parse(value, TIME); } catch (Exception ignored) { return fallback; } }
    private static String money(double value) { return String.format(Locale.US, "%.2f", value); }
    private static String number(double value) { return String.format(Locale.US, "%.4f", value); }
    private static String escape(String value) { return value == null ? "Unknown error" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static void sendJson(HttpExchange exchange, String body) throws IOException { sendJson(exchange, body, 200); }
    private static void sendJson(HttpExchange exchange, String body, int status) throws IOException { sendText(exchange, body, "application/json; charset=utf-8", status); }
    private static void sendText(HttpExchange exchange, String body, String contentType, int status) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders(); headers.set("Content-Type", contentType); headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }

    static class Market {
        final Map<String, List<StockQuote>> prices = new LinkedHashMap<>();
        final Map<String, String> names = Map.of("AAPL", "Apple", "MSFT", "Microsoft", "NVDA", "NVIDIA", "AMZN", "Amazon", "TSLA", "Tesla", "GOOGL", "Alphabet", "META", "Meta Platforms", "JPM", "JPMorgan Chase", "NFLX", "Netflix", "DIS", "Walt Disney");
        void load() throws IOException {
            if (!Files.exists(DATA_FILE)) generateCsv();
            for (String line : Files.readAllLines(DATA_FILE)) {
                if (line.startsWith("symbol")) continue;
                String[] p = line.split(",");
                prices.computeIfAbsent(p[0], key -> new ArrayList<>()).add(new StockQuote(p[0], LocalDate.parse(p[2]), LocalTime.parse(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5]), Double.parseDouble(p[6])));
            }
        }
        void generateCsv() throws IOException {
            Files.createDirectories(DATA_FILE.getParent());
            StringBuilder csv = new StringBuilder("symbol,name,date,time,price,change,changePct\n");
            List<String> symbols = new ArrayList<>(names.keySet());
            Random random = new Random(26);
            LocalDate start = LocalDate.of(2026, 9, 7);
            for (int day = 0; day < 10; day++) {
                LocalDate date = start.plusDays(day + (day / 5) * 2);
                for (String symbol : symbols) {
                    double base = 85 + symbols.indexOf(symbol) * 37 + random.nextDouble() * 20;
                    double previous = base;
                    for (int slot = 0; slot < 14; slot++) {
                        double price = previous * (1 + (random.nextDouble() - .47) / 90);
                        double change = price - base;
                        double pct = change / base * 100;
                        LocalTime time = LocalTime.of(9, 30).plusMinutes(slot * 30L);
                        csv.append(String.format(Locale.US, "%s,%s,%s,%s,%.2f,%.2f,%.4f\n", symbol, names.get(symbol), date, time, price, change, pct));
                        previous = price;
                    }
                }
            }
            Files.writeString(DATA_FILE, csv.toString());
        }
        List<String> symbols() { return new ArrayList<>(prices.keySet()); }
        String name(String symbol) { return names.getOrDefault(symbol, symbol); }
        LocalDate latestDate() { return prices.values().iterator().next().get(prices.values().iterator().next().size() - 1).date; }
        LocalTime latestTime() { return LocalTime.of(16, 0); }
        StockQuote quote(String symbol, LocalDate date, LocalTime time) {
            List<StockQuote> rows = prices.get(symbol);
            if (rows == null) throw new IllegalArgumentException("Unknown stock: " + symbol);
            return rows.stream().filter(row -> !row.date.isAfter(date)).filter(row -> !row.date.equals(date) || !row.time.isAfter(time)).max(Comparator.comparing((StockQuote row) -> row.date).thenComparing(row -> row.time)).orElse(rows.get(0));
        }
    }

    static class StockQuote {
        final String symbol; final LocalDate date; final LocalTime time; final double price; final double change; final double changePct;
        StockQuote(String symbol, LocalDate date, LocalTime time, double price, double change, double changePct) { this.symbol = symbol; this.date = date; this.time = time; this.price = price; this.change = change; this.changePct = changePct; }
    }

    static class Portfolio {
        double cash = 100_000;
        final Map<String, Holding> holdings = new LinkedHashMap<>();
        final List<Transaction> transactions = new ArrayList<>();
        void trade(String symbol, String side, int quantity, double price, LocalDate date, LocalTime time) {
            double total = quantity * price;
            Holding holding = holdings.computeIfAbsent(symbol, key -> new Holding());
            if ("BUY".equals(side)) {
                if (total > cash) throw new IllegalArgumentException("Insufficient buying power.");
                holding.quantity += quantity; holding.cost += total; cash -= total;
            } else {
                if (quantity > holding.quantity) throw new IllegalArgumentException("You do not own enough shares to sell.");
                holding.quantity -= quantity; holding.cost -= holding.averageCost() * quantity; cash += total;
                if (holding.quantity == 0) holdings.remove(symbol);
            }
            transactions.add(new Transaction(symbol, side, quantity, price, total, date, time));
        }
        String toJson(Market market) {
            double invested = 0, value = 0, pnl = 0; List<String> rows = new ArrayList<>();
            for (Map.Entry<String, Holding> entry : holdings.entrySet()) {
                StockQuote quote = market.quote(entry.getKey(), market.latestDate(), market.latestTime()); Holding h = entry.getValue();
                double marketValue = h.quantity * quote.price; double itemPnl = marketValue - h.cost; invested += h.cost; value += marketValue; pnl += itemPnl;
                rows.add("{\"symbol\":\"" + entry.getKey() + "\",\"name\":\"" + market.name(entry.getKey()) + "\",\"quantity\":" + h.quantity + ",\"averageCost\":" + money(h.averageCost()) + ",\"price\":" + money(quote.price) + ",\"value\":" + money(marketValue) + ",\"pnl\":" + money(itemPnl) + "}");
            }
            return "{\"cash\":" + money(cash) + ",\"invested\":" + money(invested) + ",\"value\":" + money(value) + ",\"pnl\":" + money(pnl) + ",\"holdings\":[" + String.join(",", rows) + "]}";
        }
        String transactionsJson() { List<String> rows = new ArrayList<>(); for (int i = transactions.size() - 1; i >= 0; i--) rows.add(transactions.get(i).json()); return "[" + String.join(",", rows) + "]"; }
    }

    static class Holding { int quantity; double cost; double averageCost() { return quantity == 0 ? 0 : cost / quantity; } }
    static class Transaction {
        final String symbol, side; final int quantity; final double price, total; final LocalDate date; final LocalTime time;
        Transaction(String symbol, String side, int quantity, double price, double total, LocalDate date, LocalTime time) { this.symbol = symbol; this.side = side; this.quantity = quantity; this.price = price; this.total = total; this.date = date; this.time = time; }
        String json() { return "{\"symbol\":\"" + symbol + "\",\"side\":\"" + side + "\",\"quantity\":" + quantity + ",\"price\":" + money(price) + ",\"total\":" + money(total) + ",\"timestamp\":\"" + date + " " + time + "\"}"; }
    }
}
