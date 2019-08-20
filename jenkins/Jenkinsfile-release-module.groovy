// Pipeline for deploying artifacts for npm

pipeline {
    agent any

    stages {
        stage("Deploy") {
            environment {
                VERSION = sh(returnStdout: true, script: "git rev-parse HEAD").trim()
            }

            steps {
                withCredentials([string(credentialsId: 'jenkins-artifactory-password', variable: 'PASSWORD')]) {
                    sh "apt-get update && apt-get install maven -y"
                    sh "mvn deploy -Dartifactory_username=jenkins -Dartifactory_password=$PASSWORD -s settings.xml"
                }

                // Post to #eng-release when modules are released
                slackSend(
                        channel: "eng-release",
                        message: ":rocket: Released *css-box* ${env.MODULE} with VERSION=${VERSION}, TAG_OR_SHA=${env.TAG_OR_SHA}." +
                                " See job ${env.BUILD_URL}")
            }
        }
    }
}
