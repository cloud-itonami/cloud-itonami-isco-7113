(ns stonemasonry.advisor
  "Workshop Coordination Advisor — proposes a workshop-scheduling/
  logistics-coordination operation (log a work record, schedule a
  crew operation, flag a safety concern, coordinate a stone-materials
  supply order) from a crew roster, confirmed task orders and a
  materials plan. Swappable mock/llm; the advisor ONLY proposes —
  `stonemasonry.governor` independently checks craftsperson/workshop
  provenance, the daily task-hour ceiling, task-order confirmation and
  the supply-order cost threshold, and always escalates safety
  concerns and over-threshold supply orders. The advisor NEVER emits
  an op outside the closed four-op allowlist, and never proposes to
  finalize a stone-cutting/carving-execution decision or override a
  workshop safety officer's judgment — this actor coordinates
  workshop SCHEDULING/LOGISTICS ONLY, it never performs stonework
  itself. Modeled on cloud-itonami-isco-3313's
  accountingsupport.advisor.

  A proposal: {:op :log-work-record|:schedule-crew-operation|
               :flag-safety-concern|:coordinate-supply-order
               :effect :propose :workshop-id str
               :craftsperson-id str|nil ... :stake kw :confidence n
               :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- confidence-for [stake]
  (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95))

(defn- infer [_store {:keys [op stake workshop-id craftsperson-id] :as request}]
  (let [base {:op op :effect :propose :workshop-id workshop-id
              :craftsperson-id craftsperson-id
              :stake (or stake :low)
              :confidence (confidence-for stake)}]
    (case op
      :log-work-record
      (merge base
             {:task-id (:task-id request)
              :materials-used (:materials-used request)
              :progress-notes (:progress-notes request)
              :rationale (str "logging work record for task " (:task-id request)
                              " by craftsperson " craftsperson-id " at workshop " workshop-id)})

      :schedule-crew-operation
      (merge base
             {:scheduled-hours (:scheduled-hours request)
              :task-order-confirmed? (boolean (:task-order-confirmed? request))
              :rationale (str "scheduling crew operation for craftsperson " craftsperson-id
                              " at workshop " workshop-id)})

      :flag-safety-concern
      (merge base
             {:concern-type (:concern-type request)
              :description (:description request)
              :rationale (str "flagging a safety concern for review at workshop " workshop-id)})

      :coordinate-supply-order
      (merge base
             {:materials (:materials request)
              :estimated-cost (:estimated-cost request)
              :rationale (str "coordinating a stone-materials supply order for workshop " workshop-id)})

      ;; unknown/out-of-allowlist op: passed through unchanged so the
      ;; governor's closed-allowlist HARD check (`:op-not-allowed`)
      ;; rejects it — the mock advisor never fabricates a safer op in
      ;; its place.
      (merge base {:rationale (str "proposed " (name op) " at workshop " workshop-id)}))))

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a stonemasonry workshop coordination advisor. Given a
   request, propose an :op from the closed four-op allowlist
   (:log-work-record, :schedule-crew-operation, :flag-safety-concern,
   :coordinate-supply-order), the workshop/craftsperson basis, an
   honest :confidence and a :stake. Never propose a scheduled
   duration beyond the craftsperson's registered daily task-hour
   ceiling, a crew operation without a confirmed task order, or any
   op that finalizes a stone-cutting/carving-execution decision or
   overrides the workshop safety officer's judgment — the governor
   independently checks all of these. Safety concerns and
   over-threshold supply orders always require human sign-off
   regardless of confidence. This actor coordinates workshop
   scheduling and logistics ONLY — it never performs stonework
   itself.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
