# Security Policy

This project handles combined facilities-support operations (cleaning,
security-round, maintenance and grounds-keeping coordination) and
facility-concern workflows. Treat vulnerabilities as potentially high
impact even when the demo data is synthetic -- this actor's domain
directly touches building-access-credential and emergency-response
concerns even though it never itself finalizes either.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real occupant, contractor or supplier data exposure
- authorization bypass
- FacilitiesSupportGovernor bypass
- audit-ledger tampering
- over-disclosure in facility-concern reports or exports
- tenant isolation failures
- any path by which a proposal could reach a building-access-credential
  grant/revocation or an emergency-response override without a
  `:scope-excluded` HARD hold

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on occupant/contractor/supplier data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real occupant, contractor and supplier data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
