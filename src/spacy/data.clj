(ns spacy.data)

(defprotocol Events
  (fetch [this slug])
  (all-slugs [this])
  (persist! [this outcome]))
