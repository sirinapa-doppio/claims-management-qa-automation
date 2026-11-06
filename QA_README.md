# Claims Management QA Delivery

This repository contains selected automated tests for the Claims Management
application: a React/Next.js UI, Spring Boot BFF and Claims services, and local
Docker infrastructure. Start with the [test report](QA_TEST_REPORT.md),
[open amount discrepancy](BUG_REPORT.md), and [test specification](TEST_SPECIFICATION.md).
Review decisions remain **Pending Review**.

Recorded selected totals: **146 passed, 3 failed, 0 skipped**. Unit/component
results were reproduced in a clean export; the one passing integration result
is from a previous execution against the existing Docker stack. No complete
fresh-stack runtime verification is claimed.

## Prerequisites and setup

- Java 21, Maven and Python 3.9+.
- Node/npm compatible with the committed lockfile; recorded frontend runs used
  Node 26.13 and Vitest 4.0.18.
- Docker with Compose v2 or later and sufficient resources for the main stack.
- Dependency/image registry access for first setup, and the ports listed in
  [QA_SETUP.md](demo-app/QA_SETUP.md) available. Fixed container/network names
  require a separate Docker host/context if another copy is already running.

Follow [QA_SETUP.md](demo-app/QA_SETUP.md) for the complete setup and readiness
instructions. From the repository root:

```sh
cd demo-app
python3 configure-local.py
bash start-infra.sh --wait
bash start-app.sh -d --wait
```

The helper creates ignored local .env and realm files from safe templates; it
does not overwrite existing files. Keep their credentials consistent and do not
commit them. CDC is optional and is not required for the implemented tests.
Application packaging skips tests/static analysis explicitly; execute the
commands below to obtain test results.

## Execute the selected tests

Each section starts from the repository root. Unit and component tests are
isolated from Spring, databases and external services; the integration test
requires the main Docker environment.

Unit — all four Java classes, JUnit 5:

```sh
mvn -f demo-app/code/claims-service/pom.xml -Djunit-jupiter.version=5.11.4 -Dtest=ClaimStatusTest,ClaimStatusTransitionTest,ClaimCreationTest,ClaimFieldValidationTest -Dcheckstyle.skip=true test
```

Recorded result: **115 passed, 3 failed, 0 skipped**. Expect exit 1 for the three
sub-cent contract-gap assertions. Do not filter, disable or change them to obtain
a green run. The JUnit override and Checkstyle skip match the recorded approach;
use `-o` only after dependencies are cached.

Component — React Testing Library / Vitest:

```sh
npm --prefix demo-app/code/demo-app-ui ci --no-audit --no-fund
cd demo-app/code/demo-app-ui
npm test -- tests/components/claim-form-validation.test.tsx --reporter=default --reporter=json --outputFile=test-results/claim-form-validation.json
```

Recorded result: **30 passed, 0 failed, 0 skipped**.

Integration — Playwright API testing, no browser download required:

```sh
cd demo-app/code/demo-app-ui
npx --no-install playwright test --config=playwright.api.config.ts
```

Install the locked frontend dependencies first if not already installed. Previous
execution: **1 passed, 0 failed, 0 skipped**. The test reads generated local realm
accounts or QA_CLAIMANT_EMAIL / QA_CLAIMANT_PASSWORD and QA_ADMIN_EMAIL /
QA_ADMIN_PASSWORD overrides. QA_BFF_URL defaults to http://localhost:8090.
Each execution creates and retains one unique claim and updates only that claim.

## Test files and results

| Group | Files, relative to repository root |
| --- | --- |
| Unit | `demo-app/code/claims-service/src/test/java/com/example/demo/domain/claim/`: `ClaimStatusTest.java`, `ClaimStatusTransitionTest.java`, `ClaimCreationTest.java`, `ClaimFieldValidationTest.java` |
| Component | [claim-form-validation.test.tsx](demo-app/code/demo-app-ui/tests/components/claim-form-validation.test.tsx), [setup.ts](demo-app/code/demo-app-ui/tests/setup.ts), [vitest.config.mts](demo-app/code/demo-app-ui/vitest.config.mts) |
| Integration | [claim-submission-status.api.ts](demo-app/code/demo-app-ui/tests/integration/claim-submission-status.api.ts), [playwright.api.config.ts](demo-app/code/demo-app-ui/playwright.api.config.ts) |
| Execution history and decisions | [QA_AI_WORK_JOURNAL.md](demo-app/QA_AI_WORK_JOURNAL.md) |

Surefire XML appears under `demo-app/code/claims-service/target/surefire-reports/`.
Component JSON and `claim-submission-status.report.json` appear under
`demo-app/code/demo-app-ui/test-results/`. These reports are ignored; a fresh
clone must generate its own. See [QA_TEST_REPORT.md](QA_TEST_REPORT.md) for the
evidence supporting each recorded execution and the combined totals.

## Known issues and limitations

- [BUG-001](BUG_REPORT.md), **Pending Clarification**: API/UI minimum 0.01 versus
  domain >0; the three failing domain cases remain visible.
- Clean configuration checks and builds passed, but fresh Docker image builds,
  complete stack startup, realm import/authentication, UI startup and first-time
  uncached downloads remain unverified. The historical integration pass does
  not verify the newly generated realm/configuration.
- Coverage is limited to the selected cases. Negative API authorization, direct
  SQL persistence checks, broader lifecycle API coverage, concurrency, Kafka
  delivery/CDC, browser end-to-end testing, performance and production security
  validation are outside this delivery's implemented test groups.
- The earlier integration 403 and its unconfirmed cause remain recorded in the
  journal. The passing test obtains a fresh CSRF token before each write.

No submission ZIP is included in this documentation delivery.
