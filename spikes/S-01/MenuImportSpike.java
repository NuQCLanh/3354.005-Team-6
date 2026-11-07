import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Spike S-01: Can CometEats automatically import UTD dining menus from Dine on Campus?
 *
 * Throw-away prototype for CS 3354 P2, section 8. Not production code.
 * No API keys or passwords are needed or stored.
 *
 * Run (Java 11 or newer; team ran it on Java 26):   java MenuImportSpike.java            (7 days, all UTD locations)
 *                           java MenuImportSpike.java 3          (3 days)
 *                           java MenuImportSpike.java 7 LOCATION_ID   (skip discovery, one location)
 *
 * Output: results.log (every request) and RESULTS.md (summary + filled-in section 8 table).
 */
public class MenuImportSpike {

    static final String API = "https://api.dineoncampus.com/v1";
    static final String SITE_SLUG = "utdallasdining";
    static final long DELAY_MS = 500; // be polite to the server

    static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    static PrintWriter log;
    static int requests = 0, succeeded = 0;
    static final List<Long> latencies = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        int days = args.length > 0 ? Integer.parseInt(args[0]) : 7;
        String fixedLocation = args.length > 1 ? args[1] : null;

        log = new PrintWriter(new FileWriter("results.log", true), true);
        log.println("# S-01 run started " + ZonedDateTime.now());
        log.println("timestamp,status,millis,bytes,url");

        // 1. Find the UTD locations (dining halls)
        List<String[]> locations = new ArrayList<>(); // {id, name}
        if (fixedLocation != null) {
            locations.add(new String[]{fixedLocation, "location " + fixedLocation});
        } else {
            Object info = getJson(API + "/sites/" + SITE_SLUG + "/info");
            String siteId = str(dig(info, "site", "id"));
            if (siteId == null) { stopWithHelp("Could not find the UTD site id."); return; }
            Object all = getJson(API + "/locations/all_locations?platform=0&site_id=" + siteId);
            Object list = dig(all, "locations");
            if (list instanceof List) {
                for (Object loc : (List<?>) list) {
                    String id = str(dig(loc, "id"));
                    String name = str(dig(loc, "name"));
                    if (id != null) locations.add(new String[]{id, name});
                }
            }
            if (locations.isEmpty()) { stopWithHelp("Could not list UTD dining locations."); return; }
        }
        System.out.println("Locations found: " + locations.size());

        // 2. For each day, location and meal period, download the menu and count dishes/tags
        int totalDishes = 0, taggedDishes = 0, menusWithDishes = 0;
        Set<String> tagNames = new TreeSet<>();
        List<Long> dayImportMillis = new ArrayList<>();

        LocalDate start = LocalDate.now();
        for (int d = 0; d < days; d++) {
            LocalDate date = start.plusDays(d);
            String dateParam = date.getYear() + "-" + date.getMonthValue() + "-" + date.getDayOfMonth();
            long dayStart = System.currentTimeMillis();
            int dayDishes = 0;

            for (String[] loc : locations) {
                Object periodsResp = getJson(API + "/location/" + loc[0] + "/periods?platform=0&date=" + dateParam);
                Object periods = dig(periodsResp, "periods");
                if (!(periods instanceof List)) continue;

                for (Object p : (List<?>) periods) {
                    String periodId = str(dig(p, "id"));
                    if (periodId == null) continue;
                    Object menu = getJson(API + "/location/" + loc[0] + "/periods/" + periodId
                            + "?platform=0&date=" + dateParam);
                    List<Map<?, ?>> items = new ArrayList<>();
                    collectItems(menu, items);
                    if (!items.isEmpty()) menusWithDishes++;
                    for (Map<?, ?> item : items) {
                        totalDishes++;
                        dayDishes++;
                        Object filters = item.get("filters");
                        if (filters instanceof List && !((List<?>) filters).isEmpty()) {
                            taggedDishes++;
                            for (Object f : (List<?>) filters) {
                                String tag = str(dig(f, "name"));
                                if (tag != null) tagNames.add(tag);
                            }
                        }
                    }
                }
            }
            long dayMillis = System.currentTimeMillis() - dayStart;
            dayImportMillis.add(dayMillis);
            System.out.printf("%s: %d dishes, full import took %.1f s%n", date, dayDishes, dayMillis / 1000.0);
        }

        // 3. Summarise
        writeResults(days, locations.size(), totalDishes, taggedDishes, menusWithDishes, tagNames, dayImportMillis);
        log.close();
        System.out.println("\nDone.");
    }

    // ---------- measurement summary ----------

    static void writeResults(int days, int locs, int dishes, int tagged, int menus,
                             Set<String> tags, List<Long> dayMillis) throws IOException {
        double successPct = requests == 0 ? 0 : 100.0 * succeeded / requests;
        double taggedPct = dishes == 0 ? 0 : 100.0 * tagged / dishes;
        long median = percentile(latencies, 50), p95 = percentile(latencies, 95);
        long slowestDay = dayMillis.stream().mapToLong(Long::longValue).max().orElse(0);
        boolean yes = successPct >= 95 && dishes > 0;
        String when = ZonedDateTime.now().toLocalDate().toString();

        String result = String.format(
                "%d of %d requests succeeded (%.1f%%). Median response %d ms, 95th percentile %d ms. "
                + "%d menus across %d locations and %d days returned %d dishes; %d dishes (%.1f%%) carried at least one dietary tag. "
                + "Slowest full one-day import: %.1f s. Distinct tag values found: %d.",
                succeeded, requests, successPct, median, p95, menus, locs, days, dishes, tagged, taggedPct,
                slowestDay / 1000.0, tags.size());


        StringBuilder md = new StringBuilder();
        md.append("# S-01 results (run ").append(when).append(")\n\n");
        md.append("## Distinct dietary tag values found\n\n");
        md.append(tags.isEmpty() ? "(none)\n" : String.join(", ", tags) + "\n");

        try (PrintWriter out = new PrintWriter(new FileWriter("RESULTS.md"))) { out.print(md); }
        System.out.println("\n" + result);
    }

    static long percentile(List<Long> values, int pct) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    // ---------- HTTP ----------

    static Object getJson(String url) {
        requests++;
        long t0 = System.currentTimeMillis();
        int status = -1; int bytes = 0; Object parsed = null;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("User-Agent", "CometEats-S01-spike/1.0")
                    .GET().build();
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            status = resp.statusCode();
            bytes = resp.body().length();
            if (status == 200) {
                parsed = new Json(resp.body()).parse();
                succeeded++;
            }
        } catch (Exception e) {
            System.out.println("  request failed: " + e.getClass().getSimpleName() + " " + url);
        }
        long ms = System.currentTimeMillis() - t0;
        latencies.add(ms);
        log.println(ZonedDateTime.now() + "," + status + "," + ms + "," + bytes + "," + url);
        try { Thread.sleep(DELAY_MS); } catch (InterruptedException ignored) { }
        return parsed;
    }

    static void stopWithHelp(String why) {
        System.out.println(why);
        System.out.println("Fix: open https://dineoncampus.com/utdallasdining/whats-on-the-menu in Chrome,");
        System.out.println("press F12 > Network, reload, find a request containing 'location/<id>/periods',");
        System.out.println("copy the <id>, then run:  java MenuImportSpike.java 7 <id>");
        log.close();
    }

    // ---------- JSON helpers ----------

    /** Finds every dish: objects inside an "items" list that have a "name". */
    static void collectItems(Object node, List<Map<?, ?>> out) {
        if (node instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) node;
            Object items = m.get("items");
            if (items instanceof List) {
                for (Object it : (List<?>) items)
                    if (it instanceof Map && ((Map<?, ?>) it).containsKey("name")) out.add((Map<?, ?>) it);
            }
            for (Map.Entry<?, ?> e : m.entrySet())
                if (!"items".equals(e.getKey())) collectItems(e.getValue(), out);
        } else if (node instanceof List) {
            for (Object o : (List<?>) node) collectItems(o, out);
        }
    }

    static Object dig(Object node, String... keys) {
        for (String k : keys) {
            if (!(node instanceof Map)) return null;
            node = ((Map<?, ?>) node).get(k);
        }
        return node;
    }

    static String str(Object o) { return o == null ? null : o.toString(); }

    /** Tiny JSON parser so the spike needs no libraries. */
    static class Json {
        final String s; int i = 0;
        Json(String s) { this.s = s; }

        Object parse() { ws(); Object v = value(); ws(); return v; }

        Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') return obj();
            if (c == '[') return arr();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return number();
        }

        Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws(); String k = string(); ws(); i++; // ':'
                m.put(k, value()); ws();
                if (s.charAt(i++) == '}') return m;   // else ','
            }
        }

        List<Object> arr() {
            List<Object> a = new ArrayList<>();
            i++; ws();
            if (s.charAt(i) == ']') { i++; return a; }
            while (true) {
                a.add(value()); ws();
                if (s.charAt(i++) == ']') return a;   // else ','
            }
        }

        String string() {
            StringBuilder b = new StringBuilder();
            i++; // opening quote
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': b.append('\n'); break;
                        case 't': b.append('\t'); break;
                        case 'r': b.append('\r'); break;
                        case 'b': b.append('\b'); break;
                        case 'f': b.append('\f'); break;
                        case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                        default: b.append(e);
                    }
                } else b.append(c);
            }
        }

        Object number() {
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(st, i));
        }

        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    }
}
