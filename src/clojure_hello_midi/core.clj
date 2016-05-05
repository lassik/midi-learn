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
            [clojure.core.match :refer [match]]))

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

(def our-receiver
  (reify javax.sound.midi.Receiver
    (close [this]
      (println "Receiver closed"))
    (send [this msg timestamp]
      (try
        (handle-midi-message
         ;; TODO: Should we mask out MIDI channel from status?
         (.getStatus msg)
         ;; TODO: Is it safe to call these for all messages?
         (.getData1 msg) (.getData2 msg))
        (catch Exception e
          ;; We need to have this exception handler here. Otherwise
          ;; exceptions thrown in this method (in case there's a bug
          ;; in the above code) are muffled, execution of the method
          ;; stops and the program continues. This probably happens
          ;; because the method is run in a different thread from the
          ;; main thread, and apparently "In the JVM, when an
          ;; exception is thrown on a thread other than the main
          ;; thread, and nothing is there to catch it, nothing
          ;; happens. The thread dies silently." See:
          ;; https://stuartsierra.com/2015/05/27/clojure-uncaught-exceptions
          (println (str "exception: " (.getMessage e))))))))

(defn -main []
  (println "Hello MIDI!")
  (println "Getting default transmitter")
  (let [transmitter (javax.sound.midi.MidiSystem/getTransmitter)]
    (println "Default transmitter =" transmitter)
    (println "Plugging our receiver into"
             "the default transmitter and listening")
    (.setReceiver transmitter our-receiver)))
