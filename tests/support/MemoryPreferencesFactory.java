import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/** Isolate MARS test settings from the Windows registry and user preferences. */
public final class MemoryPreferencesFactory implements PreferencesFactory {
    private final Preferences root = new Node(null, "");
    public Preferences systemRoot() { return root; }
    public Preferences userRoot() { return root; }

    private static final class Node extends AbstractPreferences {
        private final Map<String, String> values = new HashMap<>();
        private final Map<String, Node> children = new HashMap<>();
        Node(AbstractPreferences parent, String name) { super(parent, name); }
        protected void putSpi(String key, String value) { values.put(key, value); }
        protected String getSpi(String key) { return values.get(key); }
        protected void removeSpi(String key) { values.remove(key); }
        protected void removeNodeSpi() { values.clear(); children.clear(); }
        protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
        protected String[] childrenNamesSpi() { return children.keySet().toArray(String[]::new); }
        protected AbstractPreferences childSpi(String name) {
            return children.computeIfAbsent(name, key -> new Node(this, key));
        }
        protected void syncSpi() { }
        protected void flushSpi() { }
    }
}
