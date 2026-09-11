# cloud-itonami-isco-7113

Open Occupation Blueprint for **ISCO-08 7113**: Stonemasons, Stone Cutters, Splitters and Carvers.

This repository designs a forkable OSS business for an independent stonemasonry workshop coordination practice: a workshop scheduling/logistics coordination robot manages crew scheduling, work-record logging and stone-materials procurement proposals under a governor-gated actor, so the workshop keeps its own operating records instead of renting a closed scheduling SaaS — and so that every high-stakes, safety-relevant decision always reaches a human.

**Maturity: `:implemented`.** `src/stonemasonry/` implements the
`StonemasonryActor` as a `langgraph.graph/state-graph`
(`stonemasonry.actor`) wired to a `Workshop Coordination Advisor`
(`stonemasonry.advisor`) and an independent `StonemasonryGovernor`
(`stonemasonry.governor`), following the itonami actor pattern
(ADR-2607121000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok?) +-> :request-approval (:escalate?, human-in-the-loop interrupt)
+-> :hold (:hard?)`. 31 tests / 106 assertions green (`kbb -M:test`).

HARD invariants (always hold, never overridable): the workshop and
craftsperson must both be independently registered AND verified before
any action, `:effect` must be `:propose` only, the closed four-op
allowlist is enforced (any other `:op` is a permanent block), and any
proposal that would finalize a stone-cutting/carving-execution
decision or override a workshop safety officer's judgment is a HARD,
PERMANENT block — enforced two independent ways: those ops are
structurally absent from the allowlist, and a content-based
scope-exclusion check independently rejects any free-text rationale
naming such a finalization/override action, regardless of the `:op`
used to carry it. A `:schedule-crew-operation` proposal beyond a
craftsperson's registered daily task-hour ceiling, or without a
confirmed task order, is also a HARD block (an overwork/fatigue safety
risk and an invented operation, respectively — not routine dispatch).

Always-escalate (human sign-off regardless of confidence, mapping this
repo's Trust Controls in [`docs/business-model.md`](docs/business-model.md)):
`:flag-safety-concern` (always — the only channel by which the robot
may surface a dust-exposure/blade-hazard/material-handling concern; it
never resolves the concern itself), and `:coordinate-supply-order`
proposals above the workshop's registered cost threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a workshop scheduling/logistics coordination
robot performs crew scheduling, work-record logging and materials-procurement
coordination under an actor that proposes actions and an independent
**Stonemasonry Governor** that gates them. The governor never dispatches
hardware itself and **never performs stonework** — it only coordinates the
workshop's scheduling and logistics. `:high`/`:safety-critical` actions (such
as any safety concern, or a supply order above the workshop's registered cost
threshold) require human sign-off. **This actor coordinates workshop
scheduling and logistics ONLY — it never cuts, splits or carves stone
itself, and it never finalizes a stone-cutting/carving-execution decision or
overrides a workshop safety officer's judgment.**

## Core Contract

```text
crew roster + confirmed task orders + materials plan
        |
        v
Workshop Coordination Advisor -> Stonemasonry Governor -> log/schedule/coordinate, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, commit
an operating record above a registered ceiling, suppress a safety concern, or
finalize a stone-cutting/carving-execution decision or override a workshop
safety officer's judgment.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `7113`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
