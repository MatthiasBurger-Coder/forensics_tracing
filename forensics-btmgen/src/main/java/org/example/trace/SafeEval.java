package org.example.trace;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Side-effect free helper used in Byteman IF expressions.
 * All methods are pure, null-safe, and do not call application code.
 */
public final class SafeEval {
    private SafeEval() {
    }

    /** Null-safe equality: compares primitives, numbers, enums, and strings reliably. */
    public static boolean ifEq(Object value, Object literal) {
        if (value == literal) {
            return true;
        }
        if (value == null || literal == null) {
            return false;
        }
        if (value instanceof Enum<?> vEnum && literal instanceof String litStr) {
            return vEnum.name().contentEquals(litStr);
        }
        if (literal instanceof Enum<?> lEnum && value instanceof String valStr) {
            return lEnum.name().contentEquals(valStr);
        }
        if (isNumber(value) && isNumber(literal)) {
            return toBigDecimal(value).compareTo(toBigDecimal(literal)) == 0;
        }
        return value.equals(literal);
    }

    /** Type check without triggering user code. */
    public static boolean ifInstanceOf(Object value, String fqcn) {
        if (value == null || fqcn == null || fqcn.isEmpty()) {
            return false;
        }
        Class<?> current = value.getClass();
        while (current != null) {
            if (current.getName().equals(fqcn)) {
                return true;
            }
            if (implementsInterface(current, fqcn)) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    public static boolean and(boolean a, boolean b) {
        return a && b;
    }

    public static boolean or(boolean a, boolean b) {
        return a || b;
    }

    /** Registry entry capable of evaluating the original condition without side effects. */
    public interface Evaluator {
        boolean eval();
    }

    private static final Map<String, Evaluator> REGISTRY = new ConcurrentHashMap<>();

    /** Registers or replaces an evaluator for the given rule id. */
    public static void register(String ruleId, Evaluator evaluator) {
        if (ruleId == null || evaluator == null) {
            return;
        }
        REGISTRY.put(ruleId, evaluator);
    }

    /** Evaluates the registered predicate; returns true if none is installed (fail-open). */
    public static boolean ifMatch(String ruleId) {
        if (ruleId == null) {
            return true;
        }
        Evaluator evaluator = REGISTRY.get(ruleId);
        if (evaluator == null) {
            return true;
        }
        return safeEval(evaluator);
    }

    private static boolean safeEval(Evaluator evaluator) {
        try {
            return evaluator.eval();
        } catch (Throwable t) {
            return true;
        }
    }

    /** Visible for tests to reset state. */
    static void _clearForTests() {
        REGISTRY.clear();
    }

    private static boolean isNumber(Object value) {
        return value instanceof Byte
            || value instanceof Short
            || value instanceof Integer
            || value instanceof Long
            || value instanceof Float
            || value instanceof Double
            || value instanceof BigDecimal;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Byte b) {
            return BigDecimal.valueOf(b.longValue());
        }
        if (value instanceof Short s) {
            return BigDecimal.valueOf(s.longValue());
        }
        if (value instanceof Integer i) {
            return BigDecimal.valueOf(i.longValue());
        }
        if (value instanceof Long l) {
            return BigDecimal.valueOf(l.longValue());
        }
        if (value instanceof Float f) {
            return new BigDecimal(String.valueOf(f));
        }
        if (value instanceof Double d) {
            return new BigDecimal(String.valueOf(d));
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static boolean implementsInterface(Class<?> type, String fqcn) {
        for (Class<?> iface : type.getInterfaces()) {
            if (iface.getName().equals(fqcn)) {
                return true;
            }
            if (implementsInterface(iface, fqcn)) {
                return true;
            }
        }
        return false;
    }
}
