# Kensa SetupSteps Reference

## Why SetupSteps?

`SetupSteps` drive the application under test to a required state **before** the actual action
under test (the `whenever` call) begins. A step can set preconditions, act on the system, wait
for state and verify it was reached. If any action or check fails, the step fails and the test
stops immediately.

Typical use: driving an order or document through lifecycle stages as a prerequisite. Steps are
usually collected in their own class with an entry-point function like:

```kotlin
fun theOrderHasProgressedTo(state: OrderState): SetupStep = ...
```

## Legacy: `withTestContext`

`TestContextUtil.withTestContext { }` predates `SetupStep` and is deprecated. Since 0.9.0 it is
gated behind `@KensaInternalApi` (an opt-in *error*), so a test project calling it needs
`@file:OptIn(dev.kensa.KensaInternalApi::class)` or `-opt-in=dev.kensa.KensaInternalApi`. Flag
any use in review and migrate the setup into a `SetupStep`. The body maps onto `setup(scope)`:

| `withTestContext` | inside `setup(scope)` / `setupStep { }` |
|---|---|
| `withTestContext { execute(action) }` | `action(action)` |
| `withTestContext { execute(collector) }` | `collect(collector)` |

## The SetupStep Interface

```kotlin
interface SetupStep {
    fun givens(): GivensBlockBuilder          // preconditions, runs against GivensContext
    fun actions(): ActionBlockBuilder         // drive the app, runs against ActionContext
    fun verify(): VerificationBlockBuilder    // assert the state was reached, CollectorContext
    fun setup(scope: SetupScope)              // default body runs the three above, in order
}
```

The triple is sugar over `setup(scope)`. Overriding `setup` replaces the triple for that step only.
All four have defaults, so a step overrides only what it needs.

Each assertion flavour has a matching interface that also brings `then`/`thenEventually` into
the step: `KotestSetupStep`, `HamkrestSetupStep`, `HamcrestSetupStep`.

## Choosing a form (0.9.4+)

Pick the smallest form that fits. Review a step against this ladder and suggest the rung below
when the step carries ceremony it does not use:

| Step needs | Form |
|---|---|
| A few `ActionContext` actions, nothing read back | `setupActions(a, b)` |
| Givens plus actions plus a one-shot verify, all fixed at build time | The triple (`givens()`/`actions()`/`verify()`) |
| Read a value mid-step, poll for state, act again after a check | `setup(scope)` on an object, or `setupStep { }` |
| Same, with the flavour's assertion helpers in scope | `kotestSetupStep { }` / `hamkrestSetupStep { }` / `hamcrestSetupStep { }` |

Any multi-statement lambda lives in a private factory function, never inline in the `@Test`
body: Kensa renders the test method's source into the report sentence, so an inline block
appears in the sentence. A single-action `setupActions({ ... })` on one line is short enough to
inline.

## Defining a SetupStep with the triple

Steps are anonymous objects returned by private factory functions:

```kotlin
private fun anIssuedLc() = object : KotestSetupStep {
    override fun givens() = buildGivens {
        add(Action<GivensContext> {
            complianceStub.willClear()
            creditStub.willApprove()
        })
    }

    override fun actions() = buildActions {
        add(Action<ActionContext> { (fixtures, interactions) ->
            complianceStub.prepareFor(interactions)
            issuanceStub.prepareFor(interactions)
            val result = tradePortal.submitLetterOfCredit(fixtures[originalRequest])
            holder.issuedLcNumber = (result as LcApplicationResult.Approved).lcNumber
        })
    }

    override fun verify() = verify {
        holder.issuedLcNumber shouldNotBe null
    }
}
```

The `buildGivens`/`buildActions` block is a builder: it runs before any action executes. A value
read from `outputs` at builder level sees pre-execution state, not what the step's own actions
are about to write. A step that reads its own results uses `setup(scope)`.

## `setup(scope)`

Override `setup` when the step reads outputs mid-step, polls for a condition, or interleaves
actions with checks. Every call on `SetupScope` executes immediately, in order:

| Member | Type | Semantics |
|---|---|---|
| `fixtures` | `Fixtures` | Same instance as the rest of the test |
| `outputs` | `CapturedOutputs` | Same instance as the rest of the test |
| `given(action)` | `Action<GivensContext>` | Runs now |
| `action(action)` | `Action<ActionContext>` | Runs now, interactions recorded as setup |
| `collect(collector)` | `StateCollector<T>` | Runs now, returns the value |
| `verify(block)` | `(CollectorContext) -> Unit` | Runs once |
| `verifyEventually(duration, interval, check)` | | Retries `check` on the calling thread until it stops throwing, rethrows the last error at the deadline. Defaults 10s / 25ms. No assertion dependency needed |

```kotlin
private fun theBalanceSettles() = object : KotestSetupStep {
    override fun setup(scope: SetupScope) = with(scope) {
        action { ledger.post(fixtures[deposit]) }

        verifyEventually(5.seconds) {
            if (ledger.balanceOf(holder.accountId) == null) throw AssertionError("not settled")
        }

        val balance = collect(StateCollector { ledger.balanceOf(holder.accountId)!! })

        action { holder.openingBalance = balance }
    }
}
```

Inside a flavoured step (`KotestSetupStep` etc.) `thenEventually` and `thenContinually` work
in `setup` on the same terms as in a test body, so a step can poll with matchers instead of a
throwing check:

```kotlin
override fun setup(scope: SetupScope) = with(scope) {
    action { orderService.dispatch(holder.orderId) }
    thenEventually(2.seconds, StateCollector { orderService.stateOf(holder.orderId) }) {
        this shouldBe OrderState.Dispatched
    }
}
```

From Java, `verifyEventually` has an overload taking two `java.time.Duration` values and an
`Action<CollectorContext>`.

## One-line steps

`setupActions(vararg actions)` builds a step from `ActionContext` actions:

```kotlin
private fun anOrderIsPlacedAndPaid() = setupActions(
    { (fixtures, interactions) ->
        paymentStub.prepareFor(interactions)
        holder.orderId = orderService.place(fixtures[orderRequest]).id
    },
    { paymentStub.authorise(holder.orderId) }
)
```

`setupStep { }` takes a block over `SetupScope`, covering the same ground as `setup(scope)`:

```kotlin
private fun anOrderIsPlaced() = setupStep {
    given { paymentStub.willAuthorise() }
    action { holder.orderId = orderService.place(it.fixtures[orderRequest]).id }
    verify { check(holder.orderId != null) }
}
```

`kotestSetupStep { }`, `hamkrestSetupStep { }` and `hamcrestSetupStep { }` run the block over
the flavoured scope (`KotestSetupScope` etc.) with that flavour's helpers available:

```kotlin
private fun anOrderIsPlaced() = kotestSetupStep {
    action { holder.orderId = orderService.place(it.fixtures[orderRequest]).id }
    then(StateCollector { holder.orderId }) { this shouldNotBe null }
}
```

From Java, `dev.kensa.SetupStepKt.setupActions(...)` is usable; the receiver-lambda builders
are not, so Java keeps the anonymous `SetupStep`.

## Chaining Steps

`SetupStep.and(other)` builds a `SetupSteps` chain that runs under one `given`. The test-level
`and(step)` registers a second `given` immediately after. Same steps, same order, differ only in
how many `given` calls the sentence shows:

```kotlin
given(anIssuedLc().and(aBeneficiaryAmendmentConsent()))   // one given, chained

given(anIssuedLc())                                         // two givens
and(aBeneficiaryAmendmentConsent())
```

For steps assembled conditionally, `SetupSteps(list)` takes a `List<SetupStep>`:

```kotlin
private fun theOrderHistory(states: List<OrderState>) = SetupSteps(states.map { theOrderHasProgressedTo(it) })
```

A failure in any step halts the chain and the test.

## The Toolbox Pattern

With careful design, `SetupStep` classes become a reusable **toolbox** of composable building
blocks shared across many tests. This is the idiomatic Kensa approach and should be actively
encouraged.

Steps have full access to `fixtures` and `outputs` (via the `ActionContext` / `GivensContext`
destructuring), and are typically constructed with references to the stubs/services they need:

```kotlin
class OrderSetupSteps(
    private val orderService: OrderService,
    private val paymentStub: PaymentStub,
    private val holder: Holder,
) {
    fun theOrderHasProgressedTo(state: OrderState) = object : KotestSetupStep {
        override fun givens() = buildGivens {
            add(Action<GivensContext> {
                paymentStub.willAuthorise()
            })
        }

        override fun actions() = buildActions {
            add(Action<ActionContext> { (fixtures, interactions) ->
                paymentStub.prepareFor(interactions)
                holder.orderId = orderService.advance(fixtures[orderRequest], state).id
            })
        }

        override fun verify() = verify {
            holder.orderId shouldNotBe null
        }
    }

    fun anOrderWithApprovedPayment() = kotestSetupStep {
        given { paymentStub.willAuthorise() }
        action { (fixtures, interactions) ->
            paymentStub.prepareFor(interactions)
            holder.orderId = orderService.create(fixtures[orderRequest]).id
        }
        then(StateCollector { holder.orderId }) { this shouldNotBe null }
    }
}
```

Tests compose from the toolbox — each test names *what* state it needs. The toolbox is called
bare in the rendered body (`given(theOrderHasProgressedTo(OrderState.Dispatched))`), through the
**context mixin pattern** (BP-6 in SKILL.md):
the `SetupSteps` class is held on the typed test context, and its entry-point functions are
re-exposed as bare extension functions on the context interface. The test calls them unqualified
inside `with(context) { ... }`:

```kotlin
// Context mixin exposes the step unqualified:
interface WithOrderScenario {
    interface Context {
        val orderSteps: OrderSetupSteps
    }
    fun Context.theOrderHasProgressedTo(state: OrderState) = orderSteps.theOrderHasProgressedTo(state)
}

// Test body — reads as fluent prose:
with(context) {
    given(theOrderHasProgressedTo(OrderState.Dispatched))
    given(anOrderWithApprovedPayment().and(aShipmentConfirmed()))
}
```

### Where to instantiate the toolbox

The `SetupSteps` instance lives on the typed test context object, with stubs injected from
the shared extension companion:

```kotlin
class OrderTestContext(
    val orderService: OrderService,
    val paymentStub: PaymentStub,
    val orderSteps: OrderSetupSteps = OrderSetupSteps(orderService, paymentStub),
) : WithOrderScenario.Context
```

Givens/actions/assertions defined across the toolbox class can also be extracted into standalone
`Action<GivensContext>` or `Action<ActionContext>` functions and shared directly with test classes
that need them — the same composability applies at every level.

## SetupStrategy: Controlling Diagram Display

Setup-phase interactions appear in the sequence diagram. `@UseSetupStrategy` controls how:

```kotlin
@UseSetupStrategy(SetupStrategy.Grouped)   // setup interactions in a labelled box
@UseSetupStrategy(SetupStrategy.Ungrouped) // setup interactions inline, no box
@UseSetupStrategy(SetupStrategy.Ignored)   // setup interactions hidden
```

Apply at class or method level. `Grouped` is recommended — it clearly separates prerequisites
from the action under test in the diagram.
