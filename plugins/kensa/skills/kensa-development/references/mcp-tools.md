# Kensa MCP server

`kensa mcp` serves the project's test results to an agent over MCP. The bundle is what the last test run wrote: it shows what happened, never why. The why comes from `git diff` and the source at the location the tools give you.

With no `kensa` tools in the tool list, the same facts are in the bundle Kensa writes (`kensa-output/results/<class>.json`) and the user should know the server exists: https://kensa.dev/docs/cli#mcp-server.

## What the tool descriptions leave out

- `bundle_dir` is omitted when `.kensa-properties` declares one test folder, which is the normal case.
- `style_profile` takes the module holding the tests as `project_dir`, and reports the framework, fixture containers, matcher fields (typed field descriptors such as `JsonTextField("/item")`), stub helpers and conventions.
- `suite_summary` answers questions about the run as a whole in one call: run window and duration, class and method counts by state, duration buckets, the slowest methods, failure ids, counts by tag and package, participants. Its numbers match the report's overview page. Use it before `list_tests` when the question is how long, how many, or what was slow.
- `list_tests` returns one row per class with `methods` counts and `elapsedMs`; `children: true` adds the method rows. For triage `list_failures` is the entry point.
- `get_test` renders sentences as text; a `<class>:<method>` id returns that method only. `raw: true` is for a field the rendered form drops, and always returns the whole class file.
- `captured_interactions` caps each captured value at `max_value_chars` (default 4000) and marks a cut value with `truncated: true` and `fullLength`; pass `-1` for the whole body.
- A tool list without `suite_summary` is a Kensa CLI before 0.9.4: `list_tests` then inlines every class and child, `get_test` ignores the method part of a child id, and `captured_interactions` returns bodies whole.
- A tool list without `run_status` is a Kensa CLI before 0.9.2: `failure_evidence` is then one flat `{ testMethod, failingSentence, exception }`, `get_test` is the raw file, and there is no `captured_interactions` or `await_results`.

## Triage

1. `list_failures`. Read `bundleAge` before anything else: a bundle older than the change under investigation says nothing about it, so run the tests first (step 5) and come back. An error naming a run in progress means the tests are executing now: `await_results`, then retry.
2. `failure_evidence` on each failed class. `distinctExceptions: 1` across several failures is one cause. `sourceLocation` is where the fix goes; `failingSentence` is what the report will show as broken. Done when every failure has a `sourceLocation` you have opened, or a stated reason it has none.
3. When the exception message does not explain itself (a payload-shape mismatch, an unexpected status), `captured_interactions` on `<class>:<method>` and compare the captured body, status and headers with what the assertion expected.
4. Find the cause in the code: the source at `sourceLocation`, `git diff` against the last green build, the collaborators that produced the captured payload. Done when you can name the change that broke the test, not just the assertion that failed.
5. After the fix, run the tests in the background, call `await_results` at once, and when it returns `completed: true` call `list_failures` on the whole bundle. Done when `failures` is empty for the bundle, not only for the class you re-ran. `timedOut: true` means nothing finished in the window; `run_status` says where the run stands.

## Authoring

After generating a test, the same loop closes it: run the tests, `await_results`, `list_failures`, and `failure_evidence` on the new class if it is red. During introspect, `style_profile` on the test module gives the framework, fixture containers, matchers and conventions; the rest of the inventory (stubs, Kage infrastructure, toolboxes) still comes from reading the sources.
