# Iterate: a Kensa TDD iteration for a story

One story, one acceptance test, the smallest production change that turns it green, and evidence that the whole bundle is green. The test is the outer loop: red on the story, green on the implementation, bundle clean at the end.

The kensa MCP server supplies the evidence; `mcp-tools.md` owns the tool notes and the triage path. Without the server the same facts are in the bundle files, as that file says.

## Steps

1. **Author the test.** Run the Author pipeline (`authoring/overview.md`) on the story: outside-in, black-box, one scenario. Done when self-review reports zero violations and no production source has changed.

2. **Confirm red for the right reason.** Run the tests, or wait for the developer's run; `await_results`, then `failure_evidence` on the new class. Done when `failingSentence` is the story's own assertion. A compile error, a missing fixture or stub, a parse failure, or a red class other than the new one is the wrong red: fix the test and repeat this step.

3. **Implement.** The smallest production change that could turn the test green. Done when it compiles and touches only what the story names.

4. **Run and wait.** Run the tests in the background and call `await_results` at once. While it waits, `run_status` carries `classes`, `passed`, `failed` and `disabled` so far (kensa-core 0.9.2), so a failure shows before the run ends. Done when `await_results` returns `completed: true`.

5. **Read the result.**
   - **Green:** review the new test against How to Review in `SKILL.md`, then `list_failures` on the whole bundle. Done when `failures` is empty for the bundle, not only for the new class. Report the test, the change, and that `list_failures` result as the evidence.
   - **Red:** `failure_evidence` on the class; when the message does not explain itself, `captured_interactions` on `<class>:<method>` (`mcp-tools.md`, Triage steps 2 to 4). Return to step 3.

## Stop

Three consecutive reds with the same `failingSentence` and the same exception is a stuck loop. Stop, report the evidence gathered and the changes tried, and hand back to the developer.

## The developer's run

The developer may run the tests from the IDE. `await_results` covers that run the same way: call it as soon as the run is launched (`mcp-tools.md`, Triage step 5, has the timing and the `timedOut` case).
