import java.util.Properties
import org.kie.jenkins.MavenCommand

def runMaven(String goals, List options = [], Properties properties = null, String logFileName = null) {
    new MavenCommand(this)
            .withOptions(options)
            .withProperties(properties)
            .withLogFileName(logFileName)
            .run(goals)
}

def runMaven(String goals, boolean skipTests, List options = [], String logFileName = null) {
    new MavenCommand(this)
            .withOptions(options)
            .skipTests(skipTests)
            .withLogFileName(logFileName)
            .run(goals)
}

def runMavenWithSettings(String settingsXmlId, String goals, Properties properties, String logFileName = null) {
    configFileProvider([configFile(fileId: settingsXmlId, variable: 'MAVEN_SETTINGS_XML')]) {
        new MavenCommand(this, ['-fae'])
                .withSettingsXmlFile(MAVEN_SETTINGS_XML)
                .withProperties(properties)
                .withLogFileName(logFileName)
                .run(goals)
    }
}

def runMavenWithSettings(String settingsXmlId, String goals, boolean skipTests, String logFileName = null) {
    configFileProvider([configFile(fileId: settingsXmlId, variable: 'MAVEN_SETTINGS_XML')]) {
        new MavenCommand(this, ['-fae'])
                .withSettingsXmlFile(MAVEN_SETTINGS_XML)
                .skipTests(skipTests)
                .withLogFileName(logFileName)
                .run(goals)
    }
}

/**
 *
 * @param settingsXmlId settings.xml file
 * @param goals maven gals
 * @param sonarCloudId Jenkins token for SonarCloud*
 */
def runMavenWithSettingsSonar(String settingsXmlId, String goals, String sonarCloudId, String logFileName = null) {
    configFileProvider([configFile(fileId: settingsXmlId, variable: 'MAVEN_SETTINGS_XML')]) {
        withCredentials([string(credentialsId: sonarCloudId, variable: 'TOKEN')]) {
            new MavenCommand(this)
                    .withSettingsXmlFile(MAVEN_SETTINGS_XML)
                    .withProperty('sonar.login', "${TOKEN}")
                    .withLogFileName(logFileName)
                    .run(goals)
        }
    }
}

def mvnVersionsSet(String newVersion, boolean allowSnapshots = false) {
    mvnVersionsSet(new MavenCommand(this), newVersion, allowSnapshots)
}

def mvnVersionsSet(MavenCommand mvnCmd, String newVersion, boolean allowSnapshots = false) {
    mvnCmd.clone()
            .withOptions(['-N', '-e'])
            .withProperty('full')
            .withProperty('newVersion', newVersion)
            .withProperty('allowSnapshots', allowSnapshots)
            .withProperty('generateBackupPoms', false)
            .run('versions:set')
}

def mvnVersionsUpdateParent(String newVersion, boolean allowSnapshots = false) {
    mvnVersionsUpdateParent(new MavenCommand(this), newVersion, allowSnapshots)
}

def mvnVersionsUpdateParent(MavenCommand mvnCmd, String newVersion, boolean allowSnapshots = false) {
    mvnCmd.clone()
            .withOptions(['-N', '-e'])
            .withProperty('full')
            .withProperty('parentVersion', "[${newVersion}]")
            .withProperty('allowSnapshots', allowSnapshots)
            .withProperty('generateBackupPoms', false)
            .run('versions:update-parent')
}

def mvnVersionsUpdateChildModules(boolean allowSnapshots = false) {
    mvnVersionsUpdateChildModules(new MavenCommand(this), allowSnapshots)
}

def mvnVersionsUpdateChildModules(MavenCommand mvnCmd, boolean allowSnapshots = false) {
    mvnCmd.clone()
            .withOptions(['-N', '-e'])
            .withProperty('full')
            .withProperty('allowSnapshots', allowSnapshots)
            .withProperty('generateBackupPoms', false)
            .run('versions:update-child-modules')
}

def mvnVersionsUpdateParentAndChildModules(String newVersion, boolean allowSnapshots = false) {
    mvnVersionsUpdateParentAndChildModules(new MavenCommand(this), newVersion, allowSnapshots)
}

def mvnVersionsUpdateParentAndChildModules(MavenCommand mvnCmd, String newVersion, boolean allowSnapshots = false) {
    mvnVersionsUpdateParent(mvnCmd, newVersion, allowSnapshots)
    mvnVersionsUpdateChildModules(mvnCmd, allowSnapshots)
}

def mvnGetVersionProperty(String property, String pomFile = 'pom.xml') {
    mvnGetVersionProperty(new MavenCommand(this), property, pomFile)
}

def mvnGetVersionProperty(MavenCommand mvnCmd, String property, String pomFile = 'pom.xml') {
    mvnCmd.clone()
            .withOptions(['-q', '-f', "${pomFile}"])
            .withProperty('expression', property)
            .withProperty('forceStdout')
            .returnOutput()
            .run('help:evaluate')
            .trim()
}

def mvnSetVersionProperty(String property, String newVersion) {
    mvnSetVersionProperty(new MavenCommand(this), property, newVersion)
}

def mvnSetVersionProperty(MavenCommand mvnCmd, String property, String newVersion) {
    mvnCmd.clone()
            .withOptions(['-e'])
            .withProperty('property', property)
            .withProperty('newVersion', newVersion)
            .withProperty('allowSnapshots', true)
            .withProperty('generateBackupPoms', false)
            .run('versions:set-property')
}

def mvnCompareDependencies(String remotePom, String project = '', boolean updateDependencies = false, boolean updatePropertyVersions = false) {
    mvnCompareDependencies(new MavenCommand(this), remotePom, project, updateDependencies, updatePropertyVersions)
}

def mvnCompareDependencies(MavenCommand mvnCmd, String remotePom, String project = '', boolean updateDependencies = false, boolean updatePropertyVersions=false) {
    def newMvnCmd = mvnCmd.clone()
        .withProperty('remotePom', remotePom)
        .withProperty('updatePropertyVersions', updatePropertyVersions)
        .withProperty('updateDependencies', updateDependencies)
        .withProperty('generateBackupPoms', false)
    
    if(project) {
        newMvnCmd.withOptions(["-pl ${project}"])
    }

    newMvnCmd.run('versions:compare-dependencies')
}

def uploadLocalArtifacts(String mvnUploadCredsId, String artifactDir, String repoUrl) {
    def zipFileName = 'artifacts'
    withCredentials([usernameColonPassword(credentialsId: mvnUploadCredsId, variable: 'kieUnpack')]) {
        dir(artifactDir) {
            sh "zip -r ${zipFileName} ."
            sh "curl --silent --upload-file ${zipFileName}.zip -u ${kieUnpack} -v ${repoUrl}"
        }
    }
}

def getLatestArtifactVersionFromRepository(String repositoryUrl, String groupId, String artifactId) {
    return getMavenMetadata(repositoryUrl, groupId, artifactId).versioning?.latest?.text()
}

def getLatestArtifactVersionPrefixFromRepository(String repositoryUrl, String groupId, String artifactId, String versionPrefix) {
    return getMavenMetadata(repositoryUrl, groupId, artifactId).versioning?.versions?.childNodes().collect{ it.text() }.findAll{ it.startsWith(versionPrefix) }.max()
}

def getMavenMetadata(String repositoryUrl, String groupId, String artifactId) {
    def groupIdArtifactId = "${groupId.replaceAll("\\.", "/")}/${artifactId}"
    return new XmlSlurper().parse("${repositoryUrl}/${groupIdArtifactId}/maven-metadata.xml")
}

String getProjectPomFromBuildCmd(String buildCmd) {
    def pom = "pom.xml"
    def fileOption = "-f"

    def projectPom = "pom.xml"
    regexF = "-f[ =]"
    regexFile = "--file[ =]"
    if (buildCmd =~ regexF || buildCmd =~ regexFile) {
        projectPom = buildCmd.substring(buildCmd.indexOf(fileOption), buildCmd.indexOf(pom) + pom.length())
        projectPom = projectPom.split("=| ")[1]
    }
    return projectPom;
}

/*
* Clean Maven repository on the node
*/
void cleanRepository() {
    sh 'rm -rf $HOME/.m2/repository'
}