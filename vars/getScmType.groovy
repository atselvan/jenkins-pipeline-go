import hudson.plugins.git.GitSCM
import hudson.scm.SubversionSCM

def call(def scm) {

    if (scm.getClass() == GitSCM) {
        return 'git'
    } else if (scm.getClass() == SubversionSCM) {
        return 'svn'
    }
    return 'null'
}