(ns langchain.persist
  "Zero-dependency persistence composition for langchain.db.

  The durable host is an ordinary operation map:
    {:append (fn [stream event] stamped-event)
     :read   (fn [stream since] events)}

  This namespace has no database or storage-model dependency."
  )

(defn scoped
  "Bind a host event-log operation map to one stream and return the
  {:append :read} persistence value consumed by langchain.db/create-conn."
  [{:keys [append read]} stream]
  (when-not (and (fn? append) (fn? read))
    (throw (ex-info "langchain persistence host requires :append and :read functions"
                    {:stream stream})))
  {:append (fn [event] (append stream event))
   :read (fn [since] (read stream since))})
