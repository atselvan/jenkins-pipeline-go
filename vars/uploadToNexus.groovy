
def call(final String repositoryId, final String groupId, final String artifactId,
         final String version, final String packaging, final String filePath) {

    nexusPublisher nexusInstanceId: 'privatesquare-nexus',
            nexusRepositoryId: repositoryId,
            packages: [[$class: 'MavenPackage',
                        mavenAssetList: [[classifier: '', extension: '', filePath: filePath]],
                        mavenCoordinate: [artifactId: artifactId, groupId: groupId, packaging: packaging, version: version]]]
}