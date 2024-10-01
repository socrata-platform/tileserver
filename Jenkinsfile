@Library('socrata-pipeline-library')

String service = 'tileserver'
String project_wd = '.'
boolean isPr = env.CHANGE_ID != null
String lastStage

// Utility Libraries
def sbtbuild = new com.socrata.SBTBuild(steps, service, project_wd)
def dockerize = new com.socrata.Dockerize(steps, service, BUILD_NUMBER)
def semVerTag = new com.socrata.SemVerTag(steps)

pipeline {
  options {
    ansiColor('xterm')
    disableConcurrentBuilds(abortPrevious: true)
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 20, unit: 'MINUTES')
  }
  parameters {
    string(name: 'AGENT', defaultValue: 'build-worker', description: 'Which build agent to use')
    string(name: 'BRANCH_SPECIFIER', defaultValue: 'origin/main', description: 'Use this branch for building the artifact.')
    booleanParam(name: 'RELEASE_BUILD', defaultValue: false, description: 'Are we building a release candidate?')
  }
  agent {
    label params.AGENT
  }
  environment {
    WEBHOOK_ID = 'WORKFLOW_IQ'
    SCALA_VERSION = '2.12'
  }
  stages {
    stage('Checkout Release Tag') {
      when {
        expression { return params.RELEASE_BUILD }
      }
      steps {
        script {
          lastStage = env.STAGE_NAME
          semVerTag.checkoutClosestTag()
        }
      }
    }
    stage('Build') {
      steps {
        script {
          lastStage = env.STAGE_NAME
          sbtbuild.setScalaVersion(env.SCALA_VERSION)
          sbtbuild.build()
        }
      }
    }
    stage('Docker Build') {
      when {
        not { expression { isPr } }
      }
      steps {
        script {
          lastStage = env.STAGE_NAME
          env.DOCKER_TAG = dockerize.dockerBuildWithDefaultTag(
            version: sbtbuild.getServiceVersion(),
            sha: env.GIT_COMMIT,
            path: sbtbuild.getDockerPath(),
            artifacts: [sbtbuild.getDockerArtifact()]
          )
        }
      }
    }
    stage('Publish') {
      when {
        not { expression { isPr } }
      }
      steps {
        script {
          lastStage = env.STAGE_NAME
          if (params.RELEASE_BUILD) {
            env.BUILD_ID = dockerize.publish(sourceTag: env.DOCKER_TAG)
          } else {
            env.BUILD_ID = dockerize.publish(
              sourceTag: env.DOCKER_TAG,
              environments: ['internal']
            )
          }
          currentBuild.description = env.BUILD_ID
        }
      }
    }
    stage('Deploy') {
      when {
        not { expression { isPr } }
      }
      steps {
        script {
          lastStage = env.STAGE_NAME
          String environment = (params.RELEASE_BUILD) ? 'rc' : 'staging'
          marathonDeploy(
            serviceName: service,
            tag: env.BUILD_ID,
            environment: environment
          )
        }
      }
    }
  }
  post {
    failure {
      script {
        boolean buildingMain = env.JOB_NAME == "${service}/main"
        if (buildingMain || params.RELEASE_BUILD) {
          teamsWorkflowMessage(
            message: "[${currentBuild.fullDisplayName}](${env.BUILD_URL}) has failed in stage ${lastStage}",
            workflowCredentialID: WEBHOOK_ID
          )
        }
      }
    }
  }
}
