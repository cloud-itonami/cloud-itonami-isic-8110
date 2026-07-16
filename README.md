# cloud-itonami-isic-8110

Open Business Blueprint for **ISIC Rev.5 8110**: combined facilities
support activities -- a single contractor bundling cleaning, security,
maintenance and grounds-keeping for a building/campus under ONE
contract.

This repository publishes a combined-facilities-support
operations-COORDINATION actor -- cleaning/maintenance/security-round
service-record logging, combined cleaning/security/maintenance crew
scheduling, facility-supplies/equipment procurement coordination with a
registered supplier, and facility-concern flagging -- as an OSS business
that any qualified operator can fork, deploy, run, improve and sell, so
an independent facility-management contractor never surrenders its
operations data to a closed back-office SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, in-mem/Datomic checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **FacilitiesSupportAdvisor
⊣ FacilitiesSupportGovernor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:facilities-support-governor`, is
a distinct, independent build (checked for fleet-wide uniqueness via `gh
api search/code` against `org:cloud-itonami`, zero hits; deliberately
distinct from sibling ISIC 8129's own cleaning-services actor).

> **Why an actor layer at all?** An LLM is great at drafting a service-
> record summary, a crew-scheduling proposal, or a supply-order request
> -- but it has no license to actually grant or revoke a building-access
> credential, no way to independently confirm a facility's combined-
> services contract or a supply-order supplier is actually a registered/
> verified counterparty, and no notion of when a "flag this concern" op
> quietly turns into a claim to have already acted on it. Letting it act
> directly invites an unverified facility's data entering the ledger, an
> unverified supplier receiving a facility order, or -- worst of all -- a
> fabricated claim to have granted building access or overridden an
> emergency-response protocol, exposing occupants and staff to real
> physical-security and life-safety risk. This project seals the
> FacilitiesSupportAdvisor into a single node and wraps it with an
> independent **FacilitiesSupportGovernor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: coordination only, never access-control or emergency-response authority

This actor is **operations coordination only**. It never performs or
authorizes:

- directly finalizing a building-access-credential grant or revocation
  (issuing, activating, revoking or deactivating a keycard/badge/door-
  code or any other access credential)
- directly overriding, bypassing or disabling an emergency-response
  protocol (fire-alarm override, emergency-lockdown bypass, life-safety-
  system disablement)

The governor's `scope-exclusion-violations` check re-scans every
proposal for this failure mode independently of the advisor's own
framing, and treats it as a HARD, permanent block regardless of
confidence or how clean everything else is. Flagging a facility concern
for a human to triage is exactly this actor's job --
`:flag-facility-concern` is never excluded by this check, only
FINALIZING/enforcing/directly-acting-on that concern is.

### Actuation

**Every proposal this actor generates is `:effect :propose`, never a
direct actuation.** Two independent layers enforce this
(`facilitiesops.governor`'s `effect-not-propose-violations` HARD check
and `facilitiesops.phase`'s phase table, which never puts
`:flag-facility-concern` in any phase's `:auto` set). A human facility-
operations coordinator is always the one who actually acts on a flagged
concern or confirms a high-cost supply order -- and no op in this
actor's closed allowlist can ever grant/revoke access or override
emergency response, at any phase, with or without approval.

## The core contract

```
facility-contract/supplier registration + operations-coordination request
        |
        v
   ┌───────────────────────┐   proposal      ┌────────────────────────────┐
   │ FacilitiesSupport-    │ ─────────────▶ │ FacilitiesSupportGovernor    │  (independent system)
   │ Advisor (sealed)      │  + citations    │ facility-unverified ·       │
   └───────────────────────┘                 │ supplier-unverified (NEW) · │
          │                 commit ◀┼ effect-not-propose ·               │
          │                         │ scope-excluded (access-credential/  │
    record + ledger        escalate ┼ emergency-response finalization) · │
          │              (ALWAYS for│ op-not-allowed                      │
          │       :flag-facility-   │                                      │
          │       concern/high-cost └────────────────────────────┘
          │       supply-order)
          ▼
      human approval
```

**The FacilitiesSupportAdvisor never commits a proposal the
FacilitiesSupportGovernor would reject, and a facility-concern flag or a
high-cost supply order never commits without a human sign-off.** Hard
violations (an unregistered/unverified facility contract; an
unregistered/unverified supply-order supplier; a non-`:propose` effect;
content touching access-credential/emergency-response finalization; an
op outside the closed allowlist) force **hold** and *cannot* be approved
past.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
may perform physical domain work** (here: floor-cleaning, patrol
assistance, materials handling) under human/robot floor operations gated
by facility policy. This actor itself does not dispatch robot/hardware
actions -- it is strictly the operations-coordination layer (service-
record logging, crew-schedule proposals, supply-order coordination,
facility-concern flagging) any physical-dispatch layer could eventually
feed proposals into, always gated the same way by the independent
FacilitiesSupportGovernor.

## Features

- **Closed proposal-op allowlist**: `log-service-record`,
  `schedule-service-operation`, `coordinate-supply-order`,
  `flag-facility-concern` (all `:effect :propose`). No op in this
  allowlist can ever grant/revoke building access or override emergency
  response -- structurally absent, not merely gated.
- **Four HARD governor checks** (permanent, un-overridable):
  1. **Facility unverified** -- the target facility's combined-services
     contract must exist AND be independently registered/verified in
     the store.
  2. **Supplier unverified** (FLAGSHIP NEW) -- for `:coordinate-supply-
     order` only, the named supplier must exist AND be independently
     registered/verified -- a supply-chain counterparty-verification
     gate.
  3. **Effect is :propose** -- any other `:effect` value is rejected.
  4. **Scope exclusion** -- directly finalizing a building-access-
     credential grant/revocation, directly overriding/bypassing/
     disabling an emergency-response protocol, and an op outside the
     closed allowlist are all permanently blocked.
- **Two ESCALATE (SOFT) gates**, either forces human sign-off:
  - `:flag-facility-concern` -- ALWAYS escalates, regardless of
    confidence or phase. A "flag a concern" op is never auto-commit
    eligible and never finalizes an access-control or emergency-response
    decision itself -- it only surfaces the concern for a human.
  - `:coordinate-supply-order` above a cost threshold -- a large-value
    procurement proposal always needs a human sign-off.
  - (LLM confidence below the floor also escalates, as with every
    sibling actor.)
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: service-record logging only (approval-gated)
  - Phase 2: + combined-service crew scheduling, supply-order proposals
    (approval-gated)
  - Phase 3: auto-commits clean, high-confidence, low-cost proposals
    (facility concerns and high-cost supply orders always escalate)
- **Append-only audit ledger** -- every decision is an immutable log
  entry.
- **langgraph-clj StateGraph** -- one request = one supervised run;
  human-in-the-loop via `interrupt-before`.

### Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

### Test suite

- `test/facilitiesops/governor_test.clj` -- unit tests of governor hard
  checks, scope exclusion, and the self-trip regression test
- `test/facilitiesops/advisor_test.clj` -- advisor proposal shape and
  consistency
- `test/facilitiesops/phase_test.clj` -- rollout phase logic
- `test/facilitiesops/governor_contract_test.clj` -- full graph
  integration, audit trail
- `test/facilitiesops/store_contract_test.clj` -- Store protocol and
  MemStore implementation

### Modules

- `facilitiesops.store` -- SSoT (MemStore, String-keyed
  facility/supplier directories, append-only ledger)
- `facilitiesops.advisor` -- contained intelligence node (mock +
  real-LLM seam)
- `facilitiesops.governor` -- independent compliance layer
- `facilitiesops.phase` -- staged rollout (0→3)
- `facilitiesops.operation` -- langgraph-clj StateGraph
- `facilitiesops.sim` -- demo driver

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`8110`).

## Business-process coverage (honest)

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Cleaning/maintenance/security-round service-record logging (`:log-service-record`) | Real time-and-attendance/BMS integration |
| Combined cleaning/security/maintenance crew scheduling coordination (`:schedule-service-operation`) | Direct crew time-clock/payroll integration |
| Facility-supplies/equipment procurement coordination with a registered, verified supplier, HARD-gated on supplier verification (`:coordinate-supply-order`) | Real supplier-ordering-system integration |
| Facility-concern flagging (security-incident/maintenance-hazard/access-control), ALWAYS human-gated (`:flag-facility-concern`) | Directly finalizing a building-access-credential grant/revocation or an emergency-response override -- permanently out of scope, not a gap |
| Immutable audit ledger for every log/schedule/order/flag decision | Real access-control-system (ACS) / building-management-system (BMS) integration |

Extending coverage is additive: add the next op (e.g. an incident-
closure-note or a preventive-maintenance-schedule check) as its own
governed op with its own HARD checks and tests, following the SAME "an
independent governor re-verifies against the actor's own records before
any real-world act" pattern this repo's flagship checks already
establish. A building-access-credential-grant/revocation op or an
emergency-response-override op must never be added to the allowlist.

## Maturity

`:implemented` -- `FacilitiesSupportAdvisor` + `FacilitiesSupportGovernor`
run as real, tested code (see `Development` above), following the SAME
governed-actor architecture as every prior actor across this fleet, with
its own distinct, independently-named governor and its own novel
supply-chain supplier-verification check.

## License

Code and implementation templates are AGPL-3.0-or-later.
