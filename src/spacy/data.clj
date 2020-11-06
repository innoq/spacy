(ns spacy.data)

(defprotocol Events
  (fetch [this event-id])
  (persist! [this event-id state]))
