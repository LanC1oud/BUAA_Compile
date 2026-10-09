package frontend.symbol;

/**
 * 编译期已知的标量常量值。
 */
final public class FixedValue {
    private final int value;

    public FixedValue(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
