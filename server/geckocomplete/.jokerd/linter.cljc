(ns geckocomplete.macros)

(defmacro ->hash [& vars]
  (list `zipmap
    (mapv keyword vars)
    (vec vars)))

(defmacro cond-xlet
  "An alternative to `clojure.core/cond` where instead of a test/expression pair,
  it is possible to also have:
  :do form  - unconditionally execute `form`, useful for printf style debugging or logging
  :let []   - standard let binding vector pair
  
  Try to use :let if you know that a function call result is synchronous."
  [& clauses]
  (cond (empty? clauses)
        nil

        (not (even? (count clauses)))
        (throw (ex-info (str `cond-xlet " requires an even number of forms")
                        {:form &form
                         :meta (meta &form)}))

        :else
        (let [[test expr-or-binding-form & more-clauses] clauses]
          (cond
            (= :let test) `(let ~expr-or-binding-form (cond-xlet ~@more-clauses))
            (= :do test) `(when true ~expr-or-binding-form (cond-xlet ~@more-clauses))
           ;; standard case
            :else `(if ~test
                     ~expr-or-binding-form
                     (cond-xlet ~@more-clauses))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(in-ns 'user)
