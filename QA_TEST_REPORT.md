# QA Test Report — Claims Management

Date: 2026-11-06. QA review decision: **Pending Review**.

## Executive summary

The selected automated coverage records **146 passed, 3 failed, 0 skipped**
across 149 cases. Unit and component results were reproduced in a clean repository
export during configuration verification. The integration result comes from an
earlier execution against the existing Docker environment, before the
reproducibility configuration was committed. These totals combine those selected
results; they are not a single execution or full fresh-stack verification.

The three failures expose the documented API/domain minimum-amount discrepancy
([BUG-001](BUG_REPORT.md)); its policy resolution is **Pending Clarification**.
No test expectation was changed or failure suppressed. No tests were executed
during this documentation activity.

## Test scope and coverage

Scope is the implemented subset of [TEST_SPECIFICATION.md](TEST_SPECIFICATION.md).
This report does not claim completion of every scenario in that specification.

| Level | Implemented coverage | Passed | Failed | Skipped | Evidence basis |
| --- | --- | ---: | ---: | ---: | --- |
| Unit | Java claim creation/events; required values; dates, trimmed text and amount boundaries; all 25 status pairs, rejection side effects and lifecycle event ordering | 115 | 3 | 0 | Clean-export execution, 2026-11-06 around 15:39 +07:00 |
| Component | Real claim wizard validation, visible errors, amount/date/text boundaries and blocked progression; routing/API mocked | 30 | 0 | 0 | Clean-export execution, 2026-11-06 around 15:39 +07:00 |
| Integration | Claimant/admin login, BFF submission, owner/data/SUBMITTED checks, detail reads, admin UNDER_REVIEW then APPROVED updates and readback | 1 | 0 | 0 | **Previous execution**, 2026-11-06 15:18:38.857 +07:00; existing Docker stack |
| **Overall selected results** | **149 cases; combined execution history** | **146** | **3** | **0** | Not an additional execution |

Unit suite breakdown: ClaimStatusTest **25/0**, ClaimStatusTransitionTest **34/0**,
ClaimCreationTest **4/0**, ClaimFieldValidationTest **52/3** (passed/failed).
Unit execution reported zero errors and exited 1 because of the three failures.

## Execution evidence

The versioned [QA journal](demo-app/QA_AI_WORK_JOURNAL.md) records the execution
commands, dates, outcomes and limitations. Available local artifacts were read
again when preparing this report; generated artifacts are ignored and are not
included in the delivery source.

| Evidence | Recorded result and availability |
| --- | --- |
| Clean-export Surefire XML, `code/claims-service/target/surefire-reports/TEST-*.xml` within the temporary checkout | Four suite totals rechecked: 118 cases, 3 failures, 0 errors, 0 skipped. Latest unit run took 9.327 seconds; Java 21 / JUnit Jupiter 5.11.4. Temporary artifacts may expire. |
| Clean-export Vitest output recorded in the journal's reproducibility entry | 30 passed in 7.13 seconds; Vitest 4.0.18. No separate JSON report was generated for that latest execution. |
| [Earlier component JSON report](demo-app/code/demo-app-ui/test-results/claim-form-validation.json) | Earlier 15:02 execution also records 30 passed, 0 failed, 0 skipped; this is not the latest clean-export artifact. Local only. |
| [Previous Playwright JSON report](demo-app/code/demo-app-ui/test-results/claim-submission-status.report.json) | 1 passed in 2.079 seconds, no retries. Sanitized evidence records all expected HTTP statuses and SUBMITTED → UNDER_REVIEW → APPROVED. Local only. |

The passing integration run ID is `3130695b-744b-4d17-9ebb-979bdf43adef`;
its new claim ID is `c907657b-9727-4da6-9e1c-b61b81e30aef`, last observed
APPROVED. Earlier attempted runs are recorded in the journal and are not added
to the selected-result totals. Fresh-checkout Playwright `--list` later discovered
one test; discovery is not an integration execution.

## Rerun commands

Complete [QA_SETUP.md](demo-app/QA_SETUP.md) first. Commands start at the repository
root. Initial setup requires dependency downloads; recorded clean Java checks
used cached dependencies with `-o`. The commands below allow first-time downloads.

```sh
# All four selected unit classes; expect exit 1 with the three documented failures.
mvn -f demo-app/code/claims-service/pom.xml -Djunit-jupiter.version=5.11.4 -Dtest=ClaimStatusTest,ClaimStatusTransitionTest,ClaimCreationTest,ClaimFieldValidationTest -Dcheckstyle.skip=true test

# Install locked frontend dependencies, then run the selected component file.
npm --prefix demo-app/code/demo-app-ui ci --no-audit --no-fund
cd demo-app/code/demo-app-ui
npm test -- tests/components/claim-form-validation.test.tsx --reporter=default --reporter=json --outputFile=test-results/claim-form-validation.json

# Requires the main Docker stack and valid test accounts; creates one retained claim.
npx --no-install playwright test --config=playwright.api.config.ts
```

Surefire writes XML under the service's `target/surefire-reports/`. Frontend JSON
reports are written under `test-results/`. These outputs remain ignored. The
JUnit override preserves JUnit 5; Checkstyle is skipped, so these commands do not
establish static-analysis compliance.

## Known limitations and remaining risks

- **BUG-001:** three active domain assertions fail for 0.001, 0.009 and 0.009999.
  Domain acceptance is confirmed; HTTP acceptance, persistence rounding and
  financial loss for these values have not been demonstrated.
- **Fresh-stack runtime remains unverified.** Compose resolution, credential
  generation/idempotence, required mounts, non-root realm readability, clean Java
  packaging and locked npm installation passed. Docker image builds, complete
  startup, new-realm import/authentication, UI startup and uncached first-time
  downloads were not verified. Fixed names/ports conflict with the preserved
  running environment; use a separate Docker host/context for that verification.
- Integration covers two allowed status transitions and successful role use.
  Negative authorization, cross-owner isolation, invalid API transitions and the
  full lifecycle matrix at the API boundary are outside this implemented test.
  Persistence was checked through API readback, not independent SQL inspection.
- An earlier integration attempt returned 403 on a second admin write. The final
  client obtains CSRF before each write and passed; the earlier cause remains
  unconfirmed. See the journal for the retained failure evidence.
- Unit events do not prove Kafka delivery. CDC, concurrency, performance, broad
  security testing, browser end-to-end behavior, timezone policy, production TLS
  and credential rotation are outside the selected coverage. No coverage
  percentage or complete regression claim is made.

## QA conclusion

The selected tests provide reproducible evidence for the implemented rules and
record the unresolved amount inconsistency. Delivery review and release readiness
remain **Pending Review**, with BUG-001 **Pending Clarification** and fresh-stack
runtime verification outstanding. Preserve the failing tests while the business
minimum and rounding policy are clarified.
