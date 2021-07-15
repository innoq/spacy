(ns spacy.data)

(defprotocol Events
  (fetch [this slug])
  (all-slugs [this])
  (update! [this slug f]
    "Applies f to an event with a given slug and saves it.
    Might call f multiple times. Throws on failure."))
