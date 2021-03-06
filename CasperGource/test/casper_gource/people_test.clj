(ns casper-gource.people-test
  (:use [clojure.test] [casper-gource.people])
)

(deftest test-parsing-names
  (is (= #{"uday", "mark"} (commiters "Uday/Mark Some marklogic commit")))
  (is (= #{"uday", "mark"} (commiters "uday/Mark Some commit")))
  (is (= #{"uday", "micheal"} (commiters "uday/Mike Some commit")))
)
