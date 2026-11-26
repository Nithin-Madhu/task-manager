package parser;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SimpleJson {
    private final String s;
    private int i;

    private SimpleJson(String s) { this.s = s; }

    public static Map<String, Object> parseObject(String text) {
        if (text == null) throw new IllegalArgumentException("reply was empty");
        SimpleJson p = new SimpleJson(stripFences(text));
        Map<String, Object> map = p.object();
        p.skipWs();
        if (p.i != p.s.length()) throw p.error("unexpected text after the JSON object");
        return map;
    }

    private static String stripFences(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            t = nl < 0 ? "" : t.substring(nl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
            t = t.trim();
        }
        return t;
    }

    private Map<String, Object> object() {
        skipWs();
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWs();
        if (peek() == '}') { i++; return map; }
        while (true) {
            skipWs();
            String key = string();
            skipWs();
            expect(':');
            skipWs();
            map.put(key, value());
            skipWs();
            char c = next();
            if (c == '}') return map;
            if (c != ',') { i--; throw error("expected ',' or '}'"); }
        }
    }

    private Object value() {
        char c = peek();
        if (c == '"') return string();
        if (c == '{' || c == '[') throw error("nested objects and arrays are not supported");
        int start = i;
        while (i < s.length() && ",}".indexOf(s.charAt(i)) < 0) i++;
        String raw = s.substring(start, i).trim();
        if (raw.equals("null")) return null;
        if (raw.equals("true")) return Boolean.TRUE;
        if (raw.equals("false")) return Boolean.FALSE;
        try {
            return Double.valueOf(raw);
        } catch (NumberFormatException e) {
            throw error("unexpected value '" + raw + "'");
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            char e = next();
            switch (e) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case 'u':
                    if (i + 4 > s.length()) throw error("bad unicode escape");
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default: sb.append(e);
            }
        }
    }

    private void skipWs() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private char peek() {
        if (i >= s.length()) throw error("unexpected end of reply");
        return s.charAt(i);
    }

    private char next() {
        char c = peek();
        i++;
        return c;
    }

    private void expect(char c) {
        if (peek() != c) throw error("expected '" + c + "'");
        i++;
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException(msg + " at position " + i);
    }
}