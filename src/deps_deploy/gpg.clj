(ns deps-deploy.gpg
  (:import [java.lang Runtime]))

(defn gpg-program []
  (or (System/getenv "DEPS_DEPLOY_GPG") "gpg"))

(defn gpg [{:keys [args]}]
  (try
    (let [runtime (Runtime/getRuntime)
          process (.exec runtime #^"[Ljava.lang.String;" (into-array (into [(gpg-program)] args)))]
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. (fn [] (.destroy process))))
      (with-open [out (.getInputStream process)
                  err-output (.getErrorStream process)
                  in (.getOutputStream process)]
        (let [exit-code (.waitFor process)]
          {:exit-code exit-code
           :args (rest args)
           :success? (or (zero? exit-code) nil)
           :out (slurp out)
           :err (slurp err-output)})))
    (catch Exception e
      {:success? nil
       :args (rest args)
       :err e})))

(defn gpg-available? []
  (->> {:args ["--version"]} gpg :success?))

(defn add-key [cmd key]
  (cond-> cmd
    key (update :args #(concat ["--default-key" key] %))))

(defn sign-args [cmd file]
  (assoc cmd :args ["--yes" "--armour" "--detach-sign" file]))

(defn sign-with-key! [key file]
  (let [result (-> {}
                   (sign-args file)
                   (add-key key)
                   gpg)]
    (if (:success? result)
      (str file ".asc")
      (throw (Exception. ^String (:err result))))))
