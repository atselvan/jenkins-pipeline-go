def call() {
    // Solution from Cloudbees Support: https://support.cloudbees.com/hc/en-us/articles/230610987-Pipeline-How-to-print-out-env-variables-available-in-a-build
    echo "buildVariables=${currentBuild.buildVariables}"
    if (isUnix()) {
        sh 'env > env.txt'
        for (String i : readFile('env.txt').split("\r?\n")) {
            println i
        }
    } else {
        bat 'set > env.txt'
        for (String i : readFile('env.txt').split("\r?\n")) {
            println i
        }
    }
}