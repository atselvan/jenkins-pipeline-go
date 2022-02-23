package com.privatesquare.pipeline.utils

/**
 * Class for Git steps.
 */
class Git implements Serializable {

    def steps
    Git(steps) {this.steps = steps}

    /**
     * retrieve git repo URL.
     */
    String getUrl() {
        String gitUrl
        steps.sh 'git ls-remote --get-url origin > GIT_URL'
        gitUrl = steps.readFile('GIT_URL').trim()
        return gitUrl
    }

    void setGitConfig(){
        shell("git config user.name [this]")
        shell("git config user.email [this]")
    }

    String shellWithResponse(String command){
        String response
        steps.sh "${command} > output"
        response = steps.readFile('output').trim()
        return response
    }

    void shell(String command){
        steps.sh "${command}"
    }

    /**
     * Create the next tag in Git.
     * (e.g. if version is 1.2 and no tags exists: v1.2.0, if 1.2.2 exists, we get: v1.2.3)
     */
    String createNextTag(String currentVersion, String builderCredentialsId) {
        setGitConfig()
        String newVersion = createNextTagVersion(currentVersion)
        String newTag = "v${newVersion}"
        createTag(newTag)
        pushTagToRepo(newTag, builderCredentialsId)
        return newTag
    }

    /**
     * Create a tag with the provided tagName.
     * @param tagName the name for the tag
     */
    void createTag(String tagName) {
        assert tagName: 'tagName is a required paramter for creating a tag'
        def createTagCommand = "git tag -a ${tagName} -m \"Jenkins created version ${tagName}\""
        shell(createTagCommand)
    }

    /**
     * Create the next Tag version.
     * Based on the current version and the tags currently in Git, derived the new incremental semantic version.
     * Where the increment will be on the patch segment.
     *
     (e.g. if version is 1.2 and no tags exists: 1.2.0, if 1.2.2 exists, we get: 1.2.3)
     */
    String createNextTagVersion(String currentVersion) {
        def gitTags = retrieveGitTagsForVersion("v${currentVersion}.*")
        def tagsArray = gitTags.split('\n')
        return getNewVersion(tagsArray, currentVersion)
    }

    String retrieveGitTagsForVersion(String versionMask) {
        String command = "git tag -l \"${versionMask}\""
        return shellWithResponse(command)
    }

    String getNewVersion(def listOfExistingVersions, String currentVersion) {
        assert listOfExistingVersions: "listOfExistingVersions is a required parameter to get a new version"
        assert currentVersion: "currentVersion is a required parameter to get a new version"

        List<Integer> filteredVersions = new ArrayList<Integer>();
        for (int i =0; i < listOfExistingVersions.size(); i++) {
            String raw = listOfExistingVersions[i]
            def versionElements = raw.split('\\.')
            if (versionElements.size() > 2) {
                def patchRaw = versionElements[2] // major.minor.patch -> v1.4.*
                if (patchRaw.matches('\\d+')) {
                    filteredVersions.add(new Integer(patchRaw))
                } else if (patchRaw.contains('-')) {
                    int index = patchRaw.indexOf('-')
                    def patch = patchRaw.substring(0, index)
                    filteredVersions.add(new Integer(patch))
                }
            }
        }
        Collections.sort(filteredVersions)
        int patchPrevious = 0
        int patchNext = patchPrevious
        if (filteredVersions.isEmpty()) {
            steps.echo "We found no existing tag, so version will be .0"
        } else {
            patchPrevious = filteredVersions.get(filteredVersions.size() - 1)
            patchNext = patchPrevious + 1
            steps.echo "Max previous patch version found was ${patchPrevious}"
        }
        return "${currentVersion}.${patchNext}"
    }

    /**
     * Push the given tag to the current remote used.
     */
    void pushTagToRepo(String tagName, String credentialsId) {

        steps.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId, passwordVariable: 'pss', usernameVariable: 'usr']]) {
            String originRepo = getUrl()
            String repo = originRepo.replace('https://', " https://${steps.env.usr}:${steps.env.pss}@")
            def gitAddRemoteCommand = "git remote add bbTags ${repo}"
            def gitPushCommand = "git push bbTags ${tagName}"

            shell(gitAddRemoteCommand)
            shell(gitPushCommand)
        }
    }
}