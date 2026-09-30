package com.zynelabs.iptv.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Automatic category engine.
 *
 * Giant playlists ship hundreds of messy group-titles
 * ("UK: SKY CINEMA...", "UK MOVIES: SKY CINEMA...", "UK-Sky Cinema Family"…).
 * This engine:
 *  1. normalizes group names (trim, collapse spaces, case-insensitive dedupe),
 *  2. buckets every channel into a smart top-level category by keyword rules
 *     matched against "group + channel name".
 */
public class Cats {

    public static final String SPORTS = "\u26BD Sports";
    public static final String MOVIES = "\uD83C\uDFAC Movies";
    public static final String NEWS = "\uD83D\uDCF0 News";
    public static final String KIDS = "\uD83E\uDDD2 Kids";
    public static final String MUSIC = "\uD83C\uDFB5 Music";
    public static final String DOCS = "\uD83D\uDD2C Documentary";
    public static final String ENTERTAIN = "\uD83C\uDFAD Entertainment";
    public static final String RADIO = "\uD83D\uDCFB Radio";
    public static final String ADULT = "\uD83D\uDD1E Adult";
    public static final String OTHER = "\uD83D\uDCE6 Other";

    /** Display order of the smart categories. */
    public static final String[] ORDER = {
            SPORTS, MOVIES, NEWS, KIDS, MUSIC, DOCS, ENTERTAIN, RADIO, ADULT, OTHER
    };

    /** First match wins. Keywords are matched as whole tokens. */
    private static final String[][] RULES = {
            {ADULT, "xxx porn adult playboy xhamster redtube 18+"},
            {SPORTS, "sport espn bein football soccer nba nfl cricket tennis golf racing wwe ufc mma boxing snooker darts olympic laliga bundesliga premier eurosport fifa"},
            {MOVIES, "movie movies cinema film films hbo cinemax netflix showtime starz"},
            {NEWS, "news cnn bbc aljazeera bloomberg cnbc france24 dwnews cna"},
            {KIDS, "kids kid children cartoon cartoons disney nickelodeon nickjr cartoonito boomerang pbskids babytv"},
            {RADIO, "radio fm"},
            {MUSIC, "music mtv vh1 trace"},
            {DOCS, "documentary documentaries discovery natgeo geographic history science animalplanet"},
            {ENTERTAIN, "entertainment comedy drama reality variety show shows general family lifestyle"},
    };

    /** "a-b_c" -> "a b c", lowercased, padded with spaces for token matching. */
    private static String tokens(String s) {
        if (s == null) return " ";
        String t = s.toLowerCase(Locale.US).replaceAll("[^a-z0-9+]+", " ");
        return " " + t + " ";
    }

    /** Smart top-level category for a channel. Never null. */
    public static String of(Channel c) {
        String hay = tokens(c.group + " " + c.name);
        for (String[] rule : RULES) {
            String[] kws = rule[1].split(" ");
            for (String kw : kws) {
                if (kw.isEmpty()) continue;
                if (hay.contains(" " + kw + " ") || hay.contains(" " + kw + "+ ")) return rule[0];
            }
        }
        return OTHER;
    }

    /**
     * Tag every channel with its smart category. Call ONCE off the UI thread
     * (e.g. right after parsing) — afterwards getSmartCat() is a cheap field
     * read and all UI filtering stays fast.
     */
    public static void tagAll(List<Channel> channels) {
        for (Channel c : channels) {
            c.smartCat = of(c);
            c.country = countryOf(c);
        }
    }

    // ---------------- countries ----------------

    /** ISO code, display name, keywords. Order matters: specific before generic. */
    private static final String[][] COUNTRIES = {
            {"GB", "UK", "uk england britain british london"},
            {"US", "USA", "usa america american"},
            {"CA", "Canada", "canada canadian"},
            {"AU", "Australia", "australia aussie"},
            {"IE", "Ireland", "ireland irish"},
            {"FR", "France", "france french"},
            {"DE", "Germany", "germany german deutschland"},
            {"ES", "Spain", "spain spanish espana"},
            {"IT", "Italy", "italy italian"},
            {"PT", "Portugal", "portugal portuguese"},
            {"NL", "Netherlands", "netherlands dutch holland"},
            {"GR", "Greece", "greece greek"},
            {"TR", "Turkey", "turkey turkish"},
            {"SA", "Arabia", "arab arabic saudi emirates dubai qatar kuwait bahrain oman"},
            {"IN", "India", "india indian hindi"},
            {"PK", "Pakistan", "pakistan pakistani urdu"},
            {"BD", "Bangladesh", "bangladesh bangla bengali"},
            {"MM", "Myanmar", "myanmar burma burmese"},
            {"TH", "Thailand", "thailand thai"},
            {"MY", "Malaysia", "malaysia malay"},
            {"SG", "Singapore", "singapore"},
            {"ID", "Indonesia", "indonesia"},
            {"PH", "Philippines", "philippines filipino pinoy"},
            {"VN", "Vietnam", "vietnam vietnamese"},
            {"CN", "China", "china chinese mandarin cantonese"},
            {"JP", "Japan", "japan japanese"},
            {"KR", "Korea", "korea korean"},
            {"RU", "Russia", "russia russian"},
            {"UA", "Ukraine", "ukraine ukrainian"},
            {"PL", "Poland", "poland polish"},
            {"BR", "Brazil", "brazil brazilian"},
            {"MX", "Mexico", "mexico mexican"},
            {"AR", "Argentina", "argentina"},
            {"ZA", "Africa", "africa african nigeria ghana kenya"},
            {"NZ", "New Zealand", "zealand"},
    };

    public static final String COUNTRY_OTHER = "\uD83C\uDF10 Other";

    /** Regional-indicator flag emoji for an ISO code, e.g. "GB" -> flag. */
    public static String flag(String iso) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < iso.length(); i++) {
            char ch = iso.charAt(i);
            if (ch >= 'A' && ch <= 'Z') sb.appendCodePoint(0x1F1E6 + (ch - 'A'));
        }
        return sb.toString();
    }

    /** Country label for a channel, e.g. "<flag> UK". Empty string if unknown. */
    public static String countryOf(Channel c) {
        String hay = tokens(c.group + " " + c.name);
        for (String[] co : COUNTRIES) {
            String[] kws = co[2].split(" ");
            for (String kw : kws) {
                if (kw.isEmpty()) continue;
                if (hay.contains(" " + kw + " ")) return flag(co[0]) + " " + co[1];
            }
        }
        return "";
    }

    /** Country label never empty: unknown -> COUNTRY_OTHER. */
    public static String countryKey(Channel c) {
        String co = c.getCountry();
        return co.isEmpty() ? COUNTRY_OTHER : co;
    }

    /** Count channels per country label (single pass, uses cached field). */
    public static Map<String, Integer> countryCounts(List<Channel> channels) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Channel c : channels) {
            String co = countryKey(c);
            Integer n = m.get(co);
            m.put(co, n == null ? 1 : n + 1);
        }
        return m;
    }

    /** Normalize a raw group-title for display + dedupe. */
    public static String normGroup(String g) {
        if (g == null) return "";
        return g.trim().replaceAll("\\s+", " ");
    }

    /** Dedupe key for a normalized group (case-insensitive). */
    public static String groupKey(String g) {
        return normGroup(g).toLowerCase(Locale.US);
    }

    /**
     * Ordered smart categories present in the list, each mapped to its
     * normalized sub-groups (in first-seen order). Channels are scanned once.
     */
    public static Map<String, List<String>> index(List<Channel> channels) {
        Map<String, List<String>> cats = new LinkedHashMap<>();
        Map<String, String> seenGroups = new LinkedHashMap<>(); // groupKey -> display
        for (Channel c : channels) {
            String cat = c.getSmartCat();
            List<String> subs = cats.get(cat);
            if (subs == null) { subs = new ArrayList<>(); cats.put(cat, subs); }
            String gk = groupKey(c.displayGroup());
            if (!seenGroups.containsKey(gk)) {
                String disp = normGroup(c.displayGroup());
                if (disp.isEmpty()) disp = "Ungrouped";
                seenGroups.put(gk, disp);
            }
            String disp = seenGroups.get(gk);
            if (!subs.contains(disp)) subs.add(disp);
        }
        // order categories by ORDER, keep any extras at the end
        Map<String, List<String>> ordered = new LinkedHashMap<>();
        for (String o : ORDER) if (cats.containsKey(o)) ordered.put(o, cats.get(o));
        for (Map.Entry<String, List<String>> e : cats.entrySet())
            if (!ordered.containsKey(e.getKey())) ordered.put(e.getKey(), e.getValue());
        return ordered;
    }

    /** Count channels per smart category (single pass). */
    public static Map<String, Integer> counts(List<Channel> channels) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Channel c : channels) {
            String cat = c.getSmartCat();
            Integer n = m.get(cat);
            m.put(cat, n == null ? 1 : n + 1);
        }
        return m;
    }
}
