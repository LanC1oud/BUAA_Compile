package frontend.symbol;

import frontend.ast.declaration.ConstInitVal;
import frontend.ast.declaration.InitVal;
import frontend.ast.expression.ConstExp;
import frontend.ast.expression.Exp;
import frontend.token.Token;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final public class FixedArray {
    private final int length;
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

    // read one constant element; throws IndexOutOfBoundsException when out of range
    public int get(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException(
                    "Constant subscript " + index + " is out of bounds for length " + length);
        }
        return values.getOrDefault(index, 0);
    }

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

    // @see #from(int, ConstInitVal)
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