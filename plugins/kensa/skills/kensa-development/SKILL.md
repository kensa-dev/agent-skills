---
name: kensa-development
description: >
  Kensa BDD tests in Kotlin: review one the user shares, author one from a brief or ticket,
  scaffold one from a saved Kensa Replay scenario (scenarios/<slug>.yml), or diagnose failing
  ones through the kensa MCP server (list_failures, failure_evidence). Trigger on Kensa test
  code, Given/When/Then briefs for a Kensa project, Replay scenario files, and red Kensa builds.
---

# Kensa Test Development

Kensa is a Kotlin BDD framework where Given-When-Then structure is written directly in code (no
Gherkin). It parses test source via ANTLR to render sentences in HTML reports. **Critical: the
report shows actual source tokens in test bodies and `@ExpandableSentence` bodies — so everything
in those rendered contexts must read as prose.**

**The audience is non-developers.** BAs, testers, and product owners read these reports to verify
behaviour and design future APIs. The test body reads as prose. Per-invocation render
cost also scales with the AST in those contexts — bigger test bodies mean slower reports.
Readability and performance pull the same way; every best practice below keeps the body small,
semantic, and free of structural detail.

## Intent Router

Decide which mode you are in before doing anything else:

- **Review** — the user shared an existing test and wants critique/improvement.
  Follow How to Review below. This is the default when test code is present and no authoring
  request is made.
- **Author** — the user wants a NEW test written from requirements (a brief, a ticket,
  a Given/When/Then description, or "write a test that…"). Follow
  `references/authoring/overview.md`, which runs a four-phase pipeline and reuses the
  best-practice rules below as a self-review pass.
- **Scaffold** — the user pastes or points at a saved Kensa Replay scenario
  (`scenarios/<slug>.yml`, or a session/evidence export) and wants the test that locks in what
  the tester walked through. Follow `references/authoring/scaffold-from-replay-scenario.md`.
- **Diagnose** — tests have failed and the user wants to know why. Follow the triage steps in
  `references/mcp-tools.md`.

If both could apply (e.g. "rewrite this test to also cover X"), prefer Author — you are
producing new test code — but run the review rules over the result.

## How to Review

1. Identify rendered contexts: test method bodies and `@ExpandableSentence` bodies. The private
   functions below them are where BP-2 and BP-5 violations sit.
2. Load any on-demand reference files relevant to what's in the test.
3. Check each best practice in order, noting specific line violations.
4. Produce a concise violation list before showing the improved code.
5. Rewrite the test applying all improvements.
6. Under Key Changes, one line per violation fixed, naming the rule and why the test reads better.

If the user provides production service contracts (JSON/XML schemas or example payloads), use them
to make fixtures and request builders realistic and type-accurate.

## Review Output Format

```
## Violations Found

1. [BP-2] `aClientSubmitsAnLcApplicationFor` is @ExpandableSentence returning an Action — lambda body rendered when expanded
2. [BP-3] `Holder` carries test data (applicantId, expectedPrefix) — should be Fixtures
3. [BP-5] `shouldBeInstanceOf<LcApplicationResult.Approved>()` exposed in rendered then() block
4. [BP-7] `then` block nests 7 levels deep with 30+ inline `item { shouldBe(...) }` — extract a flat matcher DSL (`thatHas(field of value, ...)`)

## Improved Test

[full rewritten file]

## Key Changes

- [brief explanation of each change and why it matters]
```

---

## KensaTest Interface

Test classes implement `KensaTest`, which provides the Given-When-Then DSL and direct access to
the current invocation's data:

- `fixtures` — the fixture values for this invocation
- `outputs` — the captured outputs for this invocation
- `fixturesAndOutputs` — combined access (destructured as `(fixtures, outputs)`)

All three are **scoped to the current test invocation**. Parallel runs each get their own isolated
instance — never shared across tests.

### Supported API (0.9.0 and later)

Kensa froze its public API ahead of 1.0; the compiler marks the rest `internal` or behind
`@KensaInternalApi` (framework adapters only) and `@KensaExperimental`. A test that opts in to
`KensaInternalApi` is coupled to plumbing: flag it and rewrite against `KensaTest`, `Action`,
`StateCollector`, `SetupStep`, fixtures and outputs. Upgrade core and the framework adapter
together (use `kensa-bom`).

## On-Demand References

Read these files only when the relevant topic appears in the test being reviewed:

| When you see… | Read |
|---|---|
| `interactions.capture(...)`, sequence diagrams, `from().to().with()` | `references/interactions.md` |
| `SetupStep`, `SetupSteps`, `KotestSetupStep`, `buildGivens`, `buildActions`, `@UseSetupStrategy` | `references/setup-steps.md` |
| `FixtureContainer`, multi-dependency fixtures, `by fixtures(fx)`, `givens[...]`, request builders | `references/fixtures.md` |
| `CapturedOutputContainer`, `capturedOutput<T>`, `outputs[key]`, `registerCapturedOutputs` | `references/captured-outputs.md` |
| `@RenderedValue`, `@RenderedValueWithHint`, `@RenderedValueContainer` (field or parameter, `useCase.stub.sends(...)` chains), `@ExpandableRenderedValue`, qualified enum constants, `@Issue`, `@Epic`, `@Notes` | `references/rendered-value.md` |
| `thenEventually`, `thenContinually`, `andEventually`, timeouts, negative assertions, `Action` and `StateCollector` shapes, fixture registration | `references/dsl.md` |
| `withTestContext`, `TestContextUtil`, `@OptIn(KensaInternalApi::class)` | `references/setup-steps.md` (Legacy section) |
| kensa MCP tools (`list_failures`, `failure_evidence`, `await_results`, `style_profile`) in the tool list, or running tests after authoring | `references/mcp-tools.md` |

## The Best Practices

### BP-1: Rendered code reads as prose

Rendered contexts = test method bodies + `@ExpandableSentence` bodies. A rendered context holds
only prose calls: `given`/`and`/`whenever`/`then` with named helpers. Assignments
(`val response = client(request)`), loops, chained expressions with intermediate results and
inline lambda bodies move into private functions, where only the function name is rendered.

**Good:**
```kotlin
@Test
fun canIssueAnLcWhenCreditAndSanctionsArePositive() {
    given(aComplianceApprovedCounterparty())
    and(anApplicantWithSufficientCreditLimit())
    whenever(aClientSubmitsAnLcApplication())
    then(theLcResult(), shouldBeApproved())
}
```

### BP-2: Action lambdas live in private functions that return the Action

Action lambdas contain implementation code. Each one lives in a regular private function that
*returns* the Action, so the report shows the function name and nothing of the body. Inline in a
test body or inside an `@ExpandableSentence` function, the lambda body is rendered.

**Bad** — lambda rendered in @ExpandableSentence:
```kotlin
@ExpandableSentence
private fun aClientSubmitsAnLcApplicationFor(@RenderedValue applicantId: String): Action<ActionContext> {
    return Action { (_, interactions) ->
        paymentStub.prepareFor(interactions)   // rendered when expanded
        holder.result = portal.submit(...)     // rendered when expanded
    }
}
```

**Good** — only the function name is rendered:
```kotlin
private fun aClientSubmitsAnLcApplication() = Action<ActionContext> { (_, interactions) ->
    paymentStub.prepareFor(interactions)
    holder.result = portal.submit(fixtures[lcRequest])
}
```

**When IS @ExpandableSentence appropriate?** For multi-step *assertion* sequences where the
individual steps should be visible on expansion. A function that returns an Action is BP-2's
private function, unannotated.

```kotlin
@ExpandableSentence
private fun verifyLcWasApprovedWith(@RenderedValue expectedPrefix: String) {
    then(theLcResult(), shouldBeApproved())
    and(theIssuedLcNumber()) { shouldStartWith(expectedPrefix) }
    and(theApplicantId()) { shouldBe(fixtures[applicantId]) }
}
```

### BP-3: Use Fixtures for test data, @RenderedValue for outputs

Prefer the type-safe `Fixtures` system over mutable fields for test data. See `references/fixtures.md`
for setup patterns.

`fixtures[key]` and `outputs[key]` can be used freely in rendered contexts — Kensa substitutes
them with resolved values in the report.

For output produced during the action (e.g. a response assigned by the system under test), use
`@RenderedValue`:

```kotlin
// Immutable value known at construction time
@RenderedValue
val expectedState = OrderState.Confirmed

// Mutable output set during the action phase
@RenderedValue
private lateinit var result: ServiceResponse
```

`@RenderedValueContainer`, for a holder of several mutable outputs or a `@MethodSource` use-case
parameter, is in `references/rendered-value.md`.

### BP-4: Build a composable toolbox — don't repeat setup logic

Kensa's design enables a reusable toolbox of `Action<GivensContext>`, `Action<ActionContext>`,
`SetupStep`, and assertion functions shared across multiple test classes.

A well-designed test suite has:
- An abstract base class per domain consolidating `@ExtendWith`, `@UseSetupStrategy`, `@Sources`,
  `@RenderedValueWithHint`, `@KensaTab`, `KensaTest`, and `WithKotest` — concrete classes extend it
- A `FixtureContainer` object with **only fixture definitions** (plain, derived, `parameterFixture`,
  and `@Fixture` factory functions) — never request builders or general helpers
- A `CapturedOutputContainer` object for system-generated values
- A `SetupStep` class providing named entry points like `theOrderHasProgressedTo(state)`,
  built from state transitions the app must be driven through

**Request builders** assembled from several fixtures are extension functions on `Fixtures` (or
`KensaTest`/`FixturesAndOutputs`) in a dedicated object, with an override lambda for
test-specific fields; the shape is in `references/fixtures.md`, Extension Functions for Request
Builders.

**`@Sources`** — when test bodies reference field descriptor types from classes outside the test
module (e.g. a shared assertion-helper module defines the field matchers used in `thatHas(...)` calls),
the ANTLR parser must know where to look for their tokens. Without `@Sources` those values won't
be substituted in the report sentence:

```kotlin
@Sources(OrderFields::class, NotificationFields::class)
abstract class MyDomainTest : KensaTest, WithKotest
```

### BP-5: Wrap raw assertions in semantic functions

An assertion in a rendered context is a semantic function; the matcher mechanics live behind its
name. The report reads "should be approved".

**Bad:**
```kotlin
then(theLcResult()) { shouldBeInstanceOf<LcApplicationResult.Approved>() }
```

**Good:**
```kotlin
then(theLcResult(), shouldBeApproved())

private fun shouldBeApproved() = Matcher<LcApplicationResult> { result ->
    MatcherResult(
        result is LcApplicationResult.Approved,
        { "Expected Approved but was $result" },
        { "Expected not to be Approved" }
    )
}
```

### BP-6: Use typed context objects and interface mixins for scenario helpers

Helpers are called bare in rendered bodies, because `with(context)` brings them into scope:

```kotlin
// "steps." is plumbing in the report
given(steps.theOrderHasProgressedTo(OrderState.Dispatched))
whenever(orchestrationStub.sends(aPlaceOrderRequest()))

// prose
given(theOrderHasProgressedTo(OrderState.Dispatched))
whenever(orchestration.sends(aPlaceOrderRequest()))
```
 Named stubs (`orchestration`, `supplier`, etc.) live on the context, so `orchestration.sends(...)`
reads naturally in the report as a subject performing an action.

The mechanism: define a typed *test context* holding all stubs/services, expose helpers as
extension functions on *interface types* the context implements, and use `with(context)` in
test bodies. Each test only implements the mixins it actually needs.

```kotlin
// 1. Typed context — holds all stubs and services
class OrderTestContext(
    val orderService: OrderServiceStub,
    val paymentStub: PaymentStub,
    val notificationStub: NotificationStub,
) : WithPaymentScenario.Context, WithNotificationScenario.Context

// 2. Mixin interface — scenario helpers as extension functions on the context type
interface WithPaymentScenario {
    interface Context {
        val paymentStub: PaymentStub
    }

    fun Context.paymentWillSucceed() = paymentStub.returnSuccess()
    fun Context.paymentWillFail() = paymentStub.returnFailure()
}

// 3. Abstract base class — consolidates all class-level annotations
@ExtendWith(OrderExtension::class)
@UseSetupStrategy(SetupStrategy.Grouped)
@Sources(OrderFields::class, PaymentFields::class)
abstract class MyDomainTest : KensaTest, WithKotest

// 4. Concrete test — extends base, implements needed mixins, uses with(context)
class OrderCancellationTest : MyDomainTest(), WithPaymentScenario, WithNotificationScenario {

    private val context: OrderTestContext by lazy {
        with(OrderExtension) {
            OrderTestContext(orderServiceStub, paymentStub, notificationStub)
        }
    }

    @Test
    fun cancelsAnOrderWhenPaymentFails() = with(context) {
        given(paymentWillFail())
        whenever(orchestration.sends(aCancellationRequest()))
        thenEventually(theOrderStatus(), shouldBeCancelled())
    }
}
```

Flag any test that puts stub/service references directly in the superclass, or that duplicates
scenario helper logic across test classes, as a violation of this pattern.

### BP-7: Many assertions are fine — walls of inline `shouldBe` are not

Complex messages and mapping tables are real. Testers and POs *do* check specific field values to
verify behaviour and design future APIs. The discipline isn't "fewer assertions" — it's:

1. Keep the test body's AST small regardless of assertion count.
2. Treat repeated `shouldBe` shapes as a DRY violation — each repetition is a missing matcher.
3. Decide what each field deserves: domain-important on its own (named matcher) or one of many
   attributes where the collection itself is the unit of meaning (`@ExpandableRenderedValue`).

**Bad** — validation DSL inline in the body. Every `item { shouldBe(...) }` is parsed and rendered;
the report ends up 9 levels deep with dozens of lambdas:

```kotlin
then(courier {
    hasDispatched {
        shipment {
            consignee {
                address {
                    item(name = "PostCode")    { shouldBe(fixtures { PostCodeFx }) }
                    item(name = "CountryCode") { shouldBe(fixtures { CountryCodeFx }) }
                    item(name = "ServiceLevel"){ shouldBe(fixtures { ServiceLevelFx }) }
                    // ...30 more
                }
            }
        }
    }
})
```

**Good** — flat call into a matcher DSL defined elsewhere:

```kotlin
then(courier.hasDispatched(aShipment(
    thatHas(
        aPostCode     of fixtures[PostCodeFx],
        aCountryCode  of fixtures[CountryCodeFx],
        aServiceLevel of fixtures[ServiceLevelFx],
        // flat field-value pairs
    )
)))
```

`thatHas`, `aPostCode`, `of`, `aShipment` live in helper modules. Kensa doesn't recurse into them;
each assertion costs one parsed token in the body. The report reads as one sentence and POs can
still scan the field list.

**When the collection is the unit of meaning — use `@ExpandableRenderedValue`.** Sometimes a message has
30 fields where no single one stands on its own; what matters is verifying the full set. The
method does the comparison; only its return value (a list/set/map) is rendered:

```kotlin
@ExpandableRenderedValue(renderAs = Tabular, headers = ["Field", "Expected"])
private fun theShipmentFields() = listOf(
    "PostCode"     to fixtures[PostCodeFx],
    "CountryCode"  to fixtures[CountryCodeFx],
    "ServiceLevel" to fixtures[ServiceLevelFx],
    // ...
)

// In a test:
then(courier.hasDispatched(aShipmentWith(theShipmentFields())))
```

Report shows a labelled table; body stays a single line. See `references/rendered-value.md`.

**Spotting it during review:**
- Inline lambda blocks containing `shouldBe` / `shouldNotBe` inside `then` / `whenever` / `given`
- The same `item(name = ...) { shouldBe(...) }` (or equivalent) shape repeated more than twice
- A nested validation DSL where intermediate levels add no semantic meaning — just structural braces

Depth alone is not the signal. A flat `thatHas(field of value, ...)` chain that reads as English
is fine at any depth — every layer (`thenEventually`, `thatHas`, `.and(...)`) is a named, fluent
step. Nested raw lambdas and repeated identical shapes are the problem.

Each is a cue to extract a flat matcher DSL.
