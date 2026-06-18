package dev.kensa.example.kage

import com.fasterxml.jackson.databind.JsonNode
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import dev.kensa.ExpandableSentence
import dev.kensa.RenderedValue
import dev.kensa.example.kage.InventoryReservationFixtures.CatalogueItemFx
import dev.kensa.example.kage.InventoryReservationFixtures.ReservationQuantityFx
import dev.kensa.example.kage.InventoryReservationFixtures.ReservationIdFx
import dev.kensa.fixture.FixtureRegistry.registerFixtures
import dev.kensa.hamkrest.testsupport.field.json.JsonIntField
import dev.kensa.hamkrest.testsupport.field.json.JsonTextField
import dev.kensa.hamkrest.testsupport.field.of
import dev.kensa.junit.KensaTest
import dev.kensa.kage.plugin.http.stub.HttpStubPlugin
import dev.kensa.kage.server.KageServer
import dev.kensa.kotest.WithKotest
import dev.kensa.toolbox.http4k.HttpEventTypes
import dev.kensa.toolbox.kensa.SequenceDiagramCapture
import dev.kensa.toolbox.spi.EventType
import dev.kensa.toolbox.spi.InProcessBus
import dev.kensa.toolbox.spi.UuidTrackingId
import io.kotest.matchers.shouldBe
import org.http4k.core.Uri
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InProcessInventoryReservationTest : KensaTest, WithKotest {

    init {
        registerFixtures(InventoryReservationFixtures)
    }

    private lateinit var server: KageServer
    private lateinit var scenario: InventoryReservationScenario
    private var subscription: AutoCloseable? = null
    private var diagramCapture: SequenceDiagramCapture? = null

    @RenderedValue
    private lateinit var holder: InventoryReservationScenario.Holder

    @BeforeEach
    fun setUp() {
        kensaReporting()
        val bus = InProcessBus()
        val stubPlugin = HttpStubPlugin.codeWired(
            eventTypes = HttpEventTypes(
                EventType("supplier.notify-order.request"),
                EventType("supplier.notify-order.response"),
                EventType("supplier.notify-order.request")
            )
        )
        server = KageServer(bus).start(listOf(stubPlugin), UuidTrackingId.Parser)
        scenario = InventoryReservationScenario(bus, Uri.of("http://localhost:${server.port()}"))
        subscription = scenario.subscribe()
        diagramCapture = SequenceDiagramCapture(bus, reservationDescriptors()).also { it.track(scenario.trackingId) }
        holder = scenario.holder
    }

    @AfterEach
    fun tearDown() {
        diagramCapture?.close()
        subscription?.close()
        server.stop()
    }

    @Test
    fun `reserves stock with the supplier before confirming an order`() {
        given(scenario.anOrderService())
        given(
            scenario.primeSupplierToConfirmReservation(
                reservationId = fixtures(ReservationIdFx),
                quantity = fixtures(ReservationQuantityFx)
            )
        )

        whenever(scenario.placingAnOrderFor(quantity = fixtures(ReservationQuantityFx), item = fixtures(CatalogueItemFx)))

        then(scenario.theReservationRequestBody()) {
            requestsReservationOf(fixtures(ReservationQuantityFx), fixtures(CatalogueItemFx))
        }

        thenEventually(scenario.theOrderStatus()) { shouldBeConfirmed() }

        then(scenario.theReservationResponse()) {
            theReservationResponseShows(reservationId, status, quantity)
        }
    }

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

    private fun JsonNode.requestsReservationOf(quantity: Int, item: String) {
        assertThat(this, aQuantityField of quantity and (anItemField of item))
    }

    private fun OrderStatus.shouldBeConfirmed() {
        this shouldBe OrderStatus.CONFIRMED
    }

    private val aQuantityField: JsonIntField get() = JsonIntField("/quantity")
    private val anItemField: JsonTextField get() = JsonTextField("/item")
}
