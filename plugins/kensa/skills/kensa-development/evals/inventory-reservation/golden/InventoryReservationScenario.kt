package dev.kensa.example.kage

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.GivensContext
import dev.kensa.StateCollector
import dev.kensa.fixture.FixtureContainer
import dev.kensa.fixture.fixture
import dev.kensa.toolbox.http4k.HttpInteractionEvent
import dev.kensa.toolbox.http4k.HttpRequestEvent
import dev.kensa.toolbox.http4k.trackingClient
import dev.kensa.toolbox.kensa.InteractionDescriptor
import dev.kensa.toolbox.kensa.Participant
import dev.kensa.toolbox.spi.EventSource
import dev.kensa.toolbox.spi.EventType
import dev.kensa.toolbox.spi.UuidTrackingId
import org.http4k.client.JavaHttpClient
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.Uri
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

object InventoryReservationFixtures : FixtureContainer {
    val ReservationIdFx = fixture("ReservationId") { "RES-1" }
    val CatalogueItemFx = fixture("CatalogueItem") { "WIDGET-1" }
    val ReservationQuantityFx = fixture("ReservationQuantity") { 5 }
}

fun reservationDescriptors(): List<InteractionDescriptor> = listOf(
    InteractionDescriptor(
        EventType("supplier.notify-order.request"),
        Participant("OrderService"),
        Participant("Supplier")
    )
)

class InventoryReservationScenario(
    private val bus: EventSource,
    private val kageBaseUri: Uri
) {
    private val captured = LinkedBlockingQueue<HttpRequestEvent>()
    private val mapper = jacksonObjectMapper()
    val trackingId = UuidTrackingId.random()
    private val supplierUri = Uri.of("${kageBaseUri.toString().trimEnd('/')}/http-stub")

    val holder = Holder()

    fun subscribe(): AutoCloseable =
        bus.subscribe(EventType("supplier.notify-order.request")) { event ->
            captured.offer((event as HttpInteractionEvent).request)
        }

    fun anOrderService(): Action<GivensContext> = Action { _ ->
        val client = trackingClient(trackingId, JavaHttpClient())
        holder.orderService = OrderService(client, supplierUri)
    }

    fun primeSupplierToConfirmReservation(reservationId: String, quantity: Int): Action<GivensContext> = Action { _ ->
        val body = """{"reservationId":"$reservationId","status":"RESERVED","quantity":$quantity}""".replace("\"", "\\\"")
        val response = JavaHttpClient()(
            Request(POST, "${kageBaseUri.toString().trimEnd('/')}/http-stub/prime/${trackingId.asString}")
                .body(
                    """
                    {"status":200,"headers":{"Content-Type":"application/json"},"body":"$body"}
                    """.trimIndent()
                )
        )
        check(response.status == Status.NO_CONTENT) {
            "Priming failed with status ${response.status}: ${response.bodyString()}"
        }
    }

    fun placingAnOrderFor(quantity: Int, item: String): Action<ActionContext> = Action { _ ->
        holder.orderService.reserveStock(item, quantity)
    }

    fun theReservationRequestBody(): StateCollector<JsonNode> = StateCollector { _ ->
        val request = captured.poll(5, TimeUnit.SECONDS) ?: error("Supplier was never called")
        mapper.readTree(request.request.body)
    }

    fun theOrderStatus(): StateCollector<OrderStatus> = StateCollector { _ ->
        holder.orderService.orderStatus()
    }

    fun theReservationResponse(): StateCollector<ReservationConfirmation> = StateCollector { _ ->
        holder.orderService.lastReservation() ?: error("No reservation response captured yet")
    }

    class Holder {
        lateinit var orderService: OrderService
    }
}
