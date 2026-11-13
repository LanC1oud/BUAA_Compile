package frontend.symbol;

final public class FixedValue {
    private final int value;

    // 保存标量的编译期值
    public FixedValue(int value) {
        this.value = value;
    }

    // 读取已折叠的标量值
    public int getValue() {
        return value;
    }

    // 以十进制显示编译期值
    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
