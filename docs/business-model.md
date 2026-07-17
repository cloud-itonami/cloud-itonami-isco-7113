# Business Model: Independent Stonemasonry Workshop Coordination Practice

## Classification

- Repository: `cloud-itonami-isco-7113`
- ISCO-08: `7113`
- Occupation: Stonemasons, Stone Cutters, Splitters and Carvers
- Social impact: craft-preservation, worker-safety, local-jobs

## Customer

- stonemasonry workshops
- independent stone cutting/splitting/carving craftspeople
- construction and restoration contractors sourcing stonework

## Offer

- crew/task scheduling coordination
- work-record and materials-usage logging
- stone-materials supply-order coordination
- safety-concern surfacing to the workshop safety officer

## Revenue

- monthly workshop subscription
- per-scheduled-operation fee

## Trust Controls

- no crew operation scheduled beyond a craftsperson's registered daily
  task-hour ceiling without independent governor gate
- confirmed task order required before any crew operation is scheduled
- safety concerns always escalate to a human (the workshop safety
  officer) regardless of advisor confidence, and are never
  auto-commit-eligible
- supply orders above the workshop's registered cost threshold always
  escalate to human sign-off
- the actor never finalizes a stone-cutting/carving-execution decision
  and never overrides the workshop safety officer's judgment — a HARD,
  permanent block, structurally absent from the op-allowlist and
  independently rejected by a content-based scope-exclusion check
- scheduling, work-record and procurement records are auditable, not
  editable
