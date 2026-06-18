# Introspect — Project Inventory

Introspect is a read-only scan of the target project. Its job is to produce an *inventory* — structured data that the generate phase uses to emit a test that reuses existing infrastructure rather than inventing it. Proceed to Generate only when the inventory is in hand.

---

## Dispatch instruction

The main context dispatches **one read-only subagent**. Give the subagent:

- The project root path.
- This instruction: **read build files and test sources only** — do not read production source trees, generated output, or binary artifacts.
- The inventory schema below.
- The instruction to write the result to `.kensa/inventory.json` (keyed by a content-hash; see *Cache contract* below) and return the inventory as structured text in its response.

The subagent does not converse with the user. It scans, writes the cache entry, and returns the inventory. The main context reads the return value and does not re-read the cache file itself.

---

## Inventory schema

The subagent returns this shape (JSON; also reflected as structured text in its response):

```json
{
  "hash": "<sha256 of test source dirs + build files>",
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
| `hash` | content-hash of test source dirs + build files | Must be present; a missing hash is forbidden (see *Cache contract*) |
| `framework` | build deps + test imports | `junit5` if `org.junit.jupiter` on classpath; `kotest` if `io.kotest`; `testng` if `org.testng` |
| `fixtures` | `FixtureContainer` / `Fixtures` implementations in test scope | Each `container` is the class name; `names` are the factory method names |
| `stubs` | http-stub plugins + priming step classes in test scope | One entry per stub plugin; identify `primingHelper` class and the request/response shapes it primes |
| `toolboxes` | helper/extension objects in test scope | Class or object names |
| `kageInfra` | `:kage-acceptance` module (or equivalent) | Set `hasKageAcceptance: false` and leave other fields null if the module is absent |

---

## Reuse mandate

The generate phase **must** prefer existing inventory items over inventing new ones:

- If `fixtures` lists a `FixtureContainer` whose `names` cover the needed test data, use it — do not declare a new fixture.
- If `stubs` lists a `primingHelper` that primes the interaction under test, use it — do not write a new priming step.
- If `kageInfra` provides `kensaReportingHelper` / `descriptorsHelper`, wire them into the test — do not invent equivalents.

**Inventing a fixture that already exists is a defect.** The self-review phase checks for this explicitly. When the inventory is ambiguous (e.g. a fixture container exists but its names are unclear), the subagent must read further rather than leave the field blank.

---

## Cache contract

The subagent writes the inventory to `.kensa/inventory.json` in the project root.

Cache key: a SHA-256 hash of the concatenated content of (test source dirs + build files), stored in the `hash` field of the JSON object.

On a later run:

1. If `.kensa/inventory.json` exists and its `hash` matches the current content-hash → reuse it; skip the scan.
2. If the file is absent, the hash differs, or the `hash` field is missing → re-scan and overwrite.

Rules:

- `.kensa/inventory.json` is gitignored, machine-derived, never hand-edited.
- A cache entry with no `hash` field is **forbidden** — treat it as stale and re-scan.
- The main context does not modify the cache file; only the subagent writes it.

---

## Kage acceptance gate

If `kageInfra.hasKageAcceptance` is `false`, report the gap to the user and stop. Do not proceed to generate a Pattern-A toolbox test or any other substitute shape. The gap must be resolved in the project before authoring can continue.

---

## MCP-future note

When a `kensa mcp` introspection server exists, this subagent step is replaced by direct tool calls against that server. The inventory schema above is the stable contract across both paths — the generate phase consumes the same structured data regardless of whether it was produced by a subagent scan or by MCP tool responses.
