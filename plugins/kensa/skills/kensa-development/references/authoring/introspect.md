# Introspect — Project Inventory

Introspect is a read-only scan of the target project. Its job is to produce an *inventory* — structured data that the generate phase uses to emit a test that reuses existing infrastructure rather than inventing it. Proceed to Generate only when the inventory is in hand.

---

## Dispatch instruction

The main context dispatches **one read-only subagent**. Give the subagent:

- The project root path.
- This instruction: **read build files and test sources only**.
- The inventory schema below, to be returned as structured text in its response.

---

## Inventory schema

The subagent returns this shape (JSON; also reflected as structured text in its response):

```json
{
  "framework": "junit5 | kotest | testng",
  "fixtures": [
    { "container": "MyFixtures", "names": ["aLoan", "aPendingApplication"] }
  ],
  "stubs": [
    {
      "plugin": "HttpStubPlugin",
      "primingHelper": "PrimingHelper",
      "receives": "POST /loans",
      "sends": "200 OK { approved: true }"
    }
  ],
  "toolboxes": ["KensaToolbox", "LoanToolbox"],
  "kageInfra": {
    "hasKageAcceptance": true,
    "kageServerHelper": "KageServerHelper",
    "descriptorsHelper": "DescriptorsHelper",
    "kensaReportingHelper": "KensaReportingHelper"
  }
}
```

Fields:

| Field | Source | Notes |
|---|---|---|
| `framework` | build deps + test imports | `junit5` if `org.junit.jupiter` on classpath; `kotest` if `io.kotest`; `testng` if `org.testng` |
| `fixtures` | `FixtureContainer` / `Fixtures` implementations in test scope | Each `container` is the class name; `names` are the factory method names |
| `stubs` | http-stub plugins + priming step classes in test scope | One entry per stub plugin; identify `primingHelper` class and the request/response shapes it primes |
| `toolboxes` | helper/extension objects in test scope | Class or object names |
| `kageInfra` | `:kage-acceptance` module (or equivalent) | Set `hasKageAcceptance: false` and leave other fields null if the module is absent |

---

## Reuse mandate

The generate phase **must** prefer existing inventory items over inventing new ones:

- If `fixtures` lists a `FixtureContainer` whose `names` cover the needed test data, that container is the one the test consumes.
- If `stubs` lists a `primingHelper` that primes the interaction under test, the test calls it.
- If `kageInfra` provides `kensaReportingHelper` / `descriptorsHelper`, the test wires them in.

**Inventing a fixture that already exists is a defect.** The self-review phase checks for this explicitly. When the inventory is ambiguous (e.g. a fixture container exists but its names are unclear), the subagent must read further rather than leave the field blank.

---

## Kage acceptance gate

If `kageInfra.hasKageAcceptance` is `false`, tell the user which of the three pieces is missing (a `:kage-acceptance`-style Gradle module, an http-stub plugin, the Kage plugin wired into the test build) and stop: authoring continues once the project has all three.

---

## MCP head start

When the `kensa` MCP server is registered, call `style_profile` with the test module as `project_dir` before dispatching and pass the profile to the subagent. It fills `framework` and `fixtures` and names the stub helpers and matcher fields; the subagent verifies those against the sources and adds `stubs`, `toolboxes` and `kageInfra`, which the profile does not cover. The inventory schema above stays the contract either way. See `../mcp-tools.md`.
