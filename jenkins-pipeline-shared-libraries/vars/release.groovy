def gpgImportKeyFromFileWithPassword(String gpgKeyCredentialsId, String gpgKeyPasswordCredentialsId) {
    withCredentials([file(credentialsId: gpgKeyCredentialsId, variable: 'SIGNING_KEY')]) {
        withCredentials([string(credentialsId: gpgKeyPasswordCredentialsId, variable: 'SIGNING_KEY_PASSWORD')]) {
            // copy the key to singkey.gpg file in *plain text* so we can import it
            sh """
                cat $SIGNING_KEY > $WORKSPACE/signkey.gpg
                # Please do not remove list keys command. When gpg is run for the first time, it may initialize some internals.
                gpg --list-keys
                gpg --batch --pinentry-mode=loopback --passphrase \"${SIGNING_KEY_PASSWORD}\" --import signkey.gpg
                rm $WORKSPACE/signkey.gpg
            """
        }
    }
}

def gpgImportKeyFromStringWithoutPassword(String gpgKeyCredentialsId) {
    withCredentials([string(credentialsId: gpgKeyCredentialsId, variable: 'SIGNING_KEY')]) {
        // copy the key to singkey.gpg file in *plain text* so we can import it
        sh """
            echo "$SIGNING_KEY" > $WORKSPACE/signkey.gpg
            # Please do not remove list keys command. When gpg is run for the first time, it may initialize some internals.
            gpg --list-keys
            gpg --batch --pinentry-mode=loopback --import signkey.gpg
            rm $WORKSPACE/signkey.gpg
        """
    }
}

def gpgSignFileDetachedSignatureWithPassword(String file, String signatureTarget, String gpgKeyPasswordCredentialsId) {
    withCredentials([string(credentialsId: gpgKeyPasswordCredentialsId, variable: 'SIGNING_KEY_PASSWORD')]) {
        sh "gpg --batch --sign --pinentry-mode=loopback --passphrase \"${SIGNING_KEY_PASSWORD}\" --output ${signatureTarget} --detach-sig ${file}"
    }
}

def gpgSignFileDetachedSignatureWithoutPassword(String file, String signatureTarget) {
    sh "gpg --batch --sign --pinentry-mode=loopback --output ${signatureTarget} --detach-sig ${file}"
}

boolean gpgIsValidDetachedSignature(String file, String signature) {
    return sh(returnStatus: true, script: "gpg --batch --verify ${signature} ${file}") == 0
}

def svnUploadFileToRepository(String svnRepository, String svnCredentialsId, String releaseVersion, String... files) {
    withCredentials([usernamePassword(credentialsId: svnCredentialsId, usernameVariable: 'ASF_USERNAME', passwordVariable: 'ASF_PASSWORD')]) {
        sh "svn co --depth=empty ${svnRepository} svn-kie"
        for (file in files) {
            sh "cp ${file} svn-kie/${releaseVersion}/"
        }
        sh """
        svn add "svn-kie/${releaseVersion}"
        cd svn-kie
        svn ci --non-interactive --no-auth-cache --username ${ASF_USERNAME} --password '${ASF_PASSWORD}' -m "Apache KIE ${releaseVersion} artifacts"
        rm -rf svn-kie
        """
    }
}
