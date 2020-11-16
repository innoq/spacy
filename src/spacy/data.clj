(ns spacy.data)

(defprotocol Events
  (fetch [this slug])
  (persist! [this outcome]))
