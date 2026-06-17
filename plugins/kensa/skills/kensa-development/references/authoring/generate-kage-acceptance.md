# Generate — Kage Acceptance Test

This phase turns a **complete brief** (from `intake.md`) plus a **project inventory** (from
`introspect.md`) into a Kage acceptance test. The golden worked example in
`evals/inventory-reservation/golden/` is the canonical shape — these rules describe how to
reproduce its idioms for any brief. Every example below is a real line from that golden.

Before emitting anything: re-read the BP rules in `SKILL.md` (they govern rendered prose) and the
on-demand references `fixtures.md`, `interactions.md`, `rendered-value.md` (reuse them; do not
restate their content here). The single most common failure is inlining brief literals instead of
fixtures — see Rule 3.

---

## Rule 1 — Two-file shape

Emit exactly two files per behaviour.

**`<Behaviour>Scenario.kt`** — the helper. Holds the `FixtureContainer` object, the descriptors
function, and the scenario class with given/when steps, stub priming, and state collectors. No
`@Test`, no assertions. The golden:

```kotlin
object InventoryReservationFixtures : FixtureContainer { ... }

fun reservationDescriptors(): List<InteractionDescriptor> = listOf(...)

class InventoryReservationScenario(private val bus: EventSource, private val kageBaseUri: Uri) {
    fun anOrderService(): Action<GivensContext> = Action { _ -> ... }
    fun primeSupplierToConfirmReservation(reservationId: String, quantity: Int): Action<GivensContext> = ...
    fun placingAnOrderFor(quantity: Int, item: String): Action<ActionContext> = ...
    fun theReservationRequestBody(): StateCollector<JsonNode> = StateCollector { _ -> ... }
}
```

**`InProcess<Behaviour>Test.kt`** — the runner. `KensaTest, WithKotest`, `@BeforeEach` wiring,
`@AfterEach` teardown, one `@Test` in given/whenever/then prose, and `@ExpandableSentence` /
`MatcherField` helpers at the bottom:

```kotlin
class InProcessInventoryReservationTest : KensaTest, WithKotest {
    init { registerFixtures(InventoryReservationFixtures) }

    @BeforeEach
    fun setUp() { kensaReporting(); ... }

    @Test
    fun `reserves stock with the supplier before confirming an order`() { ... }
}
```

---

## Rule 2 — Map brief → structure

| Brief element | Becomes |
|---|---|
| `participants: SUT` | the scenario class + its held service (`holder.orderService`) |
| `participants: stub/supplier` | a named priming step + `InteractionDescriptor` |
| `given:` line | a Fixture (the data) **and** a `given(...)` step (the priming) |
| `when:` line | one `whenever(...)` step |
| each `then:` line | one assertion, timing keyword chosen per Rule 6 |

The golden's `when` ("places an order for 5 units of WIDGET-1") becomes a single step:

```kotlin
whenever(scenario.placingAnOrderFor(quantity = fixtures(ReservationQuantityFx), item = fixtures(CatalogueItemFx)))
```

---

## Rule 3 — Fixtures-always (non-negotiable)

**Every concrete value in the brief — references, codes, quantities — becomes a fixture.** Define
it in the `FixtureContainer`, register with `registerFixtures(...)`, consume with `fixtures(Name)`.
**NEVER inline a brief literal in a rendered position.** This is the #1 thing a naive attempt gets
wrong.

The golden's brief values `RES-1`, `WIDGET-1`, `5`:

```kotlin
object InventoryReservationFixtures : FixtureContainer {
    val ReservationIdFx = fixture("ReservationId") { "RES-1" }
    val CatalogueItemFx = fixture("CatalogueItem") { "WIDGET-1" }
    val ReservationQuantityFx = fixture("ReservationQuantity") { 5 }
}
```

Registered in the runner's `init`:

```kotlin
init { registerFixtures(InventoryReservationFixtures) }
```

Consumed in the `@Test` body — never the literal:

```kotlin
given(scenario.primeSupplierToConfirmReservation(
    reservationId = fixtures(ReservationIdFx),
    quantity = fixtures(ReservationQuantityFx)
))
```

(In a Kage acceptance test `fixtures(Name)` is the call form; the reviewer references show
`fixtures[Name]` — both resolve a registered fixture. Match whatever form the inventory already
uses in that project.)

---

## Rule 4 — MatcherField for field-level `then`s

A `then` of the form "field X equals Y" becomes a typed `MatcherField` — a `JsonField` variant
(`JsonIntField` / `JsonTextField`, or `XmlField` for XML) — declared as a property, concatenated
with `of fixtures(...)` and combined with `and (...)`. **NEVER assert with whole-body JSON-string
equality or xmlunit.** The matcher-field types (the `MatcherField|JsonField` family) live under
`dev.kensa.hamkrest.testsupport.field.json.*`.

Import the variant the inventory reports — the golden uses hamkrest:

```kotlin
import com.natpryce.hamkrest.and
import dev.kensa.hamkrest.testsupport.field.json.JsonIntField
import dev.kensa.hamkrest.testsupport.field.json.JsonTextField
import dev.kensa.hamkrest.testsupport.field.of
```

The fields are named, prose-reading properties at the bottom of the runner:

```kotlin
private val aQuantityField: JsonIntField get() = JsonIntField("/quantity")
private val anItemField: JsonTextField get() = JsonTextField("/item")
```

Used in the body — one fluent matcher, the brief's two `then` clauses joined with `and`:

```kotlin
then(scenario.theReservationRequestBody()) {
    assertThat(
        this,
        aQuantityField of fixtures(ReservationQuantityFx)
            and (anItemField of fixtures(CatalogueItemFx))
    )
}
```

---

## Rule 5 — @ExpandableSentence for drill-down `then`s

A brief line marked `[expandable detail]` (drill-down: "shows a, b, c") becomes an
`@ExpandableSentence`-annotated helper taking `@RenderedValue` params, called from a `then(...)`
block so the top-level sentence stays prose and the field checks only appear on expansion.

The golden's drill-down (response shows reservationId / status / quantity):

```kotlin
@ExpandableSentence
private fun theReservationResponseShows(
    @RenderedValue reservationId: String,
    @RenderedValue status: String,
    @RenderedValue quantity: Int
) {
    reservationId shouldBe fixtures(ReservationIdFx)
    status shouldBe "RESERVED"
    quantity shouldBe fixtures(ReservationQuantityFx)
}
```

Called from a plain `then`, keeping the body fluent:

```kotlin
then(scenario.theReservationResponse()) {
    theReservationResponseShows(reservationId, status, quantity)
}
```

(Per BP-2, an `@ExpandableSentence` only ever wraps an *assertion* sequence — never a function that
returns an `Action`.)

---

## Rule 6 — Timing → DSL keyword

Map the brief's `[timing: ...]` tag to the DSL, always using the trailing-lambda form.

| Brief timing | Keyword |
|---|---|
| `immediate` | `then(...) { ... }` / `and(...) { ... }` |
| `eventually` | `thenEventually(...) { ... }` |
| `continually` | `thenContinually(...) { ... }` |

The golden's `immediate` field check and `eventually` status check:

```kotlin
then(scenario.theReservationRequestBody()) { assertThat(this, ...) }

thenEventually(scenario.theOrderStatus()) { this shouldBe OrderStatus.CONFIRMED }
```

Never put a timeout duration literal in the body — push it into a private function (SKILL.md, DSL
quick reference).

---

## Rule 7 — Stubs, participants & wiring

Reuse the inventory's existing stub infrastructure (the `trackingClient|priming` helpers):
`trackingClient(tid, …)` and the priming step. If none exists, add a minimal one next to the
existing pattern. The golden's tracking client and HTTP priming step:

```kotlin
fun anOrderService(): Action<GivensContext> = Action { _ ->
    val client = trackingClient(trackingId, JavaHttpClient())
    holder.orderService = OrderService(client, supplierUri)
}

fun primeSupplierToConfirmReservation(reservationId: String, quantity: Int): Action<GivensContext> = Action { _ ->
    val response = JavaHttpClient()(
        Request(POST, "${kageBaseUri.toString().trimEnd('/')}/http-stub/prime/${trackingId.asString}").body(...)
    )
    check(response.status == Status.NO_CONTENT) { "Priming failed with status ${response.status}: ..." }
}
```

Each stubbed participant gets an `InteractionDescriptor` for the sequence diagram:

```kotlin
fun reservationDescriptors(): List<InteractionDescriptor> = listOf(
    InteractionDescriptor(
        EventType("supplier.notify-order.request"),
        Participant("OrderService"),
        Participant("Supplier")
    )
)
```

Wire `SequenceDiagramCapture(bus, <behaviour>Descriptors())` and call `kensaReporting()` in
`@BeforeEach`:

```kotlin
@BeforeEach
fun setUp() {
    kensaReporting()
    val bus = InProcessBus()
    ...
    server = KageServer(bus).start(listOf(stubPlugin), UuidTrackingId.Parser)
    scenario = InventoryReservationScenario(bus, Uri.of("http://localhost:${server.port()}"))
    subscription = scenario.subscribe()
    diagramCapture = SequenceDiagramCapture(bus, reservationDescriptors()).also { it.track(scenario.trackingId) }
    holder = scenario.holder
}
```

Always tear down in `@AfterEach`:

```kotlin
@AfterEach
fun tearDown() { diagramCapture?.close(); subscription?.close(); server.stop() }
```

---

## Rule 8 — Reuse over invention

Before declaring **any** fixture, priming helper, descriptor, or matcher field, check the inventory
(`introspect.md` reuse mandate). If the inventory lists a `FixtureContainer` whose names cover the
brief's data, register and consume it — do not declare a new one. If a `primingHelper` already
primes the interaction under test, call it — do not write a fresh priming step. **Inventing a
fixture that already exists is a defect** that the self-review phase checks for explicitly. Only
add a new fixture/helper when the inventory genuinely lacks one, and place it next to the existing
pattern.

---

## Rule 9 — Rendered prose discipline

The `@Test` body and `@ExpandableSentence` bodies must read as fluent English (defer to the BP
rules in `SKILL.md` — no `val` assignments, no loops, no raw matchers, no qualifier prefixes in the
rendered context). Name state collectors for *what* they represent, not *how* they were obtained:

```kotlin
fun theReservationResponse(): StateCollector<ReservationConfirmation> = StateCollector { _ ->
    holder.orderService.lastReservation() ?: error("No reservation response captured yet")
}
```

`theReservationResponse()`, not `theCapturedReservationResponse()`. The result is a body that reads
straight through:

```kotlin
given(scenario.anOrderService())
given(scenario.primeSupplierToConfirmReservation(reservationId = fixtures(ReservationIdFx), quantity = fixtures(ReservationQuantityFx)))
whenever(scenario.placingAnOrderFor(quantity = fixtures(ReservationQuantityFx), item = fixtures(CatalogueItemFx)))
then(scenario.theReservationRequestBody()) { assertThat(this, aQuantityField of fixtures(ReservationQuantityFx) and (anItemField of fixtures(CatalogueItemFx))) }
thenEventually(scenario.theOrderStatus()) { this shouldBe OrderStatus.CONFIRMED }
then(scenario.theReservationResponse()) { theReservationResponseShows(reservationId, status, quantity) }
```

---

## Output checklist

Before handing off to self-review, confirm:

- [ ] Two files: `<Behaviour>Scenario.kt` + `InProcess<Behaviour>Test.kt`.
- [ ] Every brief literal is a fixture in a `FixtureContainer`, registered via `registerFixtures(...)`.
- [ ] No brief literal inlined in any rendered position.
- [ ] Field-level `then`s use `MatcherField … of fixtures(...)` — no whole-body JSON/xmlunit equality.
- [ ] Drill-down `then`s use an `@ExpandableSentence` helper with `@RenderedValue` params.
- [ ] Timing keywords match the brief tags (`then` / `thenEventually` / `thenContinually`).
- [ ] `SequenceDiagramCapture(bus, …Descriptors())` wired; `kensaReporting()` called in `@BeforeEach`; teardown in `@AfterEach`.
- [ ] Inventory items reused where present; nothing re-invented.
- [ ] Test body and expandable bodies read as fluent English; collectors named for *what*, not *how*.
