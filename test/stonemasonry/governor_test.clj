(ns stonemasonry.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [stonemasonry.store :as store]
            [stonemasonry.advisor :as advisor]
            [stonemasonry.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-workshop! st {:workshop-id "workshop-1" :name "Kobo Stoneworks"
                                   :verified? true :max-supply-order-cost 5000})
    (store/register-craftsperson! st {:craftsperson-id "C-1" :workshop-id "workshop-1"
                                       :name "artisan-042" :verified? true
                                       :max-daily-task-hours 10})
    st))

(defn- schedule-op [hours confirmed?]
  {:op :schedule-crew-operation :effect :propose :workshop-id "workshop-1"
   :craftsperson-id "C-1" :scheduled-hours hours :task-order-confirmed? confirmed?
   :confidence 0.9 :stake :low})

(def ^:private req {:workshop-id "workshop-1"})

(deftest ok-within-ceiling-and-confirmed
  (let [st (fresh-store)
        v (governor/check req {} (schedule-op 6 true) st)]
    (is (:ok? v))))

(deftest ok-at-exact-ceiling-boundary
  (testing "the daily task-hour ceiling is inclusive"
    (let [st (fresh-store)
          v (governor/check req {} (schedule-op 10 true) st)]
      (is (:ok? v)))))

(deftest hard-on-scheduled-hours-exceeds-ceiling
  (testing "over-scheduling a craftsperson beyond the registered ceiling is an overwork/fatigue safety risk, not routine dispatch"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (schedule-op 20 true) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scheduled-hours-exceeds-ceiling (:rule %)) (:violations v))))))

(deftest hard-on-missing-task-order-confirmation
  (testing "scheduling an unconfirmed task is an invented operation, not routine dispatch"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (schedule-op 6 false) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :missing-task-order-confirmation (:rule %)) (:violations v))))))

(deftest hard-on-unknown-craftsperson
  (let [st (fresh-store)
        v (governor/check req {} (assoc (schedule-op 6 true) :craftsperson-id "ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-craftsperson (:rule %)) (:violations v)))))

(deftest hard-on-unverified-craftsperson
  (let [st (fresh-store)]
    (store/register-craftsperson! st {:craftsperson-id "C-unverified" :workshop-id "workshop-1"
                                       :name "trainee" :verified? false :max-daily-task-hours 10})
    (let [v (governor/check req {} (assoc (schedule-op 6 true) :craftsperson-id "C-unverified") st)]
      (is (:hard? v))
      (is (some #(= :craftsperson-unverified (:rule %)) (:violations v))))))

(deftest hard-on-craftsperson-wrong-workshop
  (let [st (fresh-store)]
    (store/register-workshop! st {:workshop-id "workshop-2" :name "Other Yard"
                                   :verified? true :max-supply-order-cost 1000})
    (store/register-craftsperson! st {:craftsperson-id "C-2" :workshop-id "workshop-2"
                                       :name "other-artisan" :verified? true :max-daily-task-hours 8})
    (let [v (governor/check req {} (assoc (schedule-op 6 true) :craftsperson-id "C-2") st)]
      (is (:hard? v))
      (is (some #(= :craftsperson-wrong-workshop (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-workshop
  (let [st (fresh-store)
        v (governor/check {:workshop-id "nobody"} {} (schedule-op 6 true) st)]
    (is (:hard? v))
    (is (some #(= :unknown-workshop (:rule %)) (:violations v)))))

(deftest hard-on-unverified-workshop
  (let [st (fresh-store)]
    (store/register-workshop! st {:workshop-id "workshop-unverified" :name "New Yard"
                                   :verified? false :max-supply-order-cost 1000})
    (let [v (governor/check {:workshop-id "workshop-unverified"} {}
                             (assoc (schedule-op 6 true) :workshop-id "workshop-unverified") st)]
      (is (:hard? v))
      (is (some #(= :workshop-unverified (:rule %)) (:violations v))))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (schedule-op 6 true) :effect :direct-execute) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-unknown-op
  (testing "closed op-allowlist enforced"
    (let [st (fresh-store)
          v (governor/check req {}
                             {:op :finalize-stone-cutting-execution :effect :propose
                              :workshop-id "workshop-1" :confidence 0.99 :stake :low
                              :rationale "test"} st)]
      (is (:hard? v))
      (is (some #(= :op-not-allowed (:rule %)) (:violations v))))))

(deftest every-scope-excluded-op-name-is-rejected
  (testing "every representative forbidden op is a HARD, permanent block via the closed allowlist"
    (let [st (fresh-store)]
      (doseq [op governor/scope-excluded-ops]
        (let [v (governor/check req {}
                                 {:op op :effect :propose :workshop-id "workshop-1"
                                  :confidence 0.99 :stake :low :rationale "test"} st)]
          (is (:hard? v) (str op " should be hard-blocked"))
          (is (some #(= :op-not-allowed (:rule %)) (:violations v)) (str op " should trip :op-not-allowed")))))))

(deftest hard-on-execution-finalization-scope-violation
  (let [st (fresh-store)
        v (governor/check req {}
                           {:op :log-work-record :effect :propose :workshop-id "workshop-1"
                            :craftsperson-id "C-1" :confidence 0.99 :stake :low
                            :rationale "proceed with the stone-cutting work despite the flagged concern"} st)]
    (is (:hard? v))
    (is (some #(= :scope-excluded (:rule %)) (:violations v)))))

(deftest hard-on-safety-officer-override-scope-violation
  (let [st (fresh-store)
        v (governor/check req {}
                           {:op :schedule-crew-operation :effect :propose :workshop-id "workshop-1"
                            :craftsperson-id "C-1" :scheduled-hours 6 :task-order-confirmed? true
                            :confidence 0.99 :stake :low
                            :rationale "override the workshop safety officer's judgment and proceed"} st)]
    (is (:hard? v))
    (is (some #(= :scope-excluded (:rule %)) (:violations v)))))

(deftest hard-on-work-record-decision-forbidden-key
  (let [st (fresh-store)
        v (governor/check req {}
                           {:op :log-work-record :effect :propose :workshop-id "workshop-1"
                            :craftsperson-id "C-1" :confidence 0.99 :stake :low
                            :rationale "logging" :execution-decision :cut-now} st)]
    (is (:hard? v))
    (is (some #(= :work-record-decision-forbidden (:rule %)) (:violations v)))))

(deftest hard-on-schedule-override-forbidden-key
  (let [st (fresh-store)
        v (governor/check req {}
                           {:op :schedule-crew-operation :effect :propose :workshop-id "workshop-1"
                            :craftsperson-id "C-1" :scheduled-hours 6 :task-order-confirmed? true
                            :confidence 0.99 :stake :low :rationale "scheduling"
                            :safety-officer-override true} st)]
    (is (:hard? v))
    (is (some #(= :schedule-override-forbidden (:rule %)) (:violations v)))))

(deftest always-escalates-flag-safety-concern-even-at-high-confidence
  (testing "the only channel by which the robot may surface a dust-exposure/blade-hazard/material-handling concern; it never resolves it itself"
    (let [st (fresh-store)
          v (governor/check req {}
                             {:op :flag-safety-concern :effect :propose :workshop-id "workshop-1"
                              :craftsperson-id "C-1" :concern-type :silica-dust-exposure
                              :confidence 0.99 :stake :low :rationale "flagging a safety concern for review"} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-supply-order-above-cost-threshold
  (let [st (fresh-store)
        v (governor/check req {}
                           {:op :coordinate-supply-order :effect :propose :workshop-id "workshop-1"
                            :materials "granite blocks" :estimated-cost 9000
                            :confidence 0.99 :stake :low :rationale "coordinating a supply order"} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest ok-supply-order-at-or-below-cost-threshold
  (testing "the supply-order cost threshold is inclusive"
    (let [st (fresh-store)
          v (governor/check req {}
                             {:op :coordinate-supply-order :effect :propose :workshop-id "workshop-1"
                              :materials "granite blocks" :estimated-cost 5000
                              :confidence 0.9 :stake :low :rationale "coordinating a supply order"} st)]
      (is (:ok? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (schedule-op 6 true) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "the governor's scope-exclusion phrase check must never match the mock advisor's own default rationale text for any allowed op"
    (let [st (fresh-store)
          adv (advisor/mock-advisor)
          requests [{:workshop-id "workshop-1" :op :log-work-record :craftsperson-id "C-1"
                     :task-id "T-1" :materials-used "granite slab" :progress-notes "cut to spec, polished"}
                    {:workshop-id "workshop-1" :op :schedule-crew-operation :craftsperson-id "C-1"
                     :scheduled-hours 6 :task-order-confirmed? true}
                    {:workshop-id "workshop-1" :op :flag-safety-concern :craftsperson-id "C-1"
                     :concern-type :silica-dust-exposure
                     :description "elevated silica dust levels observed near the cutting station"}
                    {:workshop-id "workshop-1" :op :coordinate-supply-order
                     :materials "granite blocks" :estimated-cost 1200}]]
      (doseq [request requests]
        (let [proposal (advisor/-advise adv st request)
              verdict (governor/check request {} proposal st)]
          (is (not (some #(= :scope-excluded (:rule %)) (:violations verdict)))
              (str "self-tripped scope-excluded on " (:op request) ": " (:violations verdict)))
          (is (not (some #(= :op-not-allowed (:rule %)) (:violations verdict)))
              (str "self-tripped op-not-allowed on " (:op request) ": " (:violations verdict))))))))
