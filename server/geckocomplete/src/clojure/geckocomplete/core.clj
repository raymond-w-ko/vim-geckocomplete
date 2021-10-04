(ns geckocomplete.core
  (:import
   [java.io File InputStreamReader BufferedReader OutputStreamWriter]
   [java.net Socket]
   [java.nio ByteBuffer]
   [java.nio.charset StandardCharsets]
   [sun.misc Signal SignalHandler]
   [org.newsclub.net.unix AFUNIXServerSocket AFUNIXSocketAddress])
  (:require
   [clojure.java.io :as io]
   [clojure.core.async :as async :refer [<! >! <!! >!! to-chan! pipeline chan]]
   [clojure.data.json :as json]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report
            logf tracef debugf infof warnf errorf fatalf reportf
            spy get-env]]
   ; [taoensso.tufte :as tufte :refer [defnp p profiled profile]]
   
   [geckocomplete.macros :refer [->hash cond-xlet]]
   [geckocomplete.complete :as complete])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(set! *warn-on-reflection* true)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

; (defonce _0 (tufte/add-basic-println-handler!
;              {:format-pstats-opts {:columns [:n-calls :p50 :mean :clock :total]
;                                    :format-id-fn name}}))
(def ^String socket-filename "geckocomplete.sock")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn read-n-bytes [^java.io.InputStream input-stream
                    n]
  (.readNBytes input-stream n))

(defn unmerge-words [global-words words]
  (letfn [(rf [m word]
            (cond-xlet
             (not (contains? m word)) m
             :let [new-count (dec (get m word))]
             (<= new-count 0) (dissoc m word)
             :else (assoc m word new-count)))]
    (reduce rf global-words words)))

(defn merge-words [global-words words]
  (letfn [(rf [m word]
            (cond-xlet
             (not (contains? m word)) (assoc m word 1)
             :let [new-count (inc (get m word))]
             :else (assoc m word new-count)))]
    (reduce rf global-words words)))

(defn create-word-set [{:as state :keys [words]}]
  (assoc state :word-set (-> words keys set)))

(defn delete-buffer [state buffer-id]
  (cond-xlet
   :let [buf (get-in state [:buffers buffer-id])]
   (not buf) state
   :let [{:keys [words]} buf]
   :return (-> state
               (update :buffers dissoc buffer-id)
               (update :words unmerge-words words)
               (create-word-set))))

(def is-word-char?
  (memoize
   (fn [ch]
     (re-matches #"\p{L}" (str ch)))))

(defn merge-buffer [state {:keys [buffer-id buffer-text iskeyword-ords buffer-path]}]
  (letfn [(push-char [{:as m :keys [word-buf]} ch]
            (cond-xlet
             :let [ord (int ch)]
             (contains? iskeyword-ords ord)
             (update m :word-buf conj ch)
             (is-word-char? ch)
             (update m :word-buf conj ch)
             (< 0 (count word-buf))
             (-> m
                 (update :words conj (apply str word-buf))
                 (assoc :word-buf []))
             :else m))
          (flush-buf [{:keys [words word-buf]}]
            (if (< 0 (count word-buf))
              (conj words (apply str word-buf))
              words))]
    (let [m (reduce push-char {:word-buf [] :words #{}} buffer-text)
          words (flush-buf m)]
      ; (debug iskeyword-ords)
      (-> state
          (delete-buffer buffer-id)
          (assoc-in [:buffers buffer-id] (->hash buffer-id  buffer-path buffer-text
                                                 iskeyword-ords words))
          (update :words merge-words words)
          (create-word-set)))))

(defn get-buffer-text [in-stream
                       {:as op-args :keys [num-bytes buffer-path read-from-disk]}]
  (debug "attempting to read buffer text inline: " num-bytes)
  (cond
   read-from-disk (slurp buffer-path :encoding "UTF-8")
   (< 0 num-bytes) (let [^bytes bites (read-n-bytes in-stream num-bytes)]
                     (debugf "read %d bytes" (count bites))
                     (new String bites StandardCharsets/UTF_8))
   :else ""))

(defn handle-merge-buffer-cmd [state in-stream op-args]
  (let [buffer-text (get-buffer-text in-stream op-args)
        op-args (-> (assoc op-args :buffer-text buffer-text)
                    (update :iskeyword-ords set))
        {:keys [buffer-id buffer-path]} op-args]
    (assert (number? buffer-id))
    (debug "merge-buffer" buffer-id buffer-path)
    (send state merge-buffer op-args)))

(defn handle-connection [^java.net.Socket sock]
  (let [in-stream (.getInputStream sock)
        out-stream (.getOutputStream sock)
        out-writer (OutputStreamWriter. out-stream "UTF-8")
        read-next-cmd
        (fn []
          (let [n (-> (read-n-bytes in-stream 4)
                      (ByteBuffer/wrap)
                      (.getInt))
                ^bytes msg-as-bytes (read-n-bytes in-stream n)
                cmd-str (new String msg-as-bytes StandardCharsets/UTF_8)]
            (debug cmd-str)
            (json/read-str cmd-str :key-fn keyword)))
        read-next-cmd-or-exit (fn []
                                (try
                                 (read-next-cmd)
                                 (catch Exception e
                                   ["exit" ""])))
        write-json (fn [obj]
                     (let [s (json/write-str obj)
                           bites (.getBytes s "UTF-8")
                           n (count bites)
                           n-as-bites (-> (ByteBuffer/allocate 4)
                                          (.putInt n)
                                          (.array))]
                       (.write out-stream n-as-bites)
                       (.write out-stream bites)
                       (.flush out-writer)))
        state (agent {:buffers {}
                      :words {}})]
    (debug "accepted a connection")
    (try
     (loop [[op op-args] (read-next-cmd-or-exit)]
       (case op
         "quit" (debug "quit")
         "exit" (debug "exit")
         "delete-buffer"
         (let [buffer-id op-args]
           (assert (number? buffer-id))
           (debug "delete-buffer" buffer-id)
           (send state delete-buffer buffer-id))
         "merge-buffer"
         (handle-merge-buffer-cmd state in-stream op-args)
         "complete"
         (let [completions (complete/complete @state op-args)]
           (write-json completions))
         (errorf "UNKNOWN CMD: %s %s" (pr-str op) (pr-str op-args)))
       (when-not (contains? #{"quit" "exit"} op)
         (recur (read-next-cmd-or-exit))))
     (catch java.io.EOFException e
       (error "EOF received")))
    (debug "closing connection")
    (.close sock)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn server-loop []
  (timbre/merge-config! {:min-level :warn})
  (debug "deleting existing socket file:" socket-filename)
  (io/delete-file socket-filename true)

  (let [socket-file (File. socket-filename)
        server (AFUNIXServerSocket/newInstance)
        addr (AFUNIXSocketAddress. socket-file)]
    (.bind server addr)
    (debug "server bind")
    (while (not (Thread/interrupted))
      (let [sock (.accept server)]
        (-> (fn []
              (handle-connection sock))
            (Thread.)
            (.start))))
    (print server)))
(comment (server-loop))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -main
  [& args]
  (when-not (.exists (io/file socket-filename))
    (.. Runtime
        (getRuntime)
        (addShutdownHook (new Thread (fn [] (io/delete-file socket-filename true)))))
    (server-loop)))
