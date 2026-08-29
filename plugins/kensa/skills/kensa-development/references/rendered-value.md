# Kensa @RenderedValue Reference

`@RenderedValue` and related annotations control how values appear in report sentences.

## Value Rendering

Whenever Kensa renders a value — from a `@RenderedValue` property, a fixture, or captured output —
it checks whether a renderer has been registered for that type. If one exists it is used; otherwise
Kensa falls back to `toString()`.

Renderers are registered via the Kensa configuration. Kotlin DSL (in a `Configuration` block):
```kotlin
withRenderers {
    valueRenderer<MyType> { value -> value.someProperty }
}
```

Or via the fluent Java-style configurator:
```kotlin
Kensa.configure()
    .withValueRenderer(MyType::class) { value -> value.someProperty }
```

This means you rarely need to override `toString()` on domain types just for reporting — register
a renderer instead. It also applies to fixture and output values referenced in test sentences.

## @RenderedValue Forms

### On a `lateinit var` field
The field's value (via `toString()`) appears in sentences wherever the field name is used.
Kensa reads the value at end of test, so it's safe for mutable output set during the action:
```kotlin
@RenderedValue
private lateinit var result: LcApplicationResult
```

### On a `val` field
The value appears in sentences wherever the field name is used:
```kotlin
@RenderedValue
private val expectedStatus = "APPROVED"
```

### On a no-arg function
The return value appears in sentences wherever the function is called:
```kotlin
@RenderedValue
private fun currentTimestamp() = clock.now().toString()
```

### On a parameter of `@ExpandableSentence`
The argument value appears in the expanded sentence:
```kotlin
@ExpandableSentence
private fun verifyLcWasApprovedWith(@RenderedValue expectedPrefix: String) {
    then(theIssuedLcNumber()) { shouldStartWith(expectedPrefix) }
}
```

### Chained paths

A rendered reference may navigate properties, and paths accept Kotlin's `?.` safe-call and `!!`
operators — the navigated value substitutes in the sentence, not the source words:

```kotlin
order?.customer?.name          // order is a @RenderedValue field
fixtures[OrderFx]!!.reference
outputs("orderId")?.length
```

Kotlin stdlib extension calls in a path (`.first()`, `.uppercase()`) resolve too; user-defined
extension functions do not — wrap those in a `@RenderedValue` no-arg function instead.

### Qualified enum constants

When two enums share a constant name and Kotlin forces qualification, `OrderStatus.PENDING` in a
test body renders as just `PENDING` as a value token with the type's simple name as a hover hint.
Nested objects and sealed-class data objects (`OrderStatus.Pending`) render the same way. A forced
qualifier is correct as written: leave it in place, unwrapped.
A qualifier that does not resolve through the file's imports renders as camel-split words.

## @ExpandableRenderedValue

Renders **only the return value** of a method (or property/parameter) — the body is hidden. Use
when many fields need verification but they aren't individually meaningful as named matchers; the
data *itself* is the unit of meaning. The method may also perform comparison work; only the return
value reaches the report.

```kotlin
annotation class ExpandableRenderedValue(
    val renderAs: RenderedValueStyle = Default,
    val headers: Array<String> = []
)
```

### Default style

Renders the return value via its registered renderer. If the value is iterable, the renderer is
invoked on each item and items appear as a flat list:

```kotlin
@ExpandableRenderedValue
private fun theDispatchedLifecycle(): List<DispatchStatus> =
    listOf(ACKNOWLEDGED, COMMITTED, DISPATCHED, DELIVERED)

// In a test:
then(theShipment(), shouldFollowLifecycle(theDispatchedLifecycle()))
```

The report shows the lifecycle list inline; the helper body is not rendered.

### Tabular style

Renders the return value as a labelled table. Default table renderer: an `Iterable<Pair<*, *>>`
becomes two-column rows. Provide explicit `headers`; register a custom `TableRenderer<T>` for
richer shapes.

```kotlin
@ExpandableRenderedValue(renderAs = Tabular, headers = ["Field", "Expected"])
private fun theShipmentFields(): List<Pair<String, String>> = listOf(
    "PostCode"     to fixtures[PostCodeFx],
    "CountryCode"  to fixtures[CountryCodeFx],
    "ServiceLevel" to fixtures[ServiceLevelFx],
)

// In a test:
then(courier.hasDispatched(aShipmentWith(theShipmentFields())))
```

The report shows a two-column table; the test body stays a single line.

### Choosing between matcher and `@ExpandableRenderedValue`

| Need | Use |
|---|---|
| Field is domain-important on its own | Named matcher (`aPostCode of value`) |
| Many fields, collection is the unit of meaning | `@ExpandableRenderedValue` |
| Same plus a labelled table in the report | `@ExpandableRenderedValue(renderAs = Tabular)` |

## @RenderedValueWithHint

Use for wrapper types (e.g. `JsonPath`, `XmlPath`) that should show the identifier name in
sentences with the underlying path/value as a hover hint:

```kotlin
@RenderedValueWithHint(
    type = JsonPath::class,
    valueStrategy = UseIdentifierName,
    hintParam = "path",
    hintStrategy = HintFromProperty
)
class MyTest : KensaTest, WithKotest { ... }
```

This annotation is placed on the **test class**. It tells Kensa:
- When it encounters a value of type `JsonPath`, render the variable's identifier name (not the path)
- Use the `path` property of `JsonPath` as the hover hint text

### `valueStrategy` options
- `UseIdentifierName` — render the variable/parameter name (e.g. `issuedLcNumber`)
- `UseToString` — render `value.toString()` (default behaviour without the annotation)

### `hintStrategy` options
- `HintFromProperty` — read the hint from a property on the object (named by `hintParam`)
- `HintFromToString` — use `value.toString()` as the hint

## @Issue

Links test results to issue tracker tickets. Appears as a badge in the HTML report.

```kotlin
@Issue("PROJ-101")                      // single ticket
@Issue("PROJ-101", "PROJ-202")          // multiple tickets
```

Place on the test method or the test class:
```kotlin
@Test
@Issue("TF-42")
fun canIssueAnLcWhenCreditAndSanctionsArePositive() { ... }
```

## @Epic

Links a test or class to one or more epics, distinct from `@Issue`. Renders as a badge beside the
issue badges, resolved against the same configured `issueTrackerUrl`, and the report tree filters
on `epic:`. Same targets as `@Issue`; vararg ids.

```kotlin
@Epic("PROJ-1")
@Issue("PROJ-42", "PROJ-43")
@Test
fun refundIsProcessedWithin24Hours() { ... }
```

## @Notes

Attaches a freeform note to a test class. Rendered as a styled card above the test list in the HTML
report. Supports inline markdown for rich text and internal report navigation links.

**Target:** `CLASS` only.

### Markdown syntax

| Syntax | Result |
|--------|--------|
| `**text**` | Bold |
| `*text*` | Italic |
| `__text__` | Underline |
| `~~text~~` | Strikethrough |
| `[label](https://...)` | External link (opens in new tab) |
| `[label](#methodName)` | Scroll to and expand a test method in the same suite |
| `[label](#ClassName)` | Navigate to another suite by simple class name |
| `[label](#ClassName.methodName)` | Navigate to that suite and expand the named method |

### Kotlin

Annotation values must be compile-time constants — `.trimIndent()` cannot be used. Start content
on the line immediately after the opening `"""` with **no leading indentation**. Blank lines become
paragraph breaks; single newlines within a paragraph become line breaks.

```kotlin
@Notes("""
**Payment gateway** uses *idempotency keys* — retries with the same key are __safe__.
See [Stripe docs](https://stripe.com/docs/idempotency) for details.

~~Direct refunds are no longer supported.~~ Use the [refund flow](#processRefund) instead.
For error-path behaviour see [RefundEdgeCasesTest](#RefundEdgeCasesTest).
""")
class PaymentTest : KensaTest, WithKotest { ... }
```

### Java

Java 15+ text blocks allow indentation stripping via the closing `"""` position:

```java
@Notes("""
    **Payment gateway** uses *idempotency keys* — retries with the same key are __safe__.
    See [Stripe docs](https://stripe.com/docs/idempotency) for details.

    ~~Direct refunds are no longer supported.~~ Use the [refund flow](#processRefund) instead.
    For error-path behaviour see [RefundEdgeCasesTest](#RefundEdgeCasesTest).
    """)
class PaymentTest implements KensaTest, WithAssertJ { ... }
```

### Common mistakes

**Adding leading indentation in Kotlin** — content must start at column 0:
```kotlin
// Bad — indented content becomes part of the note text
@Notes("""
    Some note text
""")

// Good — no leading indent
@Notes("""
Some note text
""")
```

**Using @Notes on a method** — it only applies to `CLASS`:
```kotlin
// Bad
@Test
@Notes("This test covers the happy path")
fun `payment is processed`() { ... }

// Good — put it on the class
@Notes("This suite covers payment processing happy paths.")
class PaymentTest : KensaTest, WithKotest { ... }
```

---

## @RenderedValueContainer

Marks a field or a test-method parameter whose members annotated `@RenderedValue` render as
resolved values when referenced through it in the test body.

**On a field**, as a holder for mutable outputs repeated across several test classes. Inside a
`with(holder) { }` body the bare member name resolves too:

```kotlin
@RenderedValueContainer
private inner class Holder {
    lateinit var result: LcApplicationResult
    lateinit var lcNumber: String
}

private lateinit var holder: Holder
```

For a single mutable output field, prefer `@RenderedValue lateinit var` directly on the class
rather than a container.

**On a test-method parameter**, e.g. a `@MethodSource` use-case object. A prefixed chain
(`useCase.stub`, `useCase.ref.name`) renders as the resolved value, gated on the member carrying
`@RenderedValue`. The chain may be followed by a call taking arguments: the prefix renders as the
value and the call parses as ordinary sentence words. Bare-name resolution inside
`with(container) { }` is a field-container feature only.

```kotlin
class WholesalerStub {
    fun sends(request: CheckSessionRequest): CheckSessionResponse = ...
    override fun toString() = "fastweb"
}

class WholesalerUseCase(@RenderedValue val stub: WholesalerStub)

@ParameterizedTest
@MethodSource("wholesalers")
fun canCheckSession(@RenderedValueContainer useCase: WholesalerUseCase) {
    whenever(useCase.stub.sends(aCheckSessionRequest()))
}
// Renders: When fastweb sends a check session request
```

`useCase.stub` displays via a registered `valueRenderer<WholesalerStub>` or, absent one, its
`toString()`. Kotlin chains accept `.` and `?.` and stop at `::` and `!!`. Java supports only the
chain-followed-by-call shape; a bare chain with no trailing call renders as words. A member that
is a `by fixtures(fx)` property renders as a fixture token (see `fixtures.md`).

## Common Mistakes

**Using @RenderedValue on a field that holds test data** — prefer Fixtures for test data:
```kotlin
// Bad — test data as mutable field
@RenderedValue
private var applicantId: String = ""

// Good — use fixtures
val applicantId = fixture("Applicant ID") { "CORP-001" }
```

**Using @RenderedValueContainer when only one field is needed** — prefer a direct `@RenderedValue`:
```kotlin
// Unnecessary
@RenderedValueContainer
private inner class Holder {
    lateinit var result: LcApplicationResult
}

// Better
@RenderedValue
private lateinit var result: LcApplicationResult
```
