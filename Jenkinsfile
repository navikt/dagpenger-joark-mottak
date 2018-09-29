pipeline {
  agent any

  environment {
    APPLICATION_NAME = 'dagpenger-joark-mottak'
    ZONE = 'fss'
    NAMESPACE = 'default'
    VERSION = sh(script: './gradlew -q printVersion', returnStdout: true).trim()
  }

  stages {
    stage('Install dependencies') {
      steps {
        sh "./gradlew assemble"
      }
    }

    stage('Build') {
      steps {
        sh "./gradlew check"
      }
    }

    stage('Publish') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'nexusUploader', usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD')]) {
            sh "docker login -u ${env.NEXUS_USERNAME} -p ${env.NEXUS_PASSWORD} repo.adeo.no:5443"
        }

        script {
          sh "./gradlew dockerPush"
        }
      }
    }

    stage("Publish service contract") {
      steps {
        withCredentials([usernamePassword(credentialsId: 'repo.adeo.no', usernameVariable: 'REPO_CREDENTIAL_USR', passwordVariable: 'REPO_CREDENTIAL_PSW')]) {
          sh "curl -vvv --user ${REPO_CREDENTIAL_USR}:${REPO_CREDENTIAL_PSW} --upload-file nais.yaml https://repo.adeo.no/repository/raw/nais/${APPLICATION_NAME}/${VERSION}/nais.yaml"
        }
      }
    }

    stage('Deploy to non-production') {
      steps {
        script {
          response = naisDeploy.createNaisAutodeployment(env.APPLICATION_NAME, env.VERSION,"t0",env.ZONE ,env.NAMESPACE, "")
        }
      }
    }

    stage('Deploy to production') {
      steps {
        script {
          response = naisDeploy.createNaisAutodeployment(env.APPLICATION_NAME, env.VERSION,"p", env.ZONE ,env.NAMESPACE, "")
        }
      }
    }
  }
}
