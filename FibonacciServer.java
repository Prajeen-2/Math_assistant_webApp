import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * FibonacciServer
 * - Serves index.html at "/"
 * - POST /api/calculate       -> JSON {"result":...}  (add, sub, mul, div)
 * - GET  /api/fibonacci-image -> image/png (Fibonacci spiral graph)
 * - Proper CORS and OPTIONS support
 * - Uses PORT env var (Render) or defaults to 8080
 */
public class FibonacciServer {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        System.out.println("Server running on port: " + port);

        // Serve index.html (GET)
        server.createContext("/", exchange -> {
            try {
                if (handleOptions(exchange)) return;
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendText(exchange, 405, "Method Not Allowed");
                    return;
                }
                File f = new File("index.html");
                if (!f.exists()) {
                    sendText(exchange, 404, "index.html not found");
                    return;
                }
                byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
                addCORS(exchange);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, data.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendText(exchange, 500, "Server error");
            }
        });

        // Calculator endpoint (POST) – 4 operations with two inputs
        server.createContext("/api/calculate", exchange -> {
            try {
                if (handleOptions(exchange)) return;
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendText(exchange, 405, "POST only");
                    return;
                }
                addCORS(exchange);
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String,String> p = parseForm(body);

                double a = parseDouble(p.getOrDefault("a", "0"));
                double b = parseDouble(p.getOrDefault("b", "0"));
                String op = p.getOrDefault("op", "add");

                double res;
                switch (op) {
                    case "add":
                        res = a + b;
                        break;
                    case "sub":
                        res = a - b;
                        break;
                    case "mul":
                        res = a * b;
                        break;
                    case "div":
                        res = (b == 0 ? Double.NaN : a / b);
                        break;
                    default:
                        res = Double.NaN;
                }

                // Backend only returns numeric result; frontend decides how to show it
                sendJSON(exchange, "{\"result\":" + res + "}");
            } catch (Exception e) {
                e.printStackTrace();
                sendText(exchange, 500, "Server error");
            }
        });

        // Image endpoint (GET) -> PNG with full Fibonacci spiral
        server.createContext("/api/fibonacci-image", exchange -> {
            try {
                if (handleOptions(exchange)) return;
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendText(exchange, 405, "GET only");
                    return;
                }
                addCORS(exchange);
                Map<String,String> q = parseQuery(exchange.getRequestURI().getQuery());
                int terms = parseInt(q.getOrDefault("terms","8"), 8);
                if (terms < 1) terms = 1;
                if (terms > 40) terms = 40;
                int size = parseInt(q.getOrDefault("size","600"), 600);

                BufferedImage img = renderFibonacciImage(terms, size, size);
                exchange.getResponseHeaders().set("Content-Type", "image/png");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                byte[] bytes = baos.toByteArray();
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendText(exchange, 500, "Server error");
            }
        });

        // Remove the old /api/fibonacci JSON context – no longer needed
        // (Do NOT createContext("/api/fibonacci", ...) anymore)

        server.setExecutor(null);
        server.start();
    }

    // ---------------------
    // Utilities
    // ---------------------

    private static boolean handleOptions(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            addCORS(ex);
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return true;
        }
        return false;
    }

    private static void addCORS(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    }

    private static void sendJSON(HttpExchange ex, String json) throws IOException {
        addCORS(ex);
        byte[] d = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(200, d.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(d);
        }
        ex.close();
    }

    private static void sendText(HttpExchange ex, int code, String txt) throws IOException {
        addCORS(ex);
        byte[] d = txt.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code, d.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(d);
        }
        ex.close();
    }

    private static Map<String,String> parseForm(String body) {
        Map<String,String> m = new HashMap<>();
        if (body == null || body.isEmpty()) return m;
        for (String p : body.split("&")) {
            String[] kv = p.split("=",2);
            if (kv.length == 2) {
                try {
                    m.put(
                        URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name())
                    );
                } catch (Exception ignore) {}
            }
        }
        return m;
    }

    private static Map<String,String> parseQuery(String q) {
        Map<String,String> m = new HashMap<>();
        if (q == null || q.isEmpty()) return m;
        for (String p : q.split("&")) {
            String[] kv = p.split("=",2);
            if (kv.length == 2) {
                try {
                    m.put(
                        URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name())
                    );
                } catch (Exception ignore) {
                    m.put(kv[0], kv[1]);
                }
            }
        }
        return m;
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    // ---------------------
    // Fibonacci spiral PNG renderer (unchanged)
    // ---------------------

    private static BufferedImage renderFibonacciImage(int terms, int W, int H) {
        int[] fib = new int[terms + 2];
        fib[0] = 0; fib[1] = 1;
        for (int i = 2; i < fib.length; i++) fib[i] = fib[i-1] + fib[i-2];

        List<double[]> arcsList = new ArrayList<>(); // cx,cy,r,start
        double cx = 0, cy = 0, ang = 0;
        for (int i = 1; i <= terms; i++) {
            double r = fib[i];
            arcsList.add(new double[]{cx, cy, r, ang});
            double end = ang + Math.PI / 2;
            double ex = cx + r * Math.cos(end);
            double ey = cy + r * Math.sin(end);
            cx = ex - fib[i+1] * Math.cos(end);
            cy = ey - fib[i+1] * Math.sin(end);
            ang = end;
        }

        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;

        for (double[] a : arcsList) {
            double acx = a[0], acy = a[1], ar = a[2], st = a[3];
            double en = st + Math.PI / 2;
            int samples = Math.max(36, (int)(ar * 6) + 36);
            for (int s = 0; s <= samples; s++) {
                double t = st + (en - st) * (double)s / samples;
                double x = acx + ar * Math.cos(t);
                double y = acy + ar * Math.sin(t);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
        }

        for (double[] a : arcsList) {
            double acx = a[0], acy = a[1], ar = a[2];
            minX = Math.min(minX, acx - ar);
            maxX = Math.max(maxX, acx + ar);
            minY = Math.min(minY, acy - ar);
            maxY = Math.max(maxY, acy + ar);
        }

        if (minX == Double.POSITIVE_INFINITY) {
            minX = -10; maxX = 10; minY = -10; maxY = 10;
        }

        double padX = (maxX - minX) * 0.08 + 1.0;
        double padY = (maxY - minY) * 0.08 + 1.0;
        minX -= padX; maxX += padX; minY -= padY; maxY += padY;
        double worldW = maxX - minX, worldH = maxY - minY;
        double scale = Math.min((W * 0.88) / worldW, (H * 0.78) / worldH);

        double tx = W * 0.5 - (minX + worldW * 0.5) * scale;
        double ty = H * 0.5 + (minY + worldH * 0.5) * scale;

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint sky = new GradientPaint(0, 0, new Color(220,235,255),
                                              0, H/2, new Color(245,250,255));
        g.setPaint(sky);
        g.fillRect(0, 0, W, H);

        g.setColor(new Color(238, 250, 238));
        g.fillRect(0, (int)(H * 0.78), W, (int)(H * 0.22));

        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(230,230,230));
        double approxStepPx = 80;
        double stepWorld = approxStepPx / scale;
        double nice = niceStep(stepWorld);

        double gxStart = Math.floor(minX / nice) * nice;
        for (double gx = gxStart; gx <= maxX; gx += nice) {
            int px = (int)Math.round(tx + gx * scale);
            g.drawLine(px, 0, px, H);
        }

        double gyStart = Math.floor(minY / nice) * nice;
        for (double gy = gyStart; gy <= maxY; gy += nice) {
            int py = (int)Math.round(ty - gy * scale);
            g.drawLine(0, py, W, py);
        }

        g.setColor(new Color(90,90,90));
        g.setStroke(new BasicStroke(2f));
        int axisX = (int)Math.round(tx + 0 * scale);
        int axisY = (int)Math.round(ty - 0 * scale);
        g.drawLine(axisX, 0, axisX, H);
        g.drawLine(0, axisY, W, axisY);

        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(60,60,60));
        for (double gx = gxStart; gx <= maxX; gx += nice) {
            int px = (int)Math.round(tx + gx * scale);
            g.drawString(String.format(Locale.US, "%.0f", gx), px + 2, axisY - 6);
        }
        for (double gy = gyStart; gy <= maxY; gy += nice) {
            int py = (int)Math.round(ty - gy * scale);
            g.drawString(String.format(Locale.US, "%.0f", gy), axisX + 6, py - 2);
        }

        g.setStroke(new BasicStroke(1f));
        for (double[] a : arcsList) {
            double acx = a[0], acy = a[1], ar = a[2];
            double left = tx + (acx - ar) * scale;
            double top  = ty - (acy + ar) * scale;
            double w = ar * 2 * scale, h = ar * 2 * scale;
            g.setColor(new Color(255,255,255,200));
            g.fillRoundRect((int)Math.round(left),(int)Math.round(top),
                            (int)Math.round(w),(int)Math.round(h),8,8);
            g.setColor(new Color(200,200,220));
            g.drawRoundRect((int)Math.round(left),(int)Math.round(top),
                            (int)Math.round(w),(int)Math.round(h),8,8);
            g.setColor(new Color(40,40,40));
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            String lbl = String.valueOf((int)Math.round(ar));
            FontMetrics fm = g.getFontMetrics();
            int txLbl = (int)Math.round(left + w/2 - fm.stringWidth(lbl)/2);
            int tyLbl = (int)Math.round(top + h/2 + fm.getAscent()/2 - 2);
            g.drawString(lbl, txLbl, tyLbl);
        }

        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < arcsList.size(); i++) {
            double[] a = arcsList.get(i);
            double acx = a[0], acy = a[1], ar = a[2], st = a[3];
            double en = st + Math.PI / 2;

            float t = i / (float)Math.max(1, arcsList.size() - 1);
            Color col = interpolateColor(new Color(255,120,60), new Color(0,140,220), t);
            g.setColor(col);

            int segs = Math.max(60, (int)(ar * 12));
            Path2D path = new Path2D.Double();
            for (int s = 0; s <= segs; s++) {
                double theta = st + (en - st) * (double)s / segs;
                double wx = acx + ar * Math.cos(theta);
                double wy = acy + ar * Math.sin(theta);
                double px = tx + wx * scale;
                double py = ty - wy * scale;
                if (s == 0) path.moveTo(px, py); else path.lineTo(px, py);
            }
            g.draw(path);

            double midT = st + (en - st) * 0.5;
            double mx = acx + ar * Math.cos(midT) * 0.6;
            double my = acy + ar * Math.sin(midT) * 0.6;
            int px = (int)Math.round(tx + mx * scale);
            int py = (int)Math.round(ty - my * scale);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(new Color(40,40,40));
            g.drawString(String.valueOf((int)Math.round(ar)), px - 6, py + 4);
        }

        g.setColor(new Color(30,30,30));
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString("Fibonacci Spiral — Quarter arcs with radii = Fibonacci numbers", 12, 22);

        int lx = W - 220, ly = 16;
        g.setColor(new Color(255,255,255,230));
        g.fillRoundRect(lx, ly, 200, 48, 8, 8);
        g.setColor(new Color(190,190,190));
        g.drawRoundRect(lx, ly, 200, 48, 8, 8);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.drawString("Orange → Blue : Arc progression", lx + 12, ly + 18);
        g.drawString("Squares show each radius (labelled)", lx + 12, ly + 36);

        g.dispose();
        return img;
    }

    private static double niceStep(double approx) {
        if (approx <= 0) return 1;
        double exp = Math.pow(10, Math.floor(Math.log10(approx)));
        double m = approx / exp;
        if (m < 1.5) return 1 * exp;
        if (m < 3.5) return 2 * exp;
        if (m < 7.5) return 5 * exp;
        return 10 * exp;
    }

    private static Color interpolateColor(Color a, Color b, float t) {
        int r = (int)(a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int)(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(r, g, bl);
    }
}
