(ns stonemasonry.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [stonemasonry.actor :as actor]
            [stonemasonry.advisor :as advisor]
            [stonemasonry.governor :as governor]
            [stonemasonry.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-workshop! st {:workshop-id "workshop-1" :name "Kobo Stoneworks"
                                   :verified? true :max-supply-order-cost 5000})
    (store/register-craftsperson! st {:craftsperson-id "C-1" :workshop-id "workshop-1"
                                       :name "artisan-042" :verified? true :max-daily-task-hours 10})
    st))

(deftest commits-a-within-ceiling-confirmed-schedule
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:workshop-id "workshop-1" :op :schedule-crew-operation :stake :low
                  :craftsperson-id "C-1" :scheduled-hours 6 :task-order-confirmed? true}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "workshop-1"))))))

(deftest holds-an-over-ceiling-schedule
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:workshop-id "workshop-1" :op :schedule-crew-operation :stake :low
                  :craftsperson-id "C-1" :scheduled-hours 20 :task-order-confirmed? true}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "workshop-1")))))

(deftest commits-a-work-record-log
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:workshop-id "workshop-1" :op :log-work-record :stake :low
                  :craftsperson-id "C-1" :task-id "T-1" :materials-used "limestone block"
                  :progress-notes "roughed out, awaiting finishing pass"}
        result (actor/run-request! graph request {} "thread-3")]
    (is (= :done (:status result)))
    (is (= 1 (count (store/records-of st "workshop-1"))))))

(deftest commits-a-within-threshold-supply-order
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:workshop-id "workshop-1" :op :coordinate-supply-order :stake :low
                  :materials "granite blocks" :estimated-cost 1200}
        result (actor/run-request! graph request {} "thread-4")]
    (is (= :done (:status result)))
    (is (= 1 (count (store/records-of st "workshop-1"))))))

(deftest interrupts-then-approves-safety-concern-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:workshop-id "workshop-1" :op :flag-safety-concern :stake :low
                  :craftsperson-id "C-1" :concern-type :silica-dust-exposure
                  :description "elevated silica dust levels observed near the cutting station"}
        interrupted (actor/run-request! graph request {} "thread-5")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "workshop-1")))
    (let [resumed (actor/approve! graph "thread-5")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "workshop-1")))))))

(deftest interrupts-then-approves-over-threshold-supply-order-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:workshop-id "workshop-1" :op :coordinate-supply-order :stake :low
                  :materials "marble slabs" :estimated-cost 9000}
        interrupted (actor/run-request! graph request {} "thread-6")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "workshop-1")))
    (let [resumed (actor/approve! graph "thread-6")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "workshop-1")))))))

;; Rogue-advisor end-to-end: proves the compiled StateGraph itself
;; always resolves to :hold with zero records committed even when the
;; advisor node itself is compromised — the governor, not the advisor,
;; is what enforces safety.
(defn- rogue-advisor [fixed-proposal]
  (reify advisor/Advisor
    (-advise [_ _store _request] fixed-proposal)))

(deftest holds-every-scope-excluded-op-attempt-even-via-a-rogue-advisor
  (doseq [op governor/scope-excluded-ops]
    (let [st (fresh-store)
          graph (actor/build-graph {:store st
                                     :advisor (rogue-advisor
                                               {:op op :effect :propose
                                                :workshop-id "workshop-1"
                                                :confidence 0.99 :stake :low
                                                :rationale "rogue"})})
          request {:workshop-id "workshop-1" :op op}
          result (actor/run-request! graph request {} (str "rogue-op-" (name op)))]
      (is (= :hold (:disposition (:state result))) (str op " must hold"))
      (is (empty? (store/records-of st "workshop-1")) (str op " must not commit any record")))))

(deftest holds-a-scope-excluded-rationale-attempt-even-via-a-rogue-advisor
  (let [st (fresh-store)
        graph (actor/build-graph
               {:store st
                :advisor (rogue-advisor
                          {:op :log-work-record :effect :propose :workshop-id "workshop-1"
                           :craftsperson-id "C-1" :confidence 0.99 :stake :low
                           :rationale "proceed with the stone-cutting work regardless of the hold"})})
        request {:workshop-id "workshop-1" :op :log-work-record :craftsperson-id "C-1"}
        result (actor/run-request! graph request {} "thread-rogue-rationale")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "workshop-1")))))

(deftest holds-a-work-record-decision-attempt-even-via-a-rogue-advisor
  (let [st (fresh-store)
        graph (actor/build-graph
               {:store st
                :advisor (rogue-advisor
                          {:op :log-work-record :effect :propose :workshop-id "workshop-1"
                           :craftsperson-id "C-1" :confidence 0.99 :stake :low
                           :rationale "logging" :execution-decision :cut-now})})
        request {:workshop-id "workshop-1" :op :log-work-record :craftsperson-id "C-1"}
        result (actor/run-request! graph request {} "thread-rogue-log-decision")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "workshop-1")))))

(deftest holds-a-schedule-override-attempt-even-via-a-rogue-advisor
  (let [st (fresh-store)
        graph (actor/build-graph
               {:store st
                :advisor (rogue-advisor
                          {:op :schedule-crew-operation :effect :propose :workshop-id "workshop-1"
                           :craftsperson-id "C-1" :scheduled-hours 6 :task-order-confirmed? true
                           :confidence 0.99 :stake :low :rationale "scheduling"
                           :safety-officer-override true})})
        request {:workshop-id "workshop-1" :op :schedule-crew-operation :craftsperson-id "C-1"}
        result (actor/run-request! graph request {} "thread-rogue-schedule-override")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "workshop-1")))))
