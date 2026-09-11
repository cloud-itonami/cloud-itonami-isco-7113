(ns stonemasonry.governor
  "StonemasonryGovernor — the independent safety/traceability layer
  gating every workshop-scheduling/logistics operation the Workshop
  Coordination Advisor may propose. The governor never dispatches
  hardware itself, never performs stonework, and never finalizes a
  stone-cutting/carving-execution decision or overrides a workshop
  safety officer's judgment — those are HARD, PERMANENT blocks,
  structurally excluded from the closed op-allowlist AND independently
  rejected via a content-based scope-exclusion check over free text
  (defense in depth). This actor coordinates WORKSHOP SCHEDULING/
  LOGISTICS ONLY. Modeled on cloud-itonami-isco-3313's
  accountingsupport.governor.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. workshop provenance      — the workshop must be independently
                                  registered AND verified.
    2. no-actuation             — proposal :effect must be :propose
                                  (the governor never dispatches
                                  hardware and never performs
                                  stonework; it only gates what the
                                  advisor may propose).
    3. closed op-allowlist      — any :op outside the four allowed
                                  ops is a HARD, PERMANENT block.
    4. scope exclusion          — any proposal whose free text
                                  (`:rationale`/`:description`/`:note`)
                                  names a finalization/execution
                                  ACTION for a stone-cutting/carving-
                                  execution decision, or an override
                                  of the workshop safety officer's
                                  judgment, is a HARD, PERMANENT
                                  block, evaluated unconditionally on
                                  content — independent of the closed
                                  allowlist check, so a nominally
                                  allowed op cannot smuggle forbidden
                                  intent through its rationale text.
    5. craftsperson provenance  — `:log-work-record` and
                                  `:schedule-crew-operation` must cite
                                  an independently registered AND
                                  verified craftsperson belonging to
                                  this workshop.
    6. daily task-hour ceiling  — a `:schedule-crew-operation`'s
                                  proposed scheduled hours must not
                                  exceed the craftsperson's registered
                                  `:max-daily-task-hours` (over-
                                  scheduling beyond the registered
                                  ceiling is unauthorized scheduling,
                                  an overwork/fatigue safety risk, not
                                  routine dispatch).
    7. task-order confirmation  — a `:schedule-crew-operation` must
                                  have a confirmed task order before
                                  any crew operation can be scheduled
                                  (scheduling an unconfirmed task is
                                  an invented operation, not routine
                                  dispatch).
    8. work-record content      — `:log-work-record` may only carry
                                  task/materials-usage/progress
                                  metadata; any proposal carrying an
                                  execution-decision-shaped key is a
                                  HARD, PERMANENT block.
    9. schedule content         — `:schedule-crew-operation` may only
                                  carry advance scheduling logistics;
                                  any proposal carrying an execution-
                                  override-shaped key is a HARD,
                                  PERMANENT block.

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off
  regardless of confidence):
    10. `:op :flag-safety-concern` — ALWAYS escalates, NEVER
                                  auto-commit-eligible. The only
                                  channel by which the robot may
                                  surface a dust-exposure/blade-
                                  hazard/material-handling concern; it
                                  never resolves the concern itself.
    11. `:coordinate-supply-order` whose `:estimated-cost` exceeds
                                  the workshop's registered
                                  `:max-supply-order-cost`.
    12. low confidence (< `confidence-floor`)."
  (:require [kotoba.lang.text :as str]
            [stonemasonry.store :as store]))

(def confidence-floor 0.6)

;; The closed proposal-op allowlist. This actor coordinates workshop
;; scheduling/logistics ONLY — no op anywhere in this allowlist
;; resembles a stone-cutting/carving-execution decision or a workshop
;; safety officer judgment override; those are structurally absent.
(def closed-op-allowlist
  #{:log-work-record :schedule-crew-operation
    :flag-safety-concern :coordinate-supply-order})

(def ^:private craftsperson-required-ops #{:log-work-record :schedule-crew-operation})

;; :flag-safety-concern is the only op that always requires human
;; sign-off regardless of confidence — it is a pure "surface for
;; review" channel and is NEVER auto-commit-eligible.
(def ^:private always-escalate-ops #{:flag-safety-concern})

;; Representative op names that must NEVER be proposable — structurally
;; absent from `closed-op-allowlist`, and independently rejected via
;; `:op-not-allowed` even if a rogue/compromised advisor emits one
;; directly (see `stonemasonry.actor-test` rogue-advisor end-to-end
;; coverage). This actor coordinates WORKSHOP SCHEDULING/LOGISTICS
;; ONLY — it never finalizes a stone-cutting/carving-execution
;; decision and never overrides a workshop safety officer's judgment.
(def scope-excluded-ops
  #{:finalize-stone-cutting-execution :finalize-carving-execution
    :commit-to-the-cut :proceed-with-the-cut
    :execute-stone-cutting-operation :execute-carving-operation
    :override-safety-officer-judgment
    :override-workshop-safety-officer-decision
    :authorize-unsafe-operation :bypass-safety-hold})

;; Phrased exclusively as finalization/execution ACTIONS, never bare
;; nouns — fleet-known self-trip bug class: a bare noun term (e.g.
;; "cutting", "safety") would accidentally match inside this actor's
;; own legitimate mock-advisor default rationale text (e.g.
;; :flag-safety-concern's "flagging a safety concern for review", or
;; :log-work-record's rationale naming a cutting task). See
;; `stonemasonry.governor-test/default-mock-advisor-proposals-never-
;; self-trip-scope-exclusion`.
(def scope-excluded-terms
  ["proceed with the stone-cutting work" "proceeded with the stone-cutting work"
   "proceed with the carving work" "proceeded with the carving work"
   "finalize the stone-cutting execution" "finalized the stone-cutting execution"
   "finalize the carving execution" "finalized the carving execution"
   "commit to the cut" "committed to the cut"
   "execute the stone-cutting operation" "executed the stone-cutting operation"
   "execute the carving operation" "executed the carving operation"
   "override the workshop safety officer's judgment" "overrode the workshop safety officer's judgment"
   "override the safety officer's judgment" "overrode the safety officer's judgment"
   "bypass the safety hold" "bypassed the safety hold"
   "石材切断作業を実行した" "彫刻作業を実行した" "切断作業を確定した"
   "workshop 安全責任者の判断を上書きした" "安全責任者の判断を上書きした" "安全ホールドを回避した"])

;; `:log-work-record` is task/materials-usage/progress metadata ONLY —
;; any of these keys smuggled onto the proposal is a HARD, PERMANENT
;; block (defense in depth on top of the op-allowlist itself).
(def log-record-forbidden-keys
  #{:execution-decision :cut-finalization-decision :carving-finalization-decision
    :safety-override-decision})

;; `:schedule-crew-operation` is advance scheduling logistics ONLY —
;; any of these keys smuggled onto the proposal is a HARD, PERMANENT
;; block (defense in depth on top of the op-allowlist itself).
(def schedule-forbidden-keys
  #{:execution-override :safety-officer-override :cut-authorization :carving-authorization})

(defn- text-blob [proposal]
  (str/lower (str (:rationale proposal) " " (:description proposal) " " (:note proposal))))

(defn- scope-excluded? [proposal]
  (let [blob (text-blob proposal)]
    (boolean (some #(str/includes? blob (str/lower %)) scope-excluded-terms))))

(defn- forbidden-key-violation [proposal forbidden-keys]
  (some forbidden-keys (keys proposal)))

(defn- hard-violations [{:keys [request proposal]} workshop-record craftsperson-record]
  (let [{:keys [op craftsperson-id scheduled-hours task-order-confirmed?]} proposal
        needs-craftsperson? (contains? craftsperson-required-ops op)
        craftsperson-cited? (some? craftsperson-id)
        schedule? (= :schedule-crew-operation op)
        log-record? (= :log-work-record op)]
    (cond-> []
      (nil? workshop-record)
      (conj {:rule :unknown-workshop :detail "未登録 workshop"})

      (and workshop-record (not (:verified? workshop-record)))
      (conj {:rule :workshop-unverified :detail "workshop が独立検証済みでない"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation
             :detail "effect は :propose のみ許可（governor は石材の切断・彫刻作業の実行や workshop 安全責任者の判断を直接実行・上書きしない）"})

      (not (contains? closed-op-allowlist op))
      (conj {:rule :op-not-allowed :detail (str "許可されていない op: " op "（closed allowlist）")})

      (scope-excluded? proposal)
      (conj {:rule :scope-excluded
             :detail "提案文言が石材の切断・彫刻作業の実行確定、または workshop 安全責任者の判断の上書きを示唆している — 常時ハード・恒久ブロック"})

      (and needs-craftsperson? (not craftsperson-cited?))
      (conj {:rule :missing-craftsperson-id :detail "craftsperson-id が指定されていない"})

      (and craftsperson-cited? (nil? craftsperson-record))
      (conj {:rule :unknown-craftsperson :detail "未登録 craftsperson への提案は不可"})

      (and craftsperson-cited? craftsperson-record (not (:verified? craftsperson-record)))
      (conj {:rule :craftsperson-unverified :detail "craftsperson が独立検証済みでない"})

      (and craftsperson-cited? craftsperson-record
           (not= (:workshop-id craftsperson-record) (:workshop-id request)))
      (conj {:rule :craftsperson-wrong-workshop :detail "craftsperson が別 workshop に登録されている"})

      (and schedule? craftsperson-record (number? scheduled-hours)
           (> scheduled-hours (:max-daily-task-hours craftsperson-record)))
      (conj {:rule :scheduled-hours-exceeds-ceiling
             :detail (str "予定時間 " scheduled-hours " > 登録済み上限 " (:max-daily-task-hours craftsperson-record)
                          "（登録上限を超える予定は無許可スケジューリングであって通常業務ではない — 過労/安全リスク）")})

      (and schedule? (not task-order-confirmed?))
      (conj {:rule :missing-task-order-confirmation
             :detail "task order が確認されていないスケジューリング提案は架空タスクであって効率的運用ではない"})

      (and log-record? (forbidden-key-violation proposal log-record-forbidden-keys))
      (conj {:rule :work-record-decision-forbidden
             :detail "work record は task/materials-usage/progress メタデータのみ許可 — 実行確定フィールドの混入はハード・恒久ブロック"})

      (and schedule? (forbidden-key-violation proposal schedule-forbidden-keys))
      (conj {:rule :schedule-override-forbidden
             :detail "crew scheduling は事前ロジスティクスのみ許可 — 実行上書きフィールドの混入はハード・恒久ブロック"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `stonemasonry.store/Store`. Pure — never
  mutates the store, never performs stonework, never dispatches
  hardware."
  [request context proposal store]
  (let [workshop-record (store/workshop store (:workshop-id request))
        craftsperson-record (some->> (:craftsperson-id proposal) (store/craftsperson store))
        hard (hard-violations {:request request :proposal proposal} workshop-record craftsperson-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))
        over-threshold? (and (= :coordinate-supply-order (:op proposal))
                              (number? (:estimated-cost proposal))
                              workshop-record
                              (> (:estimated-cost proposal) (:max-supply-order-cost workshop-record)))]
    {:ok? (and (not hard?) (not low?) (not always-risky?) (not over-threshold?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky? over-threshold?))}))
