package org.pots.potsmod.util;

import lombok.experimental.UtilityClass;

/**
 * Hand-written recursive-descent expression parser.
 * Returns null on any error (bad syntax, division by zero, etc.).
 * Thread-unsafe – call only from the render/game thread.
 *
 * Grammar (simplified):
 *   expr   = term  ( ('+' | '-') term )*
 *   term   = power ( ('*' | '/' | '%') power )*   -- implicit-mult applied in parseAtom
 *   power  = unary ('^' power)?                   -- right-associative
 *   unary  = '-' unary | atom
 *   atom   = number | '(' expr ')' | func '(' args ')' | constant
 */
@UtilityClass
public class CalcParser {

    /** Last valid answer – can be read as constant "ans". */
    public static double ans = 0.0;

    private static final int TOK_NUM   = 0;
    private static final int TOK_IDENT = 1;
    private static final int TOK_PLUS  = 2;
    private static final int TOK_MINUS = 3;
    private static final int TOK_MUL   = 4;
    private static final int TOK_DIV   = 5;
    private static final int TOK_MOD   = 6;
    private static final int TOK_POW   = 7;
    private static final int TOK_LPAREN = 8;
    private static final int TOK_RPAREN = 9;
    private static final int TOK_COMMA  = 10;
    private static final int TOK_EOF    = 11;

    /** Shared mutable tokenizer state (single-threaded use only). */
    private static String src;
    private static int pos;
    private static int tokType;
    private static double tokNum;
    private static String tokIdent;

    /**
     * Parse and evaluate {@code expr}. Returns null on any error.
     * On success, updates {@link #ans}.
     */
    public static Double parse(String expr) {
        if (expr == null || expr.isBlank()) return null;
        try {
            src = expr.trim();
            pos = 0;
            advance();
            double result = parseExpr();
            if (tokType != TOK_EOF) return null; // trailing garbage
            if (!Double.isFinite(result)) return null;
            ans = result;
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    // -------- tokenizer --------

    private static void advance() {
        // skip whitespace
        while (pos < src.length() && src.charAt(pos) == ' ') pos++;
        if (pos >= src.length()) { tokType = TOK_EOF; return; }

        char c = src.charAt(pos);

        if (c == '+') { tokType = TOK_PLUS;   pos++; return; }
        if (c == '-') { tokType = TOK_MINUS;  pos++; return; }
        if (c == '*') { tokType = TOK_MUL;    pos++; return; }
        if (c == '/') { tokType = TOK_DIV;    pos++; return; }
        if (c == '%') { tokType = TOK_MOD;    pos++; return; }
        if (c == '^') { tokType = TOK_POW;    pos++; return; }
        if (c == '(') { tokType = TOK_LPAREN; pos++; return; }
        if (c == ')') { tokType = TOK_RPAREN; pos++; return; }
        if (c == ',') { tokType = TOK_COMMA;  pos++; return; }

        if (Character.isDigit(c) || c == '.') {
            parseNumberToken();
            return;
        }

        if (Character.isLetter(c) || c == '_') {
            int start = pos;
            while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) pos++;
            tokIdent = src.substring(start, pos);
            tokType = TOK_IDENT;
            return;
        }

        // unknown char – throw to signal error
        throw new RuntimeException("Unexpected character: " + c);
    }

    private static void parseNumberToken() {
        int start = pos;
        while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) pos++;
        // scientific notation
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        double value = Double.parseDouble(src.substring(start, pos));

        // suffixes: k m b eb le stx (case-insensitive)
        if (pos < src.length()) {
            int sfxStart = pos;
            // collect up to 3 letters for suffix check
            int sfxEnd = pos;
            while (sfxEnd < src.length() && sfxEnd - pos < 3 && Character.isLetter(src.charAt(sfxEnd))) sfxEnd++;
            String sfx = src.substring(sfxStart, sfxEnd).toLowerCase(java.util.Locale.ROOT);
            if (sfx.equals("stx")) { value *= 262144.0; pos = sfxEnd; }
            else if (sfx.startsWith("le") && sfx.length() >= 2) { value *= 4096.0; pos = sfxStart + 2; }
            else if (sfx.startsWith("eb") && sfx.length() >= 2) { value *= 64.0;   pos = sfxStart + 2; }
            else if (sfx.startsWith("b")  && sfx.length() >= 1) { value *= 1e9;    pos = sfxStart + 1; }
            else if (sfx.startsWith("m")  && sfx.length() >= 1) { value *= 1e6;    pos = sfxStart + 1; }
            else if (sfx.startsWith("k")  && sfx.length() >= 1) { value *= 1e3;    pos = sfxStart + 1; }
            // else no suffix, leave pos as-is
        }

        tokNum = value;
        tokType = TOK_NUM;
    }

    // -------- parser --------

    /** expr = term ( ('+' | '-') term )* */
    private static double parseExpr() {
        double left = parseTerm();
        while (tokType == TOK_PLUS || tokType == TOK_MINUS) {
            if (tokType == TOK_PLUS)  { advance(); left += parseTerm(); }
            else                       { advance(); left -= parseTerm(); }
        }
        return left;
    }

    /** term = power ( ('*' | '/' | '%') power )* with implicit-mult */
    private static double parseTerm() {
        double left = parsePower();
        for (;;) {
            if (tokType == TOK_MUL) { advance(); left *= parsePower(); }
            else if (tokType == TOK_DIV) {
                advance();
                double r = parsePower();
                if (r == 0.0) throw new ArithmeticException("div0");
                left /= r;
            } else if (tokType == TOK_MOD) {
                advance();
                double r = parsePower();
                if (r == 0.0) throw new ArithmeticException("mod0");
                left %= r;
            } else if (tokType == TOK_LPAREN || tokType == TOK_NUM || tokType == TOK_IDENT) {
                // implicit multiplication: e.g. 2(3+4) or 2pi
                left *= parsePower();
            } else {
                break;
            }
        }
        return left;
    }

    /** power = unary ('^' power)? -- right-associative */
    private static double parsePower() {
        double base = parseUnary();
        if (tokType == TOK_POW) {
            advance();
            double exp = parsePower(); // right-associative
            return Math.pow(base, exp);
        }
        return base;
    }

    /** unary = '-' unary | atom */
    private static double parseUnary() {
        if (tokType == TOK_MINUS) {
            advance();
            return -parseUnary();
        }
        return parseAtom();
    }

    /** atom = number | constant | func'('args')' | '('expr')' */
    private static double parseAtom() {
        if (tokType == TOK_NUM) {
            double v = tokNum;
            advance();
            return v;
        }

        if (tokType == TOK_LPAREN) {
            advance();
            double v = parseExpr();
            expect(TOK_RPAREN);
            return v;
        }

        if (tokType == TOK_IDENT) {
            String name = tokIdent.toLowerCase(java.util.Locale.ROOT);
            advance();

            // Constants
            if (name.equals("pi"))  return Math.PI;
            if (name.equals("e"))   return Math.E;
            if (name.equals("ans")) return ans;

            // Functions – must be followed by '('
            if (tokType != TOK_LPAREN) throw new RuntimeException("Unknown identifier: " + name);
            advance(); // consume '('

            // two-arg functions
            if (name.equals("min") || name.equals("max") || name.equals("log")) {
                double a = parseExpr();
                expect(TOK_COMMA);
                double b = parseExpr();
                expect(TOK_RPAREN);
                return switch (name) {
                    case "min" -> Math.min(a, b);
                    case "max" -> Math.max(a, b);
                    case "log" -> Math.log(b) / Math.log(a); // log(base, value)
                    default    -> throw new RuntimeException("??");
                };
            }

            // one-arg functions
            double a = parseExpr();
            expect(TOK_RPAREN);
            return switch (name) {
                case "sqrt"  -> { if (a < 0) throw new ArithmeticException("sqrt<0"); yield Math.sqrt(a); }
                case "abs"   -> Math.abs(a);
                case "round" -> Math.round(a);
                case "floor" -> Math.floor(a);
                case "ceil"  -> Math.ceil(a);
                case "sin"   -> Math.sin(a);
                case "cos"   -> Math.cos(a);
                case "tan"   -> Math.tan(a);
                case "ln"    -> { if (a <= 0) throw new ArithmeticException("ln<=0"); yield Math.log(a); }
                default      -> throw new RuntimeException("Unknown function: " + name);
            };
        }

        throw new RuntimeException("Unexpected token type: " + tokType);
    }

    private static void expect(int type) {
        if (tokType != type) throw new RuntimeException("Expected token " + type + " got " + tokType);
        advance();
    }

    // -------- formatting --------

    /**
     * Format a result for display: up to 6 significant decimal places,
     * trailing zeros trimmed, thousands-separator commas on the integer part.
     */
    public static String format(double value) {
        if (!Double.isFinite(value)) return "?";
        // Round to 6 decimal places
        double rounded = Math.round(value * 1_000_000.0) / 1_000_000.0;
        String raw;
        if (rounded == Math.floor(rounded) && !Double.isInfinite(rounded) && Math.abs(rounded) < 1e15) {
            raw = String.valueOf((long) rounded);
        } else {
            raw = String.format(java.util.Locale.ROOT, "%.6f", rounded);
            // trim trailing zeros after decimal point
            if (raw.contains(".")) {
                raw = raw.replaceAll("0+$", "").replaceAll("\\.$", "");
            }
        }

        // Add thousands separators to integer part
        int dotIdx = raw.indexOf('.');
        String intPart = dotIdx < 0 ? raw : raw.substring(0, dotIdx);
        String decPart = dotIdx < 0 ? ""  : raw.substring(dotIdx);
        boolean negative = intPart.startsWith("-");
        String digits = negative ? intPart.substring(1) : intPart;
        StringBuilder sb = new StringBuilder();
        int start = digits.length() % 3;
        if (start > 0) sb.append(digits, 0, start);
        for (int i = start; i < digits.length(); i += 3) {
            if (sb.length() > 0) sb.append(',');
            sb.append(digits, i, i + 3);
        }
        if (sb.length() == 0) sb.append('0');
        return (negative ? "-" : "") + sb + decPart;
    }

    /** Format without thousands separators (for clipboard). */
    public static String formatRaw(double value) {
        if (!Double.isFinite(value)) return "?";
        double rounded = Math.round(value * 1_000_000.0) / 1_000_000.0;
        if (rounded == Math.floor(rounded) && Math.abs(rounded) < 1e15) {
            return String.valueOf((long) rounded);
        }
        String s = String.format(java.util.Locale.ROOT, "%.6f", rounded);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }
}
