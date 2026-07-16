# Contributing

`cloud-itonami-isic-8110` accepts contributions to the OSS blueprint,
capability bindings, policy tests, documentation and operator model.

## Development

```bash
clojure -M:test
clojure -M:lint
```

## Rules
- Do not commit real occupant, contractor, supplier or security-incident
  data.
- Keep service-record logging, combined-service crew scheduling, supply-
  order coordination and facility-concern flagging behind the
  FacilitiesSupportGovernor.
- Treat combined-facilities-support workflows as high-risk: add tests for
  facility/supplier verification, effect discipline, scope exclusion,
  escalation and audit logging.
- Never phrase a governor scope-exclusion term as a bare noun (e.g.
  "access", "security") -- phrase it as the finalization/execution ACTION
  (e.g. "granted building access", "overrode the emergency response
  protocol"), and add/extend the
  `default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  regression test for any new term. A bare-noun term will self-trip this
  actor's own legitimate `:flag-facility-concern` happy path -- see
  `facilitiesops.governor/scope-excluded-terms`'s docstring.
- Never add an op (or an `:auto` phase entry) that directly finalizes a
  building-access-credential grant/revocation or an emergency-response
  override -- those are permanently out of scope for this actor, not a
  rollout milestone.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
