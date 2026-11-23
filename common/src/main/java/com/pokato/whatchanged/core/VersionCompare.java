package com.pokato.whatchanged.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decides whether a version change is an update or a downgrade. Mod versions are messy ("0.92.0+1.20.1",
 * "1.2.0-beta.3", "mc1.21-5.4.2", "v2"), so this is deliberately forgiving:
 * - a leading "v" and everything after '+' (build metadata, often the Minecraft version) are ignored;
 * - the release part is compared number by number ("1.10" is newer than "1.9");
 * - a suffix ("-beta.3", "-hotfix", "b2") decides between equal numbers: a pre-release
 *   (snapshot &lt; alpha &lt; beta &lt; pre &lt; rc) is older than the release, a fix ("-hotfix", "-patch1", "-r2") is newer,
 *   a loader label ("-fabric") changes nothing, and an unknown word ("-ultimate") can't be ordered.
 * Returns 0 when it can't tell, and callers then treat the change as a plain update, so a change only shows as a
 * downgrade when the order is certain.
 */
public final class VersionCompare {
    private VersionCompare() {}

    /** &lt;0 when a is older than b, &gt;0 when a is newer, 0 when equal or not comparable. */
    public static int compare(String a, String b) {
        if (a == null || b == null) return 0;
        String x = normalize(a), y = normalize(b);
        if (x.equals(y) || !hasDigit(x) || !hasDigit(y)) return 0;
        String[] px = splitPre(x), py = splitPre(y);
        List<Object> tx = tokens(px[0]), ty = tokens(py[0]);
        // "mc1.21-5.4" vs "mc1.21-5.5" compares fine, but "abc123" vs "def456" (git hashes, code names) does not
        if (!tx.isEmpty() && !ty.isEmpty() && tx.get(0) instanceof String && ty.get(0) instanceof String
                && !tx.get(0).equals(ty.get(0))) return 0;
        if (tx.isEmpty() || ty.isEmpty()) return 0;
        List<Object> sx = suffix(tx, px[1]), sy = suffix(ty, py[1]);
        int c = compareTokens(tx, ty);
        if (c != 0) return c;
        return compareSuffix(sx, sy);
    }

    // Suffix ranks. UNKNOWN can't be ordered against anything but the same word.
    private static final int UNKNOWN = -1, SNAPSHOT = 0, ALPHA = 1, BETA = 2, PRE = 3, RC = 4, RELEASE = 5, FIX = 6;

    /**
     * Moves a word glued to the number ("1.0.0beta3" -> "beta3", "1.0.0.beta.3" -> "beta.3") out of the release
     * tokens and returns it together with the '-' suffix as one token list. A word in front ("mc1.21") stays.
     */
    private static List<Object> suffix(List<Object> release, String pre) {
        List<Object> out = new ArrayList<>();
        for (int i = 1; i < release.size(); i++) {
            if (release.get(i) instanceof String && release.get(i - 1) instanceof Long) {
                List<Object> tail = release.subList(i, release.size());
                out.addAll(tail);
                tail.clear();
                break;
            }
        }
        if (pre != null) out.addAll(tokens(pre));
        return out;
    }

    /** "1.2.3" vs "1.2.3-hotfix": compares the suffixes of two versions whose numbers are equal. */
    private static int compareSuffix(List<Object> a, List<Object> b) {
        List<Object> x = withoutLabels(a), y = withoutLabels(b);
        int rx = rank(x), ry = rank(y);
        String wx = word(x), wy = word(y);
        if (rx == UNKNOWN || ry == UNKNOWN) {
            // "1.2.3" -> "1.2.3-ultimate": no idea; "-legacy2" -> "-legacy3" is fine (same word)
            return rx == ry && wx.equals(wy) ? compareTokens(rest(x), rest(y)) : 0;
        }
        if (rx != ry) return Integer.compare(rx, ry);       // 1.2.3-beta < 1.2.3 < 1.2.3-hotfix
        if (rx == FIX && !wx.equals(wy)) return 0;            // "-hotfix" vs "-patch": can't tell
        return compareTokens(rest(x), rest(y));               // beta.2 < beta.3, hotfix < hotfix2
    }

    private static List<Object> withoutLabels(List<Object> s) {
        List<Object> out = new ArrayList<>();
        for (Object o : s) if (!(o instanceof String) || !isLabel((String) o)) out.add(o);
        return out;
    }

    /** Words that say which build it is, not which comes first ("1.2.3-fabric", "1.2.3-forge"). */
    private static boolean isLabel(String w) {
        switch (w) {
            case "fabric": case "forge": case "neoforge": case "quilt": case "mc": case "minecraft":
            case "universal": case "client": case "server": case "common":
                return true;
            default:
                return false;
        }
    }

    private static int rank(List<Object> s) {
        if (s.isEmpty()) return RELEASE;
        if (s.get(0) instanceof Long) return FIX;             // "1.20.1-forge-47.2.0": a number after a label
        String w = (String) s.get(0);
        switch (w) {
            case "snapshot": case "snap": case "nightly": case "dev": case "indev": case "experimental": case "exp":
                return SNAPSHOT;
            case "alpha": return ALPHA;
            case "beta": return BETA;
            case "pre": case "preview": case "prerelease": case "milestone": return PRE;
            case "rc": case "cr": return RC;
            case "release": case "final": case "stable": case "ga": return RELEASE;
            case "hotfix": case "hot": case "hf": case "fix": case "fixed": case "bugfix": case "patch": case "update":
            case "rev": case "revision": case "build":
                return FIX;
            default:
                break;
        }
        // single letters only count with a number: "b2" is beta 2, a lone "1.2.3a" may be a hotfix letter
        if (s.size() > 1 && s.get(1) instanceof Long) {
            switch (w) {
                case "a": return ALPHA;
                case "b": return BETA;
                case "m": return PRE;
                case "r": case "p": return FIX;
                default: break;
            }
        }
        return UNKNOWN;
    }

    private static String word(List<Object> s) {
        return s.isEmpty() ? "" : s.get(0) instanceof Long ? "#" : (String) s.get(0);
    }

    private static List<Object> rest(List<Object> s) {
        return s.isEmpty() || s.get(0) instanceof Long ? s : s.subList(1, s.size());
    }

    public static boolean isDowngrade(String oldVersion, String newVersion) {
        return compare(oldVersion, newVersion) > 0;
    }

    // [recovered 1.3.0: decompiled from the release jar, original comments lost]
    public static boolean isModDowngrade(String oldVersion, String newVersion, String... minecraft) {
        if (oldVersion != null && newVersion != null) {
            String[] a = splitMinecraft(oldVersion, minecraft);
            String[] b = splitMinecraft(newVersion, minecraft);
            if (a[1] != null && b[1] != null && !line(a[1]).equals(line(b[1]))) {
                int c = compare(line(a[1]), line(b[1]));
                if (c != 0) {
                    return c > 0;
                }
            }

            return compare(a[0], b[0]) > 0 && compare(oldVersion, newVersion) > 0;
        } else {
            return false;
        }
    }

    // [recovered 1.3.0: decompiled from the release jar, original comments lost]
    static String[] splitMinecraft(String version, String... minecraft) {
        String v = version.trim();
        if (minecraft == null) {
            return new String[]{v, null};
        } else {
            List<String> mcs = new ArrayList<>();

            for (String m : minecraft) {
                if (m != null && !m.isBlank()) {
                    String t = m.trim();
                    if (!mcs.contains(t)) {
                        mcs.add(t);
                    }

                    String[] p = t.split("\\.");
                    if (p.length > 2) {
                        String line = p[0] + "." + p[1];
                        if (!mcs.contains(line)) {
                            mcs.add(line);
                        }
                    }
                }
            }

            mcs.sort((x, y) -> y.length() - x.length());
            int plus = v.indexOf(43);
            String meta = plus >= 0 ? v.substring(plus) : "";
            String core = plus >= 0 ? v.substring(0, plus) : v;
            String loader = "(?:(?:mc|minecraft|forge|neoforge|fabric|quilt)[-_ ]?)?";

            for (String mx : mcs) {
                String q = Pattern.quote(mx);

                for (String re : new String[]{"(?i)^" + loader + q + "[-_ ]+(?=\\d)", "(?i)[-_ ]+" + loader + q + "$"}) {
                    String next = core.replaceFirst(re, "");
                    if (!next.equals(core) && hasDigit(next)) {
                        return new String[]{next + meta, mx};
                    }
                }
            }

            return new String[]{v, null};
        }
    }

    // [recovered 1.3.0: decompiled from the release jar, original comments lost]
    private static String line(String mc) {
        String[] p = mc.split("\\.");
        return p.length > 2 ? p[0] + "." + p[1] : mc;
    }

    private static String normalize(String v) {
        String s = v.trim().toLowerCase(Locale.ROOT);
        int plus = s.indexOf('+');
        if (plus > 0) s = s.substring(0, plus);
        if (s.startsWith("v") && s.length() > 1 && Character.isDigit(s.charAt(1))) s = s.substring(1);
        return s;
    }

    /** "1.2.0-beta.3" -> {"1.2.0", "beta.3"}; a '-' directly followed by a digit is part of the release ("mc1.21-5.4"). */
    private static String[] splitPre(String s) {
        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) == '-' && !Character.isDigit(s.charAt(i + 1))) {
                return new String[]{s.substring(0, i), s.substring(i + 1)};
            }
        }
        return new String[]{s, null};
    }

    /** Alternating runs of digits and letters; separators (. - _ space) only split. */
    private static List<Object> tokens(String s) {
        List<Object> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        Boolean digits = null;
        for (int i = 0; i <= s.length(); i++) {
            char ch = i < s.length() ? s.charAt(i) : '.';
            boolean isDigit = Character.isDigit(ch), isLetter = Character.isLetter(ch);
            if ((!isDigit && !isLetter) || (digits != null && digits != isDigit)) {
                if (cur.length() > 0) out.add(Boolean.TRUE.equals(digits) ? (Object) parse(cur.toString()) : cur.toString());
                cur.setLength(0);
                digits = null;
            }
            if (isDigit || isLetter) {
                cur.append(ch);
                digits = isDigit;
            }
        }
        return out;
    }

    private static Long parse(String digits) {
        try {
            return Long.parseLong(digits.length() > 18 ? digits.substring(0, 18) : digits);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static int compareTokens(List<Object> a, List<Object> b) {
        int n = Math.max(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            Object x = i < a.size() ? a.get(i) : null, y = i < b.size() ? b.get(i) : null;
            if (x == null) return y instanceof Long && (Long) y == 0 ? 0 : -1; // 1.2 < 1.2.1, but 1.2 == 1.2.0
            if (y == null) return x instanceof Long && (Long) x == 0 ? 0 : 1;
            int c;
            if (x instanceof Long && y instanceof Long) c = Long.compare((Long) x, (Long) y);
            else if (x instanceof Long) c = 1;            // number beats word: 1.0.1 > 1.0.beta
            else if (y instanceof Long) c = -1;
            else c = ((String) x).compareTo((String) y);   // alpha < beta < pre < rc
            if (c != 0) return c;
        }
        return 0;
    }

    private static boolean hasDigit(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) return true;
        return false;
    }
}
