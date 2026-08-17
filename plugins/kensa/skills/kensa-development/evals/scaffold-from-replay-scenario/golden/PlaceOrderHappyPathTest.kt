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
        TODO("Wire to the SupplierReplay val registered as supplier.responds-with-reference")

    // Replay step order-service.place-order — group "Order Service", name "Place order", target OrderService
    private fun theOrderIsPlaced(): Action<ActionContext> =
        TODO("Wire to the OrderReplay val registered as order-service.place-order")

    private fun theSupplierRequest(): StateCollector<String> =
        TODO("Collect what target 'Supplier' received — e.g. supplier.store.awaitEarliest(trackingId)")

    private fun shouldCarryTheOrderAmount(): Matcher<String> =
        TODO("Expect: Supplier receives the order body with the session's Amount; order service gets the Reference back")
}
