package land.tx3.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Environment and party values declared by one TII profile. */
public final class Profile {
  private final Map<String, JsonNode> environment;
  private final Map<String, JsonNode> parties;

  Profile(Map<String, JsonNode> environment, Map<String, JsonNode> parties) {
    this.environment = immutableNodes(environment);
    this.parties = immutableNodes(parties);
  }

  /** Returns the profile's environment values. */
  public Map<String, JsonNode> environment() {
    return Collections.unmodifiableMap(copyNodes(environment));
  }

  /** Returns the profile's bound party values. */
  public Map<String, JsonNode> parties() {
    return Collections.unmodifiableMap(copyNodes(parties));
  }

  private static Map<String, JsonNode> immutableNodes(Map<String, JsonNode> source) {
    return Collections.unmodifiableMap(copyNodes(source));
  }

  private static Map<String, JsonNode> copyNodes(Map<String, JsonNode> source) {
    var result = new LinkedHashMap<String, JsonNode>();
    source.forEach((key, value) -> result.put(key, value.deepCopy()));
    return result;
  }
}
