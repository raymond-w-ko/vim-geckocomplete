(ns geckocomplete.complete
  (:import
   [geckocomplete Algorithm])
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

(defn is-negative-score? [{:keys [score]}]
  (< score 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn is-subsequence? [haystack needle]
  (cond-xlet
   :let [n-hay (count haystack)
         n-nee (count needle)]
   (< n-hay n-nee) false
   :let [index (loop [i 0 j 0]
                 (cond-xlet
                  (<= n-hay i) j
                  :let [hay-ch (-> (get haystack i) (Character/toLowerCase))]
                  (<= n-nee j) j
                  :let [nee-ch (-> (get needle j) (Character/toLowerCase))]
                  (= hay-ch nee-ch) (recur (inc i) (inc j))
                  :return (recur (inc i) j)))]
   :return (= index n-nee)))

(deftest is-subsequence?-test
  (is (false? (is-subsequence? "a" "abc")))
  (is (true? (is-subsequence? "abc" "abc")))
  (is (true? (is-subsequence? "StartRemoteServer" "srs")))
  (is (true? (is-subsequence? "StartRemoteServer" "temver")))
  (is (true? (is-subsequence? "StartRemoteServer" "StartRemoteServer")))
  (is (false? (is-subsequence? "StartRemoteServer" "StartRemoteServerb")))
  (is (false? (is-subsequence? "StartRemoteServer" "temverb")))
  (is (false? (is-subsequence? "StartRemoteServer" "srb")))
  nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn compute-clumping-score [haystack needle]
  (cond-xlet
   (<= (count needle) 2) 0.0
   :let []))

(comment
  (Algorithm/foo)
  (compute-clumping-score "compute-clumping-score" "comcc")
  (compute-clumping-score "compute-clumping-score" "ccscore"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn compute-score [needle {:as m :keys [word word-boundary score]}]
  (let [subsequence-score (if (is-subsequence? word needle)
                            (/ (count needle) (count word))
                            0.0)
        wb-score (if (is-subsequence? word-boundary needle)
                   (/ (count needle) (count word-boundary))
                   0.0)
        clumping-score 0.0]
    (comment (compute-clumping-score word needle))
    (assoc m :score (+ subsequence-score wb-score clumping-score))))

(defn score-word-using-needle [needle]
  (comp (map format-word)
        (map compute-word-boundary)
        (map (partial compute-score needle))
        (remove is-negative-score?)))
