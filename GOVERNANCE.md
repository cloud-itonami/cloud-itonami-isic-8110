# Governance

`cloud-itonami-isic-8110` is an OSS open-business blueprint for combined
facilities-support operations coordination (ISIC Rev.5 8110 -- combined
facilities support activities: a single contractor bundling cleaning,
security, maintenance and grounds-keeping for a building/campus under one
contract).

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a proposal for an unregistered/unverified facility contract, or a
  supply order naming an unverified/unregistered supplier, can never
  commit.
- the FacilitiesSupportGovernor remains independent of the advisor.
- hard policy violations (non-`:propose` effect, building-access-
  credential-grant/revocation-finalization content, emergency-response-
  override-finalization content, an op outside the closed allowlist)
  cannot be overridden by human approval.
- every service-record log, combined-service crew schedule, supply-order
  coordination and facility-concern flag is auditable.
- building occupant, contractor and supplier data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or
license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, audit and data-flow
review.

Certified operators can lose certification for:
- bypassing service-record, scheduling, supply-order or facility-concern
  policy checks
- mishandling occupant, contractor or supplier data
- misrepresenting certification status
- failing to respond to security or emergency-response incidents
