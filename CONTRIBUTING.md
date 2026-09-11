# Contributing

`cloud-itonami-isco-7113` accepts contributions to the OSS actor, policy tests,
documentation, examples and open occupation blueprint.

## Development

```bash
kbb -M:test
```

Keep changes small and include tests for policy, audit, scheduling or
escalation behavior.

## Rules

- Do not commit real craftsperson, workshop or operating documents.
- Keep production writes behind Stonemasonry Governor.
- Treat this occupation's workflows as high-risk: add tests for
  permission, provenance, safety-escalation and audit logging.
- Never add an op, or a code path, that finalizes a stone-cutting/
  carving-execution decision or overrides a workshop safety officer's
  judgment.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which policy invariant is affected
- how it was tested
- whether operator or certification docs need updates
