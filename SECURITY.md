# Security Policy

This project handles stonemason/stone cutter/splitter/carver workshop
coordination workflows. Treat vulnerabilities as potentially high impact
even when the demo data is synthetic — this occupation carries real
physical-safety stakes (silica dust exposure, blade injury, material-
handling strain).

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real craftsperson or workshop data exposure
- authorization bypass
- Stonemasonry Governor bypass
- audit-ledger tampering
- over-disclosure in reports or exports
- unsafe robot action dispatch
- any path by which a stone-cutting/carving-execution decision could
  be finalized, or a workshop safety officer's judgment overridden, by
  the actor itself

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
gftdcojp organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on craftsperson/workshop data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real craftsperson/workshop data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
