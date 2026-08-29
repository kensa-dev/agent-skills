# Scaffold — Kensa test from a saved Replay scenario

A tester walks a Kensa Replay session, saves it as `scenarios/<slug>.yml`, and asks for the test
that locks the behaviour in: scenario file in, `KensaTest` skeleton out.

The output is a *skeleton*, not a finished test. Every call site the file cannot resolve is emitted
as a compiling `TODO(...)` naming exactly what to wire. The skeleton must compile as emitted — a
skeleton that does not compile is worse than no skeleton.

This is a one-shot transform, not the four-phase pipeline in `overview.md`: the scenario file *is*
the brief, so intake is skipped. Run the BP rules in `SKILL.md` over the result — the emitted body
is a rendered context like any other.

---

## Rule 0 — Inputs

Required: the scenario YAML (`scenarios/<slug>.yml`, schema 1).

Optional: the session's evidence — `evidence/<slug>.yml`, or the report JSON it becomes. Evidence
carries `steps[].outcome.verify` (`Passed` / `Failed` / `Skipped` plus a message) and
`steps[].outcome.response`, which turn vague `then` placeholders into specific ones (Rule 5).

**There is no `expect` field in a scenario file.** A scenario's `expect(...)` text is saved as its
`description`, and Kage puts it back on reopen (`expect = file.description`). So the file's
`description` *is* the expectation — treat it as such, not as prose for `@Notes`.

Ask for evidence once if it was not supplied, then proceed without it — the scenario alone is enough
for a skeleton.

Imports:

```kotlin
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.GivensContext
import dev.kensa.Issue
import dev.kensa.Notes
import dev.kensa.StateCollector
import dev.kensa.junit.KensaTest      // dev.kensa.kotest.KensaTest for a Kotest suite
import dev.kensa.kotest.WithKotest
import io.kotest.matchers.Matcher
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
```

`KensaTest` already carries `@ExtendWith(KensaExtension::class)`. Any *team* extension (the one
that starts their stubs) is a TODO comment on the class.

---

## Rule 1 — The scenario file shape

```yaml
schema: 1
slug: place-order-happy-path
name: "Place order: happy path"
description: "Supplier receives the order body with the session's Amount"   # the scenario's expect(...)
tags: [order, happy, app:orders]
env: local
steps:
- ref: supplier.responds-with-reference    # a catalogue step, by id
  name: Responds with reference            # display name at save time
  phase: Setup
- ref: order-service.place-order
  name: Place order
  phase: Steps
pins: {}          # fixture key -> value the session started with
locks: []         # fixture keys a rotate-all keeps
variables: {}     # user-entered values a fixture set supplied
seeAlso: [InProcessAcceptanceTest]
notes: ""
```

A step is **either** `ref` (a catalogue step, possibly with an `edit`) **or** `raw` (a payload the
tester typed):

```yaml
- name: Raw prime 1
  phase: Steps
  raw:
    kind: Prime            # Prime | Send
    target: Supplier
    priming: { status: 200, headers: { Content-Type: application/json }, body: '{"reference":"ref-9001"}' }
- name: Raw send 2
  phase: Steps
  raw:
    kind: Send
    target: OrderService
    message: { body: '{"amount":250}', contentType: application/json, headers: {}, url: /orders }
```

**Order the emitted body by phase, not by file order.** Raw steps are inserted at the session's
cursor, so the file's list is in insertion order; `phase` is the truth. Within a phase keep file order.

---

## Rule 2 — Map the file to test structure

| Scenario file | Becomes |
|---|---|
| `name` | class name (`PlaceOrderHappyPathTest`) and the `@Test` function name |
| `slug`, `env` | class KDoc only — a test picks its own wiring, not the session's env |
| `description` | the expectation (Kage reopens a scenario with `expect = description`) — a KDoc `Expect:` line and Rule 5's placeholder source; **not** `@Notes` |
| `notes`, `seeAlso` | one `@Notes("…")` on the class (Kensa's `@Notes` targets classes only) |
| tag `issue:<X>` or a ticket-shaped tag (`ABC-123`) | `@Issue("ABC-123")` |
| tag `app:<X>` | dropped — it is Replay's own routing tag |
| any other tag | `@Tag("order")` (JUnit), one per tag |
| step `phase: Setup` | `given(...)`, then `and(...)` for each one after the first |
| first step `phase: Steps` | `whenever(...)` |
| later steps `phase: Steps` | a further `whenever(...)` each — there is no `and` overload for `Action<ActionContext>` |
| evidence `steps[].outcome.verify` / `.response` (when supplied) | per-step `then` placeholders, ahead of `description` (Rule 5) |
| `pins`, `variables` | fixture overrides (Rule 6) |
| `locks` | a KDoc line — locks are a session concern with no test equivalent |

`phase: AdHoc` never reaches a file; the save folds it into `Steps`.

---

## Rule 3 — `ref` steps become named TODO call sites

A step id is `<group-slug>.<step-name-slug>`. The team's `ReplaySteps` subclass knows the Kotlin
`val` behind it (`by prime(…)` / `by send(…)` registers it), but **the file holds only the id**,
so the val name stays a TODO. Emit a private function per distinct ref:

- Name it fluently from the step's `name` and `target`, so the report sentence reads as English.
  Prime → `the<Target><StepName>()`; Send → the step name as a verb phrase (`theOrderIsPlaced()`).
- Return type follows the phase: `Action<GivensContext>` for `Setup`, `Action<ActionContext>` for `Steps`.
- Body is `TODO("…")` naming the ref id, the group and the step name. `TODO()` returns `Nothing`, so
  it satisfies any declared return type and the file compiles.
- Put the ref id in a comment above the function so the dev can grep the step library for it.

```kotlin
// Replay step supplier.responds-with-reference — group "Supplier", name "Responds with reference", target Supplier
private fun theSupplierRespondsWithReference(): Action<GivensContext> =
    TODO("Wire to the step registered as supplier.responds-with-reference in group 'Supplier'")
```

Emit one function per distinct ref even when the same ref appears twice; call it twice.

A `ref` step with an `edit` keeps the same call site, plus a parameter for what the tester changed:
`theSupplierRespondsWithReference(status = 500)`, with the edited value in the TODO text.

---

## Rule 4 — `raw` steps become Kage testkit calls

A raw step carries its whole payload, so emit a real call rather than a TODO — only the stub/party
reference is unknown, and that comes from the test's own wiring.

| Raw step | Emit |
|---|---|
| `kind: Prime`, HTTP target | `supplier.prime(Status.OK, """…body…""", mapOf(…headers…))` on the target's `HttpStub` |
| `kind: Send`, HTTP target | the team's client call for `message.url`; if there is none, a TODO naming the url and body |
| `kind: Send`, JMS target (`message.jms` present) | `billing.sends(queue, """…body…""", JmsShape(...))` on the target's `JmsParty` |

`HttpStub.prime(status, body, headers, trackingId)` and `JmsParty.sends(queue, body, shape, trackingId)`
default their tracking id from the thread-local, so pass only status/body/headers (or queue/body/shape).
The queue name is not in the file — take it from `message.jms` context or leave it a TODO constant.
The first type parameter of `HttpStub` / `JmsParty` is the tracking-id type, which a test rarely names:
star-project it (`HttpStub<*, String>`) and the tracking id defaults from the thread-local.

Name the stub val after the target, lower-camel (`supplier`, `orderService`, `billing`), declare it as
a TODO-initialised property so the skeleton still compiles, and wrap each call in a private function so
the test body stays prose (BP-2) — the lambda never appears in a rendered context:

```kotlin
import dev.kensa.kage.testkit.stub.HttpStub
import dev.kensa.kage.testkit.stub.JmsParty
import dev.kensa.toolbox.jms.JmsShape
import org.http4k.core.Status

private val supplier: HttpStub<*, String> = TODO("The HttpStub your suite already builds for target 'Supplier'")
private val billing: JmsParty<*, String> = TODO("The JmsParty your suite already builds for target 'Billing'")

private fun theSupplierAnswersWithAReference() = Action<GivensContext> {
    supplier.prime(Status.OK, """{"reference":"ref-9001"}""", mapOf("Content-Type" to "application/json"))
}

private fun theInvoiceIsPutOnTheBillingQueue() = Action<ActionContext> {
    billing.sends("billing.in", """{"reference":"ref-9001","amount":250}""", JmsShape(mapOf("priority" to 3), jmsType = "Invoice"))
}
```

---

## Rule 5 — `then` placeholders

The scenario file holds no assertions — a step's `verifying { }` lives in the team's step library, not
in the file. So placeholders are derived, in this order:

1. **With evidence** — one `then` per step whose `outcome.verify` is `Passed` or `Failed`, named from
   the step it belonged to, with the verify message in the TODO text. A step whose outcome shows a
   dispatch `response` also earns a collector for that response.
2. **Without evidence** — a single `then` per Steps-phase step, from the scenario's `description`,
   which is the `expect(...)` text Kage restores on reopen. Name the matcher from it and put it
   verbatim in the TODO.
3. **Neither** — `description` blank and no evidence: one `then` with a TODO saying the scenario
   declared no expectation.

Emit collector and matcher as separate private functions, both `TODO(...)`, both typed:

```kotlin
thenEventually(theSupplierRequest(), shouldCarryTheOrderAmount())

private fun theSupplierRequest(): StateCollector<String> =
    TODO("Collect what target 'Supplier' received — e.g. supplier.store.awaitEarliest(trackingId)")

private fun shouldCarryTheOrderAmount(): Matcher<String> =
    TODO("Expect: Supplier receives the order body with the session's Amount; order service gets the Reference back")
```

Use `thenEventually` whenever the assertion is about something the SUT does *after* the send reaches
another party (a capture, a downstream request, a queue). Use plain `then` only for a value the send
itself returned. Replay is asynchronous by nature — when in doubt, `thenEventually`.

A duration lives in a private function (`references/dsl.md`).

---

## Rule 6 — `pins`, `variables`, `locks`

`pins` are the fixture values the session ran with; `variables` are user-entered values a fixture set
supplied. Both are test data, so they become fixtures (BP-3).

- A pin whose key matches a fixture the team already has → a comment naming the fixture and the pinned
  value; the dev decides whether to pin it in the test.
- A pin or variable with no fixture behind it → an entry in a scenario-specific `FixtureContainer`
  (see `references/fixtures.md`), keyed by the pin name.
- `locks` have no test equivalent — mention them in the class KDoc and move on.

A scenario-specific `FixtureContainer` exists only when `pins` or `variables` has entries.

---

## Worked example — the sample plugin's happy path

Input (`scenarios/place-order-happy-path.yml`, saved from the sample replay plugin's `Orders` app):

```yaml
schema: 1
slug: place-order-happy-path
name: "Place order: happy path"
description: Supplier receives the order body with the session's Amount; order service gets the Reference back
tags:
- order
- happy
- app:orders
env: local
steps:
- ref: supplier.responds-with-reference
  name: Responds with reference
  phase: Setup
- ref: order-service.place-order
  name: Place order
  phase: Steps
pins: {}
locks: []
variables: {}
seeAlso:
- InProcessAcceptanceTest
notes: ""
```

Output:

```kotlin
package dev.example.orders

import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.GivensContext
import dev.kensa.Notes
import dev.kensa.StateCollector
import dev.kensa.junit.KensaTest
import dev.kensa.kotest.WithKotest
import io.kotest.matchers.Matcher
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Scaffolded from Replay scenario `place-order-happy-path` (app Orders, saved on env `local`).
 *
 * Expect: Supplier receives the order body with the session's Amount; order service gets the
 * Reference back.
 */
@Notes("Scaffolded from Replay scenario 'Place order: happy path' (scenarios/place-order-happy-path.yml). See also: InProcessAcceptanceTest")
@Tag("order")
@Tag("happy")
// TODO add the extension your suite uses to start the stubs, e.g. @ExtendWith(OrderExtension::class)
class PlaceOrderHappyPathTest : KensaTest, WithKotest {

    @Test
    fun placesAnOrderAndGetsTheSuppliersReferenceBack() {
        given(theSupplierRespondsWithReference())
        whenever(theOrderIsPlaced())
        thenEventually(theSupplierRequest(), shouldCarryTheOrderAmount())
    }

    // Replay step supplier.responds-with-reference — group "Supplier", name "Responds with reference", target Supplier
    private fun theSupplierRespondsWithReference(): Action<GivensContext> =
        TODO("Wire to the step registered as supplier.responds-with-reference in group 'Supplier'")

    // Replay step order-service.place-order — group "Order Service", name "Place order", target OrderService
    private fun theOrderIsPlaced(): Action<ActionContext> =
        TODO("Wire to the step registered as order-service.place-order in group 'Order Service'")

    private fun theSupplierRequest(): StateCollector<String> =
        TODO("Collect what target 'Supplier' received — e.g. supplier.store.awaitEarliest(trackingId)")

    private fun shouldCarryTheOrderAmount(): Matcher<String> =
        TODO("Expect: Supplier receives the order body with the session's Amount; order service gets the Reference back")
}
```

The body is four lines of prose and every unknown is one grep away. That is the whole deliverable —
resist filling the TODOs with plausible-looking guesses.

---

## Checklist before you hand it over

- [ ] The file compiles as emitted (every `TODO(...)` sits in a function with a declared return type).
- [ ] Test body is prose only: no assignments, no lambdas, no duration literals, no qualifier prefixes.
- [ ] One `given`/`and` per Setup step, `whenever` per Steps step, ordered by phase.
- [ ] Every `ref` has a comment carrying its id, group and name.
- [ ] Every `raw` step is a real testkit call, not a TODO, where the payload allows it.
- [ ] The scenario's `description` drove the `then` placeholder, not `@Notes`.
- [ ] `pins`/`variables` are fixtures or comments.
- [ ] Say plainly, in the handover, which TODOs the dev must fill and in what order.
