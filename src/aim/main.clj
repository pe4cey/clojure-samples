(ns aim.main (:gen-class) (:use clojure.contrib.zip-filter.xml)
  (:require   [clojure.zip :as zip]
              [clojure.xml :as xml]
              [clojure.contrib.zip-filter :as zf]
			  [clojure.java.io :as io]
			  [clojure.contrib.lazy-xml :as lxml]
              [clojure.contrib.duck-streams :as duck]
  ))

(defn parse-str [s] (zip/xml-zip (xml/parse (new org.xml.sax.InputSource (new java.io.StringReader s)))))

(def book [:book])
(def article [:article])
(def book-title [:book :title])

(defn locate [xml, locator] (apply xml-> xml locator))
(defn children2 [xml, locator] (locate xml (concat locator [zf/children])))
(defn children-count [xml, locator] (count (children2 xml locator)))

(defn exists? [xml, locator] (not-empty (locate xml locator)))
(defn not-exists? [xml, locator] (not (exists? xml locator)))

(defn has-markup? [xml, locator] (< 1 (children-count xml locator)))
(defn none-exists? [xml, locators] (every? (partial not-exists? xml) locators))

(defn document-is [other-type & condition] (if (every? identity condition) other-type :valid))

(defn only-supports [supported-types] (fn [xml] (document-is :unsupported (none-exists? xml supported-types))))
(defn mandatory [parent, locator] (fn [xml] (document-is :invalid (exists? xml parent) (not-exists? xml locator))))
(defn markup-in [parent, locator] (fn [xml] (document-is :unsupported (exists? xml parent) (has-markup? xml locator))))

(def all-criteria [
  (only-supports [book, article])
  (mandatory book book-title)
  (markup-in book book-title)
])

(defn is-valid [types] (let [value (some #{:invalid, :unsupported} types)] (if value value :valid)))

(defn document-type [xml] (is-valid (map (fn [criteria] (criteria xml)) all-criteria)))

(defn map-zip-entries [zip-file-path func]
  (with-open [zip (new java.util.zip.ZipFile zip-file-path)]
    (doall (map (partial func zip) (enumeration-seq (.entries zip))))))

(defn create-file [input-stream output-file]
  (do (duck/copy input-stream output-file) (.getCanonicalPath output-file)))

(defn create-new-dir [dir]
  (do
      (if (.exists dir)
      (doto dir (.delete) (.mkdirs))
      (doto dir (.mkdirs)))
      (.getCanonicalPath dir)))

(defn extract-all-files [new-location]
  (fn [zip-file entry]
    (let [new-file-location (new java.io.File new-location (.getName entry))]
      (if (.isDirectory entry)
        (create-new-dir new-file-location)
        (create-file (.getInputStream zip-file entry) new-file-location)))))

(defn unzip [zip-file-path extract-dir] (map-zip-entries zip-file-path (extract-all-files (create-new-dir (new java.io.File extract-dir)))))

(assert (= ["/private/tmp/aim/doc1.xml",
            "/private/tmp/aim/doc2.xml",
            "/private/tmp/aim/doc3.xml",
            "/private/tmp/aim/nest",
            "/private/tmp/aim/nest/doc4.xml"] (unzip "test-xml.zip" "/private/tmp/aim")))

(assert (= ["doc1.xml", "doc2.xml", "doc3.xml", "nest/", "nest/doc4.xml"] (map-zip-entries "test-xml.zip" #(.getName %2))))

(assert (= :valid (is-valid [])))
(assert (= :valid (is-valid [:valid, :valid])))
(assert (= :invalid (is-valid [:valid, :invalid])))
(assert (= :unsupported (is-valid [:valid, :unsupported])))

(assert (= :invalid (document-is :invalid true)))
(assert (= :valid (document-is :invalid true false)))
(assert (= :valid (document-is :invalid false)))

(def unsupported-content (parse-str "
  <publisher>
    <issue></issue>
  </publisher>
"))

(def valid-book (parse-str "
  <publisher>
    <book>
      <title>Some title</title>
    </book>
  </publisher>
"))

(def book-title-with-markup (parse-str "
  <publisher>
    <book>
      <title><a>Some</a> title</title>
    </book>
  </publisher>
"))

(def book-no-title (parse-str "
  <publisher>
    <book>
    </book>
  </publisher>
"))

(assert (= :unsupported ((only-supports [book, article]) unsupported-content)))
(assert (= :valid ((only-supports [book, article]) valid-book )))
(assert (= :valid ((mandatory book book-title) valid-book )))
(assert (= :valid ((mandatory book book-title) unsupported-content)))
(assert (= :invalid ((mandatory book book-title) book-no-title)))

(assert (= :valid (document-type valid-book)))
(assert (= :unsupported (document-type unsupported-content)))
(assert (= :unsupported (document-type book-title-with-markup)))

(defn xml-file? [file-path] (.endsWith file-path ".xml"))

(defn zip-stream [stream] (zip/xml-zip (xml/parse stream)))

; adds meta and into ml and returns { :file ... :type :valid }
(defn valid-import [xml file-name]
  (do (println "valid") :valid))

(defn invalid-import [xml file-name] (do (println "invalid") :invalid))
(defn unsupported-import [xml file-name] (do (println "unsupported") :unsupported))
(def actions {:valid valid-import :invalid invalid-import :unsupported unsupported-import})

(defn import-document [xml-file-path]
  (let [doc (zip-stream xml-file-path)]
   ((actions (document-type doc)) doc xml-file-path)))

(defn import-zip-file [zipFile working-dir]
  (let [xml-files (filter xml-file? (unzip zipFile working-dir))]
    (map (fn [file type] (zipmap [:file :type] [file type])) xml-files (map import-document xml-files))))

;(assert (= [
 ;           {:file "/private/tmp/aim/book-no-title.xml" :type :invalid},
  ;          {:file "/private/tmp/aim/book-with-markup.xml" :type :unsupported},
   ;         {:file "/private/tmp/aim/unsupported-document.xml" :type :unsupported},
    ;        {:file "/private/tmp/aim/valid-book.xml" :type :valid},
     ;       ] (import-zip-file "small-xml.zip" "/private/tmp/aim")))

;
;; returns a zipper with the meta stuff added
;(defn document-with-meta [doc])


;; takes in temporary xml document location
;; add meta data and import to ml
;(defn import-document [xml-file-location]
;; convert to xml/zipper
;	(let [xml (xml/xml-> (zip-stream (file xml-file-location))] ((actions (document-type xml)) xml)))
;
;

(defn zip-file? [file] (.. (.getCanonicalPath file) (endsWith ".zip")))

(defn all-files-in [path] (file-seq (new java.io.File path)))
(defn all-zip-files [files] (filter zip-file? files))

(defn import-directory [dir]
  (map (partial import-zip-file "/tmp/aim") (all-zip-files (all-files-in dir)))
)

(defn -main [& args]
  (import-zip-file "small-xml.zip", "/private/tmp/aim"))
