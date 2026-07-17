(ns stonemasonry.store
  "SSoT for the ISCO-08 7113 stonemasonry workshop coordination actor
  (itonami actor pattern, ADR-2607121000 / CLAUDE.md Actors section;
  README's 'Robotics premise' — a workshop scheduling/logistics
  coordination robot manages crew scheduling, work-record logging and
  stone-materials procurement proposals under this advisor/governor
  pair, which never performs stonework itself, never dispatches
  hardware, and never finalizes a stone-cutting/carving-execution
  decision or overrides a workshop safety officer's judgment).
  Modeled on cloud-itonami-isco-3313's accountingsupport.store.

  Domain:

    workshop      — a registered AND independently verified
                    stonemasonry workshop {:workshop-id :name
                    :verified? bool :max-supply-order-cost number}.
                    `:max-supply-order-cost` is the registered
                    threshold above which a proposed stone-materials
                    supply-order cost always escalates to human
                    sign-off.
    craftsperson  — a registered AND independently verified
                    stonemason/stone cutter/splitter/carver
                    {:craftsperson-id :workshop-id :name :verified?
                    bool :max-daily-task-hours number}.
                    `:max-daily-task-hours` is the registered ceiling
                    a proposed crew-scheduling duration must not
                    exceed — scheduling a craftsperson beyond the
                    registered ceiling is unauthorized scheduling, an
                    overwork/fatigue safety risk, not routine
                    dispatch.
    record        — a committed operating record (a logged work
                    record, a scheduled crew operation, a flagged
                    safety concern, or a coordinated supply order) —
                    written ONLY via commit-record!.
    ledger        — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (workshop [s workshop-id])
  (craftsperson [s craftsperson-id])
  (records-of [s workshop-id])
  (ledger [s])
  (register-workshop! [s w])
  (register-craftsperson! [s c])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (workshop [_ workshop-id] (get-in @a [:workshops workshop-id]))
  (craftsperson [_ craftsperson-id] (get-in @a [:craftspeople craftsperson-id]))
  (records-of [_ workshop-id] (filter #(= workshop-id (:workshop-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-workshop! [s w]
    (swap! a assoc-in [:workshops (:workshop-id w)] w) s)
  (register-craftsperson! [s c]
    (swap! a assoc-in [:craftspeople (:craftsperson-id c)] c) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:workshops {} :craftspeople {} :records [] :ledger []}
                                   seed)))))
