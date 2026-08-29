# Kensa DSL Reference

Test class shape (abstract base class per domain, concrete classes extend it): BP-6 in `SKILL.md`.

### Synchronous vs asynchronous assertions

Use `then`/`and` for results that are immediately available after the action. Use `thenEventually`
and `andEventually` when the system under test processes asynchronously (message-driven, event-sourced,
parallel workers) — these retry the assertion until it passes or a timeout is reached.

`thenContinually` asserts that the condition remains true throughout a polling window — use when
you need to verify something *stays* in a given state rather than *eventually reaches* it.

```kotlin
// Synchronous — result available immediately
then(theHttpStatus(), shouldBe200())
and(theResponseBody(), shouldContainOrderId())

// Asynchronous with default timeout — prefer this when the default is sufficient
thenEventually(theOrderStatus(), shouldBePending())

// Stable state — must hold throughout the window
thenContinually(theCircuitBreakerState(), shouldBeClosed())
```

When **several independent conditions** must hold, use the block form: it polls all assertions
in parallel within a single window, where chained `thenEventually`/`andEventually` calls poll
sequentially and spend the timeout one assertion at a time:

```kotlin
thenEventually {
    then(theOrderStatus(), shouldBeDispatched())
    and(theAuditLog(), shouldContainDispatchEntry())
}
```

`thenEventually { }` locks in each assertion as soon as it passes; `thenContinually { }` requires
every assertion to hold on every tick. If several assertions time out, the failures are aggregated
into one error listing each. A window may be passed as the first argument —
`thenEventually(2.seconds) { ... }` — subject to the duration rule below.

**Negative assertions** ("no cancellation event is ever received") use `then` when a later positive
assertion already anchors that the system has finished, otherwise `thenContinually`.
`thenEventually` passes on the first poll, before a late event could arrive. For "no
element matching" over a collection use `noneMatching(matcher)` from
`dev.kensa.kotest.testsupport.collections` (hamkrest: `dev.kensa.hamkrest.testsupport.collections`);
it ignores unrelated elements and fails listing the offending ones. `thenContinually` takes an
explicit window like `thenEventually` (`thenContinually(2.seconds, collector, matcher)`, subject
to the duration rule below) and also takes a `ThenSpec` directly, mirroring `then(spec)` and
`thenEventually(spec)`.

```kotlin
// Bad — passes trivially on the first empty poll
thenEventually(theCapturedEvents(), noneMatching(aCancelledOrderEvent()))

// Good — must hold on every tick of the window
thenContinually(theCapturedEvents(), noneMatching(aCancelledOrderEvent()))
```

When a non-default timeout is needed, **the duration lives in a private function** and the body
names the wait; a duration literal in the body reads as plumbing:

```kotlin
// plumbing in the rendered context
thenEventually(10.seconds, allNotifications(), shouldShowBothSuppliersCompleted())

// the body names the wait
thenEventuallyAllNotifications(shouldShowBothSuppliersCompleted())

private fun thenEventuallyAllNotifications(matcher: Matcher<List<Notification>>) =
    thenEventually(10.seconds, allNotifications(), matcher)
```

### Action functions
```kotlin
private fun somePrerequisite() = Action<GivensContext> { (fixtures) ->
    // setup using fixtures[myFixture]
}

private fun anActionOccurs() = Action<ActionContext> { (fixtures, interactions) ->
    holder.result = service.call(fixtures[myParam])
}
```

### State collectors
```kotlin
private fun theResult() = StateCollector { holder.result }
private fun theField() = StateCollector { fixtures[myFixture] }
```

### Fixtures
```kotlin
object MyFixtures : FixtureContainer {
    val MyValue = fixture("My Value") { "some-value" }
    val Derived = fixture("Derived Value", MyValue) { v -> buildThing(v) }
    // Up to 3 dependencies supported; type SecondaryFixture<T> for explicit typing:
    val Composite: SecondaryFixture<String> = fixture("Composite", PartA, PartB, PartC) { a, b, c -> "$a/$b/$c" }
}
```

Register in the extension companion:
```kotlin
companion object {
    init {
        registerFixtures(MyFixtures)
        registerCapturedOutputs(MyCapturedOutputs)
    }
}
```
