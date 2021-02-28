(ns geckocomplete.complete
  (:require
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is]]
   [clojure.java.io :as io]
   [clojure.core.async :refer [<! >! <!! >!! to-chan!]]
   [clojure.data.json :as json]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report
            logf tracef debugf infof warnf errorf fatalf reportf
            spy get-env]]
   
   [geckocomplete.macros :refer [->hash cond-xlet]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn is-min-length? [word]
  (< 3 (count word)))

(defn format-word [word]
  {:word word :score -1.0})

(defn compute-word-boundary [{:as m :keys [word]}]
  (cond-xlet
   (<= (count word) 3) (assoc m :word-boundary "")

   :let [n (dec (count word))
         boundary
         (loop [i 1
                boundary [(first word)]]
           (cond-xlet
            (>= i n) boundary
            :let [c1 (get word i)
                  c2 (get word (inc i))]

            ;; snake case or needle case
            (or (= \_ c1) (= \- c1))
            (recur (+ i 2) (conj boundary c2))

            ;; camelCase
            (and (Character/isLowerCase c1) (Character/isUpperCase c2))
            (recur (+ i 2) (conj boundary c2))

            :return (recur (inc i) boundary)))]
   :let [boundary (if (= 1 (count boundary))
                    ""
                    boundary)]
   :return (->> (apply str boundary)
                (str/lower-case)
                (assoc m :word-boundary))))

(deftest word-boundary-test
  (letfn [(f [word]
            (-> {:word word}
                (compute-word-boundary)
                :word-boundary))]
    (is (= "" (f "foo")))
    (is (= "" (f "food")))
    (is (= "sc" (f "snake_case")))
    (is (= "nc" (f "needle-case")))
    (is (= "lc" (f "lisp-case")))
    (is (= "cc" (f "camelCase")))
    (is (= "osw" (f "OutputStreamWriter")))
    nil))

(defn check-word-boundary-score [needle {:as m :keys [word-boundary score]}]
  (let [wb-score (and (not= word-boundary "")
                      (= needle word-boundary)
                      1.0)]
    (debug wb-score)
    (if-not wb-score
      m
      (assoc m :score (max score wb-score)))))

(defn score-word-using-needle [needle]
  (comp (map format-word)
        (map compute-word-boundary)
        (map (partial check-word-boundary-score needle))))
