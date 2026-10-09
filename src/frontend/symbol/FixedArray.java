package frontend.symbol;

import frontend.ast.declaration.ConstInitVal;
import frontend.ast.declaration.InitVal;
import frontend.ast.expression.ConstExp;
import frontend.ast.expression.Exp;
import frontend.token.Token;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An array constant known at compile time.
 *
 * <p>Only one dimension is needed for this grammar, so a flat index maps directly onto a value.
 * Every index that was never written keeps the default value {@code 0}.
 */
final public class FixedArray {
    /** Array length, used to reject out-of-range constant subscripts. */
    private final int length;
    /** Index to value. A missing key means the default value {@code 0}. */
    private final Map<Integer, Integer> values;

    public FixedArray(int length) {
        this.length = length;
        this.values = new HashMap<>();
    }

    public int getLength() {
        return length;
    }

    public void set(int index, int value) {
        values.put(index, value);
    }

    /**
     * Read a constant element.
     *
     * @param index the constant subscript
     * @return the stored value, or {@code 0} when it was never initialised
     * @throws IndexOutOfBoundsException if the subscript is outside the array
     */
    public int get(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException(
                    "Constant subscript " + index + " is out of bounds for length " + length);
        }
        return values.getOrDefault(index, 0);
    }

    /**
     * Collect every element of an initialiser into a flat array of the given length.
     *
     * <p>Braces are flattened, so a nested list is accepted as well; a value that cannot be
     * evaluated propagates its exception to the caller.
     */
    public static FixedArray from(int length, ConstInitVal init) {
        FixedArray array = new FixedArray(length);
        if (init.getKind() == ConstInitVal.Kind.Str) {
            return fromString(length, init.getString());
        }
        int index = 0;
        if (init.getKind() == ConstInitVal.Kind.Exp) {
            array.set(index, init.getExp().calculate());
        } else {
            for (ConstExp element : init.getElements()) {
                array.set(index++, element.calculate());
            }
        }
        return array;
    }

    /**
     * Collect every element of an initialiser into a flat array of the given length.
     *
     * @see #from(int, ConstInitVal)
     */
    public static FixedArray from(int length, InitVal init) {
        FixedArray array = new FixedArray(length);
        if (init.getKind() == InitVal.Kind.Str) {
            return fromString(length, init.getString());
        }
        int index = 0;
        if (init.getKind() == InitVal.Kind.Exp) {
            array.set(index, init.getExp().calculateConst());
        } else {
            for (Exp element : init.getElements()) {
                array.set(index++, element.calculateConst());
            }
        }
        return array;
    }

    /**
     * Build the element list of a string initialiser, including the terminating {@code '\0'}.
     *
     * <p>The token value has already had its escapes decoded by the lexer, so the characters can be
     * copied across directly; indices beyond the array length are dropped.
     */
    private static FixedArray fromString(int length, Token string) {
        FixedArray array = new FixedArray(length);
        String content = string.value();
        for (int i = 0; i < content.length() && i < length; i++) {
            array.set(i, content.charAt(i));
        }
        if (content.length() < length) {
            array.set(content.length(), 0);
        }
        return array;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(get(i));
        }
        return builder.append('}').toString();
    }
}

