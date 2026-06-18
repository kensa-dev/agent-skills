title: OrderService reserves stock with the supplier before confirming an order

participants:
  - OrderService   (SUT)
  - Supplier       (stub/supplier, primed via http-stub)

given:
  - the supplier is primed to confirm a reservation
    returning JSON { "reservationId": "RES-1", "status": "RESERVED", "quantity": 5 }

when:
  - OrderService places an order for 5 units of catalogue item "WIDGET-1"

then:
  - the supplier receives a reservation request whose body has field "quantity" equal to 5
    and field "item" equal to "WIDGET-1"                              [timing: immediate]
  - the order is eventually marked CONFIRMED                          [timing: eventually]
  - drill-down: the captured reservation response shows reservationId "RES-1",
    status "RESERVED", quantity 5                                     [expandable detail]
