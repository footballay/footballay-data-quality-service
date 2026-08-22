## apply: always

# AGENTS.md

## Scope

This repository is `footballay-data-quality-service`, a Spring Boot Kotlin service for inspecting collected football data and recording data quality results.

Use Korean when explaining work to the user. Korean sentences must end with `.`, `?`, or `!`, not a closing colon.

These rules bias toward caution over speed. For trivial tasks, use judgment.

## Core behavior rules

### 1. Think before coding

* Do not assume. Do not hide confusion. Surface tradeoffs.
* Before non-trivial implementation, state assumptions, a short plan, and the verification target.
* If multiple interpretations exist, present them instead of silently choosing one.
* If a simpler approach exists, say so and prefer it.
* Distinguish confirmed requirements from tentative ideas or future plans.
* If something is unclear enough to affect correctness, stop and ask.
* `{projectRoot}/plan` is intentionally listed in `.gitignore` for local development planning.

### 2. Simplicity first

* Write the minimum code that solves the requested problem.
* Do not add features beyond what was asked.
* Do not add abstractions for hypothetical future requirements.
* Do not add flexibility, configurability, dependencies, or broad error handling unless requested.
* Prefer explicit and readable processing over clever or highly generalized designs.
* If the change becomes much larger than necessary, simplify before continuing.

### 3. Surgical changes

* Touch only files directly related to the task.
* Every changed line should trace directly to the user request or to fixing a consequence of that change.
* Match existing style and architecture unless the task explicitly changes them.
* Do not refactor adjacent code, reformat unrelated files, improve unrelated comments, or delete pre-existing dead code.
* If you notice unrelated problems, mention them instead of fixing them.
* Remove only code made unused by your own change.

### 4. Goal-driven execution

* Convert work into verifiable goals.
* For bug fixes, reproduce the bug with a failing test or targeted verification before changing production code when practical.
* For validation changes, define the expected valid and invalid cases before implementation.
* For refactors, verify behavior before and after when practical.
* Do not mark work complete until the requested behavior is implemented and relevant verification has been run.

## Service responsibility rules

* Keep this service focused on data quality inspection and the production of quality results.
* Do not move unrelated football domain responsibilities into this service.
* Treat collected source data as evidence to inspect, not as domain state owned by this service.
* Validation and inspection logic should remain deterministic, explainable, and independently testable.
* Preserve enough evidence for a detected issue to be understood and reviewed later.
* Keep transport, storage, orchestration, and inspection concerns separate when they have clearly different responsibilities.
* Do not couple core inspection logic directly to infrastructure details.
* Do not change shared contracts or responsibility boundaries as part of an unrelated implementation task.
* When a requirement affects another service or a shared contract, surface that impact before changing it.

## Safety and permission rules

* Do not commit, push, delete files, change deployment configuration, or install new production dependencies without explicit user approval.
* Do not introduce new infrastructure or external services without explicit approval.
* If a logical change is ready, suggest a semantic commit message, but do not run `git commit` unless approved.
* Do not weaken assertions, skip tests, or broaden matchers just to make tests pass.
* Do not silently discard malformed data, processing failures, or detected quality issues.
* Do not expose credentials, connection information, or sensitive configuration in code, tests, logs, or documentation.

## Testing rules

* Code should be testable. Extract pure or smaller logic when a method becomes hard to test.
* One test method should verify one main concern unless the layer is inherently integration-heavy.
* Test method names should be descriptive English. Use `@DisplayName` for Korean explanation.
* Validation tests should clearly distinguish the input, expected issue, and supporting evidence.
* Include normal cases, malformed cases, and relevant boundary cases.
* If a unit test needs excessive mocking, consider an integration test or improve the implementation design.
* Prefer focused tests for inspection logic and integration tests for infrastructure boundaries.
* If code changed, run the smallest relevant test or build check before saying the work is done.
* If checks fail, inspect the actual error and evidence before guessing a fix.

## Command and token discipline

### Code dump

* When the user requests a "code dump" or "코드 덤프", create or overwrite the requested dump file by command-line streaming, not by reading each source file into the model and reproducing it through a patch.
* Resolve the target files with Git first. Unless the user specifies another scope, include every changed `src/` file relative to `HEAD`, including untracked source files, and exclude ignored planning files.
* Write each target file's complete current filesystem contents to one Markdown file, with a file-path heading and fenced code block. Do not summarize, omit code, or reinterpret source contents.
* Prefer a single shell pipeline or short shell script that writes directly to the dump file. Verify the number of dumped file headings matches the resolved target file count.

* Prefer targeted commands before full-suite commands.
* For Gradle tests, prefer.
`./gradlew test --tests "fully.qualified.TestName" --console=plain`.
* For long command output, save the raw log and inspect only relevant slices.
`./gradlew test --console=plain 2>&1 | tee /tmp/footballay-data-quality-test.log`.
* Do not paste or load huge logs into context.
* Inspect logs with `rg`, `grep`, `tail`, or `sed`.
* Do not rely only on summarized output when investigating test, database, messaging, or integration failures.
* Run the full suite only when needed or explicitly requested.  
* Git should not track code dump file.  
* If a `plan/` directory exists at the project root and is ignored by Git, write code dumps there.  

## Context notes for large tasks

* For multi-step work that spans many files or may continue across sessions, create or update the following files in `{projectRoot}/plan`.

* `checklist.md` for concrete tasks.
* `context-notes.md` for decisions and reasoning.
* Do not create these files for small, local, one-shot changes.
* Keep notes short and factual.
* Do not treat tentative context notes as confirmed requirements.

## New file comments

* For a new source file whose role in the flow is hard for an onboarding developer to understand from its name alone, add at least one line of Korean KDoc or Javadoc.
* Skip configuration files, generated files, simple DTOs, and files where such a comment would violate project style.

## Done criteria

A task is complete only under the following conditions.

* The requested behavior is implemented or the question is answered.
* Relevant tests, build, or compile checks were run when code changed.
* If checks could not run, the exact reason and command to run manually are reported.
* The diff was reviewed for unrelated changes.
* Shared contract or cross-service impacts are reported.
* The final report lists changed files, verification results, and remaining risks.
