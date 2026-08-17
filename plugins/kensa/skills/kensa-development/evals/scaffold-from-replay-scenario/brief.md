A tester walked this scenario in Kensa Replay and saved it. Scaffold the Kensa test that
locks the behaviour in.

Input: `place-order-happy-path.yml` (a saved Replay scenario, `scenarios/<slug>.yml`,
schema 1) sitting beside this file. No evidence export is available.

The scenario belongs to an `Orders` app whose replay plugin publishes two step libraries:
a `Supplier` group of primes and an `Order Service` group of sends. The scenario's declared
expectation was: "Supplier receives the order body with the session's Amount; order service
gets the Reference back".

Output: a single Kotlin file holding a `KensaTest` skeleton. It must compile as emitted;
anything the scenario file cannot resolve should be an obvious, named placeholder rather
than an invented call.
