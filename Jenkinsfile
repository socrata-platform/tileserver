@Library('socrata-pipeline-library')

String service = 'tileserver'
String project_wd = '.'
boolean isPr = env.CHANGE_ID != null
boolean lastStage

// Utility Libraries
def sbtbuild = new com.socrata.SBTBuild(steps, service, project_wd)
def dockerize = new com.socrata.Dockerize(steps, service, BUILD_NUMBER)
def releaseTag = new com.socrata.ReleaseTag(steps, service)

pipeline {
  options {
    ansiColor('xterm')
    disableConcurrentBuilds(abortPrevious: true)
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 20, unit: 'MINUTES')
  }
  parameters {
    string(name: 'AGENT', defaultValue: 'build-worker', description: 'Which build agent to use?')
    string(name: 'BRANCH_SPECIFIER', defaultValue: 'origin/main', description: 'Use this branch for building the artifact.')
    booleanParam(name: 'RELEASE_BUILD', defaultValue: false, description: 'Are we building a release candidate?')
    booleanParam(name: 'RELEASE_DRY_RUN', defaultValue: false, description: 'To test out the release build without creating a new tag.')
    string(name: 'RELEASE_NAME', defaultValue: '', description: 'For release builds, the release name which is used for the git tag and the deploy tag.')
  }
  agent {
    label params.AGENT
  }
  environment {
    WEBHOOK_ID = 'WEBHOOK_IQ'
    SCALA_VERSION = '2.12'
  }
  stages {
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
          if (params.RELEASE_BUILD) {
            env.DOCKER_TAG = dockerize.dockerBuildWithSpecificTag(
              tag: params.RELEASE_NAME,
              path: sbtbuild.getDockerPath(),
              artifacts: [sbtbuild.getDockerArtifact()]
            )
          } else {
            env.DOCKER_TAG = dockerize.dockerBuildWithDefaultTag(
              version: 'STAGING',
              sha: env.GIT_COMMIT,
              path: sbtbuild.getDockerPath(),
              artifacts: [sbtbuild.getDockerArtifact()]
            )
          }
        }
      }
      post {
        success {
          script {
            if (params.RELEASE_BUILD) {
              env.GIT_TAG = releaseTag.getFormattedTag(params.RELEASE_NAME)
              if (releaseTag.doesReleaseTagExist(params.RELEASE_NAME)) {
                echo "REBUILD: Tag ${env.GIT_TAG} already exists"
                return
              }
              if (params.RELEASE_DRY_RUN) {
                echo "DRY RUN: Would have created ${env.GIT_TAG} and pushed it to the repo"
                currentBuild.description = "${service}:${params.RELEASE_NAME} - DRY RUN"
                return
              }
              releaseTag.create(params.RELEASE_NAME)
            }
          }
        }
      }
    }
    stage('Publish') {
      when {
        not { expression { isPr } }
        not { expression { return params.RELEASE_BUILD && params.RELEASE_DRY_RUN } }
      }
      steps {
        script {
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
        not { expression { return params.RELEASE_BUILD && params.RELEASE_DRY_RUN } }
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
        if (env.JOB_NAME.contains("${service}/main")) {
          teamsMessage(
            message: "Build [${currentBuild.fullDisplayName}](${env.BUILD_URL}) has failed in stage ${lastStage}",
            webhookCredentialID: WEBHOOK_ID
          )
        }
      }
    }
  }
}
