(ns clojure-hello-midi.core
  "Show notes played from a MIDI keyboard in the console.

  Uses the Java MIDI API and the system default MIDI device. (In
  practice, the default device seems to be the first one that was
  plugged in and turned on).

  The system is not 'plug-and-play' sensitive so if you plug in or
  turn on a MIDI device you need to restart the app in order to be
  able to play the new device."
  (:gen-class)
  (:require [clojure.string :as string]
            [clojure.core.match :refer [match]]
            [net.tcp.server :as tcp]
            [clojure.pprint :as pp]))

;; To learn the Java MIDI API, start here:
;; https://docs.oracle.com/javase/tutorial/sound/overview-MIDI.html

(def note-names ["C" "C#" "D" "D#" "E" "F" "F#" "G" "G#" "A" "A#" "B"])

(def midi-middle-c 60)

(defn pitch-class [pitch] (mod (+ midi-middle-c pitch) 12))

(defn note-name [pitch] (get note-names (pitch-class pitch)))

(def pitches-on (atom (sorted-set)))

(defn notes-on []
  (map note-name @pitches-on))

(defn show-notes [notes]
  (println (if (empty? notes) "(none)" (string/join " " notes))))

(defn note-on [pitch]
  (swap! pitches-on conj pitch)
  (show-notes (notes-on))) 

(defn note-off [pitch]
  (swap! pitches-on disj pitch)
  (show-notes (notes-on)))

(defn handle-midi-message [status a b]
  (match [status a b]
    [0x80 pitch velocity] (note-off pitch)
    [0x90 pitch velocity] ((if (= 0 velocity) note-off note-on) pitch)
    :else (println "Got MIDI message" status)))

(defn bytes-from [input-stream]
  (lazy-seq
   (let [byte (.read input-stream)]
     (when-not (= -1 byte) (cons byte (bytes-from input-stream))))))

(defn midi-data-byte? [byte]
  (not (bit-test byte 7)))

(defn midi-messages-from [bytes]
  (lazy-seq
   (let [[_ bytes] (split-with midi-data-byte? bytes)]
     (when (seq bytes)
       (let [[[command] bytes] (split-at 1 bytes)]
         (let [[data bytes] (split-with midi-data-byte? bytes)]
           (let [msg (vec (cons command data))]
             (cons msg (midi-messages-from bytes)))))))))

(defn handler [input-stream output-stream]
  (println "Hello client!")
  (binding [pp/*print-base* 16]
    (doseq [msg (midi-messages-from (bytes-from input-stream))]
      (pp/pprint msg)
      (apply handle-midi-message msg)
      (flush))))

(defn -main []
  (let [server (tcp/tcp-server :host "0.0.0.0" :port 12345
                               :handler (tcp/wrap-streams handler))]
    (println "Starting TCP server")
    (tcp/start server)))
